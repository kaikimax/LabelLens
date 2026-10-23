-- Day 4: the Nutri-Score computed from the reading, with every point, so the app can explain the grade.

CREATE TABLE nutri_score (
    product_id                 UUID          PRIMARY KEY REFERENCES product (id) ON DELETE CASCADE,
    scored                     BOOLEAN       NOT NULL,
    not_scored_reason          VARCHAR(300),
    grade                      CHAR(1),
    score                      INT,
    energy_points              INT,
    sugars_points              INT,
    saturated_fat_points       INT,
    salt_points                INT,
    protein_points             INT,
    fibre_points               INT,
    fruit_veg_legumes_points   INT,
    protein_counted            BOOLEAN,
    -- Labels almost never print the fruit/vegetable %, so the app assumes 0 when it's missing.
    fruit_veg_assumed_zero     BOOLEAN       NOT NULL,
    algorithm_version          VARCHAR(20)   NOT NULL,
    created_at                 TIMESTAMPTZ   NOT NULL
);
