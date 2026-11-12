-- Index for the anonymous profile scheduler (AnonymousProfileScheduler).
--
-- The scheduler repeatedly looks for packets that still need an anonymous profile:
--   WHERE upd_dtimes < CURRENT_DATE AND is_anonymous_profile_added IS NOT TRUE
--   ORDER BY upd_dtimes, workflow_instance_id
-- Without this index that is a sequential scan plus sort of the whole registration table.
--
-- The index only contains the packets that are still pending, so it stays small, and rows
-- leave it when the scheduler flags them. The predicate must stay exactly
-- "IS NOT TRUE" for the planner to use the index.
--
-- CREATE INDEX CONCURRENTLY cannot run inside a transaction block: run this file with
-- psql (autocommit), not inside BEGIN/COMMIT.
-- Check afterwards with EXPLAIN that the scheduler query uses idx_reg_anonymous_profile_pending.

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_reg_anonymous_profile_pending
    ON regprc.registration (upd_dtimes, workflow_instance_id)
    WHERE is_anonymous_profile_added IS NOT TRUE;
