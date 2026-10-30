ALTER TABLE public.ledger_funds_operation
 ADD COLUMN closed_without_effect BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN closure_receipt_id UUID UNIQUE,
 ADD COLUMN closure_observed_at TIMESTAMPTZ,
 ADD COLUMN not_after_epoch_millis BIGINT,
 ADD COLUMN canonical_intent JSONB,
 ADD COLUMN closed_posting_key VARCHAR(100),
 ADD CONSTRAINT complete_no_effect_receipt CHECK (
   (NOT closed_without_effect AND closure_receipt_id IS NULL AND closure_observed_at IS NULL
    AND not_after_epoch_millis IS NULL AND canonical_intent IS NULL AND closed_posting_key IS NULL)
   OR (closed_without_effect AND COALESCE(
     kind='RESERVE' AND source_account_id<>destination_account_id AND reservation_id IS NULL AND posting_id IS NULL AND closure_receipt_id IS NOT NULL
     AND closure_observed_at IS NOT NULL AND isfinite(closure_observed_at) AND not_after_epoch_millis BETWEEN 1 AND 253402300799999
     AND EXTRACT(EPOCH FROM closure_observed_at)*1000>=not_after_epoch_millis
     AND jsonb_typeof(canonical_intent)='object'
     AND canonical_intent - ARRAY['operationId','sourceAccountId','destinationAccountId','amount','currency','ttlSeconds','notAfterEpochMillis']='{}'::jsonb
     AND canonical_intent->>'operationId'=operation_id::text
     AND canonical_intent->>'sourceAccountId'=source_account_id::text
     AND canonical_intent->>'destinationAccountId'=destination_account_id::text
     AND jsonb_typeof(canonical_intent->'notAfterEpochMillis')='number'
     AND canonical_intent->>'notAfterEpochMillis'=not_after_epoch_millis::text
     AND jsonb_typeof(canonical_intent->'amount')='number' AND (canonical_intent->>'amount')::numeric>0
     AND (canonical_intent->>'amount')::numeric<100000000000000000000
     AND (canonical_intent->>'amount')::numeric=round((canonical_intent->>'amount')::numeric,18)
     AND (canonical_intent->>'currency')~'^[A-Z]{3}$'
     AND (canonical_intent->>'ttlSeconds')~'^[0-9]+$'
     AND jsonb_typeof(canonical_intent->'ttlSeconds')='number' AND (canonical_intent->>'ttlSeconds')::integer BETWEEN 1 AND 900
     AND fingerprint=encode(sha256(convert_to(concat_ws('|','RESERVE',source_account_id::text,destination_account_id::text,
        canonical_intent->>'amount',canonical_intent->>'currency',canonical_intent->>'ttlSeconds',not_after_epoch_millis::text),'UTF8')),'hex')
     AND closed_posting_key='fnd:'||encode(sha256(convert_to(tenant_id||'|'||operation_id::text||'|RESERVE','UTF8')),'hex'),FALSE))
 );

CREATE OR REPLACE FUNCTION public.funds_scope_matches(t TEXT,a UUID[],writes BOOLEAN DEFAULT FALSE)
RETURNS BOOLEAN LANGUAGE SQL STABLE SET search_path=pg_catalog AS $$
 SELECT COALESCE(current_setting('firefly.funds.tenant',true)=t
   AND current_setting('firefly.funds.purpose',true) IN ('ENROLL','READ','CONTROL','FUNDING','RESERVE','COMMIT','RELEASE','EXPIRE','REVERSE','RECONCILE')
   AND (NOT writes OR current_setting('firefly.funds.purpose',true) IN ('ENROLL','CONTROL','FUNDING','RESERVE','COMMIT','RELEASE','EXPIRE','REVERSE'))
   AND cardinality(a)>0
   AND COALESCE(NULLIF(current_setting('firefly.funds.accounts',true),''),'[]')::jsonb ?& ARRAY(SELECT x::text FROM unnest(a) x),FALSE)
