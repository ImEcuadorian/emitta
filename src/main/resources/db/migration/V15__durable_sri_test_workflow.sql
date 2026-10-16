-- Auxiliary jobs only. No document states, reception attempts or certificates are changed.
ALTER TABLE emitta.processing_jobs DROP CONSTRAINT processing_jobs_kind_check;
ALTER TABLE emitta.processing_jobs ADD CONSTRAINT processing_jobs_kind_check
    CHECK (kind IN ('FISCAL','RIDE','SRI_TEST'));
