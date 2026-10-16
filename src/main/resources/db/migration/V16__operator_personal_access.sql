-- Personal sign-in is bound to an existing privileged client, never public registration.
CREATE TABLE emitta.operator_personal_access (
    api_client_id UUID PRIMARY KEY REFERENCES emitta.api_clients(id),
    email VARCHAR(254) NOT NULL UNIQUE CHECK (email = lower(email)),
    password_hash VARCHAR(255) NOT NULL,
    failures INTEGER NOT NULL DEFAULT 0 CHECK (failures >= 0),
    locked_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='emitta_app') THEN
        GRANT SELECT, INSERT, UPDATE ON emitta.operator_personal_access TO emitta_app;
    END IF;
END $$;
