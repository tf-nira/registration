# Anonymous profile scheduler: verification and rollout runbook

Applies to the `anonymous-profile-5m-per-day` branch (changes to `AnonymousProfileScheduler`,
`RegistrationRepositary`, `AnonymousProfileServiceImpl`). All SQL below is PostgreSQL and uses the
`regprc` schema. Run the staging checks in section 2 on a **non-production** database only.

The unit tests mock the database, so sections 2 and 3 are what actually prove the change works.

## 1. Read-only checks on prod (before anything is deployed)

```sql
-- 1.1 The flag column exists (it is not in the DDL scripts of this repo)
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'regprc' AND table_name = 'registration'
  AND column_name IN ('is_anonymous_profile_added', 'reference_id', 'source',
                      'pause_rule_ids', 'last_success_stage_name');
-- expect all five rows

-- 1.2 Chunks hold row locks and a connection while packets are read (seconds).
--     0 means no limit; a small value (for example 60000 ms) could kill chunks.
SHOW idle_in_transaction_session_timeout;

-- 1.3 Size of the current backlog
SELECT count(*) AS pending
FROM regprc.registration
WHERE upd_dtimes < CURRENT_DATE AND is_anonymous_profile_added IS NOT TRUE;

-- 1.4 Existing indexes on registration (look for anything on the flag or upd_dtimes)
SELECT indexname, indexdef FROM pg_indexes
WHERE schemaname = 'regprc' AND tablename = 'registration';

-- 1.5 Row size of anonymous_profile (for storage and WAL planning)
SELECT count(*) AS rows,
       pg_size_pretty(pg_total_relation_size('regprc.anonymous_profile')) AS total_size,
       avg(length(profile)) AS avg_profile_chars
FROM regprc.anonymous_profile;
```

Also note the current values of these properties on prod (config server):
`mosip.anonymous.profile.scheduler.fetchsize`, `.threads.count`, `.cron.expression`,
`mosip.anonymous.profile.bioInfo.required`, the Hikari pool size, and how many notification-service
pods run.

## 1a. Optional date range

The scheduler can be limited to packets created between two dates (`registration.cr_dtimes`), on top
of the existing `upd_dtimes < CURRENT_DATE` cutoff:

```properties
# both optional, format yyyy-MM-dd; empty = no limit on that side
mosip.anonymous.profile.scheduler.from.date=2026-11-01
mosip.anonymous.profile.scheduler.to.date=2026-11-10
```

- The end date is inclusive: `to.date=2026-11-10` includes everything created up to
  `2026-11-10 23:59:59`.
- `cr_dtimes` is compared as stored. MOSIP writes these as UTC wall-clock time, so the days are UTC days.
- A date that cannot be parsed, or `from.date` after `to.date`, makes the run log an error and do nothing.
  It never falls back to processing everything.
- To see how many packets a range covers:

```sql
SELECT count(*) FROM regprc.registration
WHERE upd_dtimes < CURRENT_DATE AND is_anonymous_profile_added IS NOT TRUE
  AND cr_dtimes >= TIMESTAMP '2026-11-01 00:00:00'
  AND cr_dtimes <  TIMESTAMP '2026-11-11 00:00:00';   -- end date + 1 day
```

- The partial index is ordered by `upd_dtimes`, so a narrow range inside a large backlog makes the
  database scan and filter. If `EXPLAIN` shows that, create the optional second index described in
  `anonymous_profile_pending_index.sql`.

## 2. Staging verification

Use a copy of production data if possible, mid-day (the eligibility cut-off is `CURRENT_DATE`, so do
not run across midnight). Deploy the branch on **two or more pods**.

### 2.1 Create the index and confirm it is used

Run `db_upgrade_scripts/mosip_regprc/sql/anonymous_profile_pending_index.sql` with psql, then:

