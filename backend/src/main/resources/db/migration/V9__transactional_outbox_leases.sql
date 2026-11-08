ALTER TABLE transactional_outbox
    ADD COLUMN lease_token UUID;

CREATE INDEX ix_transactional_outbox_expired_leases
    ON transactional_outbox(status, claimed_at)
    WHERE status = 'PROCESSING';
