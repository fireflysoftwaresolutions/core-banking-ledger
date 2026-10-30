CREATE UNIQUE INDEX idx_managed_posting_original_reversal
    ON transaction(related_transaction_id)
    WHERE managed_posting AND relation_type = 'REVERSAL';
