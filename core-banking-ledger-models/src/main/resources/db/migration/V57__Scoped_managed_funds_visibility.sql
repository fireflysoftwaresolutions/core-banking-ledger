CREATE SCHEMA IF NOT EXISTS funds_private;
REVOKE ALL ON SCHEMA funds_private FROM PUBLIC;
CREATE TABLE funds_private.visibility (
    kind VARCHAR(20) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    resource_id UUID NOT NULL,
    accounts UUID[] NOT NULL,
    PRIMARY KEY(kind,tenant_id,resource_id)
);
REVOKE ALL ON funds_private.visibility FROM PUBLIC;

CREATE FUNCTION public.funds_scope_matches(t TEXT,a UUID[],writes BOOLEAN DEFAULT FALSE)
RETURNS BOOLEAN LANGUAGE SQL STABLE SET search_path=pg_catalog AS $$
 SELECT COALESCE(current_setting('firefly.funds.tenant',true)=t
   AND current_setting('firefly.funds.purpose',true) IN ('ENROLL','READ','CONTROL','FUNDING','RESERVE','COMMIT','RELEASE','EXPIRE','REVERSE')
   AND (NOT writes OR current_setting('firefly.funds.purpose',true) IN ('ENROLL','CONTROL','FUNDING','RESERVE','COMMIT','RELEASE','EXPIRE','REVERSE'))
   AND cardinality(a)>0
   AND COALESCE(NULLIF(current_setting('firefly.funds.accounts',true),''),'[]')::jsonb ?& ARRAY(SELECT x::text FROM unnest(a) x),FALSE)
$$;
CREATE FUNCTION public.funds_reference_allowed(k TEXT,r UUID,w BOOLEAN DEFAULT FALSE)
RETURNS BOOLEAN LANGUAGE SQL STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
 SELECT NOT EXISTS(SELECT 1 FROM funds_private.visibility v WHERE v.kind=k AND v.resource_id=r)
   OR EXISTS(SELECT 1 FROM funds_private.visibility v WHERE v.kind=k AND v.resource_id=r AND public.funds_scope_matches(v.tenant_id,v.accounts,w))
$$;
CREATE FUNCTION public.funds_scoped_allowed(k TEXT,t TEXT,r UUID,w BOOLEAN DEFAULT FALSE)
RETURNS BOOLEAN LANGUAGE SQL STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
 SELECT EXISTS(SELECT 1 FROM funds_private.visibility v WHERE v.kind=k AND v.tenant_id=t AND v.resource_id=r AND public.funds_scope_matches(v.tenant_id,v.accounts,w))
$$;
REVOKE ALL ON FUNCTION public.funds_scope_matches(TEXT,UUID[],BOOLEAN),public.funds_reference_allowed(TEXT,UUID,BOOLEAN),public.funds_scoped_allowed(TEXT,TEXT,UUID,BOOLEAN) FROM PUBLIC;
-- These predicates return only visibility decisions and are required by legacy as well as managed RLS queries.
GRANT EXECUTE ON FUNCTION public.funds_scope_matches(TEXT,UUID[],BOOLEAN),public.funds_reference_allowed(TEXT,UUID,BOOLEAN),public.funds_scoped_allowed(TEXT,TEXT,UUID,BOOLEAN) TO PUBLIC;

ALTER TABLE public.ledger_funds_operation ADD COLUMN source_account_id UUID NOT NULL REFERENCES public.ledger_funds_account(account_id);
ALTER TABLE public.ledger_funds_operation ADD COLUMN destination_account_id UUID NOT NULL REFERENCES public.ledger_funds_account(account_id);
ALTER TABLE public.event_outbox ADD COLUMN funds_tenant_id VARCHAR(64);

