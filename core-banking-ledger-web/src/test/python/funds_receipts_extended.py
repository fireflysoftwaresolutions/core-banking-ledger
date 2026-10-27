"""Owned native fixtures for admitted legacy holds and advisory/row-lock ordering."""
import hashlib,json,time,uuid
from concurrent.futures import ThreadPoolExecutor
S='00000000-0000-0000-0000-000000000002';D='00000000-0000-0000-0000-000000000003'
B='00000000-0000-0000-0000-000000000001'
LEGACY=[('00000000-0000-0000-0000-000000000101','00000000-0000-0000-0000-000000000201','00000000-0000-0000-0000-000000000301'),('00000000-0000-0000-0000-000000000102','00000000-0000-0000-0000-000000000202','00000000-0000-0000-0000-000000000302'),('00000000-0000-0000-0000-000000000103','00000000-0000-0000-0000-000000000203','00000000-0000-0000-0000-000000000303')]
def uid():return str(uuid.uuid4())
def setup_before_v59(sql,context,posting_sql):
 sql(f"INSERT INTO ledger_funds_account(account_id,tenant_id,enrollment_id,contract_id,currency,currency_scale,currency_policy,kind,state,booked) VALUES('{B}','owned','{uid()}','{uid()}','USD',2,'native-fixture','BANK_CONTROL','ACTIVE',0),('{S}','owned','{uid()}','{uid()}','USD',2,'native-fixture','CUSTOMER','ACTIVE',100),('{D}','owned','{uid()}','{uid()}','USD',2,'native-fixture','CUSTOMER','ACTIVE',0);").check_returncode()
 for index,(reservation,operation,posting) in enumerate(LEGACY):
  key="'fnd:"+hashlib.sha256(f'owned|{operation}|RESERVE'.encode()).hexdigest()+"'"
  post=posting_sql(posting,key).replace(f'SELECT public.fnd_lock_posting_key({key});',f'SELECT pg_advisory_xact_lock(hashtextextended({key},55603));')
  expires="clock_timestamp()+interval '300 seconds'" if index<2 else "clock_timestamp()+interval '200 milliseconds'"
  text=context('RESERVE')+f"INSERT INTO ledger_funds_operation(tenant_id,operation_id,kind,fingerprint,source_account_id,destination_account_id) VALUES('owned','{operation}','RESERVE',encode(sha256(convert_to('RESERVE|{S}|{D}|1|USD|300|null','UTF8')),'hex'),'{S}','{D}');\n"+post+f"INSERT INTO ledger_funds_reservation(reservation_id,tenant_id,operation_id,source_account_id,destination_account_id,amount,currency,state,expires_at,posting_id) VALUES('{reservation}','owned','{operation}','{S}','{D}',1,'USD','HELD',{expires},'{posting}');UPDATE ledger_funds_account SET held=held+1,version=version+1 WHERE account_id='{S}';UPDATE ledger_funds_operation SET reservation_id='{reservation}' WHERE tenant_id='owned' AND operation_id='{operation}';COMMIT;"
  sql(text,'funds_runtime').check_returncode()
def legacy_checks(sql,check,context,action_sql):
 check('upgrade classifies only preexisting admitted holds as legacy0',sql("DO $$ BEGIN IF (SELECT count(*) FROM ledger_funds_reservation WHERE receipt_contract_version=0)<>3 THEN RAISE EXCEPTION 'legacy classification drift';END IF;END $$;"))
 r,o,p=LEGACY[0];check('valid legacy hold COMMIT drains without manufactured receipt',sql(action_sql(r,p,'COMMIT'),'funds_runtime'));check('valid legacy original REVERSE drains without manufactured receipt',sql(action_sql(r,p,'REVERSE'),'funds_runtime'))
 r,o,p=LEGACY[1];check('valid legacy hold RELEASE drains',sql(action_sql(r,p,'RELEASE'),'funds_runtime'))
 sql('SELECT pg_sleep(0.25);').check_returncode();r,o,p=LEGACY[2];check('valid legacy due hold EXPIRE drains',sql(action_sql(r,p,'EXPIRE'),'funds_runtime'))
 check('legacy drain retains version0 and explicit unavailable history',sql(f"DO $$ BEGIN IF EXISTS(SELECT 1 FROM ledger_funds_reservation WHERE receipt_contract_version=0 AND state='HELD') OR EXISTS(SELECT 1 FROM ledger_funds_reserve_intent) OR EXISTS(SELECT 1 FROM ledger_funds_monetary_receipt) OR NOT EXISTS(SELECT 1 FROM ledger_funds_account WHERE account_id='{S}' AND booked=100 AND held=0) OR NOT EXISTS(SELECT 1 FROM ledger_funds_account WHERE account_id='{D}' AND booked=0 AND held=0) THEN RAISE EXCEPTION 'legacy effect/evidence drift';END IF;END $$;"))
