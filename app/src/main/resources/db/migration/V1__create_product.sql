-- Flyway runs each V<number>__<name>.sql file once, in order, and records it.
-- Never edit a migration after it has run: add V2__..., V3__... instead.

CREATE TABLE product (
    id          UUID         PRIMARY KEY,
    created_at  TIMESTAMPTZ  NOT NULL,
    status      VARCHAR(20)  NOT NULL
);

CREATE INDEX idx_product_created_at ON product (created_at DESC);

CREATE TABLE product_photo (
    id            UUID          PRIMARY KEY,
    product_id    UUID          NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    position      INT           NOT NULL CHECK (position BETWEEN 1 AND 3),
    storage_key   VARCHAR(500)  NOT NULL,
    content_type  VARCHAR(100)  NOT NULL,
    size_bytes    BIGINT        NOT NULL,
    UNIQUE (product_id, position)
);
