package io.fireflysoftware.core.banking.ledger.core.services.funds.impl;

import static io.fireflysoftware.core.banking.ledger.core.services.funds.impl.FundsErrors.fail;

import io.fireflysoftware.core.banking.ledger.core.services.core.LedgerPostingService;
import io.fireflysoftware.core.banking.ledger.core.services.funds.FundsMoneyService;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.core.*;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.*;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.core.TransactionTypeEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.funds.*;
import io.fireflysoftware.core.banking.ledger.models.repositories.funds.FundsRepositories;
import io.r2dbc.postgresql.codec.Json;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service("fundsMoneyService")
public class FundsMoneyServiceImpl implements FundsMoneyService {
    private final FundsRepositories repos;
    private final FundsDatabaseContext context;
    private final LedgerPostingService postings;

    public FundsMoneyServiceImpl(
            FundsRepositories repos, FundsDatabaseContext context, LedgerPostingService postings) {
        this.repos = repos;
        this.context = context;
        this.postings = postings;
    }

    @Transactional
    public Mono<FundingReceipt> fund(SecurityPrincipal p, String tenant, Funding input) {
        return Mono.defer(
                () -> {
                    if (input == null) throw fail(400, "FUNDING_INPUT_REQUIRED");
                    FundsPolicy.require(
                            p,
                            tenant,
                            "funds:fund",
                            input.bankControlAccountId(),
                            input.customerAccountId());
                    validate(
                            input.operationId(),
                            input.bankControlAccountId(),
                            input.customerAccountId(),
                            input.amount(),
                            input.currency());
                    String fp =
                            hash(
                                    "FUNDING",
                                    input.bankControlAccountId(),
                                    input.customerAccountId(),
                                    canonical(input.amount()),
                                    input.currency());
                    return context.establish(
                                    p,
                                    tenant,
                                    "FUNDING",
                                    input.bankControlAccountId(),
                                    input.customerAccountId())
                            .then(operationGate(tenant, input.operationId()))
                            .then(
                                    lockAccounts(
                                            tenant,
                                            input.bankControlAccountId(),
                                            input.customerAccountId()))
                            .flatMap(
                                    accounts ->
                                            claim(
                                                            tenant,
                                                            input.operationId(),
                                                            "FUNDING",
                                                            fp,
                                                            input.bankControlAccountId(),
                                                            input.customerAccountId())
                                                    .flatMap(
                                                            operation -> {
                                                                FundsPolicy.require(
                                                                        p,
                                                                        tenant,
                                                                        "funds:fund",
                                                                        input
                                                                                .bankControlAccountId(),
                                                                        input.customerAccountId());
                                                                if (operation.getPostingId()
                                                                        != null)
                                                                    return Mono.just(
                                                                            new FundingReceipt(
                                                                                    input
                                                                                            .operationId(),
                                                                                    operation
                                                                                            .getPostingId()));
                                                                Account
                                                                        source =
                                                                                accounts.get(
                                                                                        input
                                                                                                .bankControlAccountId()),
                                                                        destination =
                                                                                accounts.get(
                                                                                        input
                                                                                                .customerAccountId());
                                                                active(source);
                                                                active(destination);
                                                                if (source.kind()
                                                                                != Kind.BANK_CONTROL
                                                                        || destination.kind()
                                                                                != Kind.CUSTOMER)
                                                                    throw fail(
                                                                            409,
                                                                            "EXPLICIT_BANK_CONTROL_FUNDING_REQUIRED");
                                                                currency(
                                                                        input.currency(),
                                                                        source,
                                                                        destination);
                                                                amount(input.amount(), source);
                                                                amount(input.amount(), destination);
                                                                BigDecimal
                                                                        debit =
                                                                                source.booked()
                                                                                        .subtract(
                                                                                                input
                                                                                                        .amount()),
                                                                        credit =
                                                                                destination
                                                                                        .booked()
                                                                                        .add(
                                                                                                input
                                                                                                        .amount());
                                                                numeric(debit);
                                                                numeric(credit);
                                                                return createPosting(
                                                                                tenant,
                                                                                input.operationId(),
                                                                                source.accountId(),
                                                                                destination
                                                                                        .accountId(),
                                                                                input.amount(),
                                                                                input.currency(),
                                                                                false)
                                                                        .flatMap(
                                                                                posting ->
                                                                                        update(
                                                                                                        tenant,
                                                                                                        source,
                                                                                                        debit,
                                                                                                        source
                                                                                                                .held())
                                                                                                .then(
                                                                                                        update(
                                                                                                                tenant,
                                                                                                                destination,
                                                                                                                credit,
                                                                                                                destination
                                                                                                                        .held()))
                                                                                                .then(
                                                                                                        completeOperation(
                                                                                                                tenant,
                                                                                                                input
                                                                                                                        .operationId(),
                                                                                                                null,
                                                                                                                posting))
                                                                                                .then(
                                                                                                        event(
                                                                                                                p,
                                                                                                                tenant,
                                                                                                                input
                                                                                                                        .operationId(),
                                                                                                                "BANK_CONTROL_FUNDING",
                                                                                                                posting))
                                                                                                .thenReturn(
                                                                                                        new FundingReceipt(
                                                                                                                input
                                                                                                                        .operationId(),
                                                                                                                posting)));
                                                            }));
                });
    }

