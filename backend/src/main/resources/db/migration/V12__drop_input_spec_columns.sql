-- V9에서 nullable화로 deprecated된 input_spec 컬럼들을 DROP.
-- frontmatter (YAML) + Pebble 본문이 입력 스펙을 완전히 대체했고,
-- 코드/엔티티/DTO에서도 동시 제거된다.

ALTER TABLE strategy_template
    DROP COLUMN default_input_spec_json;

ALTER TABLE strategy_instance
    DROP COLUMN input_spec_override_json;