CREATE FUNCTION public.stamp_funds_visibility() RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE ids UUID[];
BEGIN
 IF TG_TABLE_NAME='ledger_funds_account' THEN
   INSERT INTO funds_private.visibility VALUES('ACCOUNT',NEW.tenant_id,NEW.account_id,ARRAY[NEW.account_id]) ON CONFLICT DO NOTHING;
 ELSIF TG_TABLE_NAME='ledger_funds_reservation' THEN
   ids:=ARRAY[NEW.source_account_id,NEW.destination_account_id];
   INSERT INTO funds_private.visibility VALUES('RESERVATION',NEW.tenant_id,NEW.reservation_id,ids) ON CONFLICT DO NOTHING;
   INSERT INTO funds_private.visibility VALUES('POSTING',NEW.tenant_id,NEW.posting_id,ids) ON CONFLICT DO NOTHING;
   IF NEW.reversal_id IS NOT NULL THEN INSERT INTO funds_private.visibility VALUES('POSTING',NEW.tenant_id,NEW.reversal_id,ids) ON CONFLICT DO NOTHING; END IF;
 ELSE
   ids:=ARRAY[NEW.source_account_id,NEW.destination_account_id];
   INSERT INTO funds_private.visibility VALUES('OPERATION',NEW.tenant_id,NEW.operation_id,ids) ON CONFLICT DO NOTHING;
   IF NEW.posting_id IS NOT NULL THEN INSERT INTO funds_private.visibility VALUES('POSTING',NEW.tenant_id,NEW.posting_id,ids) ON CONFLICT DO NOTHING; END IF;
 END IF;
 RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION public.stamp_funds_visibility() FROM PUBLIC;
CREATE TRIGGER funds_account_visibility AFTER INSERT ON public.ledger_funds_account FOR EACH ROW EXECUTE FUNCTION public.stamp_funds_visibility();
CREATE TRIGGER funds_reservation_visibility AFTER INSERT OR UPDATE ON public.ledger_funds_reservation FOR EACH ROW EXECUTE FUNCTION public.stamp_funds_visibility();
CREATE TRIGGER funds_operation_visibility AFTER INSERT OR UPDATE ON public.ledger_funds_operation FOR EACH ROW EXECUTE FUNCTION public.stamp_funds_visibility();

CREATE OR REPLACE FUNCTION public.guard_managed_funds_write() RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE o JSONB; n JSONB; item UUID; ids UUID[];
BEGIN
 IF TG_OP<>'INSERT' THEN o:=to_jsonb(OLD); END IF;
 IF TG_OP<>'DELETE' THEN n:=to_jsonb(NEW); END IF;
 SELECT array_agg(DISTINCT a ORDER BY a) INTO ids FROM (
   SELECT (o->>'account_id')::uuid a UNION SELECT (n->>'account_id')::uuid
   UNION SELECT unnest(v.accounts) FROM funds_private.visibility v WHERE v.kind='POSTING'
     AND v.resource_id IN((o->>'transaction_id')::uuid,(n->>'transaction_id')::uuid)
 ) q WHERE a IS NOT NULL;
 IF ids IS NOT NULL THEN
   FOREACH item IN ARRAY ids LOOP
     PERFORM pg_advisory_xact_lock(hashtextextended(item::text,55601));
     IF NOT public.funds_reference_allowed('ACCOUNT',item,TRUE)
        OR (EXISTS(SELECT 1 FROM funds_private.visibility WHERE kind='ACCOUNT' AND resource_id=item)
            AND current_setting('firefly.funds.purpose',true) NOT IN ('FUNDING','RESERVE','COMMIT','RELEASE','EXPIRE','REVERSE')) THEN
       RAISE EXCEPTION 'MANAGED_FUNDS_COMMAND_REQUIRED' USING ERRCODE='23514';
     END IF;
   END LOOP;
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION public.guard_managed_funds_write() FROM PUBLIC;

DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='firefly_funds_policy_reader' AND NOT rolsuper AND NOT rolbypassrls AND NOT rolcanlogin)
    AND EXISTS(SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='firefly_funds_policy_writer' AND NOT rolsuper AND NOT rolbypassrls AND NOT rolcanlogin) THEN
   GRANT USAGE ON SCHEMA funds_private TO firefly_funds_policy_reader,firefly_funds_policy_writer;
   GRANT SELECT ON funds_private.visibility TO firefly_funds_policy_reader;
   GRANT INSERT ON funds_private.visibility TO firefly_funds_policy_writer;
   GRANT CREATE ON SCHEMA public TO firefly_funds_policy_reader,firefly_funds_policy_writer;
   ALTER FUNCTION public.funds_reference_allowed(TEXT,UUID,BOOLEAN) OWNER TO firefly_funds_policy_reader;
   ALTER FUNCTION public.funds_scoped_allowed(TEXT,TEXT,UUID,BOOLEAN) OWNER TO firefly_funds_policy_reader;
   ALTER FUNCTION public.guard_managed_funds_write() OWNER TO firefly_funds_policy_reader;
   ALTER FUNCTION public.stamp_funds_visibility() OWNER TO firefly_funds_policy_writer;
   REVOKE CREATE ON SCHEMA public FROM firefly_funds_policy_reader,firefly_funds_policy_writer;
 END IF;