    @Transactional
    public Mono<Reservation> reserve(SecurityPrincipal p, String tenant, Reserve input) {
        return Mono.defer(
                () -> {
                    if (input == null) throw fail(400, "RESERVATION_INPUT_REQUIRED");
                    FundsPolicy.require(
                            p,
                            tenant,
                            "funds:reserve",
                            input.sourceAccountId(),
                            input.destinationAccountId());
                    validateReservation(input);
                    return context.establish(
                                    p,
                                    tenant,
                                    "RESERVE",
                                    input.sourceAccountId(),
                                    input.destinationAccountId())
                            .then(operationGate(tenant, input.operationId()))
                            .then(
                                    lockAccounts(
                                            tenant,
                                            input.sourceAccountId(),
                                            input.destinationAccountId()))
                            .flatMap(
                                    accounts ->
                                            claim(
                                                            tenant,
                                                            input.operationId(),
                                                            "RESERVE",
                                                            reservationFingerprint(input),
                                                            input.sourceAccountId(),
                                                            input.destinationAccountId())
                                                    .flatMap(
                                                            operation -> {
                                                                FundsPolicy.require(
                                                                        p,
                                                                        tenant,
                                                                        "funds:reserve",
                                                                        input.sourceAccountId(),
                                                                        input
                                                                                .destinationAccountId());
                                                                if (operation.getReservationId()
                                                                        != null)
                                                                    return read(
                                                                            tenant,
                                                                            operation
                                                                                    .getReservationId(),
                                                                            false);
                                                                Account
                                                                        source =
                                                                                accounts.get(
                                                                                        input
                                                                                                .sourceAccountId()),
                                                                        destination =
                                                                                accounts.get(
                                                                                        input
                                                                                                .destinationAccountId());
                                                                active(source);
                                                                active(destination);
                                                                amount(input.amount(), source);
                                                                amount(input.amount(), destination);
                                                                if (source.kind() != Kind.CUSTOMER
                                                                        || destination.kind()
                                                                                != Kind.CUSTOMER)
                                                                    throw fail(
                                                                            409,
                                                                            "CUSTOMER_INTERNAL_TRANSFER_REQUIRED");
                                                                currency(
                                                                        input.currency(),
                                                                        source,
                                                                        destination);
                                                                if (source.available()
                                                                                .compareTo(
                                                                                        input
                                                                                                .amount())
                                                                        < 0)
                                                                    throw fail(
                                                                            409,
                                                                            "INSUFFICIENT_AVAILABLE_FUNDS");
                                                                numeric(
                                                                        source.held()
                                                                                .add(
                                                                                        input
                                                                                                .amount()));
                                                                return repos.nativeOps()
                                                                        .beginReserve(
                                                                                tenant,
                                                                                input.operationId(),
                                                                                input
                                                                                        .sourceAccountId(),
                                                                                input
                                                                                        .destinationAccountId(),
                                                                                input.amount(),
                                                                                input.currency(),
                                                                                input.ttlSeconds(),
                                                                                input
                                                                                        .notAfterEpochMillis())
                                                                                                                    .onErrorMap(FundsErrors::nativeBusinessConflict)
                                                                        .flatMap(
                                                                                admission ->
                                                                                        createPosting(
                                                                                                        tenant,
                                                                                                        input
                                                                                                                .operationId(),
                                                                                                        input
                                                                                                                .sourceAccountId(),
                                                                                                        input
                                                                                                                .destinationAccountId(),
                                                                                                        input
                                                                                                                .amount(),
                                                                                                        input
                                                                                                                .currency(),
                                                                                                        true)
                                                                                                .flatMap(
                                                                                                        posting ->
                                                                                                                repos.writes()
                                                                                                                        .insertReservation(
                                                                                                                                FundsReservation
                                                                                                                                        .builder()
                                                                                                                                        .reservationId(
                                                                                                                                                admission
                                                                                                                                                        .reservationId())
                                                                                                                                        .tenantId(
                                                                                                                                                tenant)
                                                                                                                                        .operationId(
                                                                                                                                                input
                                                                                                                                                        .operationId())
                                                                                                                                        .sourceAccountId(
                                                                                                                                                input
                                                                                                                                                        .sourceAccountId())
                                                                                                                                        .destinationAccountId(
                                                                                                                                                input
                                                                                                                                                        .destinationAccountId())
                                                                                                                                        .amount(
                                                                                                                                                input
                                                                                                                                                        .amount())
                                                                                                                                        .currency(
                                                                                                                                                input
                                                                                                                                                        .currency())
                                                                                                                                        .state(
                                                                                                                                                "HELD")
                                                                                                                                        .expiresAt(
                                                                                                                                                admission
                                                                                                                                                        .expiresAt())
                                                                                                                                        .postingId(
                                                                                                                                                posting)
                                                                                                                                        .version(
                                                                                                                                                0L)
                                                                                                                                        .build())
                                                                                                                        .then(
                                                                                                                                update(
                                                                                                                                        tenant,
                                                                                                                                        source,
                                                                                                                                        source
                                                                                                                                                .booked(),
                                                                                                                                        source.held()
                                                                                                                                                .add(
                                                                                                                                                        input
                                                                                                                                                                .amount())))
                                                                                                                        .then(
                                                                                                                                completeOperation(
                                                                                                                                        tenant,
                                                                                                                                        input
                                                                                                                                                .operationId(),
                                                                                                                                        admission
                                                                                                                                                .reservationId(),
                                                                                                                                        null))
                                                                                                                        .then(
                                                                                                                                repos.nativeOps()
                                                                                                                                        .finish(
                                                                                                                                                admission
                                                                                                                                                        .commandId()))
                                                                                                                        .then(
                                                                                                                                read(
                                                                                                                                        tenant,
                                                                                                                                        admission
                                                                                                                                                .reservationId(),
                                                                                                                                        false))));
                                                            }));
                });
    }

