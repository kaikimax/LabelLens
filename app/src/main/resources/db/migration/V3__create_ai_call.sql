-- Day 3: every call to an AI model, with its cost (tokens) and duration.
-- product_id is kept nullable: later, eval runs will make calls that belong to no product.

CREATE TABLE ai_call (
    id             UUID         PRIMARY KEY,
    product_id     UUID         REFERENCES product (id) ON DELETE SET NULL,
    purpose        VARCHAR(30)  NOT NULL,
    model          VARCHAR(100),
    input_tokens   INT,
    output_tokens  INT,
    duration_ms    BIGINT       NOT NULL,
    success        BOOLEAN      NOT NULL,
    error          TEXT,
    created_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_ai_call_product_id ON ai_call (product_id);
CREATE INDEX idx_ai_call_created_at ON ai_call (created_at DESC);
