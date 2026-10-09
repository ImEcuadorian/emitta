-- Explicit operator-reviewed authority, bound to the certificate and current RUC.
-- Existing certificates deliberately remain unauthorized until reviewed locally.
ALTER TABLE certificates
    ADD COLUMN authorized_ruc VARCHAR(13),
    ADD COLUMN authorization_evidence_sha256 VARCHAR(64),
    ADD COLUMN authorized_at TIMESTAMPTZ,
    ADD COLUMN authorization_trust_anchor BYTEA;

ALTER TABLE certificates ADD CONSTRAINT chk_certificate_authorization
    CHECK ((authorized_ruc IS NULL AND authorization_evidence_sha256 IS NULL
            AND authorized_at IS NULL AND authorization_trust_anchor IS NULL)
        OR (authorized_ruc IS NOT NULL AND authorization_evidence_sha256 IS NOT NULL
            AND authorized_ruc ~ '^[0-9]{13}$'
            AND authorization_evidence_sha256 ~ '^[0-9a-f]{64}$'
            AND authorized_at IS NOT NULL AND authorization_trust_anchor IS NOT NULL));