    @Transactional(readOnly = true)
    public Mono<Reservation> reservation(SecurityPrincipal p, String tenant, UUID id) {
        return Mono.defer(
                () -> {
                    FundsPolicy.require(p, tenant, "funds:read");
                    return context.establish(p, tenant, "READ", FundsPolicy.grantedAccounts(p))
                            .then(read(tenant, id, false))
                            .map(
                                    row -> {
                                        FundsPolicy.require(
                                                p,
                                                tenant,
                                                "funds:read",
                                                row.sourceAccountId(),
                                                row.destinationAccountId());
                                        return row;
                                    });
                });
    }

    @Transactional(readOnly = true)
    public Mono<Reservation> reservationByOperation(
            SecurityPrincipal p, String tenant, UUID operation) {
        return Mono.defer(
                () -> {
                    FundsPolicy.require(p, tenant, "funds:read");
                    return context.establish(p, tenant, "READ", FundsPolicy.grantedAccounts(p))
                            .then(operationGate(tenant, operation))
                            .then(
                                    repos.operations()
                                            .findById(new FundsOperationId(tenant, operation)))
                            .filter(
                                    row ->
                                            "RESERVE".equals(row.getKind())
                                                    && row.getReservationId() != null)
                            .switchIfEmpty(Mono.error(fail(404, "FUNDS_RESERVATION_NOT_FOUND")))
                            .flatMap(row -> read(tenant, row.getReservationId(), false))
                            .map(
                                    row -> {
                                        FundsPolicy.require(
                                                p,
                                                tenant,
                                                "funds:read",
                                                row.sourceAccountId(),
                                                row.destinationAccountId());
                                        return row;
                                    });
                });
    }

