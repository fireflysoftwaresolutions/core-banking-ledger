-- Preserve the original lowercase labels and support the already published API values.
ALTER TYPE transaction_type_enum ADD VALUE IF NOT EXISTS 'ach';
ALTER TYPE transaction_type_enum ADD VALUE IF NOT EXISTS 'card';
ALTER TYPE transaction_type_enum ADD VALUE IF NOT EXISTS 'direct_debit';
ALTER TYPE transaction_type_enum ADD VALUE IF NOT EXISTS 'sepa_transfer';
ALTER TYPE transaction_type_enum ADD VALUE IF NOT EXISTS 'crypto_deposit';
ALTER TYPE transaction_type_enum ADD VALUE IF NOT EXISTS 'crypto_withdrawal';
ALTER TYPE transaction_type_enum ADD VALUE IF NOT EXISTS 'crypto_transfer';
ALTER TYPE transaction_type_enum ADD VALUE IF NOT EXISTS 'crypto_swap';
ALTER TYPE transaction_type_enum ADD VALUE IF NOT EXISTS 'token_mint';
ALTER TYPE transaction_type_enum ADD VALUE IF NOT EXISTS 'token_burn';
ALTER TYPE transaction_type_enum ADD VALUE IF NOT EXISTS 'nft_transfer';
ALTER TYPE transaction_type_enum ADD VALUE IF NOT EXISTS 'staking';
ALTER TYPE transaction_type_enum ADD VALUE IF NOT EXISTS 'unstaking';
ALTER TYPE transaction_type_enum ADD VALUE IF NOT EXISTS 'reward';
