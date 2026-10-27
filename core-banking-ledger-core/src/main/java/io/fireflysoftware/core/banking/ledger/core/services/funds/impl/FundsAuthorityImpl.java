package io.fireflysoftware.core.banking.ledger.core.services.funds.impl;

import static io.fireflysoftware.core.banking.ledger.core.services.funds.impl.FundsErrors.fail;

import io.fireflysoftware.core.banking.ledger.core.services.funds.AccountEnrollmentVerifier;
import io.fireflysoftware.core.banking.ledger.core.services.funds.FundsAuthority;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.*;
import io.fireflysoftware.core.banking.ledger.models.entities.funds.*;
import io.fireflysoftware.core.banking.ledger.models.repositories.funds.FundsRepositories;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

@Service("fundsAuthority")
public class FundsAuthorityImpl implements FundsAuthority {
    private final FundsRepositories repos;
    private final String currencyPolicy;
    private final AccountEnrollmentVerifier verifier;
    private final FundsDatabaseContext context;

    public FundsAuthorityImpl(
            FundsRepositories repos,
            @Value("${funds.currency-policy.version:}") String currencyPolicy,
            AccountEnrollmentVerifier verifier,
            FundsDatabaseContext context) {
        this.repos = repos;
        this.currencyPolicy = currencyPolicy;
        this.verifier = verifier;
        this.context = context;
    }

    @Transactional
    public Mono<Account> enroll(
            SecurityPrincipal p, String tenant, UUID accountId, Enrollment input) {
        return Mono.defer(
                () -> {
                    FundsPolicy.require(p, tenant, "funds:enroll", accountId);
                    if (input == null
                            || input.enrollmentId() == null
                            || input.contractId() == null
                            || input.currency() == null
                            || !input.currency().matches("[A-Z]{3}")
                            || input.kind() == null
                            || currencyPolicy.isBlank()
                            || currencyPolicy.length() > 100)
                        throw fail(400, "EXPLICIT_FUNDS_ENROLLMENT_REQUIRED");
                    FundsPolicy.enrollment(
                            p,
                            tenant,
                            accountId,
                            input.contractId(),
                            input.currency(),
                            input.kind().name());
                    int scale;
                    try {
                        scale = Currency.getInstance(input.currency()).getDefaultFractionDigits();
                    } catch (IllegalArgumentException error) {
                        throw fail(400, "UNSUPPORTED_FUNDS_CURRENCY");
                    }
                    if (scale < 0 || scale > 18) throw fail(400, "UNSUPPORTED_FUNDS_PRECISION");
                    if (input.kind() == Kind.BANK_CONTROL)
                        FundsPolicy.require(p, tenant, "funds:bank-enroll", accountId);
                    var entity =
                            FundsAccount.builder()
                                    .accountId(accountId)
                                    .tenantId(tenant)
                                    .enrollmentId(input.enrollmentId())
                                    .contractId(input.contractId())
                                    .currency(input.currency())
                                    .currencyScale(scale)
                                    .currencyPolicy(currencyPolicy)
                                    .kind(input.kind().name())
                                    .state("BLOCKED")
                                    .booked(BigDecimal.ZERO)
                                    .held(BigDecimal.ZERO)
                                    .version(0L)
                                    .build();
                    return context.establish(p, tenant, "ENROLL", accountId)
                            .then(repos.nativeOps().accountGate(accountId.toString()))
                            .then(
                                    repos.accounts()
                                            .findLockedByTenantIdAndAccountId(tenant, accountId))
                            .switchIfEmpty(Mono.defer(() -> repos.writes().insertAccount(entity)))
                            .map(FundsRows::account)
                            .flatMap(
                                    account -> {
                                        FundsPolicy.require(p, tenant, "funds:enroll", accountId);
                                        if (input.kind() == Kind.BANK_CONTROL)
                                            FundsPolicy.require(
                                                    p, tenant, "funds:bank-enroll", accountId);
                                        if (!account.enrollmentId().equals(input.enrollmentId())
                                                || !account.contractId().equals(input.contractId())
                                                || !account.currency().equals(input.currency())
                                                || account.kind() != input.kind())
                                            return Mono.error(
                                                    fail(409, "FUNDS_ENROLLMENT_INTENT_CONFLICT"));
                                        return Mono.just(account);
                                    })
                            .onErrorMap(
                                    DuplicateKeyException.class,
                                    error -> fail(409, "FUNDS_ENROLLMENT_INTENT_CONFLICT"));
                });
    }

