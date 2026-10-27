DO $$ BEGIN
 IF current_setting('server_encoding') <> 'UTF8' THEN
  RAISE EXCEPTION 'FND_RECEIPT_UTF8_REQUIRED' USING ERRCODE='23514';
 END IF;
END $$;

SET LOCAL lock_timeout='5s';
LOCK TABLE public.ledger_funds_account,public.ledger_funds_operation,public.ledger_funds_reservation,
 public.ledger_funds_action,public.ledger_posting_request,public.transaction,public.transaction_leg,
 public.transaction_status_history,public.ledger_funds_event,public.event_outbox IN ACCESS EXCLUSIVE MODE;
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='firefly_funds_receipt_guard'
  AND NOT rolcanlogin AND NOT rolsuper AND NOT rolbypassrls)
 OR NOT EXISTS(SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='firefly_funds_receipt_api'
  AND NOT rolcanlogin AND NOT rolsuper AND NOT rolbypassrls) THEN
  RAISE EXCEPTION 'FND_RECEIPT_ROLES_REQUIRED' USING ERRCODE='42501';
 END IF;
END $$;
ALTER TABLE public.ledger_funds_reservation ADD COLUMN receipt_contract_version SMALLINT NOT NULL DEFAULT 0
 CHECK(receipt_contract_version IN(0,1));

CREATE TYPE public.fnd_snapshot_v1 AS (account_id UUID,enrollment_id UUID,contract_id UUID,
 currency TEXT,currency_scale INTEGER,currency_policy TEXT,kind TEXT);
CREATE TYPE public.fnd_balances_v1 AS (booked_before NUMERIC,held_before NUMERIC,booked_after NUMERIC,held_after NUMERIC);
CREATE TYPE public.fnd_accepted_reserve_v1 AS (tenant TEXT,reservation_id UUID,operation_id UUID,
 source_account_id UUID,destination_account_id UUID,amount NUMERIC,currency TEXT,ttl_seconds INTEGER,
 not_after_epoch_millis BIGINT,intent_recorded_at_epoch_millis BIGINT,expires_at_epoch_millis BIGINT,
 source_snapshot public.fnd_snapshot_v1,destination_snapshot public.fnd_snapshot_v1);

CREATE FUNCTION public.fnd_require(valid BOOLEAN) RETURNS VOID
 LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog AS $$
BEGIN
 IF valid IS NOT TRUE THEN RAISE EXCEPTION 'FND_RECEIPT_INVALID_CANONICAL' USING ERRCODE='23514'; END IF;
END $$;

CREATE FUNCTION public.fnd_utf16_length(value TEXT) RETURNS BIGINT
 LANGUAGE SQL IMMUTABLE SET search_path=pg_catalog AS $$
 SELECT COALESCE(sum(CASE WHEN ascii(c)>65535 THEN 2 ELSE 1 END),0)
 FROM regexp_split_to_table(value,'') AS chars(c) WHERE c<>''
$$;
CREATE FUNCTION public.fnd_is_blank(value TEXT) RETURNS BOOLEAN
 LANGUAGE SQL IMMUTABLE SET search_path=pg_catalog AS $$
 SELECT NOT EXISTS(SELECT 1 FROM regexp_split_to_table(value,'') AS chars(c)
 WHERE c<>'' AND ascii(c) NOT BETWEEN 9 AND 13 AND ascii(c) NOT BETWEEN 28 AND 32
 AND ascii(c)<>5760 AND ascii(c) NOT BETWEEN 8192 AND 8198 AND ascii(c) NOT BETWEEN 8200 AND 8202
 AND ascii(c) NOT IN(8232,8233,8287,12288))
$$;
CREATE FUNCTION public.fnd_text_valid(value TEXT,max_bytes INTEGER) RETURNS BOOLEAN
 LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog AS $$
BEGIN
 IF value IS NULL OR max_bytes IS NULL OR max_bytes<0 OR octet_length(value)>max_bytes THEN RETURN FALSE; END IF;
 RETURN NOT public.fnd_is_blank(value);
END
$$;
CREATE FUNCTION public.fnd_lp_text(value TEXT,max_bytes INTEGER DEFAULT 16384) RETURNS BYTEA
 LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog AS $$
BEGIN
 PERFORM public.fnd_require(max_bytes BETWEEN 0 AND 16384);
 IF value IS NULL THEN RETURN int4send(-1); END IF;
 PERFORM public.fnd_require(octet_length(value)<=max_bytes);
 RETURN int4send(octet_length(value))||convert_to(value,'UTF8');
END $$;
CREATE FUNCTION public.fnd_b64(value BYTEA,max_encoded INTEGER DEFAULT 65536) RETURNS TEXT
 LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog AS $$
DECLARE result TEXT;
BEGIN
 PERFORM public.fnd_require(value IS NOT NULL AND max_encoded BETWEEN 4 AND 65536);
 PERFORM public.fnd_require(octet_length(value)<=max_encoded*3/4);
 result:=replace(encode(value,'base64'),E'\n','');
 PERFORM public.fnd_require(length(result)<=max_encoded);
 RETURN result;
END $$;
CREATE FUNCTION public.fnd_numeric_text(value NUMERIC) RETURNS TEXT
 LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog AS $$
BEGIN
 PERFORM public.fnd_require(value IS NOT NULL AND value<>'NaN'::numeric
  AND value NOT IN('Infinity'::numeric,'-Infinity'::numeric)
  AND abs(value)<100000000000000000000::numeric AND value=round(value,18));
 RETURN trim_scale(value)::text;
END $$;
CREATE FUNCTION public.fnd_minor_text(value NUMERIC,actual_scale INTEGER) RETURNS TEXT
 LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog AS $$
DECLARE scaled NUMERIC;
BEGIN
 PERFORM public.fnd_numeric_text(value);
 PERFORM public.fnd_require(actual_scale BETWEEN 0 AND 18);
 scaled:=value*power(10::numeric,actual_scale);
 PERFORM public.fnd_require(scaled=trunc(scaled));
 RETURN trim_scale(scaled)::text;
END $$;
CREATE FUNCTION public.fnd_snapshot_bytes(s public.fnd_snapshot_v1) RETURNS BYTEA
 LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog AS $$
BEGIN
 PERFORM public.fnd_require(s.account_id IS NOT NULL AND s.enrollment_id IS NOT NULL AND s.contract_id IS NOT NULL
  AND (s.currency COLLATE "C")~'^[A-Z]{3}$' AND s.currency_scale BETWEEN 0 AND 18
  AND s.kind IN('CUSTOMER','BANK_CONTROL'));
 PERFORM public.fnd_require(public.fnd_text_valid(s.currency_policy,400));
 PERFORM public.fnd_require(public.fnd_utf16_length(s.currency_policy)<=100);
 RETURN public.fnd_lp_text(s.account_id::text)||public.fnd_lp_text(s.enrollment_id::text)
  ||public.fnd_lp_text(s.contract_id::text)||public.fnd_lp_text(s.currency)
  ||public.fnd_lp_text(s.currency_scale::text)||public.fnd_lp_text(s.currency_policy)||public.fnd_lp_text(s.kind);
END $$;
CREATE FUNCTION public.fnd_balances_bytes(b public.fnd_balances_v1) RETURNS BYTEA
 LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog AS $$
BEGIN
 RETURN public.fnd_lp_text(public.fnd_numeric_text(b.booked_before))
  ||public.fnd_lp_text(public.fnd_numeric_text(b.held_before))
  ||public.fnd_lp_text(public.fnd_numeric_text(b.booked_after))
  ||public.fnd_lp_text(public.fnd_numeric_text(b.held_after));
END $$;
CREATE FUNCTION public.fnd_intent_bytes(tenant TEXT,operation UUID,source UUID,destination UUID,
 amount NUMERIC,currency TEXT,ttl INTEGER,deadline BIGINT) RETURNS BYTEA
 LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog AS $$
DECLARE result BYTEA;
BEGIN
 PERFORM public.fnd_numeric_text(amount);
 PERFORM public.fnd_require((tenant COLLATE "C")~'^[A-Za-z0-9_-]{1,64}$'
  AND operation IS NOT NULL AND source IS NOT NULL AND destination IS NOT NULL AND source<>destination
  AND amount>0 AND (currency COLLATE "C")~'^[A-Z]{3}$' AND ttl BETWEEN 1 AND 900
  AND (deadline IS NULL OR deadline BETWEEN 1 AND 253402300799999));
 result:=public.fnd_lp_text('FND_RESERVE_INTENT_V1')||public.fnd_lp_text(tenant)
  ||public.fnd_lp_text(operation::text)||public.fnd_lp_text(source::text)||public.fnd_lp_text(destination::text)
  ||public.fnd_lp_text(public.fnd_numeric_text(amount))||public.fnd_lp_text(currency)
  ||public.fnd_lp_text(ttl::text)||public.fnd_lp_text(deadline::text);
 PERFORM public.fnd_b64(result,4096);
 RETURN result;
END $$;
CREATE FUNCTION public.fnd_accepted_bytes(a public.fnd_accepted_reserve_v1) RETURNS BYTEA
 LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog AS $$
DECLARE original BYTEA;source_bytes BYTEA;destination_bytes BYTEA;result BYTEA;
BEGIN
 original:=public.fnd_intent_bytes(a.tenant,a.operation_id,a.source_account_id,a.destination_account_id,
  a.amount,a.currency,a.ttl_seconds,a.not_after_epoch_millis);
 source_bytes:=public.fnd_snapshot_bytes(a.source_snapshot);
 destination_bytes:=public.fnd_snapshot_bytes(a.destination_snapshot);
 PERFORM public.fnd_require(a.reservation_id IS NOT NULL
  AND a.intent_recorded_at_epoch_millis BETWEEN 1 AND 253402300799999
  AND a.expires_at_epoch_millis BETWEEN 1 AND 253402300799999);
 PERFORM public.fnd_require(a.expires_at_epoch_millis>a.intent_recorded_at_epoch_millis
  AND a.expires_at_epoch_millis=least(a.intent_recorded_at_epoch_millis+a.ttl_seconds*1000::bigint,
   coalesce(a.not_after_epoch_millis,253402300799999))
  AND (a.source_snapshot).account_id=a.source_account_id AND (a.destination_snapshot).account_id=a.destination_account_id
  AND (a.source_snapshot).enrollment_id<>(a.destination_snapshot).enrollment_id
  AND (a.source_snapshot).currency=a.currency AND (a.destination_snapshot).currency=a.currency
  AND (a.source_snapshot).kind='CUSTOMER' AND (a.destination_snapshot).kind='CUSTOMER');
 PERFORM public.fnd_minor_text(a.amount,(a.source_snapshot).currency_scale);
 PERFORM public.fnd_minor_text(a.amount,(a.destination_snapshot).currency_scale);
 result:=public.fnd_lp_text('FND_ACCEPTED_RESERVE_V1')||public.fnd_lp_text(public.fnd_b64(original,4096))
  ||public.fnd_lp_text(a.reservation_id::text)||public.fnd_lp_text(a.intent_recorded_at_epoch_millis::text)
  ||public.fnd_lp_text(a.expires_at_epoch_millis::text)||source_bytes||destination_bytes;
 PERFORM public.fnd_b64(result,16384);
 RETURN result;
END $$;
CREATE FUNCTION public.fnd_receipt_bytes(receipt UUID,event UUID,tenant TEXT,reservation UUID,operation UUID,
 kind TEXT,action UUID,a public.fnd_accepted_reserve_v1,posting UUID,original_receipt UUID,original_posting UUID,
 recorded BIGINT,amount NUMERIC,currency TEXT,source_snapshot public.fnd_snapshot_v1,
 destination_snapshot public.fnd_snapshot_v1,source_minor TEXT,destination_minor TEXT,
 flow_from UUID,flow_to UUID,sb public.fnd_balances_v1,db public.fnd_balances_v1,issuer TEXT,subject TEXT)
 RETURNS BYTEA LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog AS $$
DECLARE accepted_bytes BYTEA;source_bytes BYTEA;destination_bytes BYTEA;source_balances BYTEA;
 destination_balances BYTEA;reverse BOOLEAN;result BYTEA;
