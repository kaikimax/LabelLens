-- Day 3: what the AI read from the label, converted to per 100 g.

ALTER TABLE product ADD COLUMN failure_reason VARCHAR(1000);

CREATE TABLE label_reading (
    product_id            UUID              PRIMARY KEY REFERENCES product (id) ON DELETE CASCADE,
    name                  VARCHAR(300),
    brand                 VARCHAR(300),
    type                  VARCHAR(20),
    energy_kj             DOUBLE PRECISION,
    fat                   DOUBLE PRECISION,
    saturated_fat         DOUBLE PRECISION,
    carbohydrates         DOUBLE PRECISION,
    sugars                DOUBLE PRECISION,
    fibre                 DOUBLE PRECISION,
    protein               DOUBLE PRECISION,
    salt                  DOUBLE PRECISION,
    ingredients_text      TEXT,
    ingredients_language  VARCHAR(10),
    printed_basis         VARCHAR(10),
    warnings              TEXT              NOT NULL DEFAULT '',
    model                 VARCHAR(100),
    raw_response          TEXT,
    created_at            TIMESTAMPTZ       NOT NULL
);