def wait_sleep(sql,marker):
 deadline=time.monotonic()+3
 while time.monotonic()<deadline:
  result=sql("SELECT count(*) FROM pg_stat_activity WHERE application_name='"+marker+"' AND wait_event='PgSleep';")
  if result.returncode==0 and result.stdout.strip()=='1':return
  time.sleep(0.025)
 raise AssertionError('Owned overlap never reached PgSleep barrier')
def bound_identity_checks(sql,check,context,retained_posting):
 """An UPDATE remains protected when only OLD names a retained money resource."""
 legacy=uid();other=uid();account=uid();history=uid()
 for posting in (legacy,other):
  sql(f"INSERT INTO transaction(transaction_id,account_id,external_reference,transaction_type,transaction_status,total_amount,currency,transaction_date) VALUES('{posting}','{account}','identity-control-{posting}','transfer','posted',1,'USD',CURRENT_TIMESTAMP);").check_returncode()
 sql(f"INSERT INTO transaction_status_history(transaction_status_history_id,transaction_id,status_code,status_start_datetime) VALUES('{history}','{legacy}','posted',CURRENT_TIMESTAMP);").check_returncode()
 check('ordinary legacy history reassociation remains outside receipt ownership',sql(context('COMMIT')+f"UPDATE transaction_status_history SET transaction_id='{other}' WHERE transaction_status_history_id='{history}';ROLLBACK;",'funds_runtime'))
 for purpose in ('COMMIT','FUNDING'):
  check(purpose+' OLD retained history cannot be reassociated to an unbound legacy posting',sql(context(purpose)+f"UPDATE transaction_status_history SET transaction_id='{legacy}' WHERE transaction_id='{retained_posting}';ROLLBACK;",'funds_runtime'),False,expected='FND_NATIVE_COMMAND_REQUIRED')
  check(purpose+' OLD retained leg cannot shed posting and account protection together',sql(context(purpose)+f"UPDATE transaction_leg SET transaction_id='{legacy}',account_id='{account}' WHERE transaction_id='{retained_posting}';ROLLBACK;",'funds_runtime'),False,expected='FND_NATIVE_COMMAND_REQUIRED')
 check('reassociation denial retains complete original posting membership',sql(f"DO $$ BEGIN IF (SELECT count(*) FROM transaction_leg WHERE transaction_id='{retained_posting}')<>2 OR (SELECT count(*) FROM transaction_status_history WHERE transaction_id='{retained_posting}')<>2 THEN RAISE EXCEPTION 'retained posting membership drift';END IF;END $$;"))
