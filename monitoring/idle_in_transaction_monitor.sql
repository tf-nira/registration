-- SQL Monitoring Script for 'Idle in Transaction' Issue
-- Database: PostgreSQL
-- Schema: mosip_regprc

-- 1. CHECK FOR IDLE IN TRANSACTION CONNECTIONS
SELECT 
    pid, usename, application_name, client_addr, state,
    query_start, state_change,
    now() - query_start as query_duration,
    now() - state_change as idle_duration,
    query
FROM pg_stat_activity 
WHERE state = 'idle in transaction' 
AND datname = 'mosip_regprc'
ORDER BY state_change;

-- 2. CHECK LONG-RUNNING TRANSACTIONS  
SELECT 
    pid, usename, application_name, state,
    now() - xact_start as transaction_duration,
    now() - query_start as query_duration,
    query
FROM pg_stat_activity
WHERE state != 'idle'
AND datname = 'mosip_regprc'
AND (now() - xact_start) > interval '30 seconds'
ORDER BY xact_start;

-- 3. CONNECTION POOL SUMMARY
SELECT 
    datname, state,
    count(*) as connection_count,
    max(now() - state_change) as max_state_duration,
    avg(now() - state_change) as avg_state_duration
FROM pg_stat_activity
WHERE datname = 'mosip_regprc'
GROUP BY datname, state
ORDER BY datname, state;
