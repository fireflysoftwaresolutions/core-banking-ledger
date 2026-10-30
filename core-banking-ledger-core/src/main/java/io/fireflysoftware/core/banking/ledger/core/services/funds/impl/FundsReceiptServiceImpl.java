package io.fireflysoftware.core.banking.ledger.core.services.funds.impl;

import static io.fireflysoftware.core.banking.ledger.core.services.funds.impl.FundsErrors.fail;
import static io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsReceiptContracts.*;

import io.fireflysoftware.core.banking.ledger.core.services.funds.FundsMoneyService;
import io.fireflysoftware.core.banking.ledger.interfaces.protocol.receipt.FundsReceiptCodec;
import io.fireflysoftware.core.banking.ledger.core.services.funds.FundsReceiptService;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.*;
import io.fireflysoftware.core.banking.ledger.models.repositories.funds.FundsRepositories;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.util.*;

@Service("fundsReceiptService")
public class FundsReceiptServiceImpl implements FundsReceiptService {
    private final FundsRepositories repos;
    private final FundsMoneyService money;
    private final FundsDatabaseContext context;

    public FundsReceiptServiceImpl(
            FundsRepositories repos, FundsMoneyService money, FundsDatabaseContext context) {
        this.repos = repos;
        this.money = money;
        this.context = context;
    }

    @Transactional(readOnly = true)
    public Mono<EvidenceView> read(SecurityPrincipal p, String tenant, UUID id) {
        return money.reservation(p, tenant, id)
                .flatMap(
                        observed ->
                                context.establish(
                                                p,
                                                tenant,
                                                "READ",
                                                observed.sourceAccountId(),
                                                observed.destinationAccountId())
                                        .then(
                                                repos.nativeOps()
                                                        .operationGate(
                                                                tenant
                                                                        + ":"
                                                                        + observed.operationId()))
                                        .then(
                                                repos.reservations()
                                                        .findByTenantIdAndReservationId(tenant, id))
                                        .switchIfEmpty(
                                                Mono.error(
                                                        fail(404, "FUNDS_RESERVATION_NOT_FOUND")))
                                        .flatMap(
                                                row -> {
                                                    Reservation current =
                                                            FundsRows.reservation(row);
                                                    if (row.getReceiptContractVersion() == null
                                                            || row.getReceiptContractVersion() < 0
                                                            || row.getReceiptContractVersion() > 1)
                                                        return Mono.error(
                                                                fail(
                                                                        503,
                                                                        "FND_INVALID_RECEIPT_VERSION"));
                                                    return live(tenant, current)
                                                            .then(
                                                                    row.getReceiptContractVersion()
                                                                                    == 0
                                                                            ? Mono.just(
                                                                                    new EvidenceView(
                                                                                            EvidenceStatus
                                                                                                    .HISTORICAL_UNAVAILABLE,
                                                                                            null,
                                                                                            null,
                                                                                            null,
                                                                                            current))
                                                                            : evidence(
                                                                                    tenant,
                                                                                    current))
                                                            .flatMap(
                                                                    view ->
                                                                            live(tenant, current)
                                                                                    .then(
                                                                                            Mono
                                                                                                    .fromSupplier(
                                                                                                            () -> {
                                                                                                                FundsPolicy
                                                                                                                        .require(
                                                                                                                                p,
                                                                                                                                tenant,
                                                                                                                                "funds:read",
                                                                                                                                current
                                                                                                                                        .sourceAccountId(),
                                                                                                                                current
                                                                                                                                        .destinationAccountId());
                                                                                                                return view;
                                                                                                            })));
                                                }))
                .onErrorMap(
                        error ->
                                error instanceof ResponseStatusException
                                        ? error
                                        : fail(503, "FND_RETAINED_EVIDENCE_UNAVAILABLE"));
    }

    private Mono<Void> live(String tenant, Reservation row) {
        return repos.nativeOps()
                .assertLive(tenant, "READ", row.sourceAccountId(), row.destinationAccountId());
    }

