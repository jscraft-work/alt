-- db-scheduler (Kagkarlsson) standard table for PostgreSQL.
-- Source: https://github.com/kagkarlsson/db-scheduler/blob/master/db-scheduler/src/test/resources/postgresql_tables.sql
-- Used to persist recurring/one-time scheduled tasks. For ALT-Web this stores one row per
-- active strategy_instance (task_name='trading-cycle', task_instance=strategyInstanceId).
-- (task_name, task_instance) PK + picked flag guarantees serial execution per strategy instance
-- without an external Redis lock.

CREATE TABLE scheduled_tasks (
    task_name text NOT NULL,
    task_instance text NOT NULL,
    task_data bytea,
    execution_time timestamptz NOT NULL,
    picked boolean NOT NULL,
    picked_by text,
    last_success timestamptz,
    last_failure timestamptz,
    consecutive_failures int,
    last_heartbeat timestamptz,
    version bigint NOT NULL,
    priority smallint,
    PRIMARY KEY (task_name, task_instance)
);

CREATE INDEX execution_time_idx ON scheduled_tasks (execution_time);
CREATE INDEX last_heartbeat_idx ON scheduled_tasks (last_heartbeat);
CREATE INDEX priority_execution_time_idx ON scheduled_tasks (priority DESC, execution_time ASC);