BEGIN
 accepted_bytes:=public.fnd_accepted_bytes(a);
 PERFORM public.fnd_numeric_text(amount);
 source_bytes:=public.fnd_snapshot_bytes(source_snapshot);
 destination_bytes:=public.fnd_snapshot_bytes(destination_snapshot);
 source_balances:=public.fnd_balances_bytes(sb);destination_balances:=public.fnd_balances_bytes(db);
 PERFORM public.fnd_require(receipt IS NOT NULL AND event=receipt AND action IS NOT NULL AND posting IS NOT NULL
  AND (tenant COLLATE "C")=(a.tenant COLLATE "C") AND reservation=a.reservation_id AND operation=a.operation_id
  AND kind IN('COMMIT','REVERSE') AND recorded BETWEEN 1 AND 253402300799999
  AND recorded>=a.intent_recorded_at_epoch_millis AND amount=a.amount AND currency=a.currency
  AND source_bytes=public.fnd_snapshot_bytes(a.source_snapshot)
  AND destination_bytes=public.fnd_snapshot_bytes(a.destination_snapshot)
  AND source_minor=public.fnd_minor_text(amount,source_snapshot.currency_scale)
  AND destination_minor=public.fnd_minor_text(amount,destination_snapshot.currency_scale)
  AND public.fnd_text_valid(issuer,2048) AND public.fnd_text_valid(subject,800)
  AND sb.held_before>=0 AND sb.held_after>=0 AND db.held_before>=0 AND db.held_after>=0
  AND sb.booked_before>=sb.held_before AND sb.booked_after>=sb.held_after
  AND db.booked_before>=db.held_before AND db.booked_after>=db.held_after);
 PERFORM public.fnd_minor_text(sb.booked_before,source_snapshot.currency_scale);
 PERFORM public.fnd_minor_text(sb.held_before,source_snapshot.currency_scale);
 PERFORM public.fnd_minor_text(sb.booked_after,source_snapshot.currency_scale);
 PERFORM public.fnd_minor_text(sb.held_after,source_snapshot.currency_scale);
 PERFORM public.fnd_minor_text(db.booked_before,destination_snapshot.currency_scale);
 PERFORM public.fnd_minor_text(db.held_before,destination_snapshot.currency_scale);
 PERFORM public.fnd_minor_text(db.booked_after,destination_snapshot.currency_scale);
 PERFORM public.fnd_minor_text(db.held_after,destination_snapshot.currency_scale);
 reverse:=kind='REVERSE';
 IF reverse THEN
  PERFORM public.fnd_require(original_receipt IS NOT NULL AND original_posting IS NOT NULL
   AND receipt<>original_receipt AND posting<>original_posting);
 ELSE
  PERFORM public.fnd_require(original_receipt IS NULL AND original_posting IS NULL AND recorded<a.expires_at_epoch_millis);
 END IF;
 PERFORM public.fnd_require(flow_from=CASE WHEN reverse THEN destination_snapshot.account_id ELSE source_snapshot.account_id END
  AND flow_to=CASE WHEN reverse THEN source_snapshot.account_id ELSE destination_snapshot.account_id END
  AND sb.booked_after=sb.booked_before+CASE WHEN reverse THEN amount ELSE -amount END
  AND db.booked_after=db.booked_before+CASE WHEN reverse THEN -amount ELSE amount END
  AND sb.held_after=CASE WHEN reverse THEN sb.held_before ELSE sb.held_before-amount END
  AND db.held_after=db.held_before);
 result:=public.fnd_lp_text('FND_MONETARY_RECEIPT_V1')||public.fnd_lp_text(receipt::text)
  ||public.fnd_lp_text(tenant)||public.fnd_lp_text(reservation::text)||public.fnd_lp_text(operation::text)
  ||public.fnd_lp_text(kind)||public.fnd_lp_text(action::text)||public.fnd_lp_text(public.fnd_b64(accepted_bytes,16384))
  ||public.fnd_lp_text(posting::text)||public.fnd_lp_text(original_receipt::text)||public.fnd_lp_text(original_posting::text)
  ||public.fnd_lp_text(recorded::text)||public.fnd_lp_text(public.fnd_numeric_text(amount))||public.fnd_lp_text(currency)
  ||source_bytes||destination_bytes||public.fnd_lp_text(source_minor)||public.fnd_lp_text(destination_minor)
  ||public.fnd_lp_text(flow_from::text)||public.fnd_lp_text(flow_to::text)||source_balances||destination_balances
  ||public.fnd_lp_text(issuer)||public.fnd_lp_text(subject);
 PERFORM public.fnd_b64(result,65536);
 RETURN result;
END $$;

CREATE FUNCTION public.fnd_key_gate_held(posting_key TEXT) RETURNS BOOLEAN
 LANGUAGE SQL VOLATILE SET search_path=pg_catalog AS $$
 SELECT COALESCE((posting_key COLLATE "C")~'^fnd:[0-9a-f]{64}$' AND EXISTS(
  SELECT 1 FROM pg_catalog.pg_locks l
  WHERE l.locktype='advisory' AND l.pid=pg_catalog.pg_backend_pid() AND l.granted
   AND l.mode='ExclusiveLock' AND l.objsubid=1
   AND l.database=(SELECT d.oid FROM pg_catalog.pg_database d WHERE d.datname=pg_catalog.current_database())
   AND l.classid=((pg_catalog.hashtextextended(posting_key COLLATE "C",55603)>>32)&4294967295)::oid
   AND l.objid=(pg_catalog.hashtextextended(posting_key COLLATE "C",55603)&4294967295)::oid),FALSE)
$$;
CREATE FUNCTION public.fnd_lock_posting_key(posting_key TEXT) RETURNS VOID
 LANGUAGE plpgsql VOLATILE SET search_path=pg_catalog AS $$
BEGIN
 PERFORM public.fnd_require((posting_key COLLATE "C")~'^fnd:[0-9a-f]{64}$');
 PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(posting_key COLLATE "C",55603));
END $$;
CREATE FUNCTION public.fnd_require_posting_key_gate(posting_key TEXT) RETURNS VOID
 LANGUAGE plpgsql VOLATILE SET search_path=pg_catalog AS $$
BEGIN
 IF public.fnd_key_gate_held(posting_key) IS NOT TRUE THEN
  RAISE EXCEPTION 'FND_POSTING_KEY_GATE_REQUIRED' USING ERRCODE='23514';
 END IF;
END $$;
REVOKE ALL ON FUNCTION public.fnd_lock_posting_key(TEXT) FROM PUBLIC;

CREATE TABLE public.ledger_funds_reserve_intent (
 tenant_id VARCHAR(64) NOT NULL,
 reservation_id UUID PRIMARY KEY REFERENCES public.ledger_funds_reservation(reservation_id) DEFERRABLE INITIALLY DEFERRED,
 operation_id UUID NOT NULL,source_account_id UUID NOT NULL,destination_account_id UUID NOT NULL,
 amount NUMERIC(38,18) NOT NULL,currency VARCHAR(3) NOT NULL,ttl_seconds SMALLINT NOT NULL,
 not_after_epoch_millis BIGINT,intent_recorded_at_epoch_millis BIGINT NOT NULL,expires_at_epoch_millis BIGINT NOT NULL,
 source_snapshot public.fnd_snapshot_v1 NOT NULL,destination_snapshot public.fnd_snapshot_v1 NOT NULL,
 command_id UUID NOT NULL UNIQUE,intent_canonical TEXT NOT NULL,intent_digest CHAR(64) NOT NULL,
 accepted_canonical TEXT NOT NULL,accepted_digest CHAR(64) NOT NULL,
 UNIQUE(tenant_id,operation_id),UNIQUE(tenant_id,reservation_id),
 CHECK((intent_canonical=public.fnd_b64(public.fnd_intent_bytes(tenant_id,operation_id,source_account_id,
  destination_account_id,amount,currency,ttl_seconds,not_after_epoch_millis),4096)) IS TRUE),
 CHECK((intent_digest=encode(sha256(public.fnd_intent_bytes(tenant_id,operation_id,source_account_id,
  destination_account_id,amount,currency,ttl_seconds,not_after_epoch_millis)),'hex')) IS TRUE),
 CHECK((accepted_canonical=public.fnd_b64(public.fnd_accepted_bytes(ROW(tenant_id,reservation_id,operation_id,
  source_account_id,destination_account_id,amount,currency,ttl_seconds,not_after_epoch_millis,
  intent_recorded_at_epoch_millis,expires_at_epoch_millis,source_snapshot,destination_snapshot)
  ::public.fnd_accepted_reserve_v1),16384)) IS TRUE),
 CHECK((accepted_digest=encode(sha256(public.fnd_accepted_bytes(ROW(tenant_id,reservation_id,operation_id,
  source_account_id,destination_account_id,amount,currency,ttl_seconds,not_after_epoch_millis,
  intent_recorded_at_epoch_millis,expires_at_epoch_millis,source_snapshot,destination_snapshot)
  ::public.fnd_accepted_reserve_v1)),'hex')) IS TRUE)
);
CREATE TABLE public.ledger_funds_monetary_receipt (
 receipt_id UUID PRIMARY KEY,event_id UUID NOT NULL UNIQUE CHECK(event_id=receipt_id),
 tenant_id VARCHAR(64) NOT NULL,reservation_id UUID NOT NULL,operation_id UUID NOT NULL,
 effect_kind VARCHAR(7) NOT NULL CHECK(effect_kind IN('COMMIT','REVERSE')),action_id UUID NOT NULL,
 posting_id UUID NOT NULL UNIQUE REFERENCES public.transaction(transaction_id),
 original_commit_receipt_id UUID REFERENCES public.ledger_funds_monetary_receipt(receipt_id),
 original_posting_id UUID REFERENCES public.transaction(transaction_id),
 effect_recorded_at_epoch_millis BIGINT NOT NULL CHECK(effect_recorded_at_epoch_millis BETWEEN 1 AND 253402300799999),
 amount NUMERIC(38,18) NOT NULL CHECK(amount>0 AND amount<100000000000000000000),currency VARCHAR(3) NOT NULL,
 source_snapshot public.fnd_snapshot_v1 NOT NULL,destination_snapshot public.fnd_snapshot_v1 NOT NULL,
 source_minor_units VARCHAR(38) NOT NULL,destination_minor_units VARCHAR(38) NOT NULL,
 from_account_id UUID NOT NULL,to_account_id UUID NOT NULL,
 source_balances public.fnd_balances_v1 NOT NULL,destination_balances public.fnd_balances_v1 NOT NULL,
 actor_issuer TEXT NOT NULL,actor_subject TEXT NOT NULL,
 accepted_canonical TEXT NOT NULL,receipt_canonical TEXT NOT NULL,receipt_digest CHAR(64) NOT NULL,
 command_id UUID NOT NULL UNIQUE,
 UNIQUE(tenant_id,reservation_id,effect_kind),
 FOREIGN KEY(tenant_id,reservation_id) REFERENCES public.ledger_funds_reserve_intent(tenant_id,reservation_id),
 FOREIGN KEY(tenant_id,action_id) REFERENCES public.ledger_funds_action(tenant_id,action_id),
 CHECK((effect_kind='COMMIT' AND original_commit_receipt_id IS NULL AND original_posting_id IS NULL)
  OR (effect_kind='REVERSE' AND original_commit_receipt_id IS NOT NULL AND original_posting_id IS NOT NULL
   AND original_commit_receipt_id<>receipt_id AND original_posting_id<>posting_id)),
 CHECK(public.fnd_text_valid(actor_issuer,2048) AND public.fnd_text_valid(actor_subject,800) AND char_length(actor_subject)<=200),
 CHECK((currency COLLATE "C")~'^[A-Z]{3}$'),
 CHECK((source_minor_units COLLATE "C")~'^[1-9][0-9]{0,37}$' AND (destination_minor_units COLLATE "C")~'^[1-9][0-9]{0,37}$'),
 CHECK(octet_length(accepted_canonical)<=16384 AND octet_length(receipt_canonical)<=65536),
 CHECK((receipt_digest COLLATE "C")~'^[0-9a-f]{64}$')
);

