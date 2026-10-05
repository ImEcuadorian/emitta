-- ============================================================
-- EMITTA
-- V2 - Initial Domain Model
--
-- Depends on:
--   V1__create_emitta_foundation.sql
--
-- Important architectural rules:
--   - PostgreSQL is the source of truth.
--   - Flyway owns schema evolution.
--   - Runtime application user has DML privileges only.
--   - ProviderIdentity is NOT persisted in V2; ADR-013 keeps it
--     as platform-controlled configuration managed by Doppler.
--   - Fiscal document processing is asynchronous (ADR-014).
-- ============================================================


-- ============================================================
-- TAXPAYERS - evolve the V1 foundation model
-- ============================================================

ALTER TABLE taxpayers
    ADD COLUMN status VARCHAR(30),
    ADD COLUMN test_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN production_enabled BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE taxpayers
SET status = CASE
                 WHEN active THEN 'ACTIVE'
                 ELSE 'DISABLED'
             END,
    test_enabled = CASE
                       WHEN environment = 'TEST' THEN TRUE
                       ELSE FALSE
                   END,
    production_enabled = CASE
                             WHEN environment = 'PRODUCTION' THEN TRUE
                             ELSE FALSE
                         END;

ALTER TABLE taxpayers
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN status SET DEFAULT 'ACTIVE';

ALTER TABLE taxpayers
    ADD CONSTRAINT chk_taxpayers_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED'));

ALTER TABLE taxpayers
    DROP CONSTRAINT chk_taxpayer_environment;

ALTER TABLE taxpayers
    DROP COLUMN environment,
    DROP COLUMN active;

-- Supports composite foreign keys that guarantee tenant ownership.
ALTER TABLE taxpayers
    ADD CONSTRAINT uq_taxpayers_tenant_id_id
        UNIQUE (tenant_id, id);


-- ============================================================
-- API CLIENTS
--
-- Long-lived client credentials belong to a Tenant.
-- Only a password hash is persisted. The raw secret must never
-- be stored in PostgreSQL.
-- ============================================================

CREATE TABLE api_clients
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    tenant_id           UUID NOT NULL,

    client_id           VARCHAR(100) NOT NULL,
    client_secret_hash  VARCHAR(255) NOT NULL,

    name                VARCHAR(150) NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at        TIMESTAMPTZ,
    rotated_at          TIMESTAMPTZ,

    CONSTRAINT fk_api_clients_tenant
        FOREIGN KEY (tenant_id)
            REFERENCES tenants(id)
            ON DELETE RESTRICT,

    CONSTRAINT uq_api_clients_client_id
        UNIQUE (client_id),

    CONSTRAINT chk_api_clients_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED'))
);

CREATE INDEX idx_api_clients_tenant
    ON api_clients(tenant_id);

CREATE INDEX idx_api_clients_tenant_status
    ON api_clients(tenant_id, status);


-- ============================================================
-- ESTABLISHMENTS
-- ============================================================

