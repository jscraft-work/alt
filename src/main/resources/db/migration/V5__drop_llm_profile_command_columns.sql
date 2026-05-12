ALTER TABLE llm_model_profile
    DROP COLUMN command,
    DROP COLUMN args_json,
    DROP COLUMN default_timeout_seconds;
