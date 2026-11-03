-- Day 9: allergens in the app (decisions log, 2026-11-03).

-- The reader now copies the "may contain" sentence separately, instead of dropping it.
ALTER TABLE label_reading ADD COLUMN may_contain_text TEXT;

-- One row per product. Tag lists are comma-separated EU-14 tags ("en:gluten,en:milk"), '' = none.
-- found_by_word_list and found_by_ai are kept so the app can say who found each allergen.
CREATE TABLE allergen_result (
    product_id           UUID           PRIMARY KEY REFERENCES product (id) ON DELETE CASCADE,
    -- false: no ingredients list was read, so an empty "contains" means "not checked", not "none".
    ingredients_read     BOOLEAN        NOT NULL,
    contains             TEXT           NOT NULL DEFAULT '',
    may_contain          TEXT           NOT NULL DEFAULT '',
    found_by_word_list   TEXT           NOT NULL DEFAULT '',
    -- null when the AI didn't give a usable answer (see ai_problem).
    found_by_ai          TEXT,
    ai_model             VARCHAR(100),
    ai_problem           VARCHAR(1000),
    created_at           TIMESTAMPTZ    NOT NULL
);