    @Transactional
    public Mono<Reservation> action(SecurityPrincipal p, String tenant, UUID id, Command input) {
        return Mono.defer(
                () -> {
                    if (input == null || input.actionId() == null || input.action() == null)
                        throw fail(400, "FUNDS_ACTION_REQUIRED");
                    String purpose = input.action().name(),
                            scope = "funds:" + purpose.toLowerCase(Locale.ROOT);
                    FundsPolicy.require(p, tenant, scope);
                    return context.establish(p, tenant, purpose, FundsPolicy.grantedAccounts(p))
                            .then(read(tenant, id, false))
                            .flatMap(
                                    observed -> {
                                        FundsPolicy.require(
                                                p,
                                                tenant,
                                                scope,
                                                observed.sourceAccountId(),
                                                observed.destinationAccountId());
                                        return context.establish(
                                                        p,
                                                        tenant,
                                                        purpose,
                                                        observed.sourceAccountId(),
                                                        observed.destinationAccountId())
                                                .then(operationGate(tenant, observed.operationId()))
                                                .then(
                                                        lockAccounts(
                                                                tenant,
                                                                observed.sourceAccountId(),
                                                                observed.destinationAccountId()))
                                                .flatMap(
                                                        accounts ->
                                                                read(tenant, id, true)
                                                                        .flatMap(
                                                                                current ->
                                                                                        actionClaim(
                                                                                                        tenant,
                                                                                                        id,
                                                                                                        input)
                                                                                                .flatMap(
                                                                                                        replay -> {
                                                                                                            FundsPolicy
                                                                                                                    .require(
                                                                                                                            p,
                                                                                                                            tenant,
                                                                                                                            scope,
                                                                                                                            current
                                                                                                                                    .sourceAccountId(),
                                                                                                                            current
                                                                                                                                    .destinationAccountId());
                                                                                                            if (replay
                                                                                                                    || already(
                                                                                                                            current,
                                                                                                                            input
                                                                                                                                    .action()))
                                                                                                                return Mono
                                                                                                                        .just(
                                                                                                                                current);
                                                                                                            return repos.nativeOps()
                                                                                                                    .beginTransition(
                                                                                                                            tenant,
                                                                                                                            id,
                                                                                                                            input
                                                                                                                                    .actionId(),
                                                                                                                            purpose)
                                                                                                                    .onErrorMap(FundsErrors::nativeBusinessConflict)
                                                                                                                    .flatMap(
                                                                                                                            admission ->
                                                                                                                                    transition(
                                                                                                                                                    tenant,
                                                                                                                                                    current,
                                                                                                                                                    input
                                                                                                                                                            .action(),
                                                                                                                                                    accounts
                                                                                                                                                            .get(
                                                                                                                                                                    current
                                                                                                                                                                            .sourceAccountId()),
                                                                                                                                                    accounts
                                                                                                                                                            .get(
                                                                                                                                                                    current
                                                                                                                                                                            .destinationAccountId()),
                                                                                                                                                    admission)
                                                                                                                                            .flatMap(
                                                                                                                                                    result ->
                                                                                                                                                            repos.nativeOps()
                                                                                                                                                                    .finish(
                                                                                                                                                                            admission
                                                                                                                                                                                    .commandId())
                                                                                                                                                                    .thenReturn(
                                                                                                                                                                            result)));
                                                                                                        })));
                                    });
                });
    }

