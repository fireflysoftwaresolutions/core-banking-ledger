package io.fireflysoftware.core.banking.ledger.core.services.funds.impl;

import static io.fireflysoftware.core.banking.ledger.core.services.funds.impl.FundsErrors.fail;

import io.fireflysoftware.core.banking.ledger.core.services.funds.FundsReconciliationService;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.*;
import io.fireflysoftware.core.banking.ledger.models.entities.funds.*;
import io.fireflysoftware.core.banking.ledger.models.repositories.funds.FundsRepositories;
import io.r2dbc.postgresql.codec.Json;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import reactor.core.publisher.Mono;

import java.time.*;
import java.util.UUID;

@Service("fundsReconciliationService")
public class FundsReconciliationServiceImpl implements FundsReconciliationService {
    private final FundsRepositories repos;
    private final FundsDatabaseContext context;
    private final Clock clock;

    public FundsReconciliationServiceImpl(
            FundsRepositories repos, FundsDatabaseContext context, ObjectProvider<Clock> clock) {
        this.repos = repos;
        this.context = context;
        this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    @Transactional
    public Mono<Reconciliation> reconcile(SecurityPrincipal p, String tenant, Reserve input) {
        return Mono.defer(
                () -> {
                    FundsMoneyServiceImpl.validateReservation(input);
                    FundsPolicy.require(
                            p,
                            tenant,
                            "funds:reconcile",
                            input.sourceAccountId(),
                            input.destinationAccountId());
                    if (input.notAfterEpochMillis() == null)
                        throw fail(400, "PINNED_RECONCILIATION_DEADLINE_REQUIRED");
                    return context.establish(
                                    p,
                                    tenant,
                                    "RECONCILE",
                                    input.sourceAccountId(),
                                    input.destinationAccountId())
                            .then(
                                    repos.nativeOps()
                                            .operationGate(tenant + ":" + input.operationId()))
                            .then(
                                    Mono.defer(
                                            () -> {
                                                FundsPolicy.require(
                                                        p,
                                                        tenant,
                                                        "funds:reconcile",
                                                        input.sourceAccountId(),
                                                        input.destinationAccountId());
                                                return repos.operations()
                                                        .findById(
                                                                new FundsOperationId(
                                                                        tenant,
                                                                        input.operationId()))
                                                        .flatMap(row -> observe(tenant, input, row))
                                                        .switchIfEmpty(
                                                                Mono.defer(
                                                                        () ->
                                                                                validateAccounts(
                                                                                                tenant,
                                                                                                input)
                                                                                        .then(
                                                                                                close(
                                                                                                        p,
                                                                                                        tenant,
                                                                                                        input))));
                                            }));
                });
    }

    private Mono<Void> validateAccounts(String tenant, Reserve input) {
        return repos.accounts()
                .findByTenantIdAndAccountId(tenant, input.sourceAccountId())
                .switchIfEmpty(Mono.error(fail(404, "FUNDS_ACCOUNT_NOT_FOUND")))
                .flatMap(
                        source ->
                                repos.accounts()
                                        .findByTenantIdAndAccountId(
                                                tenant, input.destinationAccountId())
                                        .switchIfEmpty(
                                                Mono.error(fail(404, "FUNDS_ACCOUNT_NOT_FOUND")))
                                        .map(
                                                destination -> {
                                                    for (var a :
                                                            java.util.List.of(
                                                                    source, destination)) {
                                                        if (!"CUSTOMER".equals(a.getKind())
                                                                || !input.currency()
                                                                        .equals(a.getCurrency()))
                                                            throw fail(
                                                                    409,
                                                                    "CUSTOMER_INTERNAL_TRANSFER_REQUIRED");
                                                        if (input.amount()
                                                                        .stripTrailingZeros()
                                                                        .scale()
                                                                > a.getCurrencyScale())
                                                            throw fail(
                                                                    400,
                                                                    "CURRENCY_PRECISION_EXCEEDED");
                                                    }
                                                    return true;
                                                }))
                .then();
    }

    private Mono<Reconciliation> observe(String tenant, Reserve input, FundsOperation row) {
        if (!"RESERVE".equals(row.getKind())
                || !FundsMoneyServiceImpl.reservationFingerprint(input)
                        .equals(row.getFingerprint()))
            return Mono.error(fail(409, "FUNDS_OPERATION_INTENT_CONFLICT"));
        if (Boolean.TRUE.equals(row.getClosedWithoutEffect()))
            return Mono.just(
                    new Reconciliation(
                            ReconciliationOutcome.ABSENT_CLOSED,
                            row.getClosureReceiptId(),
                            row.getClosureObservedAt().toInstant(),
                            input.notAfterEpochMillis(),
                            input,
                            null));
        if (row.getReservationId() == null)
            return Mono.error(fail(503, "INCOMPLETE_FUNDS_OPERATION"));
        return repos.reservations()
                .findByTenantIdAndReservationId(tenant, row.getReservationId())
                .switchIfEmpty(Mono.error(fail(404, "FUNDS_RESERVATION_NOT_FOUND")))
                .map(
                        r ->
                                new Reconciliation(
                                        ReconciliationOutcome.FOUND,
                                        null,
                                        clock.instant(),
                                        input.notAfterEpochMillis(),
                                        input,
                                        FundsRows.reservation(r)));
    }

    private Mono<Reconciliation> close(SecurityPrincipal p, String tenant, Reserve input) {
        Instant observed = clock.instant();
        if (observed.isBefore(Instant.ofEpochMilli(input.notAfterEpochMillis())))
            return Mono.error(fail(409, "RECONCILIATION_DEADLINE_NOT_DUE"));
        UUID receipt = UUID.randomUUID();
        String intent = FundsMoneyServiceImpl.reservationIntentJson(input);
        var row =
                FundsOperation.builder()
                        .id(new FundsOperationId(tenant, input.operationId()))
                        .kind("RESERVE")
                        .fingerprint(FundsMoneyServiceImpl.reservationFingerprint(input))
                        .sourceAccountId(input.sourceAccountId())
                        .destinationAccountId(input.destinationAccountId())
                        .closedWithoutEffect(true)
                        .closureReceiptId(receipt)
                        .closureObservedAt(OffsetDateTime.ofInstant(observed, ZoneOffset.UTC))
                        .notAfterEpochMillis(input.notAfterEpochMillis())
                        .canonicalIntent(Json.of(intent))
                        .closedPostingKey(
                                FundsMoneyServiceImpl.postingKey(
                                        tenant, input.operationId(), "RESERVE"))
                        .build();
        UUID event = UUID.randomUUID();
        Json payload =
                Json.of(
                        "{\"receiptId\":\""
                                + receipt
                                + "\",\"observedAt\":\""
                                + observed
                                + "\",\"intent\":"
                                + intent
                                + "}");
        return repos.writes()
                .insertOperation(row)
                .onErrorMap(
                        DuplicateKeyException.class,
                        error -> fail(409, "FUNDS_OPERATION_SCOPE_CONFLICT"))
                .then(
                        repos.writes()
                                .insertEvent(
                                        FundsEvent.builder()
                                                .eventId(event)
                                                .tenantId(tenant)
                                                .resourceId(input.operationId())
                                                .actor(p.subject())
                                                .eventType("FUNDS_ABSENT_CLOSED")
                                                .payload(payload)
                                                .build())
                                .then(
                                        repos.writes()
                                                .insertOutbox(
                                                        FundsOutbox.builder()
                                                                .eventId(event)
                                                                .aggregateType("MANAGED_FUNDS")
                                                                .aggregateId(
                                                                        input.operationId()
                                                                                .toString())
                                                                .eventType("FUNDS_ABSENT_CLOSED")
                                                                .payload(payload)
                                                                .fundsTenantId(tenant)
                                                                .build()))
                                .onErrorMap(
                                        DataAccessException.class,
                                        error ->
                                                fail(
                                                        503,
                                                        "FUNDS_RECONCILIATION_AUDIT_UNAVAILABLE")))
                .thenReturn(
                        new Reconciliation(
                                ReconciliationOutcome.ABSENT_CLOSED,
                                receipt,
                                observed,
                                input.notAfterEpochMillis(),
                                input,
                                null));
    }
}