def concurrency_checks(sql,check,context,reserve_sql,action_sql,run_dir):
 operation,posting=uid(),uid();r=sql(reserve_sql(operation,posting),'funds_runtime');check('overlap fixture admits a fresh native hold',r);reservation=next(json.loads(v)['reservation'] for v in r.stdout.splitlines() if v.startswith('{'))
 marker='fnd-row-key-'+uid();action=uid();begin=context('COMMIT')+f"SET LOCAL application_name='{marker}';INSERT INTO ledger_funds_action VALUES('owned','{action}','{reservation}','COMMIT');SELECT * FROM public.fnd_begin_transition('owned','{reservation}','{action}','COMMIT');SELECT pg_sleep(0.8);ROLLBACK;"
 with ThreadPoolExecutor(max_workers=1) as pool:
  waiting=pool.submit(sql,begin,'funds_runtime');wait_sleep(sql,marker)
  direct=sql(context('RESERVE')+f"SET LOCAL lock_timeout='250ms';UPDATE ledger_posting_request SET input_fingerprint=repeat('1',64) WHERE transaction_id='{posting}';COMMIT;",'funds_runtime')
  check('direct UPDATE rejects missing gate before any row to key wait',direct,False,expected='FND_POSTING_KEY_GATE_REQUIRED');check('normal admitted begin has no row key deadlock',waiting.result())
 check('overlap rollback leaves hold intact',sql(f"DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM ledger_funds_reservation WHERE reservation_id='{reservation}' AND state='HELD') THEN RAISE EXCEPTION 'overlap changed hold';END IF;END $$;"))
 check('row key overlap hold RELEASE drains',sql(action_sql(reservation,posting,'RELEASE'),'funds_runtime'))
 operation,posting=uid(),uid();marker='fnd-absent-key-'+uid();text=reserve_sql(operation,posting);point=text.index('SELECT public.fnd_lock_posting_key(');text=text[:point]+f"SET LOCAL application_name='{marker}';SELECT pg_sleep(0.8);\n"+text[point:];key='fnd:'+hashlib.sha256(f'owned|{operation}|RESERVE'.encode()).hexdigest()
 with ThreadPoolExecutor(max_workers=1) as pool:
  admitted=pool.submit(sql,text,'funds_runtime');wait_sleep(sql,marker)
  competing=sql(context('RESERVE')+f"SET LOCAL lock_timeout='250ms';INSERT INTO ledger_posting_request(idempotency_key,input_fingerprint) VALUES('{key}',repeat('0',64));COMMIT;",'funds_runtime')
  check('absent claim INSERT waits at preindex advisory gate',competing,False,expected='canceling statement due to lock timeout');check('absent INSERT timeout identifies actual native advisory primitive',competing,False,expected='pg_advisory_xact_lock');result=admitted.result();check('native absent key admission survives direct INSERT competition',result)
 reservation=next(json.loads(v)['reservation'] for v in result.stdout.splitlines() if v.startswith('{'))
 check('absent key overlap hold RELEASE drains',sql(action_sql(reservation,posting,'RELEASE'),'funds_runtime'))
 (run_dir/'concurrency-scope.json').write_text(json.dumps({'barrier':'pg_stat_activity/PgSleep on owned uniquely named sessions','row_update_expected':'FND_POSTING_KEY_GATE_REQUIRED; lock-timeout would fail this test','absent_insert_expected':'lock timeout before native claim INSERT, then admitted writer succeeds','sleep_seconds':0.8,'lock_timeout_millis':250},indent=2)+'\n')