CREATE TABLE funds_private.receipt_claim_insert (
 kind TEXT NOT NULL CHECK(kind IN('OPERATION','ACTION')),tenant_id TEXT NOT NULL,claim_id UUID NOT NULL,
 created_xid XID8 NOT NULL,executor_role NAME NOT NULL,new_row JSONB NOT NULL,
 PRIMARY KEY(kind,tenant_id,claim_id)
);
CREATE TABLE funds_private.receipt_command (
 command_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),created_xid XID8 NOT NULL UNIQUE,executor_role NAME NOT NULL,
 tenant_id TEXT NOT NULL,operation_id UUID NOT NULL,reservation_id UUID NOT NULL,
 contract_version SMALLINT NOT NULL CHECK(contract_version IN(0,1)),
 purpose TEXT NOT NULL CHECK(purpose IN('RESERVE','COMMIT','RELEASE','EXPIRE','REVERSE')),
 action_id UUID,recorded_at TIMESTAMPTZ NOT NULL,recorded_at_epoch_millis BIGINT NOT NULL,
 token_expires_epoch_millis BIGINT NOT NULL,actor_issuer TEXT NOT NULL,actor_subject TEXT NOT NULL,
 source_account_id UUID NOT NULL,destination_account_id UUID NOT NULL,
 source_before JSONB NOT NULL,destination_before JSONB NOT NULL,reservation_before JSONB,original_intent JSONB,
 expected_posting_key VARCHAR(100) NOT NULL,expires_at TIMESTAMPTZ,expires_at_epoch_millis BIGINT,
 CHECK((purpose='RESERVE' AND action_id IS NULL AND reservation_before IS NULL AND original_intent IS NOT NULL
   AND contract_version=1 AND expires_at IS NOT NULL AND expires_at_epoch_millis IS NOT NULL)
  OR (purpose<>'RESERVE' AND action_id IS NOT NULL AND reservation_before IS NOT NULL AND original_intent IS NULL)),
 CHECK(recorded_at_epoch_millis BETWEEN 1 AND 253402300799999 AND isfinite(recorded_at)),
 CHECK(extract(epoch FROM recorded_at)*1000=recorded_at_epoch_millis),
 CHECK(token_expires_epoch_millis BETWEEN 1 AND 253402300799999 AND token_expires_epoch_millis>recorded_at_epoch_millis),
 CHECK(source_account_id<>destination_account_id)
);
CREATE TABLE funds_private.receipt_row_witness (
 command_id UUID NOT NULL REFERENCES funds_private.receipt_command(command_id),
 ordinal BIGINT GENERATED ALWAYS AS IDENTITY,
 relation_kind TEXT NOT NULL CHECK(relation_kind IN('ACCOUNT','RESERVATION','HEADER','LEG','HISTORY',
  'POSTING_CLAIM','OPERATION','INTENT','RECEIPT','AUDIT','OUTBOX')),
 row_key TEXT NOT NULL,operation TEXT NOT NULL CHECK(operation IN('INSERT','UPDATE','DELETE')),
 old_row JSONB,new_row JSONB,PRIMARY KEY(command_id,ordinal),
 CHECK((operation='INSERT' AND old_row IS NULL AND new_row IS NOT NULL)
  OR (operation='UPDATE' AND old_row IS NOT NULL AND new_row IS NOT NULL)
  OR (operation='DELETE' AND old_row IS NOT NULL AND new_row IS NULL))
);
CREATE TABLE funds_private.receipt_binding (
 kind TEXT NOT NULL CHECK(kind IN('RESERVATION','POSTING','POSTING_CLAIM','OPERATION','EVENT')),
 tenant_id TEXT NOT NULL,resource_key TEXT NOT NULL,operation_id UUID NOT NULL,reservation_id UUID NOT NULL,
 source_account_id UUID NOT NULL,destination_account_id UUID NOT NULL,
 contract_version SMALLINT NOT NULL CHECK(contract_version IN(0,1)),
 PRIMARY KEY(kind,tenant_id,resource_key),CHECK(source_account_id<>destination_account_id)
);
REVOKE ALL ON funds_private.receipt_claim_insert,funds_private.receipt_command,
 funds_private.receipt_row_witness,funds_private.receipt_binding FROM PUBLIC;

CREATE FUNCTION public.fnd_immutable_retained_row() RETURNS TRIGGER
 LANGUAGE plpgsql SET search_path=pg_catalog AS $$
BEGIN
 RAISE EXCEPTION 'FND_IMMUTABLE_RETAINED_EVIDENCE' USING ERRCODE='23514';
END $$;
REVOKE ALL ON FUNCTION public.fnd_immutable_retained_row() FROM PUBLIC;
CREATE TRIGGER fnd_original_immutable BEFORE UPDATE OR DELETE ON public.ledger_funds_reserve_intent
 FOR EACH ROW EXECUTE FUNCTION public.fnd_immutable_retained_row();
CREATE TRIGGER fnd_original_no_truncate BEFORE TRUNCATE ON public.ledger_funds_reserve_intent
 FOR EACH STATEMENT EXECUTE FUNCTION public.fnd_immutable_retained_row();
CREATE TRIGGER fnd_receipt_immutable BEFORE UPDATE OR DELETE ON public.ledger_funds_monetary_receipt
 FOR EACH ROW EXECUTE FUNCTION public.fnd_immutable_retained_row();
CREATE TRIGGER fnd_receipt_no_truncate BEFORE TRUNCATE ON public.ledger_funds_monetary_receipt
 FOR EACH STATEMENT EXECUTE FUNCTION public.fnd_immutable_retained_row();
ALTER TABLE public.ledger_funds_reserve_intent ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ledger_funds_reserve_intent FORCE ROW LEVEL SECURITY;
CREATE POLICY fnd_original_read ON public.ledger_funds_reserve_intent FOR SELECT
 USING(public.funds_scope_matches(tenant_id,ARRAY[source_account_id,destination_account_id],FALSE));
ALTER TABLE public.ledger_funds_monetary_receipt ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ledger_funds_monetary_receipt FORCE ROW LEVEL SECURITY;
CREATE POLICY fnd_receipt_read ON public.ledger_funds_monetary_receipt FOR SELECT
 USING(public.funds_scope_matches(tenant_id,ARRAY[(source_snapshot).account_id,(destination_snapshot).account_id],FALSE));

CREATE TYPE public.fnd_admission_v1 AS (command_id UUID,reservation_id UUID,
 recorded_at_epoch_millis BIGINT,expires_at TIMESTAMPTZ,contract_version SMALLINT);
CREATE FUNCTION public.fnd_posting_key(tenant TEXT,operation UUID,kind TEXT) RETURNS TEXT
 LANGUAGE SQL IMMUTABLE SET search_path=pg_catalog AS $$
 SELECT 'fnd:'||encode(sha256(convert_to(tenant||'|'||operation::text||'|'||kind,'UTF8')),'hex')
$$;
CREATE FUNCTION public.fnd_account_snapshot(a JSONB) RETURNS public.fnd_snapshot_v1
 LANGUAGE SQL IMMUTABLE SET search_path=pg_catalog AS $$
 SELECT ROW((a->>'account_id')::uuid,(a->>'enrollment_id')::uuid,(a->>'contract_id')::uuid,
 a->>'currency',(a->>'currency_scale')::integer,a->>'currency_policy',a->>'kind')::public.fnd_snapshot_v1
$$;
CREATE FUNCTION public.fnd_assert_live_context(tenant TEXT,purpose TEXT,source UUID,destination UUID) RETURNS VOID
 LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE resources JSONB;expiration BIGINT;principal NAME:=session_user;
BEGIN
 resources:=COALESCE(NULLIF(current_setting('firefly.funds.accounts',true),''),'[]')::jsonb;
 expiration:=NULLIF(current_setting('firefly.funds.token_expires_epoch_millis',true),'')::bigint;
 IF tenant IS DISTINCT FROM current_setting('firefly.funds.tenant',true)
 OR purpose IS DISTINCT FROM current_setting('firefly.funds.purpose',true)
 OR source IS NULL OR destination IS NULL OR source=destination
 OR jsonb_typeof(resources)<>'array' OR jsonb_array_length(resources)<>2
 OR NOT resources ?& ARRAY[source::text,destination::text]
 OR expiration IS NULL OR expiration NOT BETWEEN 1 AND 253402300799999
 OR expiration<=floor(extract(epoch FROM clock_timestamp())*1000)::bigint
 OR NOT public.fnd_text_valid(current_setting('firefly.funds.actor_issuer',true),2048)
 OR NOT public.fnd_text_valid(current_setting('firefly.funds.actor_subject',true),800)
 OR char_length(current_setting('firefly.funds.actor_subject',true))>200
 OR NOT EXISTS(SELECT 1 FROM pg_catalog.pg_roles WHERE rolname=principal AND NOT rolsuper AND NOT rolbypassrls)
 OR NOT pg_catalog.pg_has_role(principal,'firefly_funds_receipt_api','USAGE')
 OR pg_catalog.pg_has_role(principal,'firefly_funds_receipt_guard','MEMBER')
 OR pg_catalog.pg_has_role(principal,'firefly_funds_policy_reader','MEMBER')
 OR pg_catalog.pg_has_role(principal,'firefly_funds_policy_writer','MEMBER')
 OR pg_catalog.has_schema_privilege(principal,'funds_private','USAGE') THEN
  RAISE EXCEPTION 'FND_CURRENT_AUTHORITY_REQUIRED' USING ERRCODE='42501';
 END IF;
END $$;
CREATE FUNCTION public.fnd_active_command() RETURNS funds_private.receipt_command
 LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE c funds_private.receipt_command;key UUID;
BEGIN
 key:=NULLIF(current_setting('firefly.funds.receipt_command',true),'')::uuid;
 SELECT * INTO c FROM funds_private.receipt_command WHERE command_id=key
  AND created_xid=pg_current_xact_id() AND executor_role=session_user;
 IF NOT FOUND THEN RAISE EXCEPTION 'FND_NATIVE_COMMAND_REQUIRED' USING ERRCODE='42501'; END IF;
 PERFORM public.fnd_assert_live_context(c.tenant_id,c.purpose,c.source_account_id,c.destination_account_id);
 IF c.actor_issuer IS DISTINCT FROM current_setting('firefly.funds.actor_issuer',true)
 OR c.actor_subject IS DISTINCT FROM current_setting('firefly.funds.actor_subject',true)
 OR c.token_expires_epoch_millis IS DISTINCT FROM NULLIF(current_setting('firefly.funds.token_expires_epoch_millis',true),'')::bigint THEN
  RAISE EXCEPTION 'FND_COMMAND_CONTEXT_CHANGED' USING ERRCODE='42501';
 END IF;
 RETURN c;
END $$;
CREATE FUNCTION public.fnd_command_allows(t TEXT,s UUID,d UUID,p TEXT) RETURNS BOOLEAN
 LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE c funds_private.receipt_command;
BEGIN
 c:=public.fnd_active_command();
 RETURN c.tenant_id=t AND c.source_account_id=s AND c.destination_account_id=d AND c.purpose=p;
END $$;
CREATE FUNCTION public.fnd_stamp_claim_insert() RETURNS TRIGGER
 LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE kind TEXT;identity UUID;
BEGIN
 IF TG_TABLE_NAME='ledger_funds_operation' THEN
  IF NEW.kind<>'RESERVE' THEN RETURN NEW; END IF;kind:='OPERATION';identity:=NEW.operation_id;
 ELSE kind:='ACTION';identity:=NEW.action_id;END IF;
 INSERT INTO funds_private.receipt_claim_insert VALUES(kind,NEW.tenant_id,identity,pg_current_xact_id(),session_user,to_jsonb(NEW));
 RETURN NEW;
END $$;
CREATE TRIGGER fnd_operation_insert_witness AFTER INSERT ON public.ledger_funds_operation
 FOR EACH ROW EXECUTE FUNCTION public.fnd_stamp_claim_insert();
CREATE TRIGGER fnd_action_insert_witness AFTER INSERT ON public.ledger_funds_action
 FOR EACH ROW EXECUTE FUNCTION public.fnd_stamp_claim_insert();
CREATE FUNCTION public.fnd_lock_accounts(t TEXT,s UUID,d UUID) RETURNS VOID
 LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE account UUID;