    private Mono<Reservation> transition(
            String tenant,
            Reservation current,
            Action action,
            Account source,
            Account destination,
            FundsAdmission admission) {
        if (action == Action.REVERSE) {
            if (current.state() != ReservationState.COMMITTED)
                throw fail(409, "ONLY_COMMITTED_FUNDS_CAN_REVERSE");
            active(source);
            active(destination);
            if (destination.available().compareTo(current.amount()) < 0)
                throw fail(409, "REVERSAL_REQUIRES_AVAILABLE_RECIPIENT_FUNDS");
            BigDecimal debit = source.booked().add(current.amount()),
                    credit = destination.booked().subtract(current.amount());
            numeric(debit);
            numeric(credit);
            return postings.reverse(
                            current.postingId(),
                            postingKey(tenant, current.operationId(), "REVERSE"))
                    .flatMap(
                            posting ->
                                    update(tenant, source, debit, source.held())
                                            .then(
                                                    update(
                                                            tenant,
                                                            destination,
                                                            credit,
                                                            destination.held()))
                                            .then(
                                                    changed(
                                                            repos.writes()
                                                                    .updateReservation(
                                                                            tenant,
                                                                            current.reservationId(),
                                                                            current.version(),
                                                                            current.state().name(),
                                                                            "REVERSED",
                                                                            posting.getTransaction()
                                                                                    .getTransactionId())))
                                            .then(read(tenant, current.reservationId(), false)));
        }
        if (current.state() != ReservationState.HELD) throw fail(409, "RESERVATION_IS_TERMINAL");
        ReservationState target;
        if (action == Action.COMMIT) {
            if (!admission.recordedAt().isBefore(current.expiresAt()))
                throw fail(409, "RESERVATION_EXPIRED");
            active(source);
            active(destination);
            target = ReservationState.COMMITTED;
        } else if (action == Action.EXPIRE) {
            if (admission.recordedAt().isBefore(current.expiresAt()))
                throw fail(409, "RESERVATION_NOT_DUE");
            target = ReservationState.EXPIRED;
        } else target = ReservationState.RELEASED;
        BigDecimal held = source.held().subtract(current.amount());
        if (held.signum() < 0) throw fail(409, "FUNDS_HELD_INVARIANT_VIOLATION");
        BigDecimal debit = source.booked(), credit = destination.booked();
        if (target == ReservationState.COMMITTED) {
            debit = debit.subtract(current.amount());
            credit = credit.add(current.amount());
        }
        numeric(debit);
        numeric(credit);
        BigDecimal sourceNext = debit, destinationNext = credit;
        Mono<?> effect =
                target == ReservationState.COMMITTED
                        ? postings.confirm(current.postingId())
                        : postings.release(current.postingId());
        return effect.then(update(tenant, source, sourceNext, held))
                .then(
                        target == ReservationState.COMMITTED
                                ? update(tenant, destination, destinationNext, destination.held())
                                : Mono.empty())
                .then(
                        changed(
                                repos.writes()
                                        .updateReservation(
                                                tenant,
                                                current.reservationId(),
                                                current.version(),
                                                current.state().name(),
                                                target.name(),
                                                null)))
                .then(read(tenant, current.reservationId(), false));
    }

    private boolean already(Reservation row, Action action) {
        return switch (action) {
            case COMMIT -> row.state() == ReservationState.COMMITTED;
            case RELEASE ->
                    row.state() == ReservationState.RELEASED
                            || row.state() == ReservationState.EXPIRED;
            case EXPIRE -> row.state() == ReservationState.EXPIRED;
            case REVERSE -> row.state() == ReservationState.REVERSED;
        };
    }

    private Mono<Boolean> actionClaim(String tenant, UUID id, Command input) {
        var key = new FundsActionId(tenant, input.actionId());
        return repos.actions()
                .findById(key)
                .map(
                        row -> {
                            if (!id.equals(row.getReservationId())
                                    || !input.action().name().equals(row.getAction()))
                                throw fail(409, "FUNDS_ACTION_INTENT_CONFLICT");
                            return true;
                        })
                .switchIfEmpty(
                        Mono.defer(
                                () ->
                                        repos.writes()
                                                .insertAction(
                                                        FundsAction.builder()
                                                                .id(key)
                                                                .reservationId(id)
                                                                .action(input.action().name())
                                                                .build())
                                                .thenReturn(false)))
                .onErrorMap(
                        DuplicateKeyException.class,
                        error -> fail(409, "FUNDS_ACTION_SCOPE_CONFLICT"));
    }

