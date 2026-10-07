-- ============================================================
-- EMITTA
-- V8 - Require invoice item primary code
--
-- SRI Invoice XML 2.1.0 requires <codigoPrincipal>
-- for every invoice detail.
-- ============================================================


ALTER TABLE invoice_items
    ADD CONSTRAINT chk_invoice_items_sku_not_blank
        CHECK (
            length(trim(sku)) > 0
            );


ALTER TABLE invoice_items
    ALTER COLUMN sku SET NOT NULL;


COMMENT ON COLUMN invoice_items.sku IS
    'Primary item code serialized as SRI <codigoPrincipal>. Required, max 25 characters.';