BEGIN
 FOR account IN SELECT x FROM unnest(ARRAY[s,d]) x ORDER BY x::text LOOP
  PERFORM pg_advisory_xact_lock(hashtextextended(account::text,55601));
  PERFORM 1 FROM public.ledger_funds_account WHERE tenant_id=t AND account_id=account FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'FND_ACCOUNT_UNAVAILABLE' USING ERRCODE='42501'; END IF;
 END LOOP;
END $$;
CREATE FUNCTION public.fnd_begin_reserve(t TEXT,operation UUID,source UUID,destination UUID,
 amount NUMERIC,currency TEXT,ttl INTEGER,deadline BIGINT) RETURNS public.fnd_admission_v1
 LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE s public.ledger_funds_account;d public.ledger_funds_account;o public.ledger_funds_operation;
 command UUID:=gen_random_uuid();reservation UUID:=gen_random_uuid();now_raw TIMESTAMPTZ;recorded TIMESTAMPTZ;
 recorded_ms BIGINT;expires_ms BIGINT;expires TIMESTAMPTZ;key TEXT;fingerprint TEXT;
BEGIN
 PERFORM public.fnd_assert_live_context(t,'RESERVE',source,destination);
 PERFORM public.fnd_intent_bytes(t,operation,source,destination,amount,currency,ttl,deadline);
 PERFORM pg_advisory_xact_lock(hashtextextended(t||':'||operation::text,55602));
 PERFORM public.fnd_lock_accounts(t,source,destination);
 SELECT * INTO s FROM public.ledger_funds_account WHERE tenant_id=t AND account_id=source;
 SELECT * INTO d FROM public.ledger_funds_account WHERE tenant_id=t AND account_id=destination;
 SELECT * INTO o FROM public.ledger_funds_operation WHERE tenant_id=t AND operation_id=operation FOR UPDATE;
 fingerprint:=encode(sha256(convert_to('RESERVE|'||source::text||'|'||destination::text||'|'
  ||public.fnd_numeric_text(amount)||'|'||currency||'|'||ttl::text||'|'||coalesce(deadline::text,'null'),'UTF8')),'hex');
 PERFORM public.fnd_require(o.kind='RESERVE' AND NOT o.closed_without_effect AND o.reservation_id IS NULL
  AND o.fingerprint=fingerprint AND o.source_account_id=source AND o.destination_account_id=destination
  AND EXISTS(SELECT 1 FROM funds_private.receipt_claim_insert w WHERE w.kind='OPERATION' AND w.tenant_id=t
   AND w.claim_id=operation AND w.created_xid=pg_current_xact_id() AND w.executor_role=session_user
   AND w.new_row=to_jsonb(o)));
 key:=public.fnd_posting_key(t,operation,'RESERVE');PERFORM public.fnd_lock_posting_key(key);
 PERFORM 1 FROM public.ledger_posting_request WHERE idempotency_key=key FOR UPDATE;
 IF FOUND THEN RAISE EXCEPTION 'FND_POSTING_KEY_CONFLICT' USING ERRCODE='23514'; END IF;
 PERFORM public.fnd_require(s.kind='CUSTOMER' AND d.kind='CUSTOMER' AND s.state='ACTIVE' AND d.state='ACTIVE'
  AND s.currency=currency AND d.currency=currency AND s.booked-s.held>=amount);
 PERFORM public.fnd_minor_text(amount,s.currency_scale);PERFORM public.fnd_minor_text(amount,d.currency_scale);
 now_raw:=clock_timestamp();recorded:=date_trunc('milliseconds',now_raw);
 recorded_ms:=floor(extract(epoch FROM recorded)*1000)::bigint;
 PERFORM public.fnd_assert_live_context(t,'RESERVE',source,destination);
 expires_ms:=least(recorded_ms+ttl*1000::bigint,coalesce(deadline,253402300799999));
 IF (expires_ms>recorded_ms) IS NOT TRUE THEN
  RAISE EXCEPTION 'FND_RESERVATION_DEADLINE_ELAPSED' USING ERRCODE='23514';
 END IF;
 expires:=recorded+(expires_ms-recorded_ms)*INTERVAL '1 millisecond';
 INSERT INTO funds_private.receipt_command VALUES(command,pg_current_xact_id(),session_user,t,operation,reservation,1,
  'RESERVE',NULL,recorded,recorded_ms,current_setting('firefly.funds.token_expires_epoch_millis')::bigint,
  current_setting('firefly.funds.actor_issuer'),current_setting('firefly.funds.actor_subject'),source,destination,
  to_jsonb(s),to_jsonb(d),NULL,jsonb_build_object('amount',amount,'currency',currency,'ttlSeconds',ttl,'notAfterEpochMillis',deadline),
  key,expires,expires_ms);
 PERFORM set_config('firefly.funds.receipt_command',command::text,true);
 RETURN ROW(command,reservation,recorded_ms,expires,1)::public.fnd_admission_v1;
END $$;
CREATE FUNCTION public.fnd_begin_transition(t TEXT,reservation UUID,p_action UUID,purpose TEXT)
 RETURNS public.fnd_admission_v1 LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE r public.ledger_funds_reservation;s public.ledger_funds_account;d public.ledger_funds_account;
 command UUID:=gen_random_uuid();key TEXT;original_key TEXT;now_raw TIMESTAMPTZ;recorded TIMESTAMPTZ;recorded_ms BIGINT;
BEGIN
 SELECT * INTO r FROM public.ledger_funds_reservation WHERE tenant_id=t AND reservation_id=reservation;
 IF NOT FOUND THEN RAISE EXCEPTION 'FND_RESERVATION_UNAVAILABLE' USING ERRCODE='42501'; END IF;
 PERFORM public.fnd_assert_live_context(t,purpose,r.source_account_id,r.destination_account_id);
 PERFORM pg_advisory_xact_lock(hashtextextended(t||':'||r.operation_id::text,55602));
 PERFORM public.fnd_lock_accounts(t,r.source_account_id,r.destination_account_id);
 SELECT * INTO r FROM public.ledger_funds_reservation WHERE tenant_id=t AND reservation_id=reservation FOR UPDATE;
 SELECT * INTO s FROM public.ledger_funds_account WHERE tenant_id=t AND account_id=r.source_account_id;
 SELECT * INTO d FROM public.ledger_funds_account WHERE tenant_id=t AND account_id=r.destination_account_id;
 PERFORM public.fnd_require(purpose IN('COMMIT','RELEASE','EXPIRE','REVERSE') AND EXISTS(
  SELECT 1 FROM public.ledger_funds_action a JOIN funds_private.receipt_claim_insert w
  ON w.kind='ACTION' AND w.tenant_id=a.tenant_id AND w.claim_id=a.action_id
  WHERE a.tenant_id=t AND a.action_id=p_action AND a.reservation_id=reservation AND a.action=purpose
   AND w.created_xid=pg_current_xact_id() AND w.executor_role=session_user));
 original_key:=public.fnd_posting_key(t,r.operation_id,'RESERVE');key:=CASE WHEN purpose='REVERSE'
  THEN public.fnd_posting_key(t,r.operation_id,'REVERSE') ELSE original_key END;
 FOR original_key IN SELECT x FROM unnest(ARRAY[original_key,key]) x GROUP BY x ORDER BY x LOOP
  PERFORM public.fnd_lock_posting_key(original_key);
  PERFORM 1 FROM public.ledger_posting_request WHERE idempotency_key=original_key FOR UPDATE;
 END LOOP;
 PERFORM 1 FROM public.transaction WHERE transaction_id=r.posting_id FOR UPDATE;
 PERFORM 1 FROM public.transaction_leg WHERE transaction_id=r.posting_id ORDER BY transaction_leg_id FOR KEY SHARE;
 now_raw:=clock_timestamp();recorded:=date_trunc('milliseconds',now_raw);
 recorded_ms:=floor(extract(epoch FROM recorded)*1000)::bigint;
 PERFORM public.fnd_assert_live_context(t,purpose,r.source_account_id,r.destination_account_id);
 IF purpose IN('COMMIT','RELEASE','EXPIRE') AND r.state IS DISTINCT FROM 'HELD' THEN
  RAISE EXCEPTION 'FND_RESERVATION_IS_TERMINAL' USING ERRCODE='23514';
 END IF;
 IF purpose='REVERSE' AND r.state IS DISTINCT FROM 'COMMITTED' THEN
  RAISE EXCEPTION 'FND_ONLY_COMMITTED_FUNDS_CAN_REVERSE' USING ERRCODE='23514';
 END IF;
 IF (purpose<>'COMMIT' OR now_raw<r.expires_at) IS NOT TRUE THEN
  RAISE EXCEPTION 'FND_RESERVATION_EXPIRED' USING ERRCODE='23514';
 END IF;
 IF (purpose<>'EXPIRE' OR now_raw>=r.expires_at) IS NOT TRUE THEN
  RAISE EXCEPTION 'FND_RESERVATION_NOT_DUE' USING ERRCODE='23514';
 END IF;
 IF (purpose NOT IN('COMMIT','REVERSE') OR (s.state='ACTIVE' AND d.state='ACTIVE')) IS NOT TRUE THEN
  RAISE EXCEPTION 'FND_FUNDS_ACCOUNT_BLOCKED' USING ERRCODE='23514';
 END IF;
 IF (purpose<>'REVERSE' OR d.booked-d.held>=r.amount) IS NOT TRUE THEN
  RAISE EXCEPTION 'FND_REVERSAL_REQUIRES_AVAILABLE_RECIPIENT_FUNDS' USING ERRCODE='23514';
 END IF;
 INSERT INTO funds_private.receipt_command VALUES(command,pg_current_xact_id(),session_user,t,r.operation_id,reservation,
  r.receipt_contract_version,purpose,p_action,recorded,recorded_ms,current_setting('firefly.funds.token_expires_epoch_millis')::bigint,
  current_setting('firefly.funds.actor_issuer'),current_setting('firefly.funds.actor_subject'),r.source_account_id,r.destination_account_id,
  to_jsonb(s),to_jsonb(d),to_jsonb(r),NULL,key,NULL,NULL);
 PERFORM set_config('firefly.funds.receipt_command',command::text,true);
 RETURN ROW(command,reservation,recorded_ms,r.expires_at,r.receipt_contract_version)::public.fnd_admission_v1;
END $$;

CREATE FUNCTION public.fnd_guard_reservation() RETURNS TRIGGER
 LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE c funds_private.receipt_command;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'FND_IMMUTABLE_RESERVATION' USING ERRCODE='23514'; END IF;
 c:=public.fnd_active_command();
 IF TG_OP='INSERT' THEN
  NEW.receipt_contract_version:=1;
  PERFORM public.fnd_require(c.contract_version=1 AND c.purpose='RESERVE' AND NEW.state='HELD' AND NEW.version=0
   AND NEW.reservation_id=c.reservation_id AND NEW.operation_id=c.operation_id AND NEW.tenant_id=c.tenant_id
   AND NEW.source_account_id=c.source_account_id AND NEW.destination_account_id=c.destination_account_id
   AND NEW.amount=(c.original_intent->>'amount')::numeric AND NEW.currency=c.original_intent->>'currency'
   AND NEW.expires_at=c.expires_at AND NEW.reversal_id IS NULL);
 ELSE
  PERFORM public.fnd_require(NEW.receipt_contract_version=OLD.receipt_contract_version
   AND (to_jsonb(NEW)-ARRAY['state','version','reversal_id'])=(to_jsonb(OLD)-ARRAY['state','version','reversal_id'])
   AND NEW.version=OLD.version+1 AND NEW.reservation_id=c.reservation_id AND NEW.tenant_id=c.tenant_id
   AND c.contract_version=OLD.receipt_contract_version
   AND ((OLD.state='HELD' AND NEW.state=CASE c.purpose WHEN 'COMMIT' THEN 'COMMITTED'
    WHEN 'RELEASE' THEN 'RELEASED' WHEN 'EXPIRE' THEN 'EXPIRED' END AND NEW.reversal_id IS NULL)
    OR (OLD.state='COMMITTED' AND NEW.state='REVERSED' AND c.purpose='REVERSE' AND NEW.reversal_id IS NOT NULL)));
 END IF;
 RETURN NEW;
