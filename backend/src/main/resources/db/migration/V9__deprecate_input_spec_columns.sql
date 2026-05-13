-- prompt frontmatter가 입력 스펙을 대체. 기존 input_spec 컬럼은 더 이상 사용되지 않으나
-- 데이터 손실 방지를 위해 NOT NULL 제약만 풀고 컬럼은 보존 (v2에서 DROP).

ALTER TABLE strategy_template
    ALTER COLUMN default_input_spec_json DROP NOT NULL;
