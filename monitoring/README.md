# Database Monitoring Scripts

This directory contains SQL scripts for monitoring database health and performance.

## Files

### idle_in_transaction_monitor.sql

Monitors PostgreSQL connections for the 'idle in transaction' issue.

**Purpose**: Track and identify connections that remain in 'idle in transaction' state, which can cause:
- Database connection pool exhaustion
- Locks preventing other transactions
- VACUUM operations being blocked
- Poor database performance

**Usage**:
```bash
# Run directly in psql
psql -U postgres -d mosip_regprc -f idle_in_transaction_monitor.sql

# Or connect and run individual queries
psql -U postgres -d mosip_regprc
\i monitoring/idle_in_transaction_monitor.sql
```

**Queries Included**:

1. **Check for Idle in Transaction Connections**
   - Shows all connections in 'idle in transaction' state
   - AFTER FIX: Should show zero or minimal connections

2. **Check Long-Running Transactions**
   - Shows transactions running > 30 seconds
   - AFTER FIX: Read transactions should complete in < 1 second

3. **Connection Pool Summary**
   - Overview of connection states
   - Monitor for proper connection recycling

**Expected Results After Fix**:

Before the fix:
```
 pid  | state               | idle_duration | query
------+---------------------+---------------+----------------------------------------
 1234 | idle in transaction | 00:05:23      | SELECT ... WHERE workflow_instance_id=...
```

After the fix:
```
 pid | state | idle_duration | query
-----+-------+---------------+-------
(0 rows)
```

**Monitoring Recommendations**:

- Run Query #1 every minute during peak load
- Alert if > 5 connections in 'idle in transaction' state
- Run Query #2 every 5 minutes
- Alert if transactions running > 2 minutes
- Run Query #3 hourly to track trends

**Related Documentation**:
- `IDLE_IN_TRANSACTION_FIX.md` - Details of the fix
- `QUERY_LOCATION_ANALYSIS.md` - Query origin analysis
