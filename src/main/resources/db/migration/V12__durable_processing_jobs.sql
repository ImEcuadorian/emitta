-- Auxiliary durable work. No fiscal document or certificate is modified by this migration.
-- Default table grants must not let the runtime account rewrite Flyway's audit trail.
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='emitta_app') THEN
  REVOKE INSERT, UPDATE, DELETE ON emitta.flyway_schema_history FROM emitta_app;
 END IF;
END $$;
CREATE TABLE emitta.processing_jobs (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 tenant_id UUID NOT NULL REFERENCES emitta.tenants(id),
 document_id UUID NOT NULL REFERENCES emitta.documents(id),
 kind VARCHAR(16) NOT NULL CHECK(kind IN ('FISCAL','RIDE')),
 state VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','RUNNING','COMPLETED','DEAD','BLOCKED')),
 attempts INTEGER NOT NULL DEFAULT 0 CHECK(attempts>=0),
 retry_budget_from INTEGER NOT NULL DEFAULT 0 CHECK(retry_budget_from>=0 AND retry_budget_from<=attempts),
 recoveries INTEGER NOT NULL DEFAULT 0 CHECK(recoveries>=0),
 next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 lease_until TIMESTAMPTZ, lease_token UUID,
 failure_category VARCHAR(32),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(document_id,kind)
);
CREATE INDEX idx_processing_jobs_due ON emitta.processing_jobs(kind,next_attempt_at) WHERE state IN ('PENDING','RUNNING');
CREATE INDEX idx_processing_jobs_tenant ON emitta.processing_jobs(tenant_id,document_id);
CREATE TABLE emitta.processing_job_attempts (
 job_id UUID NOT NULL REFERENCES emitta.processing_jobs(id),
 attempt_number INTEGER NOT NULL CHECK(attempt_number>0),
 lease_token UUID NOT NULL,
 started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 finished_at TIMESTAMPTZ,
 result VARCHAR(32),
 PRIMARY KEY(job_id,attempt_number)
);
