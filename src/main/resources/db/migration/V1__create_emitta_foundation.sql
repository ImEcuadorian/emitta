-- ============================================================
-- EMITTA
-- V1 - Foundation
-- ============================================================


-- ============================================================
-- TENANTS
--
-- Cada organización que consume Emitta.
-- ============================================================

CREATE TABLE tenants
(
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name            VARCHAR(150) NOT NULL,

    status          VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_tenants_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED'))
);


-- ============================================================
-- TAXPAYERS
--
-- Contribuyentes/emisores asociados a un tenant.
-- ============================================================

CREATE TABLE taxpayers
(
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    tenant_id       UUID NOT NULL,

    ruc             VARCHAR(13) NOT NULL,

    legal_name      VARCHAR(300) NOT NULL,
    trade_name      VARCHAR(300),

    main_address    VARCHAR(500),

    environment     VARCHAR(20) NOT NULL DEFAULT 'TEST',

    active          BOOLEAN NOT NULL DEFAULT TRUE,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_taxpayer_tenant
        FOREIGN KEY (tenant_id)
            REFERENCES tenants(id),

    CONSTRAINT uq_taxpayer_tenant_ruc
        UNIQUE (tenant_id, ruc),

    CONSTRAINT chk_taxpayer_ruc
        CHECK (ruc ~ '^[0-9]{13}$'),

    CONSTRAINT chk_taxpayer_environment
        CHECK (environment IN ('TEST', 'PRODUCTION'))
);


CREATE INDEX idx_taxpayers_tenant
    ON taxpayers(tenant_id);


-- ============================================================
-- OUTBOX
--
-- Base del patrón Transactional Outbox.
-- ============================================================

CREATE TABLE outbox_events
(
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    tenant_id       UUID,

    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    UUID NOT NULL,

    event_type      VARCHAR(150) NOT NULL,

    payload         JSONB NOT NULL,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    published_at    TIMESTAMPTZ,

    attempts        INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT fk_outbox_tenant
        FOREIGN KEY (tenant_id)
            REFERENCES tenants(id),

    CONSTRAINT chk_outbox_attempts
        CHECK (attempts >= 0)
);


CREATE INDEX idx_outbox_pending
    ON outbox_events(created_at)
    WHERE published_at IS NULL;


CREATE INDEX idx_outbox_tenant
    ON outbox_events(tenant_id);