CREATE TABLE establishments
(
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    taxpayer_id     UUID NOT NULL,

    code            CHAR(3) NOT NULL,
    name            VARCHAR(200),
    address         VARCHAR(500),
    status          VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_establishments_taxpayer
        FOREIGN KEY (taxpayer_id)
            REFERENCES taxpayers(id)
            ON DELETE RESTRICT,

    CONSTRAINT uq_establishments_taxpayer_code
        UNIQUE (taxpayer_id, code),

    CONSTRAINT uq_establishments_taxpayer_id_id
        UNIQUE (taxpayer_id, id),

    CONSTRAINT chk_establishments_code
        CHECK (code ~ '^[0-9]{3}$'),

    CONSTRAINT chk_establishments_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_establishments_taxpayer
    ON establishments(taxpayer_id);


-- ============================================================
-- POINTS OF ISSUE
-- ============================================================

CREATE TABLE points_of_issue
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    establishment_id    UUID NOT NULL,

    code                CHAR(3) NOT NULL,
    name                VARCHAR(200),
    status              VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_points_of_issue_establishment
        FOREIGN KEY (establishment_id)
            REFERENCES establishments(id)
            ON DELETE RESTRICT,

    CONSTRAINT uq_points_of_issue_establishment_code
        UNIQUE (establishment_id, code),

    CONSTRAINT chk_points_of_issue_code
        CHECK (code ~ '^[0-9]{3}$'),

    CONSTRAINT chk_points_of_issue_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_points_of_issue_establishment
    ON points_of_issue(establishment_id);


-- ============================================================
-- DOCUMENT SEQUENCES
--
-- Never allocate fiscal numbers using MAX(sequential) + 1.
-- Allocation must use an atomic UPDATE ... RETURNING operation.
--
-- current_value stores the LAST allocated sequential number.
-- The first allocation from zero therefore returns 1.
-- ============================================================

CREATE TABLE document_sequences
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    point_of_issue_id   UUID NOT NULL,

    document_type       VARCHAR(40) NOT NULL,
    environment         VARCHAR(20) NOT NULL,

    current_value       BIGINT NOT NULL DEFAULT 0,

    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_document_sequences_point_of_issue
        FOREIGN KEY (point_of_issue_id)
            REFERENCES points_of_issue(id)
            ON DELETE RESTRICT,

    CONSTRAINT uq_document_sequences_scope
        UNIQUE (point_of_issue_id, document_type, environment),

    CONSTRAINT chk_document_sequences_type
        CHECK (
            document_type IN (
                'INVOICE',
                'PURCHASE_SETTLEMENT',
                'CREDIT_NOTE',
                'DEBIT_NOTE',
                'WITHHOLDING',
                'DISPATCH_GUIDE'
            )
        ),

    CONSTRAINT chk_document_sequences_environment
        CHECK (environment IN ('TEST', 'PRODUCTION')),

    CONSTRAINT chk_document_sequences_value
        CHECK (current_value BETWEEN 0 AND 999999999)
);

CREATE INDEX idx_document_sequences_point_of_issue
    ON document_sequences(point_of_issue_id);


-- ============================================================
-- SIGNING CERTIFICATES
--
-- PKCS#12 content and password are encrypted at application
-- level before persistence. Plaintext certificate passwords or
-- private keys must never be stored here.
-- ============================================================

CREATE TABLE certificates
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    taxpayer_id         UUID NOT NULL,

    alias               VARCHAR(150),
    fingerprint         VARCHAR(128) NOT NULL,
    subject             TEXT NOT NULL,
    issuer              TEXT NOT NULL,

    valid_from          TIMESTAMPTZ NOT NULL,
    valid_until         TIMESTAMPTZ NOT NULL,

    status              VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

    encrypted_content   BYTEA NOT NULL,
    encrypted_password  BYTEA NOT NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_certificates_taxpayer
        FOREIGN KEY (taxpayer_id)
            REFERENCES taxpayers(id)
            ON DELETE RESTRICT,

    CONSTRAINT uq_certificates_taxpayer_fingerprint
        UNIQUE (taxpayer_id, fingerprint),

    CONSTRAINT chk_certificates_validity
        CHECK (valid_until > valid_from),

    CONSTRAINT chk_certificates_status
        CHECK (status IN ('ACTIVE', 'EXPIRED', 'REVOKED', 'DISABLED'))
);

CREATE INDEX idx_certificates_taxpayer
    ON certificates(taxpayer_id);

CREATE INDEX idx_certificates_taxpayer_status
    ON certificates(taxpayer_id, status);


-- ============================================================
-- CUSTOMERS
--
-- Reusable tenant-owned master data. Historical invoices must
-- store their own buyer snapshot and must not depend on this
-- record remaining unchanged.
-- ============================================================

