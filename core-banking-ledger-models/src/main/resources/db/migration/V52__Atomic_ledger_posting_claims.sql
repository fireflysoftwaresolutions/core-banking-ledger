ALTER TABLE transaction ADD COLUMN managed_posting BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE transaction ALTER COLUMN total_amount TYPE NUMERIC(38,18);
ALTER TABLE transaction_leg ALTER COLUMN amount TYPE NUMERIC(38,18);

CREATE TABLE ledger_posting_request (
    idempotency_key VARCHAR(100) PRIMARY KEY,
    input_fingerprint CHAR(64) NOT NULL,
    transaction_id UUID UNIQUE REFERENCES transaction(transaction_id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ledger_posting_request IS 'Durable atomic posting input claims; committed rows reference complete ledger aggregates';
