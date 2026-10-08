-- ============================================================
-- EMITTA
-- V9 - Explicit signing certificate selection
--
-- A taxpayer may store multiple certificates for rotation and
-- audit purposes, but exactly one certificate can be selected
-- as the current signing certificate.
--
-- Documents also retain the certificate selected for signing so
-- the fiscal operation remains auditable after future rotations.
-- ============================================================


-- ============================================================
-- CERTIFICATES
--
-- Required by composite foreign keys below. It guarantees that
-- a certificate reference always belongs to the expected
-- taxpayer.
-- ============================================================

ALTER TABLE certificates
    ADD CONSTRAINT uq_certificates_taxpayer_id_id
        UNIQUE (taxpayer_id, id);


-- ============================================================
-- TAXPAYERS
--
-- Explicit pointer to the certificate currently configured for
-- new fiscal signatures.
--
-- NULL is allowed because onboarding may exist before a signing
-- certificate is configured.
-- ============================================================

ALTER TABLE taxpayers
    ADD COLUMN active_signing_certificate_id UUID;

ALTER TABLE taxpayers
    ADD CONSTRAINT fk_taxpayers_active_signing_certificate
        FOREIGN KEY (
                     id,
                     active_signing_certificate_id
            )
            REFERENCES certificates (
                                     taxpayer_id,
                                     id
                )
            ON DELETE RESTRICT;


-- ============================================================
-- DOCUMENTS
--
-- Once a document reaches the signing stage, Emitta stores the
-- exact certificate selected for that fiscal operation.
--
-- This must not silently change when the taxpayer later rotates
-- its active certificate.
-- ============================================================

ALTER TABLE documents
    ADD COLUMN signing_certificate_id UUID;

ALTER TABLE documents
    ADD CONSTRAINT fk_documents_signing_certificate
        FOREIGN KEY (
                     taxpayer_id,
                     signing_certificate_id
            )
            REFERENCES certificates (
                                     taxpayer_id,
                                     id
                )
            ON DELETE RESTRICT;

CREATE INDEX idx_documents_signing_certificate
    ON documents(signing_certificate_id);