-- ============================================================
-- EMITTA
-- V6 - API client authorization scopes
-- ============================================================

CREATE TABLE api_client_scopes
(
    api_client_id UUID NOT NULL,
    scope         VARCHAR(100) NOT NULL,

    CONSTRAINT pk_api_client_scopes
        PRIMARY KEY (
            api_client_id,
            scope
        ),

    CONSTRAINT fk_api_client_scopes_client
        FOREIGN KEY (api_client_id)
            REFERENCES api_clients(id)
            ON DELETE CASCADE,

    CONSTRAINT chk_api_client_scope
        CHECK (
            scope ~ '^[a-z0-9][a-z0-9._-]*:[a-z0-9][a-z0-9._-]*$'
        )
);

CREATE INDEX idx_api_client_scopes_scope
    ON api_client_scopes(scope);