    private Mono<FundsOperation> claim(
            String tenant, UUID operation, String kind, String fp, UUID source, UUID destination) {
        var id = new FundsOperationId(tenant, operation);
        return repos.operations()
                .findLockedById(id)
                .switchIfEmpty(
                        Mono.defer(
                                () ->
                                        repos.writes()
                                                .insertOperation(
                                                        FundsOperation.builder()
                                                                .id(id)
                                                                .kind(kind)
                                                                .fingerprint(fp)
                                                                .sourceAccountId(source)
                                                                .destinationAccountId(destination)
                                                                .closedWithoutEffect(false)
                                                                .build())))
                .map(
                        row -> {
                            if (!kind.equals(row.getKind()) || !fp.equals(row.getFingerprint()))
                                throw fail(409, "FUNDS_OPERATION_INTENT_CONFLICT");
                            if (Boolean.TRUE.equals(row.getClosedWithoutEffect()))
                                throw fail(409, "FUNDS_OPERATION_CLOSED_WITHOUT_EFFECT");
                            return row;
                        })
                .onErrorMap(
                        DuplicateKeyException.class,
                        error -> fail(409, "FUNDS_OPERATION_SCOPE_CONFLICT"));
    }

    private Mono<Map<UUID, Account>> lockAccounts(String tenant, UUID... ids) {
        var ordered =
                Arrays.stream(ids).distinct().sorted(Comparator.comparing(UUID::toString)).toList();
        return Flux.fromIterable(ordered)
                .concatMap(id -> repos.nativeOps().accountGate(id.toString()))
                .thenMany(
                        Flux.fromIterable(ordered)
                                .concatMap(
                                        id ->
                                                repos.accounts()
                                                        .findLockedByTenantIdAndAccountId(
                                                                tenant, id)
                                                        .switchIfEmpty(
                                                                Mono.error(
                                                                        fail(
                                                                                404,
                                                                                "FUNDS_ACCOUNT_NOT_FOUND")))
                                                        .map(FundsRows::account)))
                .collectMap(Account::accountId);
    }

    Mono<Void> operationGate(String tenant, UUID operation) {
        return repos.nativeOps().operationGate(tenant + ":" + operation);
    }

    Mono<Reservation> read(String tenant, UUID id, boolean locked) {
        return (locked
                        ? repos.reservations().findLockedByTenantIdAndReservationId(tenant, id)
                        : repos.reservations().findByTenantIdAndReservationId(tenant, id))
                .map(FundsRows::reservation)
                .switchIfEmpty(Mono.error(fail(404, "FUNDS_RESERVATION_NOT_FOUND")));
    }

    private Mono<Void> completeOperation(
            String tenant, UUID operation, UUID reservation, UUID posting) {
        return changed(
                repos.writes()
                        .completeOperation(
                                new FundsOperationId(tenant, operation), reservation, posting));
    }

    private Mono<Void> update(String tenant, Account account, BigDecimal booked, BigDecimal held) {
        return changed(
                repos.writes()
                        .updateMoney(
                                tenant,
                                account.accountId(),
                                account.version(),
                                account.state().name(),
                                booked,
                                held));
    }

    private Mono<Void> changed(Mono<Long> result) {
        return result.switchIfEmpty(Mono.error(fail(409, "FUNDS_NATIVE_WRITE_CONFLICT")))
                .flatMap(
                        count ->
                                count == 1
                                        ? Mono.empty()
                                        : Mono.error(fail(409, "FUNDS_NATIVE_WRITE_CONFLICT")));
    }

    private Mono<UUID> createPosting(
            String tenant,
            UUID operation,
            UUID debit,
            UUID credit,
            BigDecimal amount,
            String currency,
            boolean pending) {
        var header = new LedgerPostingHeaderDTO();
        header.setAccountId(debit);
        header.setCurrency(currency);
        header.setTotalAmount(amount);
        header.setTransactionType(TransactionTypeEnum.TRANSFER);
        header.setTransactionStatus(
                pending
                        ? LedgerPostingHeaderDTO.InitialStatus.PENDING
                        : LedgerPostingHeaderDTO.InitialStatus.POSTED);
        var request = new LedgerPostingRequestDTO();
        request.setIdempotencyKey(postingKey(tenant, operation, pending ? "RESERVE" : "FUNDING"));
        request.setTransaction(header);
        var legs = new ArrayList<LedgerPostingLegDTO>();
        for (var entry : List.of(Map.entry(debit, "DEBIT"), Map.entry(credit, "CREDIT"))) {
            var leg = new LedgerPostingLegDTO();
            leg.setAccountId(entry.getKey());
            leg.setLegType(entry.getValue());
            leg.setAmount(amount);
            leg.setCurrency(currency);
            legs.add(leg);
        }
        request.setLegs(legs);
        return postings.create(request).map(row -> row.getTransaction().getTransactionId());
    }

