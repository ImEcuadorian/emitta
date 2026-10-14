-- Read-only diagnostics for an explicitly authorized environment. Never executed against the pilot by tests.
-- Collector may need pg_read_all_stats; do not grant superuser. No query text, PID, user or fiscal labels.
SELECT state, wait_event_type, count(*) AS sessions
FROM pg_stat_activity WHERE datname=current_database() AND pid<>pg_backend_pid()
GROUP BY state, wait_event_type;

SELECT COALESCE(max(EXTRACT(EPOCH FROM clock_timestamp()-xact_start)),0) AS oldest_transaction_seconds
FROM pg_stat_activity WHERE datname=current_database() AND xact_start IS NOT NULL AND pid<>pg_backend_pid();

SELECT count(*) AS sessions_waiting_on_locks
FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock';

SELECT count(*) AS unpublished_events FROM emitta.outbox_events WHERE published_at IS NULL;
SELECT state,kind,count(*) AS jobs FROM emitta.processing_jobs GROUP BY state,kind;