    private Mono<EvidenceView> evidence(String tenant, Reservation current) {
        return repos.originals()
                .findByTenantIdAndReservationId(tenant, current.reservationId())
                .switchIfEmpty(Mono.error(fail(503, "FND_ORIGINAL_EVIDENCE_MISSING")))
                .map(
                        row -> {
                            var v = FundsReceiptCodec.decodeAccepted(row.getAcceptedCanonical());
                            var e = FundsReceiptCodec.envelope(v);
                            if (!e.canonical().digest().equals(row.getAcceptedDigest())
                                    || !v.tenant().equals(tenant)
                                    || !v.reservationId().equals(current.reservationId())
                                    || !v.intent().operationId().equals(current.operationId())
                                    || !v.intent()
                                            .sourceAccountId()
                                            .equals(current.sourceAccountId())
                                    || !v.intent()
                                            .destinationAccountId()
                                            .equals(current.destinationAccountId())
                                    || v.intent().amount().compareTo(current.amount()) != 0
                                    || !v.intent().currency().equals(current.currency()))
                                throw fail(503, "FND_ORIGINAL_EVIDENCE_MISMATCH");
                            return e;
                        })
                .flatMap(
                        original ->
                                repos.receipts()
                                        .findTop3ByTenantIdAndReservationIdOrderByEffectKindAsc(
                                                tenant, current.reservationId())
                                        .collectList()
                                        .map(
                                                rows -> {
                                                    var effects =
                                                            new EnumMap<
                                                                    EffectKind,
                                                                    MonetaryReceiptEnvelope>(
                                                                    EffectKind.class);
                                                    for (var row : rows) {
                                                        var e =
                                                                FundsReceiptCodec.envelope(
                                                                        FundsReceiptCodec
                                                                                .decodeReceipt(
                                                                                        row
                                                                                                .getReceiptCanonical()));
                                                        if (!e.canonical()
                                                                        .digest()
                                                                        .equals(
                                                                                row
                                                                                        .getReceiptDigest())
                                                                || !FundsReceiptCodec
                                                                        .canonicalAccepted(
                                                                                e.value()
                                                                                        .acceptedReserve())
                                                                        .equals(
                                                                                original
                                                                                        .canonical())
                                                                || effects.put(
                                                                                e.value()
                                                                                        .effectKind(),
                                                                                e)
                                                                        != null)
                                                            throw fail(
                                                                    503,
                                                                    "FND_EFFECT_EVIDENCE_MISMATCH");
                                                    }
                                                    var commit = effects.get(EffectKind.COMMIT);
                                                    var reverse = effects.get(EffectKind.REVERSE);
                                                    if (reverse != null) {
                                                        if (commit == null)
                                                            throw fail(
                                                                    503,
                                                                    "FND_ORIGINAL_COMMIT_MISSING");
                                                        FundsReceiptCodec.validateReversal(
                                                                reverse.value(), commit.value());
                                                    }
                                                    if ((current.state()
                                                                            == ReservationState
                                                                                    .COMMITTED
                                                                    && (commit == null
                                                                            || reverse != null))
                                                            || (current.state()
                                                                            == ReservationState
                                                                                    .REVERSED
                                                                    && (commit == null
                                                                            || reverse == null))
                                                            || ((current.state()
                                                                                    == ReservationState
                                                                                            .HELD
                                                                            || current.state()
                                                                                    == ReservationState
                                                                                            .RELEASED
                                                                            || current.state()
                                                                                    == ReservationState
                                                                                            .EXPIRED)
                                                                    && !effects.isEmpty()))
                                                        throw fail(
                                                                503, "FND_EFFECT_STATE_MISMATCH");
                                                    return new EvidenceView(
                                                            EvidenceStatus.RETAINED,
                                                            original,
                                                            commit,
                                                            reverse,
                                                            current);
                                                }));
    }
}