$$;
-- RECONCILE can insert only a complete no-effect operation; it cannot update money or reservations.
ALTER POLICY funds_insert ON public.ledger_funds_operation WITH CHECK (
 public.funds_scope_matches(tenant_id,ARRAY[source_account_id,destination_account_id],TRUE)
 OR (closed_without_effect AND current_setting('firefly.funds.purpose',true)='RECONCILE'
     AND public.funds_scope_matches(tenant_id,ARRAY[source_account_id,destination_account_id],FALSE)));

CREATE FUNCTION public.guard_no_effect_operation() RETURNS TRIGGER LANGUAGE plpgsql SET search_path=pg_catalog AS $$
BEGIN
 IF TG_OP<>'INSERT' AND OLD.closed_without_effect THEN
   RAISE EXCEPTION 'IMMUTABLE_FUNDS_NO_EFFECT_RECEIPT' USING ERRCODE='23514';
 END IF;
 IF TG_OP<>'DELETE' AND NEW.closed_without_effect
   AND (TG_OP<>'INSERT' OR current_setting('firefly.funds.purpose',true) IS DISTINCT FROM 'RECONCILE') THEN
   RAISE EXCEPTION 'EXPLICIT_RECONCILIATION_RECEIPT_REQUIRED' USING ERRCODE='23514';
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION public.guard_no_effect_operation() FROM PUBLIC;
CREATE TRIGGER no_effect_operation_immutable BEFORE INSERT OR UPDATE OR DELETE ON public.ledger_funds_operation
 FOR EACH ROW EXECUTE FUNCTION public.guard_no_effect_operation();

CREATE OR REPLACE FUNCTION public.require_complete_funds_operation() RETURNS TRIGGER LANGUAGE plpgsql SET search_path=pg_catalog AS $$
DECLARE current_row public.ledger_funds_operation%ROWTYPE;
BEGIN
 SELECT * INTO current_row FROM public.ledger_funds_operation WHERE tenant_id=NEW.tenant_id AND operation_id=NEW.operation_id;
 IF NOT FOUND THEN RETURN NEW; END IF;
 IF current_row.closed_without_effect THEN
   IF EXISTS(SELECT 1 FROM public.ledger_funds_reservation WHERE tenant_id=current_row.tenant_id AND operation_id=current_row.operation_id)
      OR EXISTS(SELECT 1 FROM public.transaction WHERE external_reference=current_row.closed_posting_key)
      OR EXISTS(SELECT 1 FROM public.ledger_posting_request WHERE idempotency_key=current_row.closed_posting_key) THEN
      RAISE EXCEPTION 'NO_EFFECT_RECEIPT_HAS_FINANCIAL_EFFECT' USING ERRCODE='23514';
   END IF;
 ELSIF (current_row.kind='RESERVE' AND current_row.reservation_id IS NULL) OR (current_row.kind='FUNDING' AND current_row.posting_id IS NULL) THEN
   RAISE EXCEPTION 'INCOMPLETE_FUNDS_OPERATION' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE FUNCTION public.require_reservation_operation() RETURNS TRIGGER LANGUAGE plpgsql SET search_path=pg_catalog AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM public.ledger_funds_operation o WHERE o.tenant_id=NEW.tenant_id AND o.operation_id=NEW.operation_id
     AND o.kind='RESERVE' AND NOT o.closed_without_effect AND o.reservation_id=NEW.reservation_id
     AND o.source_account_id=NEW.source_account_id AND o.destination_account_id=NEW.destination_account_id) THEN
   RAISE EXCEPTION 'RESERVATION_REQUIRES_OPEN_MATCHING_OPERATION' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION public.require_complete_funds_operation(),public.require_reservation_operation() FROM PUBLIC;
CREATE CONSTRAINT TRIGGER reservation_operation_complete AFTER INSERT OR UPDATE ON public.ledger_funds_reservation
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.require_reservation_operation();

-- Hash-only metadata makes a committed closure fence visible to posting triggers without exposing hidden receipts.
CREATE TABLE funds_private.closed_posting(posting_key VARCHAR(100) PRIMARY KEY);
REVOKE ALL ON funds_private.closed_posting FROM PUBLIC;
CREATE FUNCTION public.stamp_closed_posting() RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
 IF NEW.closed_without_effect THEN INSERT INTO funds_private.closed_posting VALUES(NEW.closed_posting_key); END IF;
 RETURN NEW;