    private Mono<Void> event(
            SecurityPrincipal p, String tenant, UUID resource, String type, UUID posting) {
        UUID id = UUID.randomUUID();
        Json payload =
                Json.of("{\"resourceId\":\"" + resource + "\",\"postingId\":\"" + posting + "\"}");
        return repos.writes()
                .insertEvent(
                        FundsEvent.builder()
                                .eventId(id)
                                .tenantId(tenant)
                                .resourceId(resource)
                                .actor(p.subject())
                                .eventType(type)
                                .payload(payload)
                                .build())
                .then(
                        repos.writes()
                                .insertOutbox(
                                        FundsOutbox.builder()
                                                .eventId(id)
                                                .aggregateType("MANAGED_FUNDS")
                                                .aggregateId(resource.toString())
                                                .eventType(type)
                                                .payload(payload)
                                                .fundsTenantId(tenant)
                                                .build()))
                .then();
    }

    private static void active(Account account) {
        if (account.state() != AccountState.ACTIVE) throw fail(409, "FUNDS_ACCOUNT_BLOCKED");
    }

    private static void currency(String expected, Account s, Account d) {
        if (!expected.equals(s.currency()) || !expected.equals(d.currency()))
            throw fail(409, "FUNDS_CURRENCY_MISMATCH");
    }

    private static void amount(BigDecimal value, Account account) {
        if (value.stripTrailingZeros().scale() > account.currencyScale())
            throw fail(400, "CURRENCY_PRECISION_EXCEEDED");
    }

    static void numeric(BigDecimal value) {
        if (value.signum() != 0 && (long) value.precision() - value.scale() > 20)
            throw fail(400, "FUNDS_NUMERIC_RANGE_EXCEEDED");
        var n = value.stripTrailingZeros();
        if ((long) n.precision() - n.scale() > 20 || n.scale() > 18)
            throw fail(400, "FUNDS_NUMERIC_RANGE_EXCEEDED");
    }

    private static void validate(
            UUID operation, UUID source, UUID destination, BigDecimal amount, String currency) {
        if (operation == null
                || source == null
                || destination == null
                || source.equals(destination)
                || amount == null
                || amount.signum() <= 0
                || currency == null
                || !currency.matches("[A-Z]{3}")) throw fail(400, "INVALID_FUNDS_INTENT");
        numeric(amount);
    }

    static void validateReservation(Reserve input) {
        if (input == null) throw fail(400, "RESERVATION_INPUT_REQUIRED");
        validate(
                input.operationId(),
                input.sourceAccountId(),
                input.destinationAccountId(),
                input.amount(),
                input.currency());
        if (input.ttlSeconds() < 1 || input.ttlSeconds() > 900)
            throw fail(400, "RESERVATION_TTL_OUT_OF_RANGE");
        if (input.notAfterEpochMillis() != null
                && (input.notAfterEpochMillis() <= 0
                        || input.notAfterEpochMillis() > 253402300799999L))
            throw fail(400, "RESERVATION_DEADLINE_OUT_OF_RANGE");
    }

    static String reservationFingerprint(Reserve input) {
        return hash(
                "RESERVE",
                input.sourceAccountId(),
                input.destinationAccountId(),
                canonical(input.amount()),
                input.currency(),
                input.ttlSeconds(),
                input.notAfterEpochMillis());
    }

    static String reservationIntentJson(Reserve i) {
        return "{\"operationId\":\""
                + i.operationId()
                + "\",\"sourceAccountId\":\""
                + i.sourceAccountId()
                + "\",\"destinationAccountId\":\""
                + i.destinationAccountId()
                + "\",\"amount\":"
                + canonical(i.amount())
                + ",\"currency\":\""
                + i.currency()
                + "\",\"ttlSeconds\":"
                + i.ttlSeconds()
                + ",\"notAfterEpochMillis\":"
                + i.notAfterEpochMillis()
                + "}";
    }

    private static String canonical(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    static String postingKey(String tenant, UUID operation, String kind) {
        return "fnd:" + hash(tenant, operation, kind);
    }

    private static String hash(Object... values) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            Arrays.stream(values)
                                                    .map(String::valueOf)
                                                    .collect(
                                                            java.util.stream.Collectors.joining(
                                                                    "|"))
                                                    .getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
