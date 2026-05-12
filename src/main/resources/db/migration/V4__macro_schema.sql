CREATE TABLE macro_item (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    base_date date NOT NULL,
    payload_json jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_macro_item_base_date UNIQUE (base_date)
);

CREATE INDEX idx_macro_item_base_date ON macro_item (base_date DESC);