def rollback_and_witness_checks(sql,check,context,reserve_sql,action_sql):
 operation,posting=uid(),uid();r=sql(reserve_sql(operation,posting),'funds_runtime');check('rollback matrix admits a fresh native hold',r);reservation=next(json.loads(v)['reservation'] for v in r.stdout.splitlines() if v.startswith('{'))
 intact=f"DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM ledger_funds_reservation WHERE reservation_id='{reservation}' AND state='HELD') OR EXISTS(SELECT 1 FROM ledger_funds_monetary_receipt WHERE reservation_id='{reservation}') OR EXISTS(SELECT 1 FROM ledger_funds_event WHERE resource_id='{reservation}' AND event_type='FUNDS_COMMITTED') OR EXISTS(SELECT 1 FROM event_outbox WHERE aggregate_id='{reservation}' AND event_type='FUNDS_COMMITTED') OR NOT EXISTS(SELECT 1 FROM ledger_funds_account WHERE account_id='{S}' AND booked=100 AND held=1) OR NOT EXISTS(SELECT 1 FROM ledger_funds_account WHERE account_id='{D}' AND booked=0 AND held=0) THEN RAISE EXCEPTION 'atomic rollback drift';END IF;END $$;"
 for table,condition in [('ledger_funds_monetary_receipt',"effect_kind<>'COMMIT'"),('ledger_funds_event',"event_type<>'FUNDS_COMMITTED'"),('event_outbox',"event_type<>'FUNDS_COMMITTED'")]:
  constraint='fnd_fixture_deny_'+table;sql(f'ALTER TABLE {table} ADD CONSTRAINT {constraint} CHECK({condition}) NOT VALID;').check_returncode()
  try:check('injected '+table+' failure aborts actual COMMIT',sql(action_sql(reservation,posting,'COMMIT'),'funds_runtime'),False,expected=constraint)
  finally:sql(f'ALTER TABLE {table} DROP CONSTRAINT {constraint};').check_returncode()
  check(table+' failure rolls back receipt effect audit and outbox',sql(intact))
 text=action_sql(reservation,posting,'COMMIT').replace('SET booked=booked-1,held=held-1','SET booked=booked-2,held=held-1')
 check('incorrect booked effect rejects complete writes',sql(text,'funds_runtime'),False,expected='FND_RECEIPT_INVALID_CANONICAL');check('incorrect effect rollback retains hold',sql(intact))
 text=action_sql(reservation,posting,'COMMIT').replace("SET transaction_status='posted'","SET transaction_status='posted',description='unadmitted change'")
 check('COMMIT header OLD NEW preserves unrelated fields',sql(text,'funds_runtime'),False,expected='FND_RECEIPT_INVALID_CANONICAL');check('header mutation rollback retains hold',sql(intact))
 text=action_sql(reservation,posting,'COMMIT').replace("SET transaction_status='posted'","SET transaction_status='posted',booking_date=clock_timestamp()")
 check('COMMIT cannot rewrite original booking date',sql(text,'funds_runtime'),False,expected='FND_RECEIPT_INVALID_CANONICAL');check('booking mutation rollback retains hold',sql(intact))
 text=action_sql(reservation,posting,'COMMIT').replace("SET transaction_status='posted'","SET transaction_status='posted',date_updated=clock_timestamp()")
 check('technical header date update remains a valid COMMIT control',sql(text,'funds_runtime'))
 check('technical date COMMIT can REVERSE normally',sql(action_sql(reservation,posting,'REVERSE'),'funds_runtime'))
 check('actual row witness helper has no runtime execute grant',sql(context('READ')+f"SELECT public.fnd_actual_row('ACCOUNT','{S}');COMMIT;",'funds_runtime'),False,expected='permission denied for function fnd_actual_row')
 operation,posting=uid(),uid();text=reserve_sql(operation,posting).replace(f"SET transaction_id='{posting}' WHERE",f"SET input_fingerprint=repeat('1',64),transaction_id='{posting}' WHERE")
 check('claim OLD NEW preserves fingerprint and metadata',sql(text,'funds_runtime'),False,expected='FND_RECEIPT_INVALID_CANONICAL')
 check('claim mismatch rolls back operation and posting',sql(f"DO $$ BEGIN IF EXISTS(SELECT 1 FROM ledger_funds_operation WHERE operation_id='{operation}') OR EXISTS(SELECT 1 FROM transaction WHERE transaction_id='{posting}') THEN RAISE EXCEPTION 'claim rollback drift';END IF;END $$;"))
 operation,posting=uid(),uid();text=reserve_sql(operation,posting).replace('INSERT INTO ledger_funds_reservation(',f"UPDATE ledger_funds_operation SET fingerprint=repeat('1',64) WHERE operation_id='{operation}';\nINSERT INTO ledger_funds_reservation(")
 check('operation OLD NEW matches admitted original insert before binding',sql(text,'funds_runtime'),False,expected='FND_RECEIPT_INVALID_CANONICAL')
 check('operation mismatch rolls back operation and posting',sql(f"DO $$ BEGIN IF EXISTS(SELECT 1 FROM ledger_funds_operation WHERE operation_id='{operation}') OR EXISTS(SELECT 1 FROM transaction WHERE transaction_id='{posting}') THEN RAISE EXCEPTION 'operation rollback drift';END IF;END $$;"))
