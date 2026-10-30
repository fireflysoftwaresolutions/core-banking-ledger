package io.fireflysoftware.core.banking.ledger.models.repositories.funds;

import io.fireflysoftware.core.banking.ledger.models.entities.funds.*;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.Repository;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

public interface FundsNativeRepository extends Repository<FundsAccount, UUID> {
    @Query(
"""
SELECT NOT r.rolsuper AND NOT r.rolbypassrls
                          AND NOT pg_catalog.has_schema_privilege(current_user,'public','CREATE')
                          AND NOT pg_catalog.has_schema_privilege(current_user,'funds_private','USAGE')
                          AND pg_catalog.pg_has_role(current_user,'firefly_funds_receipt_api','USAGE')
                          AND NOT pg_catalog.pg_has_role(current_user,'firefly_funds_receipt_guard','MEMBER')
                          AND NOT pg_catalog.pg_has_role(current_user,'firefly_funds_policy_reader','MEMBER')
                          AND NOT pg_catalog.pg_has_role(current_user,'firefly_funds_policy_writer','MEMBER')
                          AND EXISTS(SELECT 1 FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace
                            JOIN pg_catalog.pg_roles owner ON owner.oid=p.proowner WHERE n.nspname='public' AND p.proname='fnd_begin_reserve'
                            AND p.prosecdef AND owner.rolname='firefly_funds_receipt_guard'
                            AND NOT owner.rolsuper AND NOT owner.rolbypassrls AND NOT owner.rolcanlogin)
                          AND (SELECT count(*) FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
                            WHERE n.nspname='public' AND c.relname IN('ledger_funds_reserve_intent','ledger_funds_monetary_receipt')
                            AND c.relrowsecurity AND c.relforcerowsecurity)=2
                          AND NOT EXISTS(SELECT 1 FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
                            WHERE n.nspname='public' AND c.relname IN('ledger_funds_reservation','ledger_funds_reserve_intent','ledger_funds_monetary_receipt')
                            AND pg_catalog.has_table_privilege(current_user,c.oid,'TRUNCATE'))
                          AND NOT EXISTS(SELECT 1 FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
                            WHERE n.nspname='public' AND c.relrowsecurity AND pg_catalog.pg_get_userbyid(c.relowner)=current_user)
                          AND EXISTS(SELECT 1 FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace
                            JOIN pg_catalog.pg_roles owner ON owner.oid=p.proowner WHERE n.nspname='public' AND p.proname='funds_reference_allowed'
                            AND p.prosecdef AND NOT owner.rolsuper AND NOT owner.rolbypassrls AND NOT owner.rolcanlogin) AS allowed
                        FROM pg_catalog.pg_roles r WHERE r.rolname=current_user
""")
    Mono<Boolean> runtimeAllowed();

    @Modifying
    @Query(
            "SELECT"
                + " set_config('firefly.funds.tenant',:tenant,true),set_config('firefly.funds.accounts',:accounts,true),set_config('firefly.funds.purpose',:purpose,true),set_config('firefly.funds.actor_issuer',:issuer,true),set_config('firefly.funds.actor_subject',:subject,true),set_config('firefly.funds.token_expires_epoch_millis',:expires,true)")
    Mono<Void> establish(
            String tenant,
            String accounts,
            String purpose,
            String issuer,
            String subject,
            String expires);

    @Modifying
    @Query("SELECT pg_advisory_xact_lock(hashtextextended(:account,55601))")
    Mono<Void> accountGate(String account);

    @Modifying
    @Query("SELECT pg_advisory_xact_lock(hashtextextended(:key,55602))")
    Mono<Void> operationGate(String key);

    @Modifying
    @Query("SELECT pg_advisory_xact_lock(hashtextextended(:key,55603))")
    Mono<Void> postingGate(String key);

    @Query("SELECT public.funds_reference_allowed('POSTING',:id,TRUE)")
    Mono<Boolean> postingAllowed(UUID id);

    @Query(
            "SELECT bool_and(public.funds_reference_allowed('ACCOUNT',a,TRUE)) AND (:managed=FALSE"
                    + " OR current_setting('firefly.funds.purpose',true) IN"
                    + " ('FUNDING','RESERVE','REVERSE')) FROM unnest(CAST(:accounts AS uuid[])) a")
    Mono<Boolean> postingAccountsAllowed(UUID[] accounts, boolean managed);

    @Query(
            "SELECT * FROM"
                + " public.fnd_begin_reserve(:tenant,:operation,:source,:destination,:amount,:currency,:ttl,:deadline)")
    Mono<FundsAdmission> beginReserve(
            String tenant,
            UUID operation,
            UUID source,
            UUID destination,
            BigDecimal amount,
            String currency,
            int ttl,
            Long deadline);

    @Query("SELECT clock_timestamp()")
    Mono<java.time.OffsetDateTime> databaseNow();

    @Query("SELECT * FROM public.fnd_begin_transition(:tenant,:reservation,:action,:purpose)")
    Mono<FundsAdmission> beginTransition(
            String tenant, UUID reservation, UUID action, String purpose);

    @Modifying
    @Query("SELECT public.fnd_finish_command(:command)")
    Mono<Void> finish(UUID command);

    @Modifying
    @Query("SELECT public.fnd_assert_live_context(:tenant,:purpose,:source,:destination)")
    Mono<Void> assertLive(String tenant, String purpose, UUID source, UUID destination);

    /** Requires an existing original anchor; missing authority/anchor/function must fail, never create a replacement. */
    @Query("SELECT * FROM public.fnd_require_audit_capture(:authority,:tenant,:resourceTenant,:action,:resource,:process,:commandKey,:digest,:until)")
    Mono<FundsAuditAnchor> requireAuditCapture(UUID authority, String tenant, UUID resourceTenant,
            String action, UUID resource, UUID process, String commandKey, String digest,
            java.time.OffsetDateTime until);

    /** Native integrity coupling on the original transaction, not ordinary CRUD or an authority grant. */
    @Query("SELECT public.fnd_pair_audit_capture(:nonce,:event,:sequence,:recordHash,:evidenceDigest,:replay)")
    Mono<UUID> pairAuditCapture(UUID nonce, UUID event, long sequence, String recordHash,
            String evidenceDigest, boolean replay);
}