END $$;

ALTER TABLE public.transaction ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transaction FORCE ROW LEVEL SECURITY;
CREATE POLICY funds_select ON public.transaction FOR SELECT USING (public.funds_reference_allowed('ACCOUNT',account_id,FALSE) AND public.funds_reference_allowed('POSTING',transaction_id,FALSE));
CREATE POLICY funds_insert ON public.transaction FOR INSERT WITH CHECK (public.funds_reference_allowed('ACCOUNT',account_id,TRUE) AND public.funds_reference_allowed('POSTING',transaction_id,TRUE));
CREATE POLICY funds_update ON public.transaction FOR UPDATE USING (public.funds_reference_allowed('ACCOUNT',account_id,TRUE) AND public.funds_reference_allowed('POSTING',transaction_id,TRUE)) WITH CHECK (public.funds_reference_allowed('ACCOUNT',account_id,TRUE) AND public.funds_reference_allowed('POSTING',transaction_id,TRUE));
CREATE POLICY funds_delete ON public.transaction FOR DELETE USING (public.funds_reference_allowed('ACCOUNT',account_id,TRUE) AND public.funds_reference_allowed('POSTING',transaction_id,TRUE) AND NOT EXISTS(SELECT 1 FROM public.ledger_funds_account f WHERE f.account_id=transaction.account_id));

ALTER TABLE public.transaction_leg ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transaction_leg FORCE ROW LEVEL SECURITY;
CREATE POLICY funds_select ON public.transaction_leg FOR SELECT USING (public.funds_reference_allowed('ACCOUNT',account_id,FALSE) AND public.funds_reference_allowed('POSTING',transaction_id,FALSE));
CREATE POLICY funds_insert ON public.transaction_leg FOR INSERT WITH CHECK (public.funds_reference_allowed('ACCOUNT',account_id,TRUE) AND public.funds_reference_allowed('POSTING',transaction_id,TRUE));
CREATE POLICY funds_update ON public.transaction_leg FOR UPDATE USING (public.funds_reference_allowed('ACCOUNT',account_id,TRUE) AND public.funds_reference_allowed('POSTING',transaction_id,TRUE)) WITH CHECK (public.funds_reference_allowed('ACCOUNT',account_id,TRUE) AND public.funds_reference_allowed('POSTING',transaction_id,TRUE));
CREATE POLICY funds_delete ON public.transaction_leg FOR DELETE USING (public.funds_reference_allowed('ACCOUNT',account_id,TRUE) AND public.funds_reference_allowed('POSTING',transaction_id,TRUE) AND NOT EXISTS(SELECT 1 FROM public.ledger_funds_account f WHERE f.account_id=transaction_leg.account_id));

ALTER TABLE public.transaction_status_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transaction_status_history FORCE ROW LEVEL SECURITY;
CREATE POLICY funds_select ON public.transaction_status_history FOR SELECT USING (public.funds_reference_allowed('POSTING',transaction_id,FALSE));
CREATE POLICY funds_insert ON public.transaction_status_history FOR INSERT WITH CHECK (public.funds_reference_allowed('POSTING',transaction_id,TRUE));
CREATE POLICY funds_update ON public.transaction_status_history FOR UPDATE USING (public.funds_reference_allowed('POSTING',transaction_id,TRUE)) WITH CHECK (public.funds_reference_allowed('POSTING',transaction_id,TRUE));
CREATE POLICY funds_delete ON public.transaction_status_history FOR DELETE USING (public.funds_reference_allowed('POSTING',transaction_id,TRUE));