END $$;
CREATE FUNCTION public.fnd_guard_account_money() RETURNS TRIGGER
 LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE c funds_private.receipt_command;before JSONB;purpose TEXT;
BEGIN
 PERFORM public.fnd_require((to_jsonb(NEW)-ARRAY['booked','held','version','state'])=(to_jsonb(OLD)-ARRAY['booked','held','version','state']));
 IF NEW.booked IS NOT DISTINCT FROM OLD.booked AND NEW.held IS NOT DISTINCT FROM OLD.held THEN RETURN NEW; END IF;
 purpose:=current_setting('firefly.funds.purpose',true);
 IF purpose='FUNDING' AND public.funds_scope_matches(NEW.tenant_id,ARRAY[NEW.account_id],TRUE) THEN RETURN NEW; END IF;
 c:=public.fnd_active_command();
 before:=CASE WHEN NEW.account_id=c.source_account_id THEN c.source_before
  WHEN NEW.account_id=c.destination_account_id THEN c.destination_before END;
 PERFORM public.fnd_require(before IS NOT NULL AND to_jsonb(OLD)=before AND NEW.tenant_id=c.tenant_id
  AND (to_jsonb(NEW)-ARRAY['booked','held','version'])=(to_jsonb(OLD)-ARRAY['booked','held','version'])
  AND NEW.version=OLD.version+1);
 RETURN NEW;
END $$;
CREATE FUNCTION public.fnd_guard_posting_claim() RETURNS TRIGGER
 LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE key TEXT;c funds_private.receipt_command;
BEGIN
 key:=CASE WHEN TG_OP='DELETE' THEN OLD.idempotency_key ELSE NEW.idempotency_key END;
 IF key NOT LIKE 'fnd:%' AND (TG_OP='INSERT' OR OLD.idempotency_key NOT LIKE 'fnd:%') THEN RETURN NEW; END IF;
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'FND_IMMUTABLE_POSTING_CLAIM' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' THEN
  PERFORM public.fnd_require(NEW.idempotency_key=OLD.idempotency_key);
  PERFORM public.fnd_require_posting_key_gate(key);
  PERFORM public.fnd_require(OLD.transaction_id IS NULL AND NEW.transaction_id IS NOT NULL
   AND (to_jsonb(NEW)-'transaction_id')=(to_jsonb(OLD)-'transaction_id')
   AND EXISTS(SELECT 1 FROM public.transaction h WHERE h.transaction_id=NEW.transaction_id
    AND h.managed_posting AND h.external_reference=key));
 ELSE
  PERFORM public.fnd_lock_posting_key(key);
  PERFORM public.fnd_require(NEW.transaction_id IS NULL AND (NEW.input_fingerprint COLLATE "C")~'^[0-9a-f]{64}$');
 END IF;
 IF current_setting('firefly.funds.purpose',true)='FUNDING' AND EXISTS(
  SELECT 1 FROM public.ledger_funds_operation o WHERE o.kind='FUNDING'
  AND public.fnd_posting_key(o.tenant_id,o.operation_id,'FUNDING')=key
  AND public.funds_scope_matches(o.tenant_id,ARRAY[o.source_account_id,o.destination_account_id],TRUE)) THEN RETURN NEW; END IF;
 c:=public.fnd_active_command();
 PERFORM public.fnd_require(c.purpose IN('RESERVE','REVERSE') AND key=c.expected_posting_key);
 RETURN NEW;
END $$;
CREATE FUNCTION public.fnd_guard_bound_write() RETURNS TRIGGER
 LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE o JSONB;n JSONB;identity UUID;known BOOLEAN;c funds_private.receipt_command;
BEGIN
 IF TG_OP<>'INSERT' THEN o:=to_jsonb(OLD); END IF;IF TG_OP<>'DELETE' THEN n:=to_jsonb(NEW); END IF;
 IF TG_TABLE_NAME IN('ledger_funds_event','event_outbox') THEN
  IF TG_TABLE_NAME='event_outbox' AND TG_OP='UPDATE'
   AND (n->>'aggregate_type'='MANAGED_FUNDS' OR o->>'aggregate_type'='MANAGED_FUNDS') THEN
   PERFORM public.fnd_require((n-ARRAY['processed','processed_at','retry_count','last_error'])
    =(o-ARRAY['processed','processed_at','retry_count','last_error']));RETURN NEW;
  END IF;
  IF TG_TABLE_NAME='event_outbox' AND coalesce(n->>'aggregate_type',o->>'aggregate_type')<>'MANAGED_FUNDS' THEN RETURN NEW; END IF;
  IF coalesce(n->>'event_type',o->>'event_type') IN('BANK_CONTROL_FUNDING','FUNDS_ABSENT_CLOSED') THEN RETURN NEW; END IF;
  identity:=coalesce(n->>'resource_id',n->>'aggregate_id',o->>'resource_id',o->>'aggregate_id')::uuid;
  SELECT EXISTS(SELECT 1 FROM funds_private.receipt_binding WHERE kind='RESERVATION' AND resource_key=identity::text) INTO known;
  IF TG_OP='UPDATE' AND TG_TABLE_NAME='event_outbox' THEN
   PERFORM public.fnd_require((n-ARRAY['processed','processed_at','retry_count','last_error'])
    =(o-ARRAY['processed','processed_at','retry_count','last_error']));RETURN NEW;
  END IF;
 ELSE
  identity:=coalesce(n->>'transaction_id',o->>'transaction_id')::uuid;
  -- A retained OLD resource cannot shed native protection by changing its identifiers.
  SELECT EXISTS(SELECT 1 FROM funds_private.receipt_binding WHERE kind='POSTING'
    AND resource_key IN(n->>'transaction_id',o->>'transaction_id'))
   OR EXISTS(SELECT 1 FROM funds_private.visibility WHERE kind='ACCOUNT'
    AND resource_id IN((n->>'account_id')::uuid,(o->>'account_id')::uuid)) INTO known;
  IF TG_TABLE_NAME='transaction_status_history' AND NOT known THEN
   SELECT EXISTS(SELECT 1 FROM public.transaction t JOIN public.ledger_funds_account a ON a.account_id=t.account_id
    WHERE t.transaction_id IN((n->>'transaction_id')::uuid,(o->>'transaction_id')::uuid)) INTO known;
  END IF;
 END IF;
 IF NOT known THEN
  IF TG_OP='DELETE' THEN RETURN OLD; END IF;RETURN NEW;
 END IF;
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'FND_IMMUTABLE_BOUND_EFFECT' USING ERRCODE='23514'; END IF;
 IF TG_TABLE_NAME='transaction_leg' AND TG_OP<>'INSERT' THEN
  RAISE EXCEPTION 'FND_IMMUTABLE_BOUND_EFFECT' USING ERRCODE='23514';
 END IF;
 IF current_setting('firefly.funds.purpose',true)='FUNDING'
 AND TG_TABLE_NAME NOT IN('ledger_funds_event','event_outbox')
 AND NOT EXISTS(SELECT 1 FROM funds_private.receipt_binding WHERE kind='POSTING'
  AND resource_key IN(n->>'transaction_id',o->>'transaction_id'))
 THEN RETURN NEW; END IF;
 c:=public.fnd_active_command();
 IF TG_TABLE_NAME='transaction' THEN
  IF TG_OP='UPDATE' THEN
   -- Spring auditing updates technical date_updated; financial recordedAt remains the native command time.
   PERFORM public.fnd_require(c.purpose IN('COMMIT','RELEASE','EXPIRE')
    AND OLD.transaction_status::text='pending'
    AND NEW.transaction_status::text=CASE WHEN c.purpose='COMMIT' THEN 'posted' ELSE 'reversed' END
    AND NEW.row_version=OLD.row_version+1 AND isfinite(NEW.date_updated)
    AND (to_jsonb(NEW)-ARRAY['transaction_status','row_version','date_updated'])
     =(to_jsonb(OLD)-ARRAY['transaction_status','row_version','date_updated']));
  ELSE
   PERFORM public.fnd_require(c.purpose IN('RESERVE','REVERSE') AND NEW.row_version=0);
  END IF;
 END IF;
 IF TG_TABLE_NAME IN('ledger_funds_event','event_outbox') THEN
  PERFORM public.fnd_require(identity=c.reservation_id AND (n->>'event_id')::uuid=c.command_id);
 ELSE
  PERFORM public.fnd_require(c.purpose IN('RESERVE','COMMIT','RELEASE','EXPIRE','REVERSE'));
 END IF;
 RETURN NEW;
END $$;
CREATE FUNCTION public.fnd_stamp_binding() RETURNS TRIGGER
 LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
 INSERT INTO funds_private.receipt_binding VALUES('RESERVATION',NEW.tenant_id,NEW.reservation_id::text,NEW.operation_id,
  NEW.reservation_id,NEW.source_account_id,NEW.destination_account_id,NEW.receipt_contract_version) ON CONFLICT DO NOTHING;
 INSERT INTO funds_private.receipt_binding VALUES('OPERATION',NEW.tenant_id,NEW.operation_id::text,NEW.operation_id,
  NEW.reservation_id,NEW.source_account_id,NEW.destination_account_id,NEW.receipt_contract_version) ON CONFLICT DO NOTHING;
 INSERT INTO funds_private.receipt_binding VALUES('POSTING',NEW.tenant_id,NEW.posting_id::text,NEW.operation_id,
  NEW.reservation_id,NEW.source_account_id,NEW.destination_account_id,NEW.receipt_contract_version) ON CONFLICT DO NOTHING;
 IF NEW.reversal_id IS NOT NULL THEN
  INSERT INTO funds_private.receipt_binding VALUES('POSTING',NEW.tenant_id,NEW.reversal_id::text,NEW.operation_id,
   NEW.reservation_id,NEW.source_account_id,NEW.destination_account_id,NEW.receipt_contract_version) ON CONFLICT DO NOTHING;
 END IF;
 RETURN NEW;
END $$;
CREATE FUNCTION public.fnd_record_row() RETURNS TRIGGER
 LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE c funds_private.receipt_command;o JSONB;n JSONB;kind TEXT;identity TEXT;
BEGIN
 IF NULLIF(current_setting('firefly.funds.receipt_command',true),'') IS NULL THEN RETURN NULL; END IF;
 c:=public.fnd_active_command();
 IF TG_OP<>'INSERT' THEN o:=to_jsonb(OLD); END IF;IF TG_OP<>'DELETE' THEN n:=to_jsonb(NEW); END IF;
 kind:=CASE TG_TABLE_NAME WHEN 'ledger_funds_account' THEN 'ACCOUNT' WHEN 'ledger_funds_reservation' THEN 'RESERVATION'
  WHEN 'transaction' THEN 'HEADER' WHEN 'transaction_leg' THEN 'LEG' WHEN 'transaction_status_history' THEN 'HISTORY'
  WHEN 'ledger_posting_request' THEN 'POSTING_CLAIM' WHEN 'ledger_funds_operation' THEN 'OPERATION'
  WHEN 'ledger_funds_reserve_intent' THEN 'INTENT' WHEN 'ledger_funds_monetary_receipt' THEN 'RECEIPT'
  WHEN 'ledger_funds_event' THEN 'AUDIT' WHEN 'event_outbox' THEN 'OUTBOX' END;
 identity:=coalesce(n->>'account_id',o->>'account_id');
 IF kind='RESERVATION' OR kind='INTENT' THEN identity:=coalesce(n->>'reservation_id',o->>'reservation_id');
 ELSIF kind='HEADER' THEN identity:=coalesce(n->>'transaction_id',o->>'transaction_id');
 ELSIF kind='LEG' THEN identity:=coalesce(n->>'transaction_leg_id',o->>'transaction_leg_id');
 ELSIF kind='HISTORY' THEN identity:=coalesce(n->>'transaction_status_history_id',o->>'transaction_status_history_id');
 ELSIF kind='POSTING_CLAIM' THEN identity:=coalesce(n->>'idempotency_key',o->>'idempotency_key');
 ELSIF kind='OPERATION' THEN identity:=coalesce(n->>'operation_id',o->>'operation_id');
 ELSIF kind='RECEIPT' THEN identity:=coalesce(n->>'receipt_id',o->>'receipt_id');
 ELSIF kind IN('AUDIT','OUTBOX') THEN identity:=coalesce(n->>'event_id',o->>'event_id');END IF;
 PERFORM public.fnd_require(kind IS NOT NULL AND identity IS NOT NULL AND TG_OP<>'DELETE'
  AND (SELECT count(*) FROM funds_private.receipt_row_witness WHERE command_id=c.command_id)<32);
 INSERT INTO funds_private.receipt_row_witness(command_id,relation_kind,row_key,operation,old_row,new_row)
  VALUES(c.command_id,kind,identity,TG_OP,o,n);
 RETURN NULL;
