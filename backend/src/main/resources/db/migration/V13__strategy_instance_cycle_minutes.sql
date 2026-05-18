ALTER TABLE strategy_instance
    ADD COLUMN cycle_minutes integer,
    ADD CONSTRAINT chk_strategy_instance_cycle_minutes
        CHECK (cycle_minutes IS NULL OR cycle_minutes BETWEEN 1 AND 30);
