-- V50 did not convert status history and removed its transaction mapping.
-- Retain every legacy identifier without inventing a UUID association.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM transaction_status_history) THEN
        RAISE EXCEPTION 'LEDGER_HISTORY_UUID_REPAIR_REQUIRED: V50 removed the transaction UUID mapping. Preserve and reconcile every legacy status history row against pre-V50 transaction identity evidence before upgrading. No rows were changed.';
    END IF;
END $$;

ALTER TABLE transaction_status_history RENAME COLUMN transaction_status_history_id TO legacy_history_id;
ALTER TABLE transaction_status_history RENAME COLUMN transaction_id TO legacy_transaction_id;
ALTER TABLE transaction_status_history DROP CONSTRAINT transaction_status_history_pkey;
ALTER TABLE transaction_status_history ALTER COLUMN legacy_history_id DROP NOT NULL;
ALTER TABLE transaction_status_history ALTER COLUMN legacy_transaction_id DROP NOT NULL;
ALTER TABLE transaction_status_history ADD COLUMN transaction_status_history_id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE transaction_status_history ADD COLUMN transaction_id UUID NOT NULL REFERENCES transaction(transaction_id);
ALTER TABLE transaction_status_history ADD CONSTRAINT transaction_status_history_pkey PRIMARY KEY (transaction_status_history_id);
CREATE INDEX idx_status_history_transaction_uuid ON transaction_status_history(transaction_id);
COMMENT ON COLUMN transaction_status_history.legacy_transaction_id IS 'Original BIGINT transaction association preserved verbatim. V50 deleted the mapping; reconcile using retained pre-upgrade evidence before assigning a UUID.';
