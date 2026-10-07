-- ============================================================
-- EMITTA
-- V7 - Document Artifacts
--
-- Stores metadata for fiscal document artifacts.
-- Artifact binary content is stored outside PostgreSQL.
-- ============================================================


CREATE TABLE document_artifacts
(
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    document_id     UUID NOT NULL,

    artifact_type   VARCHAR(40) NOT NULL,

    content_type    VARCHAR(100) NOT NULL,

    storage_key     VARCHAR(1000) NOT NULL,

    sha256          VARCHAR(64) NOT NULL,

    size_bytes      BIGINT NOT NULL,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_document_artifacts_document
        FOREIGN KEY (document_id)
            REFERENCES documents(id)
            ON DELETE RESTRICT,

    CONSTRAINT uq_document_artifacts_document_type
        UNIQUE (
                document_id,
                artifact_type
            ),

    CONSTRAINT chk_document_artifacts_type
        CHECK (
            artifact_type IN (
                              'UNSIGNED_XML',
                              'SIGNED_XML',
                              'AUTHORIZED_XML',
                              'RIDE_PDF'
                )
            ),

    CONSTRAINT chk_document_artifacts_content_type
        CHECK (
            length(trim(content_type)) > 0
            ),

    CONSTRAINT chk_document_artifacts_storage_key
        CHECK (
            length(trim(storage_key)) > 0
            ),

    CONSTRAINT chk_document_artifacts_sha256
        CHECK (
            sha256 ~ '^[0-9a-f]{64}$'
            ),

    CONSTRAINT chk_document_artifacts_size
        CHECK (
            size_bytes >= 0
            )
);


CREATE INDEX idx_document_artifacts_document
    ON document_artifacts(document_id);


COMMENT ON TABLE document_artifacts IS
    'Metadata for immutable fiscal document artifacts stored in external object storage.';

COMMENT ON COLUMN document_artifacts.storage_key IS
    'Internal object-storage key. It is not a public URL.';

COMMENT ON COLUMN document_artifacts.sha256 IS
    'SHA-256 digest of the exact stored artifact bytes.';