CREATE TABLE emitta.administrative_audit_events (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES emitta.tenants(id),
    actor_client_id UUID,
    action VARCHAR(60) NOT NULL,
    resource_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT administrative_audit_action CHECK (action IN ('CLIENT_CREATED','CLIENT_SECRET_ROTATED','CLIENT_REVOKED','CERTIFICATE_REGISTERED'))
);
CREATE INDEX administrative_audit_tenant_time ON emitta.administrative_audit_events(tenant_id,created_at DESC);
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='emitta_app') THEN
        GRANT SELECT, INSERT ON emitta.administrative_audit_events TO emitta_app;
        REVOKE UPDATE, DELETE ON emitta.administrative_audit_events FROM emitta_app;
    END IF;
END $$;
