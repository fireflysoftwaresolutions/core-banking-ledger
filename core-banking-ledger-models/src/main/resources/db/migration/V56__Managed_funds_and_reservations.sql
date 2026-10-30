CREATE TABLE ledger_funds_account (
    account_id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    enrollment_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    currency_scale INTEGER NOT NULL CHECK (currency_scale BETWEEN 0 AND 18),
    currency_policy VARCHAR(100) NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('CUSTOMER','BANK_CONTROL')),
    state VARCHAR(20) NOT NULL DEFAULT 'BLOCKED' CHECK (state IN ('BLOCKED','ACTIVE','CLOSED')),
    booked NUMERIC(38,18) NOT NULL DEFAULT 0,
    held NUMERIC(38,18) NOT NULL DEFAULT 0 CHECK (held >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (kind = 'BANK_CONTROL' OR booked >= held),
    UNIQUE (tenant_id, enrollment_id)
);

CREATE FUNCTION guard_funds_enrollment() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(hashtextextended(NEW.account_id::text,55601));
    IF EXISTS(SELECT 1 FROM ledger_funds_account WHERE account_id=NEW.account_id) THEN RETURN NEW; END IF;
    IF EXISTS(SELECT 1 FROM transaction WHERE account_id=NEW.account_id)
       OR EXISTS(SELECT 1 FROM transaction_leg WHERE account_id=NEW.account_id) THEN
        RAISE EXCEPTION 'FUNDS_HISTORY_CUTOVER_REQUIRED' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER funds_enrollment_history BEFORE INSERT ON ledger_funds_account
FOR EACH ROW EXECUTE FUNCTION guard_funds_enrollment();

CREATE FUNCTION guard_managed_funds_write() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE old_data JSONB; new_data JSONB; item UUID; account_ids UUID[];
BEGIN
    IF TG_OP<>'INSERT' THEN old_data:=to_jsonb(OLD); END IF;
    IF TG_OP<>'DELETE' THEN new_data:=to_jsonb(NEW); END IF;
    SELECT array_agg(DISTINCT a ORDER BY a) INTO account_ids FROM (
        SELECT (old_data->>'account_id')::uuid AS a
        UNION SELECT (new_data->>'account_id')::uuid
        UNION SELECT t.account_id FROM transaction t WHERE t.transaction_id IN (
            (old_data->>'transaction_id')::uuid,(new_data->>'transaction_id')::uuid)
        UNION SELECT l.account_id FROM transaction_leg l WHERE l.transaction_id IN (
            (old_data->>'transaction_id')::uuid,(new_data->>'transaction_id')::uuid)
    ) q WHERE a IS NOT NULL;
    IF account_ids IS NOT NULL THEN
        FOREACH item IN ARRAY account_ids LOOP
            PERFORM pg_advisory_xact_lock(hashtextextended(item::text,55601));
        END LOOP;
        IF EXISTS(SELECT 1 FROM ledger_funds_account WHERE account_id=ANY(account_ids)) THEN
            RAISE EXCEPTION 'MANAGED_FUNDS_COMMAND_REQUIRED' USING ERRCODE='23514';
        END IF;
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER managed_funds_header_fence BEFORE INSERT OR UPDATE OR DELETE ON transaction FOR EACH ROW EXECUTE FUNCTION guard_managed_funds_write();
CREATE TRIGGER managed_funds_leg_fence BEFORE INSERT OR UPDATE OR DELETE ON transaction_leg FOR EACH ROW EXECUTE FUNCTION guard_managed_funds_write();
CREATE TRIGGER managed_funds_history_fence BEFORE INSERT OR UPDATE OR DELETE ON transaction_status_history FOR EACH ROW EXECUTE FUNCTION guard_managed_funds_write();

CREATE TABLE ledger_funds_reservation (
    reservation_id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    operation_id UUID NOT NULL,
    source_account_id UUID NOT NULL REFERENCES ledger_funds_account(account_id),
    destination_account_id UUID NOT NULL REFERENCES ledger_funds_account(account_id),
    amount NUMERIC(38,18) NOT NULL CHECK(amount>0),
    currency VARCHAR(3) NOT NULL,
    state VARCHAR(20) NOT NULL CHECK(state IN ('HELD','COMMITTED','RELEASED','EXPIRED','REVERSED')),
    expires_at TIMESTAMPTZ NOT NULL,
    posting_id UUID NOT NULL UNIQUE REFERENCES transaction(transaction_id),
    reversal_id UUID UNIQUE REFERENCES transaction(transaction_id),
    version BIGINT NOT NULL DEFAULT 0,
    CHECK(source_account_id<>destination_account_id),
    UNIQUE(tenant_id,operation_id)
);
CREATE INDEX ledger_funds_due ON ledger_funds_reservation(expires_at) WHERE state='HELD';
CREATE TABLE ledger_funds_operation (
    tenant_id VARCHAR(64) NOT NULL,
    operation_id UUID NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK(kind IN ('RESERVE','FUNDING')),
    fingerprint CHAR(64) NOT NULL,
    reservation_id UUID REFERENCES ledger_funds_reservation(reservation_id),
    posting_id UUID REFERENCES transaction(transaction_id),
    PRIMARY KEY(tenant_id,operation_id)
);
CREATE FUNCTION require_complete_funds_operation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS(SELECT 1 FROM ledger_funds_operation WHERE tenant_id=NEW.tenant_id AND operation_id=NEW.operation_id
        AND ((kind='RESERVE' AND reservation_id IS NULL) OR (kind='FUNDING' AND posting_id IS NULL))) THEN
        RAISE EXCEPTION 'INCOMPLETE_FUNDS_OPERATION' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER complete_funds_operation AFTER INSERT OR UPDATE ON ledger_funds_operation
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_complete_funds_operation();
CREATE TABLE ledger_funds_action (
    tenant_id VARCHAR(64) NOT NULL,
    action_id UUID NOT NULL,
    reservation_id UUID NOT NULL REFERENCES ledger_funds_reservation(reservation_id),
    action VARCHAR(20) NOT NULL,
    PRIMARY KEY(tenant_id,action_id)
);
CREATE TABLE ledger_funds_event (
    event_id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    resource_id UUID NOT NULL,
    actor VARCHAR(200) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    payload JSONB NOT NULL
);
CREATE FUNCTION immutable_funds_audit() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'IMMUTABLE_FUNDS_AUDIT' USING ERRCODE='23514'; END $$;
CREATE TRIGGER immutable_funds_event BEFORE UPDATE OR DELETE ON ledger_funds_event FOR EACH ROW EXECUTE FUNCTION immutable_funds_audit();
CREATE TRIGGER immutable_funds_action BEFORE UPDATE OR DELETE ON ledger_funds_action FOR EACH ROW EXECUTE FUNCTION immutable_funds_audit();

CREATE TABLE ledger_funds_control (
    tenant_id VARCHAR(64) NOT NULL,
    action_id UUID NOT NULL,
    account_id UUID NOT NULL REFERENCES ledger_funds_account(account_id),
    state VARCHAR(20) NOT NULL CHECK(state IN ('BLOCKED','ACTIVE','CLOSED')),
    PRIMARY KEY(tenant_id,action_id)
);
CREATE TRIGGER immutable_funds_control BEFORE UPDATE OR DELETE ON ledger_funds_control FOR EACH ROW EXECUTE FUNCTION immutable_funds_audit();
