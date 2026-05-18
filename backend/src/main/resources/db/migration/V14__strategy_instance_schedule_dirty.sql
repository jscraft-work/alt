ALTER TABLE strategy_instance
    ADD COLUMN schedule_dirty boolean NOT NULL DEFAULT false;
