-- Explicit optional installation after the original V59; never a default Flyway migration.
BEGIN;
SET LOCAL statement_timeout='5s';
SET LOCAL lock_timeout='5s';
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='firefly_funds_receipt_guard'
  AND NOT rolcanlogin AND NOT rolsuper AND NOT rolbypassrls)
 OR NOT EXISTS(SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='firefly_funds_receipt_api'
  AND NOT rolcanlogin AND NOT rolsuper AND NOT rolbypassrls)
 OR pg_catalog.has_schema_privilege('firefly_funds_receipt_guard','public','CREATE')
 OR NOT EXISTS(SELECT 1 FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_roles r ON r.oid=p.proowner
  WHERE p.oid=to_regprocedure('public.fnd_active_command()') AND p.prosecdef AND p.provolatile='v'
   AND r.rolname='firefly_funds_receipt_guard' AND p.proconfig=ARRAY['search_path=pg_catalog'])
 OR NOT EXISTS(SELECT 1 FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_roles r ON r.oid=p.proowner
  WHERE p.oid=to_regprocedure('public.fnd_assert_command_complete(uuid)') AND p.prosecdef AND p.provolatile='v'
   AND r.rolname='firefly_funds_receipt_guard' AND p.proconfig=ARRAY['search_path=pg_catalog']) THEN
  RAISE EXCEPTION 'FND_ORIGINAL_RECEIPT_GUARDS_REQUIRED' USING ERRCODE='42501';
 END IF;
END $$;

CREATE FUNCTION public.fnd_require_completed_reserve(p_tenant TEXT,p_operation UUID,p_reservation UUID,
 p_source UUID,p_destination UUID,p_canonical TEXT,p_digest TEXT,p_fingerprint TEXT,
 p_issuer TEXT,p_subject TEXT,p_until TIMESTAMPTZ)
RETURNS TABLE(command_id UUID,reservation_id UUID,operation_id UUID,owner_tenant TEXT,
 source_account_id UUID,destination_account_id UUID,original_intent_canonical TEXT,original_intent_digest TEXT,
 operation_fingerprint TEXT,actor_issuer TEXT,actor_subject TEXT,created_xid TEXT,current_xid TEXT,
 executor_role TEXT,session_role TEXT,function_role TEXT,backend_id INTEGER,observed_at_epoch_millis BIGINT,
 token_expires_epoch_millis BIGINT,read_until_epoch_millis BIGINT)
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE c funds_private.receipt_command;again funds_private.receipt_command;
 i public.ledger_funds_reserve_intent;o public.ledger_funds_operation;original BYTEA;
 caller_until BIGINT;effective_until BIGINT;observed BIGINT;
