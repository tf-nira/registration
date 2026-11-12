-- Rollback of anonymous_profile_pending_index.sql
-- DROP INDEX CONCURRENTLY also cannot run inside a transaction block.
DROP INDEX CONCURRENTLY IF EXISTS regprc.idx_reg_anonymous_profile_pending;