    @Transactional
    public Mono<Account> control(
            SecurityPrincipal p, String tenant, UUID accountId, Control input) {
        return Mono.defer(
                () -> {
                    FundsPolicy.require(p, tenant, "funds:control", accountId);
                    if (input == null || input.actionId() == null || input.state() == null)
                        throw fail(400, "FUNDS_CONTROL_REQUIRED");
                    var key = new FundsControlId(tenant, input.actionId());
                    return context.establish(p, tenant, "CONTROL", accountId)
                            .then(repos.nativeOps().accountGate(accountId.toString()))
                            .then(
                                    repos.accounts()
                                            .findLockedByTenantIdAndAccountId(tenant, accountId))
                            .switchIfEmpty(Mono.error(fail(404, "FUNDS_ACCOUNT_NOT_FOUND")))
                            .map(FundsRows::account)
                            .flatMap(
                                    current -> {
                                        FundsPolicy.require(p, tenant, "funds:control", accountId);
                                        return repos.controls()
                                                .findById(key)
                                                .flatMap(
                                                        existing -> {
                                                            if (!accountId.equals(
                                                                            existing.getAccountId())
                                                                    || !input.state()
                                                                            .name()
                                                                            .equals(
                                                                                    existing
                                                                                            .getState()))
                                                                return Mono.error(
                                                                        fail(
                                                                                409,
                                                                                "FUNDS_CONTROL_INTENT_CONFLICT"));
                                                            return Mono.just(current);
                                                        })
                                                .switchIfEmpty(
                                                        Mono.defer(
                                                                () -> {
                                                                    if (current.state()
                                                                                    == AccountState
                                                                                            .CLOSED
                                                                            && input.state()
                                                                                    != AccountState
                                                                                            .CLOSED)
                                                                        throw fail(
                                                                                409,
                                                                                "CLOSED_FUNDS_ACCOUNT_IS_FINAL");
                                                                    if (input.state()
                                                                                    == AccountState
                                                                                            .CLOSED
                                                                            && (current.state()
                                                                                            != AccountState
                                                                                                    .BLOCKED
                                                                                    || current.booked()
                                                                                                    .signum()
                                                                                            != 0
                                                                                    || current.held()
                                                                                                    .signum()
                                                                                            != 0))
                                                                        throw fail(
                                                                                409,
                                                                                "CLOSURE_REQUIRES_BLOCKED_ZERO_FUNDS");
                                                                    Mono<Void> verification =
                                                                            input.state()
                                                                                            == AccountState
                                                                                                    .ACTIVE
                                                                                    ? verifier
                                                                                            .verify(
                                                                                                    tenant,
                                                                                                    current)
                                                                                    : Mono.empty();
                                                                    return repos.writes()
                                                                            .insertControl(
                                                                                    FundsControl
                                                                                            .builder()
                                                                                            .id(key)
                                                                                            .accountId(
                                                                                                    accountId)
                                                                                            .state(
                                                                                                    input.state()
                                                                                                            .name())
                                                                                            .build())
                                                                            .then(verification)
                                                                            .then(
                                                                                    Mono.defer(
                                                                                            () -> {
                                                                                                FundsPolicy
                                                                                                        .require(
                                                                                                                p,
                                                                                                                tenant,
                                                                                                                "funds:control",
                                                                                                                accountId);
                                                                                                return repos.writes()
                                                                                                        .updateAccountState(
                                                                                                                tenant,
                                                                                                                accountId,
                                                                                                                current
                                                                                                                        .version(),
                                                                                                                current.state()
                                                                                                                        .name(),
                                                                                                                input.state()
                                                                                                                        .name());
                                                                                            }))
                                                                            .switchIfEmpty(
                                                                                    Mono.error(
                                                                                            fail(
                                                                                                    409,
                                                                                                    "FUNDS_CONTROL_SCOPE_CONFLICT")))
                                                                            .flatMap(
                                                                                    count ->
                                                                                            count
                                                                                                            == 1
                                                                                                    ? repos.accounts()
                                                                                                            .findByTenantIdAndAccountId(
                                                                                                                    tenant,
                                                                                                                    accountId)
                                                                                                            .map(
                                                                                                                    FundsRows
                                                                                                                            ::account)
                                                                                                    : Mono
                                                                                                            .error(
                                                                                                                    fail(
                                                                                                                            409,
                                                                                                                            "FUNDS_CONTROL_SCOPE_CONFLICT")));
                                                                }));
                                    })
                            .onErrorMap(
                                    DuplicateKeyException.class,
                                    error -> fail(409, "FUNDS_CONTROL_SCOPE_CONFLICT"));
                });
    }

    @Transactional(readOnly = true)
    public Mono<Account> account(SecurityPrincipal p, String tenant, UUID accountId) {
        return Mono.defer(
                () -> {
                    FundsPolicy.require(p, tenant, "funds:read", accountId);
                    return context.establish(p, tenant, "READ", accountId)
                            .then(repos.accounts().findByTenantIdAndAccountId(tenant, accountId))
                            .map(FundsRows::account)
                            .switchIfEmpty(Mono.error(fail(404, "FUNDS_ACCOUNT_NOT_FOUND")));
                });
    }
}
