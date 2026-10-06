-- ============================================================
-- EMITTA
-- V4 - Outbox claiming and retry support
-- ============================================================

ALTER TABLE outbox_events
    ADD COLUMN claimed_at TIMESTAMPTZ,
    ADD COLUMN claimed_by VARCHAR(100),
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN last_error TEXT;

ALTER TABLE outbox_events
    ADD CONSTRAINT chk_outbox_claim_consistency
        CHECK (
            (claimed_at IS NULL AND claimed_by IS NULL)
                OR
            (claimed_at IS NOT NULL AND claimed_by IS NOT NULL)
            );

CREATE INDEX idx_outbox_events_claimable
    ON outbox_events(next_attempt_at, created_at)
    WHERE published_at IS NULL;