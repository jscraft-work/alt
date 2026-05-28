-- cycleMinutes 범위 확장: 1~30 → 1~10080 (주 1회 사이클까지 허용)
-- 운영 DB는 2026-05-28 hotfix ALTER로 선반영. 본 마이그레이션은 정합성 동기화.

ALTER TABLE strategy_instance DROP CONSTRAINT IF EXISTS chk_strategy_instance_cycle_minutes;
ALTER TABLE strategy_instance ADD CONSTRAINT chk_strategy_instance_cycle_minutes
    CHECK (cycle_minutes IS NULL OR cycle_minutes BETWEEN 1 AND 10080);

ALTER TABLE strategy_template DROP CONSTRAINT IF EXISTS chk_strategy_template_cycle_minutes;
ALTER TABLE strategy_template ADD CONSTRAINT chk_strategy_template_cycle_minutes
    CHECK (default_cycle_minutes BETWEEN 1 AND 10080);
