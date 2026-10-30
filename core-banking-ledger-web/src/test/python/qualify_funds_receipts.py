#!/usr/bin/env python3
"""Owned native invariant fixture; never establishes HTTP/SDK/enrollment or external financial acceptance."""
import argparse,json,os,secrets,socket,subprocess,tempfile,uuid
from pathlib import Path
PG=Path('/opt/homebrew/opt/postgresql@17/bin')
REPO=Path(__file__).resolve().parents[4]
MIGRATIONS=REPO/'core-banking-ledger-models/src/main/resources/db/migration'
S='00000000-0000-0000-0000-000000000002';D='00000000-0000-0000-0000-000000000003'
def uid():return str(uuid.uuid4())
def q(v):return "'"+str(v).replace("'","''")+"'"
def context(purpose):
 return "BEGIN; SELECT set_config('firefly.funds.tenant','owned',true),set_config('firefly.funds.purpose',"+q(purpose)+",true),set_config('firefly.funds.accounts',"+q(json.dumps([S,D]))+",true),set_config('firefly.funds.actor_issuer','https://native-fixture.invalid',true),set_config('firefly.funds.actor_subject','synthetic-native',true),set_config('firefly.funds.token_expires_epoch_millis',(floor(extract(epoch FROM clock_timestamp())*1000)::bigint+300000)::text,true);\n"
def posting_sql(posting,key,reverse=False,original=None):
 related=",related_transaction_id,relation_type" if reverse else ''
 relation=","+q(original)+",'REVERSAL'" if reverse else ''
 return f"""SELECT public.fnd_lock_posting_key({key});
INSERT INTO ledger_posting_request(idempotency_key,input_fingerprint) VALUES({key},repeat('0',64));
INSERT INTO transaction(transaction_id,account_id,external_reference,transaction_type,transaction_status,total_amount,currency,transaction_date,managed_posting{related})
 VALUES('{posting}','{S}',{key},'transfer','{'posted' if reverse else 'pending'}',1,'USD',CURRENT_TIMESTAMP,true{relation});
INSERT INTO transaction_status_history(transaction_status_history_id,transaction_id,status_code,status_start_datetime) VALUES('{uid()}','{posting}','{'posted' if reverse else 'pending'}',CURRENT_TIMESTAMP);
INSERT INTO transaction_leg(transaction_leg_id,transaction_id,account_id,leg_type,amount,currency) VALUES
 ('{uid()}','{posting}','{S}','{'CREDIT' if reverse else 'DEBIT'}',1,'USD'),('{uid()}','{posting}','{D}','{'DEBIT' if reverse else 'CREDIT'}',1,'USD');
UPDATE ledger_posting_request SET transaction_id='{posting}' WHERE idempotency_key={key};
"""
def reserve_sql(operation,posting,finish=True):
 key=f"public.fnd_posting_key('owned','{operation}','RESERVE')"
 text=context('RESERVE')+fr"""INSERT INTO ledger_funds_operation(tenant_id,operation_id,kind,fingerprint,source_account_id,destination_account_id)
 VALUES('owned','{operation}','RESERVE',encode(sha256(convert_to('RESERVE|{S}|{D}|1|USD|300|null','UTF8')),'hex'),'{S}','{D}');
SELECT * FROM public.fnd_begin_reserve('owned','{operation}','{S}','{D}',1,'USD',300,NULL) \gset
"""+posting_sql(posting,key)+f"""INSERT INTO ledger_funds_reservation(reservation_id,tenant_id,operation_id,source_account_id,destination_account_id,amount,currency,state,expires_at,posting_id,receipt_contract_version)
 VALUES(:'reservation_id','owned','{operation}','{S}','{D}',1,'USD','HELD',:'expires_at'::timestamptz,'{posting}',0);
UPDATE ledger_funds_account SET held=held+1,version=version+1 WHERE account_id='{S}';
UPDATE ledger_funds_operation SET reservation_id=:'reservation_id' WHERE tenant_id='owned' AND operation_id='{operation}';
"""
 if finish:text+="SELECT public.fnd_finish_command(:'command_id');\n"
 return text+"SELECT jsonb_build_object('reservation',:'reservation_id','command',:'command_id'); COMMIT;"