ALTER TABLE public.ledger_posting_request ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ledger_posting_request FORCE ROW LEVEL SECURITY;
CREATE POLICY funds_select ON public.ledger_posting_request FOR SELECT USING (transaction_id IS NULL OR public.funds_reference_allowed('POSTING',transaction_id,FALSE));
CREATE POLICY funds_insert ON public.ledger_posting_request FOR INSERT WITH CHECK ((idempotency_key NOT LIKE 'fnd:%' OR current_setting('firefly.funds.purpose',true) IN ('FUNDING','RESERVE','REVERSE')) AND (transaction_id IS NULL OR public.funds_reference_allowed('POSTING',transaction_id,TRUE)));
CREATE POLICY funds_update ON public.ledger_posting_request FOR UPDATE USING ((idempotency_key NOT LIKE 'fnd:%' OR current_setting('firefly.funds.purpose',true) IN ('FUNDING','RESERVE','REVERSE')) AND (transaction_id IS NULL OR public.funds_reference_allowed('POSTING',transaction_id,TRUE))) WITH CHECK ((idempotency_key NOT LIKE 'fnd:%' OR current_setting('firefly.funds.purpose',true) IN ('FUNDING','RESERVE','REVERSE')) AND (transaction_id IS NULL OR public.funds_reference_allowed('POSTING',transaction_id,TRUE)));
CREATE POLICY funds_delete ON public.ledger_posting_request FOR DELETE USING (FALSE);

ALTER TABLE public.ledger_funds_account ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ledger_funds_account FORCE ROW LEVEL SECURITY;
CREATE POLICY funds_select ON public.ledger_funds_account FOR SELECT USING (public.funds_scope_matches(tenant_id,ARRAY[account_id],FALSE));
CREATE POLICY funds_insert ON public.ledger_funds_account FOR INSERT WITH CHECK (public.funds_scope_matches(tenant_id,ARRAY[account_id],TRUE));
CREATE POLICY funds_update ON public.ledger_funds_account FOR UPDATE USING (public.funds_scope_matches(tenant_id,ARRAY[account_id],TRUE)) WITH CHECK (public.funds_scope_matches(tenant_id,ARRAY[account_id],TRUE));
CREATE POLICY funds_delete ON public.ledger_funds_account FOR DELETE USING (FALSE);

ALTER TABLE public.ledger_funds_reservation ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ledger_funds_reservation FORCE ROW LEVEL SECURITY;
CREATE POLICY funds_select ON public.ledger_funds_reservation FOR SELECT USING (public.funds_scope_matches(tenant_id,ARRAY[source_account_id,destination_account_id],FALSE));
CREATE POLICY funds_insert ON public.ledger_funds_reservation FOR INSERT WITH CHECK (public.funds_scope_matches(tenant_id,ARRAY[source_account_id,destination_account_id],TRUE));
CREATE POLICY funds_update ON public.ledger_funds_reservation FOR UPDATE USING (public.funds_scope_matches(tenant_id,ARRAY[source_account_id,destination_account_id],TRUE)) WITH CHECK (public.funds_scope_matches(tenant_id,ARRAY[source_account_id,destination_account_id],TRUE));
CREATE POLICY funds_delete ON public.ledger_funds_reservation FOR DELETE USING (FALSE);

ALTER TABLE public.ledger_funds_operation ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ledger_funds_operation FORCE ROW LEVEL SECURITY;
CREATE POLICY funds_select ON public.ledger_funds_operation FOR SELECT USING (public.funds_scope_matches(tenant_id,ARRAY[source_account_id,destination_account_id],FALSE));
CREATE POLICY funds_insert ON public.ledger_funds_operation FOR INSERT WITH CHECK (public.funds_scope_matches(tenant_id,ARRAY[source_account_id,destination_account_id],TRUE));
CREATE POLICY funds_update ON public.ledger_funds_operation FOR UPDATE USING (public.funds_scope_matches(tenant_id,ARRAY[source_account_id,destination_account_id],TRUE)) WITH CHECK (public.funds_scope_matches(tenant_id,ARRAY[source_account_id,destination_account_id],TRUE));
CREATE POLICY funds_delete ON public.ledger_funds_operation FOR DELETE USING (FALSE);