```sql
EXPLAIN
SELECT r.workflow_instance_id, r.reg_id, r.process, r.status_code, r.reg_stage_name, r.upd_dtimes
FROM regprc.registration r
WHERE r.upd_dtimes < CURRENT_DATE AND r.is_anonymous_profile_added IS NOT TRUE
  AND r.cr_dtimes >= TIMESTAMP '1970-01-01' AND r.cr_dtimes < TIMESTAMP '9999-12-31'
  AND (r.upd_dtimes, r.workflow_instance_id) > (TIMESTAMP '1970-01-01', '')
ORDER BY r.upd_dtimes, r.workflow_instance_id
LIMIT 1000 FOR UPDATE SKIP LOCKED;
-- expect an Index Scan using idx_reg_anonymous_profile_pending, no Seq Scan and no separate Sort
```

### 2.2 Snapshot before the run

```sql
-- the columns the old saveAll could have overwritten with NULL, plus a few others that must not change
CREATE TABLE regprc.zz_reg_snapshot AS
SELECT workflow_instance_id, source, pause_rule_ids, last_success_stage_name, reference_id,
       status_code, latest_trn_status_code, reg_stage_name, upd_dtimes
FROM regprc.registration
WHERE upd_dtimes < CURRENT_DATE AND is_anonymous_profile_added IS NOT TRUE;

SELECT now() AT TIME ZONE 'UTC' AS test_started_utc;   -- write this down

SELECT (SELECT count(*) FROM regprc.zz_reg_snapshot)       AS pending_before,
       (SELECT count(*) FROM regprc.anonymous_profile)     AS profiles_before;
```

### 2.3 Run, then wait until the backlog is drained

Let the scheduler run (several cron cycles if the backlog is large). Done when:

```sql
SELECT count(*) AS still_pending
FROM regprc.registration r
JOIN regprc.zz_reg_snapshot s USING (workflow_instance_id)
WHERE r.is_anonymous_profile_added IS NOT TRUE;
-- 0, or only packets listed in the application log as "Failed to add anonymous profile"
```

### 2.4 Checks after the run

```sql
-- A. No duplicates and no losses: profiles inserted must equal packets flagged.
--    The profile table has no registration id, so this count comparison is the duplicate check.
SELECT
  (SELECT count(*) FROM regprc.registration r
     JOIN regprc.zz_reg_snapshot s USING (workflow_instance_id)
    WHERE r.is_anonymous_profile_added IS TRUE)                       AS packets_flagged,
  (SELECT count(*) FROM regprc.anonymous_profile
    WHERE cr_by = 'SYSTEM'
      AND cr_dtimes >= TIMESTAMP '<test_started_utc from 2.2>')       AS profiles_inserted;
-- the two numbers must be identical

-- B. No other column changed on the flagged rows (expect 0 rows)
SELECT r.workflow_instance_id
FROM regprc.registration r
JOIN regprc.zz_reg_snapshot s USING (workflow_instance_id)
WHERE r.source                  IS DISTINCT FROM s.source
   OR r.pause_rule_ids          IS DISTINCT FROM s.pause_rule_ids
   OR r.last_success_stage_name IS DISTINCT FROM s.last_success_stage_name
   OR r.reference_id            IS DISTINCT FROM s.reference_id
   OR r.status_code             IS DISTINCT FROM s.status_code
   OR r.latest_trn_status_code  IS DISTINCT FROM s.latest_trn_status_code
   OR r.reg_stage_name          IS DISTINCT FROM s.reg_stage_name
   OR r.upd_dtimes              IS DISTINCT FROM s.upd_dtimes
LIMIT 20;

-- C. Profiles look sane: no empty process stage or profile
SELECT count(*) FROM regprc.anonymous_profile
WHERE cr_dtimes >= TIMESTAMP '<test_started_utc>'
  AND (process_stage IS NULL OR profile IS NULL OR profile = '');   -- expect 0

-- D. Throughput per minute (cr_dtimes is stored as UTC wall-clock time)
SELECT date_trunc('minute', cr_dtimes) AS minute, count(*) AS profiles
FROM regprc.anonymous_profile
WHERE cr_dtimes >= (now() AT TIME ZONE 'UTC') - interval '60 minutes'
GROUP BY 1 ORDER BY 1;
-- target: at least 5M / 86400 = about 58 per second = about 3,500 per minute, sustained
```