def action_sql(reservation,posting,kind,omit_finish=False):
 action=uid();text=context(kind)+f"INSERT INTO ledger_funds_action VALUES('owned','{action}','{reservation}','{kind}');\nSELECT * FROM public.fnd_begin_transition('owned','{reservation}','{action}','{kind}') "+chr(92)+"gset\n"
 if kind=='COMMIT':
  text+=f"""UPDATE transaction SET transaction_status='posted',row_version=row_version+1 WHERE transaction_id='{posting}';
INSERT INTO transaction_status_history(transaction_status_history_id,transaction_id,status_code,status_start_datetime) VALUES('{uid()}','{posting}','posted',CURRENT_TIMESTAMP);
UPDATE ledger_funds_account SET booked=booked-1,held=held-1,version=version+1 WHERE account_id='{S}';
UPDATE ledger_funds_account SET booked=booked+1,version=version+1 WHERE account_id='{D}';
UPDATE ledger_funds_reservation SET state='COMMITTED',version=version+1 WHERE reservation_id='{reservation}';
"""
 elif kind in ('RELEASE','EXPIRE'):
  state='RELEASED' if kind=='RELEASE' else 'EXPIRED'
  text+=f"UPDATE transaction SET transaction_status='reversed',row_version=row_version+1 WHERE transaction_id='{posting}';INSERT INTO transaction_status_history(transaction_status_history_id,transaction_id,status_code,status_start_datetime) VALUES('{uid()}','{posting}','reversed',CURRENT_TIMESTAMP);UPDATE ledger_funds_account SET held=held-1,version=version+1 WHERE account_id='{S}';UPDATE ledger_funds_reservation SET state='{state}',version=version+1 WHERE reservation_id='{reservation}';\n"
 else:
  reversal=uid();text+=posting_sql(reversal,f"public.fnd_posting_key('owned',(SELECT operation_id FROM ledger_funds_reservation WHERE reservation_id='{reservation}'),'REVERSE')",True,posting)
  text+=f"""UPDATE ledger_funds_account SET booked=booked+1,version=version+1 WHERE account_id='{S}';
UPDATE ledger_funds_account SET booked=booked-1,version=version+1 WHERE account_id='{D}';
UPDATE ledger_funds_reservation SET state='REVERSED',reversal_id='{reversal}',version=version+1 WHERE reservation_id='{reservation}';
"""
 if not omit_finish:text+="SELECT public.fnd_finish_command(:'command_id');\n"
 return text+"COMMIT;"