BEGIN
 IF current_user<>'firefly_funds_receipt_guard'
 OR NOT EXISTS(SELECT 1 FROM pg_catalog.pg_roles WHERE rolname=current_user
  AND NOT rolcanlogin AND NOT rolsuper AND NOT rolbypassrls)
 OR pg_catalog.has_schema_privilege(session_user,'public','CREATE') THEN
  RAISE EXCEPTION 'FND_RESTRICTED_FIRST_WITNESS_ROLE_REQUIRED' USING ERRCODE='42501';
 END IF;
 c:=public.fnd_active_command();
 PERFORM public.fnd_require(c.purpose='RESERVE' AND c.contract_version=1 AND c.action_id IS NULL
  AND c.reservation_before IS NULL AND c.original_intent IS NOT NULL
  AND c.created_xid=pg_current_xact_id() AND c.executor_role=session_user
  AND (c.tenant_id COLLATE "C")=(p_tenant COLLATE "C") AND c.operation_id=p_operation
  AND c.reservation_id=p_reservation AND c.source_account_id=p_source AND c.destination_account_id=p_destination
  AND (c.actor_issuer COLLATE "C")=(p_issuer COLLATE "C") AND (c.actor_subject COLLATE "C")=(p_subject COLLATE "C"));
 PERFORM public.fnd_require(p_until IS NOT NULL AND isfinite(p_until)
  AND public.fnd_text_valid(p_canonical,4096) AND (p_digest COLLATE "C")~'^[0-9a-f]{64}$'
  AND (p_fingerprint COLLATE "C")~'^[0-9a-f]{64}$');
 caller_until:=floor(extract(epoch FROM p_until)*1000)::bigint;
 effective_until:=least(caller_until,c.token_expires_epoch_millis,c.expires_at_epoch_millis);
 observed:=floor(extract(epoch FROM clock_timestamp())*1000)::bigint;
 PERFORM public.fnd_require(observed BETWEEN 1 AND 253402300799999 AND caller_until BETWEEN 1 AND 253402300799999
  AND effective_until>observed AND caller_until<=observed+5000);

 -- Reuse the complete original write/effect check, not a status-only completion inference.
 PERFORM public.fnd_assert_command_complete(c.command_id);
 SELECT x.* INTO i FROM public.ledger_funds_reserve_intent x
  WHERE x.tenant_id=c.tenant_id AND x.reservation_id=c.reservation_id AND x.command_id=c.command_id;
 PERFORM public.fnd_require(FOUND AND i.command_id=c.command_id AND i.operation_id=c.operation_id
  AND i.source_account_id=c.source_account_id AND i.destination_account_id=c.destination_account_id
  AND i.intent_recorded_at_epoch_millis=c.recorded_at_epoch_millis AND i.expires_at_epoch_millis=c.expires_at_epoch_millis);
 original:=public.fnd_intent_bytes(c.tenant_id,c.operation_id,c.source_account_id,c.destination_account_id,
  (c.original_intent->>'amount')::numeric,c.original_intent->>'currency',
  (c.original_intent->>'ttlSeconds')::integer,(c.original_intent->>'notAfterEpochMillis')::bigint);
 PERFORM public.fnd_require((i.intent_canonical COLLATE "C")=(public.fnd_b64(original,4096) COLLATE "C")
  AND (i.intent_digest COLLATE "C")=(encode(sha256(original),'hex') COLLATE "C")
  AND (i.intent_canonical COLLATE "C")=(p_canonical COLLATE "C") AND (i.intent_digest COLLATE "C")=(p_digest COLLATE "C"));
 SELECT x.* INTO o FROM public.ledger_funds_operation x WHERE x.tenant_id=c.tenant_id AND x.operation_id=c.operation_id;
 PERFORM public.fnd_require(FOUND AND o.kind='RESERVE' AND NOT o.closed_without_effect
  AND o.reservation_id=c.reservation_id AND o.source_account_id=c.source_account_id AND o.destination_account_id=c.destination_account_id
  AND (o.fingerprint COLLATE "C")=(p_fingerprint COLLATE "C"));
 again:=public.fnd_active_command();
 PERFORM public.fnd_require(again.command_id=c.command_id AND again.created_xid=c.created_xid
  AND again.created_xid=pg_current_xact_id() AND again.executor_role=session_user);
 observed:=floor(extract(epoch FROM clock_timestamp())*1000)::bigint;
 PERFORM public.fnd_require(observed BETWEEN 1 AND 253402300799999 AND effective_until>observed);
 RETURN QUERY SELECT c.command_id,c.reservation_id,c.operation_id,c.tenant_id::text,c.source_account_id,c.destination_account_id,
  i.intent_canonical,i.intent_digest::text,o.fingerprint::text,c.actor_issuer,c.actor_subject,c.created_xid::text,
  pg_current_xact_id()::text,c.executor_role::text,session_user::text,current_user::text,pg_backend_pid(),observed,
  c.token_expires_epoch_millis,effective_until;
END $$;

REVOKE ALL ON FUNCTION public.fnd_require_completed_reserve(TEXT,UUID,UUID,UUID,UUID,TEXT,TEXT,TEXT,TEXT,TEXT,TIMESTAMPTZ) FROM PUBLIC;
-- Match V59's atomic owner handoff, without leaving CREATE on the non-login guard.
GRANT CREATE ON SCHEMA public TO firefly_funds_receipt_guard;
ALTER FUNCTION public.fnd_require_completed_reserve(TEXT,UUID,UUID,UUID,UUID,TEXT,TEXT,TEXT,TEXT,TEXT,TIMESTAMPTZ) OWNER TO firefly_funds_receipt_guard;
REVOKE CREATE ON SCHEMA public FROM firefly_funds_receipt_guard;
GRANT EXECUTE ON FUNCTION public.fnd_require_completed_reserve(TEXT,UUID,UUID,UUID,UUID,TEXT,TEXT,TEXT,TEXT,TEXT,TIMESTAMPTZ) TO firefly_funds_receipt_api;
DO $$ DECLARE fn OID;guard OID;api OID;BEGIN
 fn:=to_regprocedure('public.fnd_require_completed_reserve(text,uuid,uuid,uuid,uuid,text,text,text,text,text,timestamp with time zone)');
 SELECT oid INTO guard FROM pg_catalog.pg_roles WHERE rolname='firefly_funds_receipt_guard';
 SELECT oid INTO api FROM pg_catalog.pg_roles WHERE rolname='firefly_funds_receipt_api';
 IF NOT EXISTS(SELECT 1 FROM pg_catalog.pg_proc p WHERE p.oid=fn AND p.proowner=guard AND p.prosecdef
  AND p.provolatile='v' AND p.proconfig=ARRAY['search_path=pg_catalog'])
 OR pg_catalog.has_schema_privilege('firefly_funds_receipt_guard','public','CREATE')
 OR NOT pg_catalog.has_function_privilege('firefly_funds_receipt_api',fn,'EXECUTE')
 OR EXISTS(SELECT 1 FROM pg_catalog.pg_proc p CROSS JOIN LATERAL aclexplode(COALESCE(p.proacl,acldefault('f',p.proowner))) a
  WHERE p.oid=fn AND (a.grantee NOT IN(guard,api) OR a.privilege_type<>'EXECUTE' OR a.grantee=api AND a.is_grantable)) THEN
  RAISE EXCEPTION 'FND_FIRST_WITNESS_INSTALLATION_REFUSED' USING ERRCODE='42501';
 END IF;
END $$;
COMMIT;