Clean up afterwards: `DROP TABLE regprc.zz_reg_snapshot;`

### 2.5 While it runs (second terminal)

```sql
-- Chunks should be short-lived and spread over the pods; a long "idle in transaction" means
-- packet reads are slow, or the idle_in_transaction_session_timeout risk from 1.2 is real.
SELECT pid, application_name, state, now() - xact_start AS xact_age, left(query, 80) AS last_query
FROM pg_stat_activity
WHERE datname = current_database() AND state <> 'idle'
ORDER BY xact_start;
```

Application log lines to watch (all from `AnonymousProfileScheduler`):

| Log line | Meaning |
|---|---|
| `Anonymous profile chunk done: claimed X, added Y, took Z ms` | Normal. `added < claimed` means some packets failed to build. |
| `Failed to add anonymous profile` | A packet could not be read or built. Retried on the next run. |
| `chunk failed and was rolled back (N in a row)` | DB error. 3 in a row stops the run. Investigate before scaling up. |
| `Previous anonymous profile batch job is still running on this pod, skipping` | The run takes longer than the cron interval. Fine, but it means the job is saturated. |
| `Expected to flag N packets ... but M rows were updated` | Should never appear. Stop and investigate. |

Staging passes if: 2.4 A is equal, 2.4 B and C return nothing, the index is used, and no
unexplained rolled-back chunks appear.

## 3. Production rollout

1. **Index.** Run `anonymous_profile_pending_index.sql` with psql in a quiet period. It does not block
   writes, but it adds load to a large table, and it is harmless for the old code. Confirm with `EXPLAIN`
   (2.1).
2. **Deploy one pod** with conservative settings, outside peak registration hours:

   ```properties
   mosip.anonymous.profile.scheduler.fetchsize=200
   mosip.anonymous.profile.scheduler.threads.count=10
   mosip.anonymous.profile.scheduler.max.run.seconds=60
   mosip.anonymous.profile.scheduler.insert.batch.size=200
   ```

   Run the queries in 2.4 A and 2.5 for the profiles written since the deploy (use the deploy time as
   `test_started_utc`). Watch packet-manager and object-store latency and error rates: this change
   removes the old throttle, so their load goes up.
3. **Raise gradually**, checking 2.4 A and the log after each step: `fetchsize` 200 -> 500 -> 1000, then
   `max.run.seconds` 60 -> 150, then `threads.count` 10 -> 30. Stop raising when packet-manager latency
   rises or the DB shows lock waits or connection pool exhaustion (Hikari pool must be larger than
   threads in use plus other work per pod).
4. **Add pods** one at a time. Check 2.4 A again with two pods running: this is the check that
   `SKIP LOCKED` works in prod.
5. **Backlog.** The backlog from before the deploy is processed from the oldest packets first. Expect it
   to take a while; track it with query 1.3.

### Go / no-go

Stop and roll back if any of these happens:

- 2.4 A numbers differ (profiles inserted does not equal packets flagged).
- 2.4 B returns rows on a prod sample (compare a handful of flagged packets by hand if there is no snapshot).
- Repeated `rolled back` log lines with the same cause.
- Packet-manager or DB latency affects live registrations.

### Rollback

Redeploy the previous image and the previous properties. No data migration is needed: the flag
semantics are unchanged, so old and new versions can run side by side, and any in-flight chunk is
rolled back by the database if its pod dies. The partial index can stay (it is harmless), or be dropped with
`anonymous_profile_pending_index_rollback.sql`.

Duplicate profiles cannot be cleaned up afterwards because the profile table does not store the
registration id. That is why check 2.4 A should be done in staging, and again at the first prod step.

## 4. Known limits

- Packets that keep failing are retried on every run and nothing records that they failed. If there are many
  (for example packets missing from the object store), consider a failure marker on `registration`.
- Most of the time per packet is spent reading from packet manager (four calls, including biometrics).
  If the target is not met, set `mosip.anonymous.profile.bioInfo.required=false` if the business accepts
  profiles without biometric quality data.
