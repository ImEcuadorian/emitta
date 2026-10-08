-- Immutable authorization evidence returned by Ecuador SRI.

CREATE TABLE emitta.sri_authorizations
(
    document_id UUID PRIMARY KEY,

    authorization_number VARCHAR(49) NOT NULL,
    authorized_at TIMESTAMPTZ NOT NULL,
    environment VARCHAR(20) NOT NULL,

    authorized_xml_sha256 VARCHAR(64) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_sri_authorizations_document
        FOREIGN KEY (document_id)
            REFERENCES emitta.documents(id)
            ON DELETE RESTRICT,

    CONSTRAINT chk_sri_authorization_number
        CHECK (
            authorization_number ~ '^[0-9]{49}$'
            ),

    CONSTRAINT chk_sri_authorization_environment
        CHECK (
            environment IN ('TEST', 'PRODUCTION')
            ),

    CONSTRAINT chk_sri_authorization_sha256
        CHECK (
            authorized_xml_sha256 ~ '^[0-9a-f]{64}$'
            )
);

CREATE TABLE emitta.sri_authorization_messages
(
    document_id UUID NOT NULL,
    ordinal INTEGER NOT NULL,

    identifier VARCHAR(100) NOT NULL,
    message TEXT NOT NULL,
    additional_information TEXT,
    type VARCHAR(40) NOT NULL,

    PRIMARY KEY (document_id, ordinal),

    CONSTRAINT fk_sri_authorization_messages
        FOREIGN KEY (document_id)
            REFERENCES emitta.sri_authorizations(document_id)
            ON DELETE RESTRICT,

    CONSTRAINT chk_sri_authorization_message_ordinal
        CHECK (ordinal > 0)
);