END $$;
CREATE FUNCTION public.guard_closed_funds_posting() RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE key TEXT;
BEGIN
 IF TG_TABLE_NAME='transaction' THEN key:=NEW.external_reference; ELSE key:=NEW.idempotency_key; END IF;
 IF EXISTS(SELECT 1 FROM funds_private.closed_posting p WHERE p.posting_key=key) THEN
   RAISE EXCEPTION 'FUNDS_OPERATION_CLOSED_WITHOUT_EFFECT' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION public.stamp_closed_posting(),public.guard_closed_funds_posting() FROM PUBLIC;
CREATE TRIGGER no_effect_posting_visibility AFTER INSERT ON public.ledger_funds_operation FOR EACH ROW EXECUTE FUNCTION public.stamp_closed_posting();
CREATE TRIGGER no_effect_header_fence BEFORE INSERT OR UPDATE OF external_reference ON public.transaction FOR EACH ROW EXECUTE FUNCTION public.guard_closed_funds_posting();
CREATE TRIGGER no_effect_claim_fence BEFORE INSERT OR UPDATE OF idempotency_key ON public.ledger_posting_request FOR EACH ROW EXECUTE FUNCTION public.guard_closed_funds_posting();
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='firefly_funds_policy_reader' AND NOT rolsuper AND NOT rolbypassrls AND NOT rolcanlogin)
    AND EXISTS(SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='firefly_funds_policy_writer' AND NOT rolsuper AND NOT rolbypassrls AND NOT rolcanlogin) THEN
   GRANT SELECT ON funds_private.closed_posting TO firefly_funds_policy_reader;
   GRANT INSERT ON funds_private.closed_posting TO firefly_funds_policy_writer;
   GRANT CREATE ON SCHEMA public TO firefly_funds_policy_reader,firefly_funds_policy_writer;
   ALTER FUNCTION public.guard_closed_funds_posting() OWNER TO firefly_funds_policy_reader;
   ALTER FUNCTION public.stamp_closed_posting() OWNER TO firefly_funds_policy_writer;
   REVOKE CREATE ON SCHEMA public FROM firefly_funds_policy_reader,firefly_funds_policy_writer;
 END IF;
END $$;

ALTER POLICY funds_select ON public.ledger_funds_event USING (public.funds_scoped_allowed(CASE WHEN event_type IN('BANK_CONTROL_FUNDING','FUNDS_ABSENT_CLOSED') THEN 'OPERATION' ELSE 'RESERVATION' END,tenant_id,resource_id,FALSE));
ALTER POLICY funds_insert ON public.ledger_funds_event WITH CHECK (
 public.funds_scoped_allowed(CASE WHEN event_type='BANK_CONTROL_FUNDING' THEN 'OPERATION' ELSE 'RESERVATION' END,tenant_id,resource_id,TRUE)
 OR (event_type='FUNDS_ABSENT_CLOSED' AND current_setting('firefly.funds.purpose',true)='RECONCILE' AND public.funds_scoped_allowed('OPERATION',tenant_id,resource_id,FALSE)));
ALTER POLICY funds_select ON public.event_outbox USING (CASE WHEN aggregate_type='MANAGED_FUNDS' THEN public.funds_scoped_allowed(CASE WHEN event_type IN('BANK_CONTROL_FUNDING','FUNDS_ABSENT_CLOSED') THEN 'OPERATION' ELSE 'RESERVATION' END,funds_tenant_id,aggregate_id::uuid,FALSE) ELSE TRUE END);
ALTER POLICY funds_insert ON public.event_outbox WITH CHECK (CASE WHEN aggregate_type='MANAGED_FUNDS' THEN
 public.funds_scoped_allowed(CASE WHEN event_type='BANK_CONTROL_FUNDING' THEN 'OPERATION' ELSE 'RESERVATION' END,funds_tenant_id,aggregate_id::uuid,TRUE)
 OR (event_type='FUNDS_ABSENT_CLOSED' AND current_setting('firefly.funds.purpose',true)='RECONCILE' AND public.funds_scoped_allowed('OPERATION',funds_tenant_id,aggregate_id::uuid,FALSE)) ELSE TRUE END);