CREATE TABLE customers
(
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    tenant_id               UUID NOT NULL,

    identification_type     VARCHAR(30) NOT NULL,
    identification          VARCHAR(30) NOT NULL,

    name                    VARCHAR(300) NOT NULL,
    email                   VARCHAR(320),
    address                 VARCHAR(500),

    status                  VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_customers_tenant
        FOREIGN KEY (tenant_id)
            REFERENCES tenants(id)
            ON DELETE RESTRICT,

    CONSTRAINT uq_customers_tenant_identification
        UNIQUE (tenant_id, identification_type, identification),

    CONSTRAINT chk_customers_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_customers_tenant
    ON customers(tenant_id);

CREATE INDEX idx_customers_tenant_identification
    ON customers(tenant_id, identification);


-- ============================================================
-- WEBHOOK ENDPOINTS
--
-- The webhook secret is encrypted, not hashed, because Emitta
-- needs the secret material to produce outbound HMAC signatures.
-- ============================================================

CREATE TABLE webhook_endpoints
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    tenant_id           UUID NOT NULL,

    url                 TEXT NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

    encrypted_secret    BYTEA NOT NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_webhook_endpoints_tenant
        FOREIGN KEY (tenant_id)
            REFERENCES tenants(id)
            ON DELETE RESTRICT,

    CONSTRAINT chk_webhook_endpoints_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX idx_webhook_endpoints_tenant
    ON webhook_endpoints(tenant_id);

CREATE INDEX idx_webhook_endpoints_tenant_status
    ON webhook_endpoints(tenant_id, status);


-- ============================================================
-- DOCUMENTS
--
-- Shared fiscal envelope for all current/future document types.
-- access_key and sequential remain nullable until allocated /
-- generated by the fiscal processing workflow.
-- ============================================================

CREATE TABLE documents
(
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    tenant_id               UUID NOT NULL,
    taxpayer_id             UUID NOT NULL,
    point_of_issue_id       UUID NOT NULL,

    document_type           VARCHAR(40) NOT NULL,
    environment             VARCHAR(20) NOT NULL,

    sequential              BIGINT,
    access_key              VARCHAR(49),

    status                  VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',

    idempotency_key         VARCHAR(255) NOT NULL,

    issued_at               TIMESTAMPTZ NOT NULL,
    received_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    queued_at               TIMESTAMPTZ,
    processing_started_at   TIMESTAMPTZ,
    signed_at               TIMESTAMPTZ,
    submitted_at            TIMESTAMPTZ,
    authorized_at           TIMESTAMPTZ,
    failed_at               TIMESTAMPTZ,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_documents_tenant
        FOREIGN KEY (tenant_id)
            REFERENCES tenants(id)
            ON DELETE RESTRICT,

    -- Composite FK prevents a document from referencing a
    -- taxpayer owned by another tenant.
    CONSTRAINT fk_documents_tenant_taxpayer
        FOREIGN KEY (tenant_id, taxpayer_id)
            REFERENCES taxpayers(tenant_id, id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_documents_point_of_issue
        FOREIGN KEY (point_of_issue_id)
            REFERENCES points_of_issue(id)
            ON DELETE RESTRICT,

    CONSTRAINT uq_documents_tenant_idempotency
        UNIQUE (tenant_id, idempotency_key),

    CONSTRAINT chk_documents_type
        CHECK (
            document_type IN (
                'INVOICE',
                'PURCHASE_SETTLEMENT',
                'CREDIT_NOTE',
                'DEBIT_NOTE',
                'WITHHOLDING',
                'DISPATCH_GUIDE'
            )
        ),

    CONSTRAINT chk_documents_environment
        CHECK (environment IN ('TEST', 'PRODUCTION')),

    CONSTRAINT chk_documents_sequential
        CHECK (
            sequential IS NULL
            OR sequential BETWEEN 1 AND 999999999
        ),

    CONSTRAINT chk_documents_access_key
        CHECK (
            access_key IS NULL
            OR access_key ~ '^[0-9]{49}$'
        ),

    CONSTRAINT chk_documents_status
        CHECK (
            status IN (
                'RECEIVED',
                'QUEUED',
                'GENERATING',
                'SIGNED',
                'SUBMITTED',
                'RETRY_PENDING',
                'AUTHORIZED',
                'REJECTED',
                'FAILED'
            )
        )
);

-- PostgreSQL allows multiple NULL values in a UNIQUE constraint.
ALTER TABLE documents
    ADD CONSTRAINT uq_documents_access_key
        UNIQUE (access_key);

-- Prevent duplicate fiscal numbering once the sequential exists.
CREATE UNIQUE INDEX uq_documents_fiscal_sequence
    ON documents (
        taxpayer_id,
        point_of_issue_id,
        document_type,
        environment,
        sequential
    )
    WHERE sequential IS NOT NULL;

CREATE INDEX idx_documents_tenant_created
    ON documents(tenant_id, created_at DESC);

CREATE INDEX idx_documents_tenant_status
    ON documents(tenant_id, status);

CREATE INDEX idx_documents_taxpayer_issued
    ON documents(taxpayer_id, issued_at DESC);

CREATE INDEX idx_documents_point_of_issue
    ON documents(point_of_issue_id);


-- ============================================================
-- INVOICES
--
-- One-to-one specialization of Document.
-- Buyer fields are the authoritative historical snapshot.
--
-- Monetary fields use NUMERIC(14,2).
-- SRI invoice detail quantity/unit price supports up to six
-- decimal places, therefore line-level quantity/unit price use
-- NUMERIC(18,6).
-- ============================================================

CREATE TABLE invoices
(
    document_id                 UUID PRIMARY KEY,

    customer_id                 UUID,

    buyer_identification_type   VARCHAR(30) NOT NULL,
    buyer_identification        VARCHAR(30) NOT NULL,
    buyer_name                  VARCHAR(300) NOT NULL,
    buyer_email                 VARCHAR(320),
    buyer_address               VARCHAR(500),

    subtotal                    NUMERIC(14,2) NOT NULL,
    discount_total              NUMERIC(14,2) NOT NULL DEFAULT 0,
    tax_total                   NUMERIC(14,2) NOT NULL DEFAULT 0,
    total                       NUMERIC(14,2) NOT NULL,

    currency                    VARCHAR(15) NOT NULL DEFAULT 'DOLAR',

    CONSTRAINT fk_invoices_document
        FOREIGN KEY (document_id)
            REFERENCES documents(id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_invoices_customer
        FOREIGN KEY (customer_id)
            REFERENCES customers(id)
            ON DELETE RESTRICT,

    CONSTRAINT chk_invoices_subtotal
        CHECK (subtotal >= 0),

    CONSTRAINT chk_invoices_discount_total
        CHECK (discount_total >= 0),

    CONSTRAINT chk_invoices_tax_total
        CHECK (tax_total >= 0),

    CONSTRAINT chk_invoices_total
        CHECK (total >= 0)
);

CREATE INDEX idx_invoices_customer
    ON invoices(customer_id)
    WHERE customer_id IS NOT NULL;


-- ============================================================
-- INVOICE ITEMS
-- ============================================================

CREATE TABLE invoice_items
(
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    invoice_id      UUID NOT NULL,

    line_number     INTEGER NOT NULL,

    sku             VARCHAR(25),
    description     VARCHAR(300) NOT NULL,

    quantity        NUMERIC(18,6) NOT NULL,
    unit_price      NUMERIC(18,6) NOT NULL,

    discount        NUMERIC(14,2) NOT NULL DEFAULT 0,
    subtotal        NUMERIC(14,2) NOT NULL,
    tax_total       NUMERIC(14,2) NOT NULL DEFAULT 0,
    total           NUMERIC(14,2) NOT NULL,

    CONSTRAINT fk_invoice_items_invoice
        FOREIGN KEY (invoice_id)
            REFERENCES invoices(document_id)
            ON DELETE RESTRICT,

    CONSTRAINT uq_invoice_items_line
        UNIQUE (invoice_id, line_number),

    CONSTRAINT chk_invoice_items_line_number
        CHECK (line_number > 0),

    CONSTRAINT chk_invoice_items_quantity
        CHECK (quantity > 0),

    CONSTRAINT chk_invoice_items_unit_price
        CHECK (unit_price >= 0),

    CONSTRAINT chk_invoice_items_discount
        CHECK (discount >= 0),

    CONSTRAINT chk_invoice_items_subtotal
        CHECK (subtotal >= 0),

    CONSTRAINT chk_invoice_items_tax_total
        CHECK (tax_total >= 0),

    CONSTRAINT chk_invoice_items_total
        CHECK (total >= 0)
);

CREATE INDEX idx_invoice_items_invoice
    ON invoice_items(invoice_id);


-- ============================================================
-- INVOICE ITEM TAXES
-- ============================================================

CREATE TABLE invoice_item_taxes
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    invoice_item_id     UUID NOT NULL,

    tax_code            VARCHAR(4) NOT NULL,
    percentage_code     VARCHAR(4) NOT NULL,

    rate                NUMERIC(7,4) NOT NULL,
    taxable_base        NUMERIC(14,2) NOT NULL,
    tax_amount          NUMERIC(14,2) NOT NULL,

    CONSTRAINT fk_invoice_item_taxes_item
        FOREIGN KEY (invoice_item_id)
            REFERENCES invoice_items(id)
            ON DELETE RESTRICT,

    CONSTRAINT chk_invoice_item_taxes_rate
        CHECK (rate >= 0),

    CONSTRAINT chk_invoice_item_taxes_taxable_base
        CHECK (taxable_base >= 0),

    CONSTRAINT chk_invoice_item_taxes_tax_amount
        CHECK (tax_amount >= 0)
);

CREATE INDEX idx_invoice_item_taxes_item
    ON invoice_item_taxes(invoice_item_id);


-- ============================================================
-- DOCUMENT STATUS HISTORY
--
-- Append-only audit trail for durable domain transitions.
-- ============================================================

CREATE TABLE document_status_history
(
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    document_id     UUID NOT NULL,

    from_status     VARCHAR(30),
    to_status       VARCHAR(30) NOT NULL,

    reason          VARCHAR(500),
    metadata        JSONB NOT NULL DEFAULT '{}'::JSONB,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_document_status_history_document
        FOREIGN KEY (document_id)
            REFERENCES documents(id)
            ON DELETE RESTRICT,

    CONSTRAINT chk_document_status_history_from
        CHECK (
            from_status IS NULL
            OR from_status IN (
                'RECEIVED',
                'QUEUED',
                'GENERATING',
                'SIGNED',
                'SUBMITTED',
                'RETRY_PENDING',
                'AUTHORIZED',
                'REJECTED',
                'FAILED'
            )
        ),

    CONSTRAINT chk_document_status_history_to
        CHECK (
            to_status IN (
                'RECEIVED',
                'QUEUED',
                'GENERATING',
                'SIGNED',
                'SUBMITTED',
                'RETRY_PENDING',
                'AUTHORIZED',
                'REJECTED',
                'FAILED'
            )
        ),

    CONSTRAINT chk_document_status_history_change
        CHECK (from_status IS NULL OR from_status <> to_status)
);

CREATE INDEX idx_document_status_history_document_created
    ON document_status_history(document_id, created_at);


-- ============================================================
-- DOCUMENT ATTEMPTS
--
-- Technical attempt history. This is intentionally separate
-- from business-state history.
-- ============================================================

CREATE TABLE document_attempts
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    document_id         UUID NOT NULL,

    attempt_number      INTEGER NOT NULL,
    operation           VARCHAR(60) NOT NULL,

    result              VARCHAR(40),

    error_code          VARCHAR(100),
    error_message       TEXT,
    response_payload    JSONB,

    started_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at         TIMESTAMPTZ,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_document_attempts_document
        FOREIGN KEY (document_id)
            REFERENCES documents(id)
            ON DELETE RESTRICT,

    CONSTRAINT uq_document_attempts_operation_number
        UNIQUE (document_id, operation, attempt_number),

    CONSTRAINT chk_document_attempts_number
        CHECK (attempt_number > 0),

    CONSTRAINT chk_document_attempts_time
        CHECK (
            finished_at IS NULL
            OR finished_at >= started_at
        )
);

CREATE INDEX idx_document_attempts_document_started
    ON document_attempts(document_id, started_at);


-- ============================================================
-- OUTBOX - additional indexes for publishing
-- ============================================================

CREATE INDEX idx_outbox_events_unpublished_attempts
    ON outbox_events(attempts, created_at)
    WHERE published_at IS NULL;


-- ============================================================
-- COMMENTS - operational/documentation hints
-- ============================================================

COMMENT ON TABLE api_clients IS
    'Machine-to-machine credentials owned by a tenant. Raw client secrets are never persisted.';

COMMENT ON TABLE document_sequences IS
    'Atomic fiscal sequential counters scoped by point of issue, document type and environment.';

COMMENT ON TABLE documents IS
    'Shared fiscal document envelope and current processing state.';

COMMENT ON TABLE document_status_history IS
    'Append-only audit trail of fiscal document state transitions.';

COMMENT ON TABLE document_attempts IS
    'Technical execution attempts, separate from domain status history.';

COMMENT ON COLUMN invoices.customer_id IS
    'Optional reference to reusable customer master data. Invoice buyer_* columns are the historical source of truth.';

COMMENT ON COLUMN certificates.encrypted_content IS
    'Application-encrypted PKCS#12 certificate bytes. Never plaintext.';

COMMENT ON COLUMN certificates.encrypted_password IS
    'Application-encrypted certificate password. Never plaintext.';

COMMENT ON COLUMN webhook_endpoints.encrypted_secret IS
    'Application-encrypted HMAC signing secret. It must be decryptable by Emitta for outbound webhook signing.';