ALTER TABLE public.ledger_funds_action ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ledger_funds_action FORCE ROW LEVEL SECURITY;
CREATE POLICY funds_select ON public.ledger_funds_action FOR SELECT USING (public.funds_scoped_allowed('RESERVATION',tenant_id,reservation_id,FALSE));
CREATE POLICY funds_insert ON public.ledger_funds_action FOR INSERT WITH CHECK (public.funds_scoped_allowed('RESERVATION',tenant_id,reservation_id,TRUE));
CREATE POLICY funds_update ON public.ledger_funds_action FOR UPDATE USING (public.funds_scoped_allowed('RESERVATION',tenant_id,reservation_id,TRUE)) WITH CHECK (public.funds_scoped_allowed('RESERVATION',tenant_id,reservation_id,TRUE));
CREATE POLICY funds_delete ON public.ledger_funds_action FOR DELETE USING (FALSE);

ALTER TABLE public.ledger_funds_control ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ledger_funds_control FORCE ROW LEVEL SECURITY;
CREATE POLICY funds_select ON public.ledger_funds_control FOR SELECT USING (public.funds_scope_matches(tenant_id,ARRAY[account_id],FALSE));
CREATE POLICY funds_insert ON public.ledger_funds_control FOR INSERT WITH CHECK (public.funds_scope_matches(tenant_id,ARRAY[account_id],TRUE));
CREATE POLICY funds_update ON public.ledger_funds_control FOR UPDATE USING (public.funds_scope_matches(tenant_id,ARRAY[account_id],TRUE)) WITH CHECK (public.funds_scope_matches(tenant_id,ARRAY[account_id],TRUE));
CREATE POLICY funds_delete ON public.ledger_funds_control FOR DELETE USING (FALSE);

ALTER TABLE public.ledger_funds_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ledger_funds_event FORCE ROW LEVEL SECURITY;
CREATE POLICY funds_select ON public.ledger_funds_event FOR SELECT USING (public.funds_scoped_allowed(CASE WHEN event_type='BANK_CONTROL_FUNDING' THEN 'OPERATION' ELSE 'RESERVATION' END,tenant_id,resource_id,FALSE));
CREATE POLICY funds_insert ON public.ledger_funds_event FOR INSERT WITH CHECK (public.funds_scoped_allowed(CASE WHEN event_type='BANK_CONTROL_FUNDING' THEN 'OPERATION' ELSE 'RESERVATION' END,tenant_id,resource_id,TRUE));
CREATE POLICY funds_update ON public.ledger_funds_event FOR UPDATE USING (public.funds_scoped_allowed(CASE WHEN event_type='BANK_CONTROL_FUNDING' THEN 'OPERATION' ELSE 'RESERVATION' END,tenant_id,resource_id,TRUE)) WITH CHECK (public.funds_scoped_allowed(CASE WHEN event_type='BANK_CONTROL_FUNDING' THEN 'OPERATION' ELSE 'RESERVATION' END,tenant_id,resource_id,TRUE));
CREATE POLICY funds_delete ON public.ledger_funds_event FOR DELETE USING (FALSE);

ALTER TABLE public.event_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.event_outbox FORCE ROW LEVEL SECURITY;
CREATE POLICY funds_select ON public.event_outbox FOR SELECT USING (aggregate_type<>'MANAGED_FUNDS' OR public.funds_scoped_allowed(CASE WHEN event_type='BANK_CONTROL_FUNDING' THEN 'OPERATION' ELSE 'RESERVATION' END,funds_tenant_id,aggregate_id::uuid,FALSE));
CREATE POLICY funds_insert ON public.event_outbox FOR INSERT WITH CHECK (aggregate_type<>'MANAGED_FUNDS' OR public.funds_scoped_allowed(CASE WHEN event_type='BANK_CONTROL_FUNDING' THEN 'OPERATION' ELSE 'RESERVATION' END,funds_tenant_id,aggregate_id::uuid,TRUE));
CREATE POLICY funds_update ON public.event_outbox FOR UPDATE USING (aggregate_type<>'MANAGED_FUNDS' OR public.funds_scoped_allowed(CASE WHEN event_type='BANK_CONTROL_FUNDING' THEN 'OPERATION' ELSE 'RESERVATION' END,funds_tenant_id,aggregate_id::uuid,TRUE)) WITH CHECK (aggregate_type<>'MANAGED_FUNDS' OR public.funds_scoped_allowed(CASE WHEN event_type='BANK_CONTROL_FUNDING' THEN 'OPERATION' ELSE 'RESERVATION' END,funds_tenant_id,aggregate_id::uuid,TRUE));
CREATE POLICY funds_delete ON public.event_outbox FOR DELETE USING (FALSE);
