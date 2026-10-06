-- ============================================================
-- EMITTA
-- V3 - Normalize fiscal codes to VARCHAR
--
-- Hibernate maps Java String fields to VARCHAR by default.
-- Fixed fiscal lengths continue to be enforced by CHECK
-- constraints, so CHAR semantics and padding are unnecessary.
-- ============================================================

ALTER TABLE establishments
    ALTER COLUMN code TYPE VARCHAR(3)
        USING BTRIM(code);

ALTER TABLE points_of_issue
    ALTER COLUMN code TYPE VARCHAR(3)
        USING BTRIM(code);