END $$;

CREATE FUNCTION public.fnd_accepted_record(i public.ledger_funds_reserve_intent)
 RETURNS public.fnd_accepted_reserve_v1 LANGUAGE SQL IMMUTABLE SET search_path=pg_catalog AS $$
 SELECT ROW(i.tenant_id,i.reservation_id,i.operation_id,i.source_account_id,i.destination_account_id,i.amount,
 i.currency,i.ttl_seconds,i.not_after_epoch_millis,i.intent_recorded_at_epoch_millis,i.expires_at_epoch_millis,
 i.source_snapshot,i.destination_snapshot)::public.fnd_accepted_reserve_v1
$$;
CREATE FUNCTION public.fnd_finish_command(identity UUID) RETURNS VOID
 LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE c funds_private.receipt_command;r public.ledger_funds_reservation;i public.ledger_funds_reserve_intent;
 s public.ledger_funds_account;d public.ledger_funds_account;prior public.ledger_funds_monetary_receipt;
 a public.fnd_accepted_reserve_v1;ss public.fnd_snapshot_v1;ds public.fnd_snapshot_v1;
 sb public.fnd_balances_v1;db public.fnd_balances_v1;original BYTEA;accepted BYTEA;encoded BYTEA;
 posting UUID;event_type TEXT;payload JSONB;
BEGIN
 c:=public.fnd_active_command();PERFORM public.fnd_require(c.command_id=identity);
 SELECT * INTO r FROM public.ledger_funds_reservation WHERE tenant_id=c.tenant_id AND reservation_id=c.reservation_id;
 PERFORM public.fnd_require(r.reservation_id IS NOT NULL AND r.operation_id=c.operation_id AND r.receipt_contract_version=c.contract_version);
 SELECT * INTO s FROM public.ledger_funds_account WHERE tenant_id=c.tenant_id AND account_id=c.source_account_id;
 SELECT * INTO d FROM public.ledger_funds_account WHERE tenant_id=c.tenant_id AND account_id=c.destination_account_id;
 ss:=public.fnd_account_snapshot(c.source_before);ds:=public.fnd_account_snapshot(c.destination_before);
 posting:=CASE WHEN c.purpose='REVERSE' THEN r.reversal_id ELSE r.posting_id END;
 event_type:=CASE c.purpose WHEN 'RESERVE' THEN 'FUNDS_RESERVED' WHEN 'COMMIT' THEN 'FUNDS_COMMITTED'
  WHEN 'RELEASE' THEN 'FUNDS_RELEASED' WHEN 'EXPIRE' THEN 'FUNDS_EXPIRED' WHEN 'REVERSE' THEN 'FUNDS_REVERSED' END;
 payload:=jsonb_build_object('resourceId',r.reservation_id,'postingId',posting);
 IF c.purpose='RESERVE' THEN
  a:=ROW(c.tenant_id,r.reservation_id,c.operation_id,c.source_account_id,c.destination_account_id,
   (c.original_intent->>'amount')::numeric,c.original_intent->>'currency',(c.original_intent->>'ttlSeconds')::integer,
   (c.original_intent->>'notAfterEpochMillis')::bigint,c.recorded_at_epoch_millis,c.expires_at_epoch_millis,ss,ds)::public.fnd_accepted_reserve_v1;
  original:=public.fnd_intent_bytes(a.tenant,a.operation_id,a.source_account_id,a.destination_account_id,a.amount,
   a.currency,a.ttl_seconds,a.not_after_epoch_millis);accepted:=public.fnd_accepted_bytes(a);
  INSERT INTO public.ledger_funds_reserve_intent VALUES(a.tenant,a.reservation_id,a.operation_id,a.source_account_id,
   a.destination_account_id,a.amount,a.currency,a.ttl_seconds,a.not_after_epoch_millis,a.intent_recorded_at_epoch_millis,
   a.expires_at_epoch_millis,a.source_snapshot,a.destination_snapshot,c.command_id,public.fnd_b64(original,4096),
   encode(sha256(original),'hex'),public.fnd_b64(accepted,16384),encode(sha256(accepted),'hex'));
  payload:=payload||jsonb_build_object('intentEncoding','FND_RESERVE_INTENT_V1','intentDigest',encode(sha256(original),'hex'),
   'acceptedDigest',encode(sha256(accepted),'hex'),'canonicalAccepted',public.fnd_b64(accepted,16384));
 ELSIF c.contract_version=1 AND c.purpose IN('COMMIT','REVERSE') THEN
  SELECT * INTO i FROM public.ledger_funds_reserve_intent WHERE tenant_id=c.tenant_id AND reservation_id=c.reservation_id;
  PERFORM public.fnd_require(i.reservation_id IS NOT NULL);a:=public.fnd_accepted_record(i);
  IF c.purpose='REVERSE' THEN
   SELECT * INTO prior FROM public.ledger_funds_monetary_receipt WHERE tenant_id=c.tenant_id
    AND reservation_id=c.reservation_id AND effect_kind='COMMIT';
   PERFORM public.fnd_require(prior.receipt_id IS NOT NULL AND prior.posting_id=r.posting_id
    AND prior.action_id<>c.action_id AND prior.effect_recorded_at_epoch_millis<=c.recorded_at_epoch_millis);
  END IF;
  sb:=ROW((c.source_before->>'booked')::numeric,(c.source_before->>'held')::numeric,s.booked,s.held)::public.fnd_balances_v1;
  db:=ROW((c.destination_before->>'booked')::numeric,(c.destination_before->>'held')::numeric,d.booked,d.held)::public.fnd_balances_v1;
  encoded:=public.fnd_receipt_bytes(c.command_id,c.command_id,c.tenant_id,c.reservation_id,c.operation_id,c.purpose,c.action_id,a,
   posting,prior.receipt_id,CASE WHEN c.purpose='REVERSE' THEN r.posting_id END,c.recorded_at_epoch_millis,r.amount,r.currency,
   ss,ds,public.fnd_minor_text(r.amount,ss.currency_scale),public.fnd_minor_text(r.amount,ds.currency_scale),
   CASE WHEN c.purpose='REVERSE' THEN c.destination_account_id ELSE c.source_account_id END,
   CASE WHEN c.purpose='REVERSE' THEN c.source_account_id ELSE c.destination_account_id END,sb,db,c.actor_issuer,c.actor_subject);
  INSERT INTO public.ledger_funds_monetary_receipt VALUES(c.command_id,c.command_id,c.tenant_id,c.reservation_id,c.operation_id,
   c.purpose,c.action_id,posting,prior.receipt_id,CASE WHEN c.purpose='REVERSE' THEN r.posting_id END,c.recorded_at_epoch_millis,
   r.amount,r.currency,ss,ds,public.fnd_minor_text(r.amount,ss.currency_scale),public.fnd_minor_text(r.amount,ds.currency_scale),
   CASE WHEN c.purpose='REVERSE' THEN c.destination_account_id ELSE c.source_account_id END,
   CASE WHEN c.purpose='REVERSE' THEN c.source_account_id ELSE c.destination_account_id END,sb,db,c.actor_issuer,c.actor_subject,
   i.accepted_canonical,public.fnd_b64(encoded),encode(sha256(encoded),'hex'),c.command_id);
  payload:=payload||jsonb_build_object('receiptId',c.command_id,'receiptEncoding','FND_MONETARY_RECEIPT_V1',
   'receiptDigest',encode(sha256(encoded),'hex'),'canonicalReceipt',public.fnd_b64(encoded));
 END IF;
 INSERT INTO public.ledger_funds_event(event_id,tenant_id,resource_id,actor,event_type,occurred_at,payload)
  VALUES(c.command_id,c.tenant_id,c.reservation_id,c.actor_subject,event_type,c.recorded_at,payload);
 INSERT INTO public.event_outbox(event_id,aggregate_type,aggregate_id,event_type,payload,created_at,funds_tenant_id)
  VALUES(c.command_id,'MANAGED_FUNDS',c.reservation_id::text,event_type,payload,c.recorded_at AT TIME ZONE 'UTC',c.tenant_id);
END $$;
CREATE FUNCTION public.fnd_actual_row(kind TEXT,key TEXT) RETURNS JSONB
 LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE c funds_private.receipt_command;actual JSONB;
BEGIN
 c:=public.fnd_active_command();
 CASE kind
 WHEN 'ACCOUNT' THEN SELECT to_jsonb(x) INTO actual FROM public.ledger_funds_account x WHERE x.tenant_id=c.tenant_id AND x.account_id::text=key;
 WHEN 'RESERVATION' THEN SELECT to_jsonb(x) INTO actual FROM public.ledger_funds_reservation x WHERE x.tenant_id=c.tenant_id AND x.reservation_id::text=key;
 WHEN 'HEADER' THEN SELECT to_jsonb(x) INTO actual FROM public.transaction x WHERE x.transaction_id::text=key;
 WHEN 'LEG' THEN SELECT to_jsonb(x) INTO actual FROM public.transaction_leg x WHERE x.transaction_leg_id::text=key;
 WHEN 'HISTORY' THEN SELECT to_jsonb(x) INTO actual FROM public.transaction_status_history x WHERE x.transaction_status_history_id::text=key;
 WHEN 'POSTING_CLAIM' THEN SELECT to_jsonb(x) INTO actual FROM public.ledger_posting_request x WHERE x.idempotency_key=key;
 WHEN 'OPERATION' THEN SELECT to_jsonb(x) INTO actual FROM public.ledger_funds_operation x WHERE x.tenant_id=c.tenant_id AND x.operation_id::text=key;
 WHEN 'INTENT' THEN SELECT to_jsonb(x) INTO actual FROM public.ledger_funds_reserve_intent x WHERE x.tenant_id=c.tenant_id AND x.reservation_id::text=key;
 WHEN 'RECEIPT' THEN SELECT to_jsonb(x) INTO actual FROM public.ledger_funds_monetary_receipt x WHERE x.tenant_id=c.tenant_id AND x.receipt_id::text=key;
 WHEN 'AUDIT' THEN SELECT to_jsonb(x) INTO actual FROM public.ledger_funds_event x WHERE x.tenant_id=c.tenant_id AND x.event_id::text=key;
 WHEN 'OUTBOX' THEN SELECT to_jsonb(x) INTO actual FROM public.event_outbox x WHERE x.funds_tenant_id=c.tenant_id AND x.event_id::text=key;
 ELSE PERFORM public.fnd_require(FALSE);
 END CASE;
 RETURN actual;
END $$;

CREATE FUNCTION public.fnd_assert_command_complete(identity UUID) RETURNS VOID
 LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE c funds_private.receipt_command;r public.ledger_funds_reservation;s public.ledger_funds_account;d public.ledger_funds_account;
 h public.transaction;i public.ledger_funds_reserve_intent;p public.ledger_funds_monetary_receipt;prior public.ledger_funds_monetary_receipt;
 audit public.ledger_funds_event;outbox public.event_outbox;before_s NUMERIC;before_d NUMERIC;held_s NUMERIC;held_d NUMERIC;
 expected_state TEXT;expected_status TEXT;financial BOOLEAN;reverse BOOLEAN;expected_count INTEGER;posting UUID;
 a public.fnd_accepted_reserve_v1;bytes BYTEA;
