-- ============================================================
-- EMITTA
-- V5 - Invoice payments
--
-- SRI invoices require at least one payment entry in the
-- <pagos> section.
-- ============================================================

CREATE TABLE invoice_payments
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    invoice_id          UUID NOT NULL,

    line_number         INTEGER NOT NULL,

    payment_method      VARCHAR(2) NOT NULL,

    total               NUMERIC(14,2) NOT NULL,

    term                NUMERIC(14,2),
    unit_time           VARCHAR(10),

    CONSTRAINT fk_invoice_payments_invoice
        FOREIGN KEY (invoice_id)
            REFERENCES invoices(document_id)
            ON DELETE RESTRICT,

    CONSTRAINT uq_invoice_payments_line
        UNIQUE (invoice_id, line_number),

    CONSTRAINT chk_invoice_payments_line_number
        CHECK (line_number > 0),

    CONSTRAINT chk_invoice_payments_method
        CHECK (payment_method ~ '^[0-9]{2}$'),

    CONSTRAINT chk_invoice_payments_total
        CHECK (total > 0),

    CONSTRAINT chk_invoice_payments_term
        CHECK (
            (
                term IS NULL
                    AND unit_time IS NULL
                )
                OR
            (
                term IS NOT NULL
                    AND term >= 0
                    AND unit_time IS NOT NULL
                )
            )
);

CREATE INDEX idx_invoice_payments_invoice
    ON invoice_payments(invoice_id);