def main():
 p=argparse.ArgumentParser();p.add_argument('--run-dir',type=Path,required=True);p.add_argument('--java-native',action='store_true');p.add_argument('--extended',action='store_true');p.add_argument('--identity-controls',action='store_true');a=p.parse_args();a.run_dir.mkdir(parents=True,exist_ok=False)
 sock=Path(tempfile.mkdtemp(prefix='fnd-money-',dir='/tmp'));data=a.run_dir/'data';started=False;checks=[];failures=[];password=secrets.token_urlsafe(32);owned_pid=None
 with socket.socket() as listener:listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
 def sql(text,user='receipt_owner'):
  return subprocess.run([str(PG/'psql'),'-X','-h',str(sock),'-p',str(port),'-U',user,'-d','postgres','-v','ON_ERROR_STOP=1','-At'],input=text,text=True,capture_output=True,timeout=15)
 def check(label,result,success=True,expected=None,expected_output=None):
  ok=(result.returncode==0)==success and (success or expected is not None and expected in result.stderr) and (expected_output is None or (result.stdout or '').strip()==expected_output);checks.append({'case':label,'passed':ok})
  if not ok:failures.append({'case':label,'stderr':(result.stderr or '')[-1600:],'stdout':(result.stdout or '')[-1000:]})
  print(('PASS ' if ok else 'FAIL ')+label,flush=True)
  if not ok:raise AssertionError(label)
 try:
  r=subprocess.run([str(PG/'initdb'),'-D',str(data),'-U','receipt_owner','--auth-local=trust','--auth-host=scram-sha-256','--no-locale','--encoding=UTF8'],capture_output=True,text=True,timeout=30);r.check_returncode()
  bind_host='127.0.0.1' if a.java_native else ''
  r=subprocess.run([str(PG/'pg_ctl'),'-D',str(data),'-l',str(a.run_dir/'postgres.log'),'-o',f"-F -k {sock} -p {port} -h '{bind_host}' -c shared_buffers=16MB -c max_connections=10",'-w','start'],capture_output=True,text=True,timeout=30);started=(data/'postmaster.pid').exists();r.check_returncode();owned_pid=int((data/'postmaster.pid').read_text().splitlines()[0])
  sql("CREATE ROLE funds_runtime LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD "+q(password)+"; CREATE ROLE firefly_funds_policy_reader NOLOGIN NOSUPERUSER NOBYPASSRLS; CREATE ROLE firefly_funds_policy_writer NOLOGIN NOSUPERUSER NOBYPASSRLS; CREATE ROLE firefly_funds_receipt_guard NOLOGIN NOSUPERUSER NOBYPASSRLS; CREATE ROLE firefly_funds_receipt_api NOLOGIN NOSUPERUSER NOBYPASSRLS; GRANT firefly_funds_receipt_api TO funds_runtime; REVOKE CREATE ON SCHEMA public FROM PUBLIC; GRANT USAGE ON SCHEMA public TO funds_runtime; ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO funds_runtime;").check_returncode()
  for path in sorted(MIGRATIONS.glob('V*.sql'),key=lambda path:int(path.name.split('__')[0][1:])):
   r=sql("BEGIN;\n"+path.read_text()+"\nCOMMIT;\n");(a.run_dir/(path.stem+'.log')).write_text(r.stdout+r.stderr);r.check_returncode();assert r.stdout.rstrip().endswith("COMMIT"),path.name
   if a.extended and path.name.startswith('V58__'):
    import funds_receipts_extended as extended
    extended.setup_before_v59(sql,context,posting_sql)
  # Match read-only projection deployment grants instead of test-only broad table defaults.
  views=["ach","card","deposit","direct_debit","fee","sepa_transfer","standing_order","transfer","wire_transfer","withdrawal"]
  for view in views:
   sql("REVOKE INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER ON public.ledger_search_transaction_line_"+view+" FROM funds_runtime;").check_returncode()
  check('invoker projections expose no runtime DML privileges',sql("DO $$ BEGIN IF EXISTS(SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relname LIKE 'ledger_search_%' AND (has_table_privilege(current_user,c.oid,'INSERT') OR has_table_privilege(current_user,c.oid,'UPDATE') OR has_table_privilege(current_user,c.oid,'DELETE'))) THEN RAISE EXCEPTION 'view DML granted'; END IF; END $$;",'funds_runtime'))
  if a.extended:extended.legacy_checks(sql,check,context,action_sql)
  r=sql(f"INSERT INTO ledger_funds_account(account_id,tenant_id,enrollment_id,contract_id,currency,currency_scale,currency_policy,kind,state,booked) VALUES('{S}','owned','{uid()}','{uid()}','USD',2,'native-fixture','CUSTOMER','ACTIVE',100),('{D}','owned','{uid()}','{uid()}','USD',2,'native-fixture','CUSTOMER','ACTIVE',0) ON CONFLICT(account_id) DO NOTHING;");r.check_returncode()
  check('actual unprivileged nonowner runtime',sql("DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname=current_user AND NOT rolsuper AND NOT rolbypassrls AND NOT has_schema_privilege(current_user,'funds_private','USAGE')) THEN RAISE EXCEPTION 'privileged runtime'; END IF; END $$;",'funds_runtime'))
  sql("CREATE ROLE hostile_runtime LOGIN NOINHERIT PASSWORD "+q(password)+";GRANT funds_runtime TO hostile_runtime WITH INHERIT TRUE,SET FALSE;GRANT firefly_funds_receipt_guard TO hostile_runtime WITH INHERIT FALSE,SET TRUE;").check_returncode()
  check('hostile NOINHERIT membership actually permits SET ROLE',sql("DO $$ BEGIN IF pg_has_role(current_user,'firefly_funds_receipt_guard','USAGE') OR NOT pg_has_role(current_user,'firefly_funds_receipt_guard','MEMBER') THEN RAISE EXCEPTION 'fixture is not hostile NOINHERIT';END IF;END $$;SET ROLE firefly_funds_receipt_guard;RESET ROLE;",'hostile_runtime'))
  check('hostile assumable guard membership denies before effects',sql(context('RESERVE')+f"SELECT public.fnd_begin_reserve('owned','{uid()}','{S}','{D}',1,'USD',300,NULL);COMMIT;",'hostile_runtime'),False,expected='FND_CURRENT_AUTHORITY_REQUIRED')
  catalogue=(REPO/'core-banking-ledger-models/src/main/java/io/fireflysoftware/core/banking/ledger/models/repositories/funds/FundsNativeRepository.java').read_text().split(chr(34)*3,2)[1]
  for owner,user in [('firefly_funds_policy_reader','hostile_policy_reader'),('firefly_funds_policy_writer','hostile_policy_writer')]:
   sql("CREATE ROLE "+user+" LOGIN NOINHERIT PASSWORD "+q(password)+";GRANT funds_runtime TO "+user+" WITH INHERIT TRUE,SET FALSE;GRANT "+owner+" TO "+user+" WITH INHERIT FALSE,SET TRUE;").check_returncode()
   check(owner+' hostile membership permits SET ROLE without inherited privileges',sql("DO $$ BEGIN IF pg_has_role(current_user,'"+owner+"','USAGE') OR NOT pg_has_role(current_user,'"+owner+"','MEMBER') OR has_schema_privilege(current_user,'funds_private','USAGE') THEN RAISE EXCEPTION 'invalid hostile membership fixture';END IF;END $$;SET ROLE "+owner+";RESET ROLE;",user))
   check(owner+' is rejected by actual runtime catalogue query',sql(catalogue,user),expected_output='f')
   check(owner+' is rejected by native current-principal context',sql(context('READ')+f"SELECT public.fnd_assert_live_context('owned','READ','{S}','{D}');COMMIT;",user),False,expected='FND_CURRENT_AUTHORITY_REQUIRED')
  operation=uid();posting=uid();r=sql(reserve_sql(operation,posting),'funds_runtime');check('native reserve stamps marker and retains full original',r)
  record=next(json.loads(line) for line in r.stdout.splitlines() if line.startswith('{'));reservation=record['reservation']
  check('native owner stamped marker1 and single retained reserve time',sql(f"DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM ledger_funds_reservation r JOIN ledger_funds_reserve_intent i USING(reservation_id) WHERE r.reservation_id='{reservation}' AND r.receipt_contract_version=1 AND i.expires_at_epoch_millis=i.intent_recorded_at_epoch_millis+300000 AND r.expires_at=to_timestamp(i.expires_at_epoch_millis::numeric/1000)) THEN RAISE EXCEPTION 'stamp/time mismatch'; END IF;END $$;"))
  check('privileged insert cannot classify a new row as legacy',sql(f"INSERT INTO ledger_funds_reservation(reservation_id,tenant_id,operation_id,source_account_id,destination_account_id,amount,currency,state,expires_at,posting_id,receipt_contract_version) SELECT '{uid()}',tenant_id,'{uid()}',source_account_id,destination_account_id,amount,currency,state,expires_at,posting_id,0 FROM ledger_funds_reservation WHERE reservation_id='{reservation}';"),False,expected='FND_NATIVE_COMMAND_REQUIRED')
  check('missing finish rolls back actual COMMIT',sql(action_sql(reservation,posting,'COMMIT',True),'funds_runtime'),False,expected='FND_RECEIPT_INVALID_CANONICAL')
  check('reservation remains HELD after failed native completion',sql(f"DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM ledger_funds_reservation WHERE reservation_id='{reservation}' AND state='HELD') THEN RAISE EXCEPTION 'changed'; END IF;END $$;"))
  check('actual native COMMIT retains receipt audit and outbox',sql(action_sql(reservation,posting,'COMMIT'),'funds_runtime'))
  check('actual native REVERSE links immutable original COMMIT',sql(action_sql(reservation,posting,'REVERSE'),'funds_runtime'))
  check('two immutable receipts and exact conservation',sql(f"DO $$ BEGIN IF (SELECT count(*) FROM ledger_funds_monetary_receipt WHERE reservation_id='{reservation}')<>2 OR NOT EXISTS(SELECT 1 FROM ledger_funds_account WHERE account_id='{S}' AND booked=100 AND held=0) OR NOT EXISTS(SELECT 1 FROM ledger_funds_account WHERE account_id='{D}' AND booked=0 AND held=0) THEN RAISE EXCEPTION 'drift'; END IF;END $$;"))
  check('marker cannot downgrade',sql(context('COMMIT')+f"UPDATE ledger_funds_reservation SET receipt_contract_version=0 WHERE reservation_id='{reservation}'; COMMIT;",'funds_runtime'),False,expected='FND_NATIVE_COMMAND_REQUIRED')
  check('direct claim UPDATE without preheld key denies',sql(context('RESERVE')+f"UPDATE ledger_posting_request SET input_fingerprint=repeat('1',64) WHERE transaction_id='{posting}'; COMMIT;",'funds_runtime'),False,expected='FND_POSTING_KEY_GATE_REQUIRED')
  check('receipt UPDATE is RLS-invisible to runtime',sql(context('COMMIT')+f"DO $$ DECLARE changed bigint;BEGIN UPDATE ledger_funds_monetary_receipt SET actor_subject='forged' WHERE reservation_id='{reservation}';GET DIAGNOSTICS changed=ROW_COUNT;IF changed<>0 THEN RAISE EXCEPTION 'receipt mutated';END IF;END $$; COMMIT;",'funds_runtime'))
  check('receipt owner cannot mutate retained evidence',sql(f"UPDATE ledger_funds_monetary_receipt SET actor_subject='forged' WHERE reservation_id='{reservation}';"),False,expected='FND_IMMUTABLE_RETAINED_EVIDENCE')

  if a.identity_controls:
   import funds_receipts_extended as extended
   extended.bound_identity_checks(sql,check,context,posting)

  sql(f"INSERT INTO transaction_line_deposit(transaction_line_deposit_id,transaction_id,deposit_method,deposit_timestamp) VALUES('{uid()}','{posting}','NATIVE','2026-01-01'),('{uid()}','{posting}','NATIVE','2026-01-02'),('{uid()}','{posting}','NATIVE','2026-01-03');").check_returncode()
  assertion="DO $$ BEGIN IF (SELECT count(*) FROM ledger_search_transaction_line_deposit WHERE ledger_transaction_total_amount=1)<>3 THEN RAISE EXCEPTION 'wrong joined count';END IF;END $$;COMMIT;"
  check('invoker joined amount projection reads exact current pair rows',sql(context('READ')+assertion,'funds_runtime'))
  invisible="DO $$ BEGIN IF EXISTS(SELECT 1 FROM ledger_search_transaction_line_deposit) THEN RAISE EXCEPTION 'cross context leak';END IF;END $$;COMMIT;"
  check('invoker view has no managed rows without context',sql(invisible,'funds_runtime'))
  check('invoker view hides wrong tenant',sql(context('READ').replace("'owned'","'other'")+invisible,'funds_runtime'))
  check('invoker view hides partial resource grants',sql(context('READ').replace(q(json.dumps([S,D])),q(json.dumps([S])))+invisible,'funds_runtime'))
  check('native current context rejects expired principal',sql(context('READ')+f"SELECT set_config('firefly.funds.token_expires_epoch_millis','1',true);SELECT public.fnd_assert_live_context('owned','READ','{S}','{D}');COMMIT;",'funds_runtime'),False,expected='FND_CURRENT_AUTHORITY_REQUIRED')
  sql("CREATE ROLE projection_reader LOGIN;GRANT firefly_funds_receipt_api TO projection_reader;").check_returncode()
  check('view SELECT grant cannot substitute underlying invoker privileges',sql(context('READ')+"SELECT count(*) FROM ledger_search_transaction_line_deposit;COMMIT;",'projection_reader'),False,expected='permission denied for table')
  plan=sql(context('READ')+"EXPLAIN (FORMAT JSON) SELECT * FROM ledger_search_transaction_line_deposit WHERE ledger_transaction_total_amount>=1 ORDER BY deposit_timestamp DESC LIMIT 2 OFFSET 1;COMMIT;",'funds_runtime')
  check('projection filter sort limit stay in PostgreSQL query plan',plan)
  (a.run_dir/'projection-query-plan.log').write_text(plan.stdout)

  for label in ['posted','failed','reversed']:
   sql(f"INSERT INTO transaction(transaction_id,account_id,external_reference,transaction_type,transaction_status,total_amount,currency,transaction_date,managed_posting) VALUES('{uid()}','{uid()}','native-filter-{label}','transfer','{label}',2,'USD',CURRENT_TIMESTAMP,false);").check_returncode()
  if a.extended:
   extended.concurrency_checks(sql,check,context,reserve_sql,action_sql,a.run_dir)
   extended.rollback_and_witness_checks(sql,check,context,reserve_sql,action_sql)
  if a.java_native:
   env=os.environ.copy();env['JAVA_HOME']='/Library/Java/JavaVirtualMachines/openjdk-25.jdk/Contents/Home';env['LEDGER_NATIVE_PASSWORD']=password
   command=['/opt/homebrew/bin/mvn','-o','-B','-ntp','-s','../framework-validation-redaction/private-settings.xml','-Dmaven.repo.local=../ledger-receipt-delivery-m2','-DskipOpenApiGen=true','-Dopenapi.gen.skip=true','-Dtest=FundsRepositoryNativeIT','-Dsurefire.failIfNoSpecifiedTests=false','-Dledger.native.port='+str(port),'-Dledger.native.extended='+str(a.extended).lower(),'-pl','core-banking-ledger-core','-am','test']
   (a.run_dir/'java-command.json').write_text(json.dumps(command,indent=2)+'\n')
   with (a.run_dir/'java-native.log').open('w') as log:
    result=subprocess.run(command,cwd=REPO,env=env,stdout=log,stderr=subprocess.STDOUT,text=True,timeout=60)
   check('genuine Spring Data native service and MonoVoid financial roundtrip',result)

 except BaseException as error:
  failures.append({"case":"fixture setup/runner exception","type":type(error).__name__,"message":str(error)})
  raise
 finally:
  if started:subprocess.run([str(PG/'pg_ctl'),'-D',str(data),'-m','fast','-w','stop'],capture_output=True,text=True,timeout=20)
  with socket.socket() as probe:port_absent=probe.connect_ex(('127.0.0.1',port))!=0
  status=subprocess.run([str(PG/'pg_ctl'),'-D',str(data),'status'],capture_output=True,text=True,timeout=5)
  (a.run_dir/'cleanup.json').write_text(json.dumps({'owned_pid':owned_pid,'owned_port':port,'tcp_bound_to':'127.0.0.1' if a.java_native else None,'postmaster_pid_absent':not (data/'postmaster.pid').exists(),'pg_ctl_status':status.returncode,'owned_port_absent':port_absent,'http_started':False,'producer_started':False},indent=2)+'\n')
  try:sock.rmdir()
  except OSError:pass
  (a.run_dir/'result.json').write_text(json.dumps({'status':'PASS' if checks and not failures else 'FAIL','scope':'native synthetic invariants only; no real enrollment/HTTP/SDK/external-money acceptance','checks':checks,'failures':failures,'count':len(checks)},indent=2)+'\n')
 if failures:raise SystemExit(1)
if __name__=='__main__':main()