BEGIN
 c:=public.fnd_active_command();PERFORM public.fnd_require(c.command_id=identity);
 SELECT * INTO r FROM public.ledger_funds_reservation WHERE tenant_id=c.tenant_id AND reservation_id=c.reservation_id;
 SELECT * INTO s FROM public.ledger_funds_account WHERE tenant_id=c.tenant_id AND account_id=c.source_account_id;
 SELECT * INTO d FROM public.ledger_funds_account WHERE tenant_id=c.tenant_id AND account_id=c.destination_account_id;
 reverse:=c.purpose='REVERSE';financial:=c.purpose IN('COMMIT','REVERSE');
 expected_state:=CASE c.purpose WHEN 'RESERVE' THEN 'HELD' WHEN 'COMMIT' THEN 'COMMITTED'
  WHEN 'REVERSE' THEN 'REVERSED' WHEN 'RELEASE' THEN 'RELEASED' ELSE 'EXPIRED' END;
 expected_status:=CASE WHEN c.purpose='RESERVE' THEN 'pending' WHEN financial THEN 'posted' ELSE 'reversed' END;
 expected_count:=CASE c.purpose WHEN 'RESERVE' THEN 12 WHEN 'COMMIT' THEN 8 WHEN 'REVERSE' THEN 12 ELSE 6 END;
 IF financial AND c.contract_version=0 THEN expected_count:=expected_count-1;END IF;
 PERFORM public.fnd_require((SELECT count(*) FROM funds_private.receipt_row_witness WHERE command_id=identity)=expected_count
  AND r.state=expected_state AND r.receipt_contract_version=c.contract_version AND r.operation_id=c.operation_id
  AND r.source_account_id=c.source_account_id AND r.destination_account_id=c.destination_account_id
  AND (SELECT count(*) FROM funds_private.receipt_row_witness WHERE command_id=identity AND relation_kind='RESERVATION'
   AND row_key=c.reservation_id::text AND operation=CASE WHEN c.purpose='RESERVE' THEN 'INSERT' ELSE 'UPDATE' END)=1);
 before_s:=(c.source_before->>'booked')::numeric;before_d:=(c.destination_before->>'booked')::numeric;
 held_s:=(c.source_before->>'held')::numeric;held_d:=(c.destination_before->>'held')::numeric;
 PERFORM public.fnd_require(s.booked=before_s+CASE WHEN reverse THEN r.amount WHEN c.purpose='COMMIT' THEN -r.amount ELSE 0 END
  AND d.booked=before_d+CASE WHEN reverse THEN -r.amount WHEN c.purpose='COMMIT' THEN r.amount ELSE 0 END
  AND s.held=held_s+CASE WHEN c.purpose='RESERVE' THEN r.amount WHEN reverse THEN 0 ELSE -r.amount END AND d.held=held_d
  AND (to_jsonb(s)-ARRAY['booked','held','version'])=(c.source_before-ARRAY['booked','held','version'])
  AND (to_jsonb(d)-ARRAY['booked','held','version'])=(c.destination_before-ARRAY['booked','held','version'])
  AND s.version=(c.source_before->>'version')::bigint+1
  AND d.version=(c.destination_before->>'version')::bigint+CASE WHEN financial THEN 1 ELSE 0 END
  AND (SELECT count(*) FROM funds_private.receipt_row_witness WHERE command_id=identity AND relation_kind='ACCOUNT')
   =CASE WHEN financial THEN 2 ELSE 1 END
  AND NOT EXISTS(SELECT 1 FROM funds_private.receipt_row_witness WHERE command_id=identity AND relation_kind='ACCOUNT'
   AND (operation<>'UPDATE' OR row_key NOT IN(c.source_account_id::text,c.destination_account_id::text)
   OR old_row<>CASE WHEN row_key=c.source_account_id::text THEN c.source_before ELSE c.destination_before END)));
 posting:=CASE WHEN reverse THEN r.reversal_id ELSE r.posting_id END;
 SELECT * INTO h FROM public.transaction WHERE transaction_id=posting;
 PERFORM public.fnd_require(h.transaction_id IS NOT NULL AND h.managed_posting AND h.transaction_status::text=expected_status
  AND h.transaction_type::text='transfer' AND h.account_id=c.source_account_id AND h.account_space_id IS NULL
  AND h.total_amount=r.amount AND h.currency=r.currency AND h.external_reference=c.expected_posting_key
  AND ((reverse AND h.related_transaction_id=r.posting_id AND h.relation_type='REVERSAL')
   OR (NOT reverse AND h.related_transaction_id IS NULL))
  AND (SELECT count(*) FROM public.transaction_leg WHERE transaction_id=posting)=2
  AND (SELECT count(*) FROM public.transaction_leg WHERE transaction_id=posting AND amount=r.amount AND currency=r.currency
   AND account_space_id IS NULL AND ((account_id=c.source_account_id AND leg_type=CASE WHEN reverse THEN 'CREDIT' ELSE 'DEBIT' END)
    OR (account_id=c.destination_account_id AND leg_type=CASE WHEN reverse THEN 'DEBIT' ELSE 'CREDIT' END)))=2
  AND (SELECT count(*) FROM funds_private.receipt_row_witness WHERE command_id=identity AND relation_kind='HEADER'
   AND row_key=posting::text AND operation=CASE WHEN c.purpose IN('RESERVE','REVERSE') THEN 'INSERT' ELSE 'UPDATE' END)=1
  AND (SELECT count(*) FROM funds_private.receipt_row_witness WHERE command_id=identity AND relation_kind='HISTORY'
   AND operation='INSERT' AND (new_row->>'transaction_id')::uuid=posting AND new_row->>'status_code'=expected_status)=1
  AND (SELECT count(*) FROM funds_private.receipt_row_witness WHERE command_id=identity AND relation_kind='LEG')
   =CASE WHEN c.purpose IN('RESERVE','REVERSE') THEN 2 ELSE 0 END
  AND NOT EXISTS(SELECT 1 FROM funds_private.receipt_row_witness w
   WHERE w.command_id=identity AND w.relation_kind='LEG'
    AND (w.operation<>'INSERT' OR w.old_row IS NOT NULL
     OR (w.new_row->>'transaction_id')::uuid IS DISTINCT FROM posting))
  AND (SELECT count(*) FROM funds_private.receipt_row_witness WHERE command_id=identity AND relation_kind='POSTING_CLAIM'
   AND row_key=c.expected_posting_key)=CASE WHEN c.purpose IN('RESERVE','REVERSE') THEN 2 ELSE 0 END);
 -- Every witnessed final write must still be the actual row at the end of this transaction.
 -- The initial NULL posting claim is joined to its one permitted completion below.
 PERFORM public.fnd_require(NOT EXISTS(
  SELECT 1 FROM funds_private.receipt_row_witness w WHERE w.command_id=identity
   AND NOT(w.relation_kind='POSTING_CLAIM' AND w.operation='INSERT')
   AND w.new_row IS DISTINCT FROM public.fnd_actual_row(w.relation_kind,w.row_key)));
 PERFORM public.fnd_require(NOT EXISTS(
  SELECT 1 FROM funds_private.receipt_row_witness w WHERE w.command_id=identity
   AND w.relation_kind='RESERVATION' AND c.purpose<>'RESERVE'
   AND w.old_row IS DISTINCT FROM c.reservation_before));
 PERFORM public.fnd_require(EXISTS(SELECT 1 FROM public.ledger_posting_request claim
  WHERE claim.idempotency_key=c.expected_posting_key AND claim.transaction_id=posting));
 IF c.purpose IN('RESERVE','REVERSE') THEN
  PERFORM public.fnd_require(EXISTS(
   SELECT 1 FROM funds_private.receipt_row_witness ins JOIN funds_private.receipt_row_witness upd
    ON upd.command_id=ins.command_id AND upd.relation_kind='POSTING_CLAIM'
    AND upd.operation='UPDATE' AND upd.row_key=ins.row_key
   WHERE ins.command_id=identity AND ins.relation_kind='POSTING_CLAIM' AND ins.operation='INSERT'
    AND ins.row_key=c.expected_posting_key AND ins.new_row->>'transaction_id' IS NULL
    AND upd.old_row=ins.new_row AND (upd.new_row-'transaction_id')=(upd.old_row-'transaction_id')
    AND (upd.new_row->>'transaction_id')::uuid=posting));
 END IF;
 IF c.purpose='RESERVE' THEN
  PERFORM public.fnd_require(EXISTS(
   SELECT 1 FROM funds_private.receipt_row_witness w JOIN funds_private.receipt_claim_insert claim
    ON claim.kind='OPERATION' AND claim.tenant_id=c.tenant_id AND claim.claim_id=c.operation_id
    AND claim.created_xid=c.created_xid AND claim.executor_role=c.executor_role
   WHERE w.command_id=identity AND w.relation_kind='OPERATION' AND w.operation='UPDATE'
    AND w.row_key=c.operation_id::text AND w.old_row=claim.new_row
    AND (w.new_row-'reservation_id')=(w.old_row-'reservation_id')
    AND w.old_row->>'reservation_id' IS NULL AND (w.new_row->>'reservation_id')::uuid=c.reservation_id));
 END IF;
 IF reverse THEN
  PERFORM public.fnd_require(EXISTS(SELECT 1 FROM public.transaction WHERE transaction_id=r.posting_id AND transaction_status::text='posted'));
 END IF;
 IF c.purpose='RESERVE' THEN
  SELECT * INTO i FROM public.ledger_funds_reserve_intent WHERE tenant_id=c.tenant_id AND reservation_id=c.reservation_id;
  PERFORM public.fnd_require(i.reservation_id IS NOT NULL AND i.command_id=c.command_id AND i.intent_recorded_at_epoch_millis=c.recorded_at_epoch_millis
   AND r.expires_at=c.expires_at AND EXISTS(SELECT 1 FROM public.ledger_funds_operation WHERE tenant_id=c.tenant_id
    AND operation_id=c.operation_id AND kind='RESERVE' AND NOT closed_without_effect AND reservation_id=c.reservation_id)
   AND (SELECT count(*) FROM funds_private.receipt_row_witness WHERE command_id=identity AND relation_kind='INTENT' AND row_key=c.reservation_id::text)=1
   AND (SELECT count(*) FROM funds_private.receipt_row_witness WHERE command_id=identity AND relation_kind='OPERATION' AND row_key=c.operation_id::text)=1);
 ELSE
  PERFORM public.fnd_require(EXISTS(SELECT 1 FROM funds_private.receipt_claim_insert WHERE kind='ACTION' AND tenant_id=c.tenant_id
   AND claim_id=c.action_id AND created_xid=c.created_xid AND executor_role=c.executor_role));
 END IF;
 IF financial AND c.contract_version=1 THEN
  SELECT * INTO i FROM public.ledger_funds_reserve_intent WHERE tenant_id=c.tenant_id AND reservation_id=c.reservation_id;
  SELECT * INTO p FROM public.ledger_funds_monetary_receipt WHERE receipt_id=c.command_id;
  a:=public.fnd_accepted_record(i);
  PERFORM public.fnd_require(p.receipt_id=c.command_id AND p.event_id=c.command_id AND p.command_id=c.command_id
   AND p.tenant_id=c.tenant_id AND p.reservation_id=c.reservation_id AND p.operation_id=c.operation_id
   AND p.action_id=c.action_id AND p.effect_kind=c.purpose AND p.posting_id=posting
   AND p.effect_recorded_at_epoch_millis=c.recorded_at_epoch_millis AND p.actor_issuer=c.actor_issuer AND p.actor_subject=c.actor_subject
   AND p.accepted_canonical=i.accepted_canonical
   AND p.source_balances=ROW(before_s,held_s,s.booked,s.held)::public.fnd_balances_v1
   AND p.destination_balances=ROW(before_d,held_d,d.booked,d.held)::public.fnd_balances_v1);
  IF reverse THEN
   SELECT * INTO prior FROM public.ledger_funds_monetary_receipt WHERE tenant_id=c.tenant_id AND reservation_id=c.reservation_id AND effect_kind='COMMIT';
   PERFORM public.fnd_require(p.original_commit_receipt_id=prior.receipt_id AND p.original_posting_id=r.posting_id
    AND c.action_id<>prior.action_id AND prior.effect_recorded_at_epoch_millis<=c.recorded_at_epoch_millis);
  END IF;
  bytes:=public.fnd_receipt_bytes(p.receipt_id,p.event_id,p.tenant_id,p.reservation_id,p.operation_id,p.effect_kind,p.action_id,a,
   p.posting_id,p.original_commit_receipt_id,p.original_posting_id,p.effect_recorded_at_epoch_millis,p.amount,p.currency,
   p.source_snapshot,p.destination_snapshot,p.source_minor_units,p.destination_minor_units,p.from_account_id,p.to_account_id,
   p.source_balances,p.destination_balances,p.actor_issuer,p.actor_subject);
  PERFORM public.fnd_require(p.receipt_canonical=public.fnd_b64(bytes) AND p.receipt_digest=encode(sha256(bytes),'hex')
   AND (SELECT count(*) FROM funds_private.receipt_row_witness WHERE command_id=identity AND relation_kind='RECEIPT' AND row_key=c.command_id::text)=1);
 ELSE PERFORM public.fnd_require(NOT EXISTS(SELECT 1 FROM public.ledger_funds_monetary_receipt WHERE command_id=c.command_id));END IF;
 SELECT * INTO audit FROM public.ledger_funds_event WHERE event_id=c.command_id;
 SELECT * INTO outbox FROM public.event_outbox WHERE event_id=c.command_id;
 PERFORM public.fnd_require(audit.event_id IS NOT NULL AND outbox.event_id IS NOT NULL AND audit.tenant_id=c.tenant_id
  AND audit.resource_id=c.reservation_id AND audit.actor=c.actor_subject AND audit.occurred_at=c.recorded_at
  AND audit.event_type=CASE c.purpose WHEN 'RESERVE' THEN 'FUNDS_RESERVED' WHEN 'COMMIT' THEN 'FUNDS_COMMITTED'
   WHEN 'REVERSE' THEN 'FUNDS_REVERSED' WHEN 'RELEASE' THEN 'FUNDS_RELEASED' ELSE 'FUNDS_EXPIRED' END
  AND outbox.event_type=audit.event_type AND outbox.aggregate_type='MANAGED_FUNDS' AND outbox.aggregate_id=c.reservation_id::text
  AND outbox.funds_tenant_id=c.tenant_id AND outbox.created_at=c.recorded_at AT TIME ZONE 'UTC' AND outbox.payload=audit.payload
  AND (audit.payload->>'resourceId')::uuid=c.reservation_id AND (audit.payload->>'postingId')::uuid=posting
  AND (SELECT count(*) FROM funds_private.receipt_row_witness WHERE command_id=identity AND relation_kind='AUDIT' AND row_key=c.command_id::text)=1
  AND (SELECT count(*) FROM funds_private.receipt_row_witness WHERE command_id=identity AND relation_kind='OUTBOX' AND row_key=c.command_id::text)=1);
 IF financial AND c.contract_version=1 THEN
  PERFORM public.fnd_require(audit.payload=jsonb_build_object('resourceId',r.reservation_id,'postingId',posting,'receiptId',p.receipt_id,
   'receiptEncoding','FND_MONETARY_RECEIPT_V1','receiptDigest',p.receipt_digest,'canonicalReceipt',p.receipt_canonical));
 END IF;
