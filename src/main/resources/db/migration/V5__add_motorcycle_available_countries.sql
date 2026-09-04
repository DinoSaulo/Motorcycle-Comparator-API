-- Countries where a motorcycle is officially available. A bike can list zero, one or many, so this is
-- an @ElementCollection table exactly like motorcycle_additional_specs: a composite PK keyed on the
-- code itself, no surrogate id, because the code is the only attribute there is.
--
-- Only the ISO 3166-1 alpha-2 code is stored (e.g. "BR", "US", "PT"), not a country name: two bytes
-- per row keeps the join table small even once every bike in a large catalogue carries a handful of rows.
-- VARCHAR(2), not CHAR(2): Hibernate maps a plain String column to varchar, and ddl-auto: validate
-- (dev, prod) would otherwise reject this table as drift from the entity on every boot.
CREATE TABLE motorcycle_available_countries (
    motorcycle_id BIGINT      NOT NULL,
    country_code  VARCHAR(2)  NOT NULL,
    CONSTRAINT pk_motorcycle_available_countries PRIMARY KEY (motorcycle_id, country_code),
    -- ISO 3166-1 alpha-2 is always two uppercase letters; the API normalises case before this is reached.
    CONSTRAINT ck_available_countries_code_format CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT fk_available_countries_motorcycle FOREIGN KEY (motorcycle_id) REFERENCES motorcycles (id) ON DELETE CASCADE
);

COMMENT ON TABLE motorcycle_available_countries IS 'ISO 3166-1 alpha-2 codes of the countries a motorcycle is officially sold in; no row means no country data yet.';

-- Serves "which motorcycles are sold in country X", the reverse direction of the join from motorcycle_id.
CREATE INDEX idx_available_countries_country_code ON motorcycle_available_countries (country_code);