END $$;
CREATE FUNCTION public.fnd_deferred_command_check() RETURNS TRIGGER
 LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN PERFORM public.fnd_assert_command_complete(NEW.command_id);RETURN NULL;END $$;
CREATE CONSTRAINT TRIGGER fnd_command_complete AFTER INSERT ON funds_private.receipt_command
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.fnd_deferred_command_check();
CREATE CONSTRAINT TRIGGER fnd_witness_complete AFTER INSERT ON funds_private.receipt_row_witness
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.fnd_deferred_command_check();

CREATE FUNCTION public.fnd_guard_retained_operation() RETURNS TRIGGER
 LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE c funds_private.receipt_command;
BEGIN
 IF NULLIF(current_setting('firefly.funds.receipt_command',true),'') IS NULL
 AND NOT EXISTS(SELECT 1 FROM funds_private.receipt_binding WHERE kind='OPERATION' AND tenant_id=OLD.tenant_id
  AND resource_key=OLD.operation_id::text) THEN RETURN NEW; END IF;
 c:=public.fnd_active_command();
 PERFORM public.fnd_require(c.purpose='RESERVE' AND NEW.tenant_id=c.tenant_id AND NEW.operation_id=c.operation_id
  AND OLD.reservation_id IS NULL AND NEW.reservation_id=c.reservation_id
  AND (to_jsonb(NEW)-'reservation_id')=(to_jsonb(OLD)-'reservation_id'));
 RETURN NEW;
END $$;
CREATE TRIGGER fnd_operation_retained BEFORE UPDATE ON public.ledger_funds_operation
 FOR EACH ROW EXECUTE FUNCTION public.fnd_guard_retained_operation();
CREATE TRIGGER fnd_reservation_marker BEFORE INSERT OR UPDATE OR DELETE ON public.ledger_funds_reservation
 FOR EACH ROW EXECUTE FUNCTION public.fnd_guard_reservation();
CREATE TRIGGER fnd_reservation_no_truncate BEFORE TRUNCATE ON public.ledger_funds_reservation
 FOR EACH STATEMENT EXECUTE FUNCTION public.fnd_immutable_retained_row();
CREATE TRIGGER fnd_account_money BEFORE UPDATE ON public.ledger_funds_account
 FOR EACH ROW EXECUTE FUNCTION public.fnd_guard_account_money();
CREATE TRIGGER fnd_posting_claim_gate BEFORE INSERT OR UPDATE OR DELETE ON public.ledger_posting_request
 FOR EACH ROW EXECUTE FUNCTION public.fnd_guard_posting_claim();
CREATE TRIGGER fnd_reservation_binding AFTER INSERT OR UPDATE ON public.ledger_funds_reservation
 FOR EACH ROW EXECUTE FUNCTION public.fnd_stamp_binding();
DO $$ DECLARE relation TEXT;BEGIN
 FOREACH relation IN ARRAY ARRAY['transaction','transaction_leg','transaction_status_history','ledger_funds_event','event_outbox'] LOOP
  EXECUTE format('CREATE TRIGGER fnd_bound_effect BEFORE INSERT OR UPDATE OR DELETE ON public.%I FOR EACH ROW EXECUTE FUNCTION public.fnd_guard_bound_write()',relation);
 END LOOP;
 FOREACH relation IN ARRAY ARRAY['ledger_funds_account','ledger_funds_reservation','transaction','transaction_leg',
  'transaction_status_history','ledger_posting_request','ledger_funds_operation','ledger_funds_reserve_intent',
  'ledger_funds_monetary_receipt','ledger_funds_event','event_outbox'] LOOP
  EXECUTE format('CREATE TRIGGER fnd_actual_row_witness AFTER INSERT OR UPDATE OR DELETE ON public.%I FOR EACH ROW EXECUTE FUNCTION public.fnd_record_row()',relation);
 END LOOP;
 FOREACH relation IN ARRAY ARRAY['receipt_claim_insert','receipt_command','receipt_row_witness','receipt_binding'] LOOP
  EXECUTE format('CREATE TRIGGER fnd_private_immutable BEFORE UPDATE OR DELETE ON funds_private.%I FOR EACH ROW EXECUTE FUNCTION public.fnd_immutable_retained_row()',relation);
  EXECUTE format('CREATE TRIGGER fnd_private_no_truncate BEFORE TRUNCATE ON funds_private.%I FOR EACH STATEMENT EXECUTE FUNCTION public.fnd_immutable_retained_row()',relation);
 END LOOP;
END $$;
INSERT INTO funds_private.receipt_binding
 SELECT 'RESERVATION',tenant_id,reservation_id::text,operation_id,reservation_id,source_account_id,destination_account_id,0
 FROM public.ledger_funds_reservation
 UNION ALL SELECT 'OPERATION',tenant_id,operation_id::text,operation_id,reservation_id,source_account_id,destination_account_id,0
 FROM public.ledger_funds_reservation
 UNION ALL SELECT 'POSTING',tenant_id,posting_id::text,operation_id,reservation_id,source_account_id,destination_account_id,0
 FROM public.ledger_funds_reservation
 UNION ALL SELECT 'POSTING',tenant_id,reversal_id::text,operation_id,reservation_id,source_account_id,destination_account_id,0
 FROM public.ledger_funds_reservation WHERE reversal_id IS NOT NULL;
ALTER TABLE public.ledger_funds_reservation ALTER COLUMN receipt_contract_version SET DEFAULT 1;
CREATE POLICY fnd_original_insert ON public.ledger_funds_reserve_intent FOR INSERT
 WITH CHECK(public.fnd_command_allows(tenant_id,source_account_id,destination_account_id,'RESERVE'));
CREATE POLICY fnd_receipt_insert ON public.ledger_funds_monetary_receipt FOR INSERT
 WITH CHECK(public.fnd_command_allows(tenant_id,(source_snapshot).account_id,(destination_snapshot).account_id,effect_kind));

GRANT USAGE ON SCHEMA public,funds_private TO firefly_funds_receipt_guard;
GRANT SELECT ON public.ledger_funds_account,public.ledger_funds_operation,public.ledger_funds_reservation,
 public.ledger_funds_action,public.ledger_posting_request,public.transaction,public.transaction_leg,
 public.transaction_status_history,public.ledger_funds_event,public.event_outbox,
 public.ledger_funds_reserve_intent,public.ledger_funds_monetary_receipt TO firefly_funds_receipt_guard;
-- PostgreSQL row locks require UPDATE on at least one column, even for SELECT FOR UPDATE.
-- This non-login function owner receives only identity-column privileges; callers cannot assume it.
GRANT UPDATE(account_id) ON public.ledger_funds_account TO firefly_funds_receipt_guard;
GRANT UPDATE(operation_id) ON public.ledger_funds_operation TO firefly_funds_receipt_guard;
GRANT UPDATE(reservation_id) ON public.ledger_funds_reservation TO firefly_funds_receipt_guard;
GRANT UPDATE(idempotency_key) ON public.ledger_posting_request TO firefly_funds_receipt_guard;
GRANT UPDATE(transaction_id) ON public.transaction TO firefly_funds_receipt_guard;
GRANT UPDATE(transaction_leg_id) ON public.transaction_leg TO firefly_funds_receipt_guard;
GRANT INSERT ON public.ledger_funds_reserve_intent,public.ledger_funds_monetary_receipt,
 public.ledger_funds_event,public.event_outbox TO firefly_funds_receipt_guard;
GRANT SELECT ON funds_private.visibility TO firefly_funds_receipt_guard;
GRANT SELECT,INSERT ON funds_private.receipt_claim_insert,funds_private.receipt_command,
 funds_private.receipt_row_witness,funds_private.receipt_binding TO firefly_funds_receipt_guard;
GRANT USAGE,SELECT ON SEQUENCE funds_private.receipt_row_witness_ordinal_seq TO firefly_funds_receipt_guard;
GRANT CREATE ON SCHEMA public TO firefly_funds_receipt_guard;
DO $$ DECLARE fn RECORD;BEGIN
 FOR fn IN SELECT p.oid,p.proname,pg_catalog.pg_get_function_identity_arguments(p.oid) args
  FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace
  WHERE n.nspname='public' AND p.proname LIKE 'fnd_%' AND p.prosecdef LOOP
  EXECUTE format('ALTER FUNCTION public.%I(%s) OWNER TO firefly_funds_receipt_guard',fn.proname,fn.args);
  EXECUTE format('REVOKE ALL ON FUNCTION public.%I(%s) FROM PUBLIC',fn.proname,fn.args);
 END LOOP;
END $$;
REVOKE CREATE ON SCHEMA public FROM firefly_funds_receipt_guard;
GRANT EXECUTE ON FUNCTION public.fnd_lock_posting_key(TEXT) TO firefly_funds_receipt_guard,firefly_funds_receipt_api;
GRANT EXECUTE ON FUNCTION public.fnd_begin_reserve(TEXT,UUID,UUID,UUID,NUMERIC,TEXT,INTEGER,BIGINT),
 public.fnd_begin_transition(TEXT,UUID,UUID,TEXT),public.fnd_finish_command(UUID),
 public.fnd_assert_live_context(TEXT,TEXT,UUID,UUID),public.fnd_command_allows(TEXT,UUID,UUID,TEXT)
 TO firefly_funds_receipt_api;
GRANT SELECT,INSERT ON public.ledger_funds_reserve_intent,public.ledger_funds_monetary_receipt TO firefly_funds_receipt_api;
