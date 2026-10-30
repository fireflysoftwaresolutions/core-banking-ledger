package io.fireflysoftware.core.banking.ledger.core.services.funds;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.*;

import org.fireflyframework.security.api.domain.SecurityPrincipal;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FundsAuthority {
    Mono<Account> enroll(
            SecurityPrincipal principal, String tenant, UUID accountId, Enrollment input);

    Mono<Account> control(
            SecurityPrincipal principal, String tenant, UUID accountId, Control input);

    Mono<Account> account(SecurityPrincipal principal, String tenant, UUID accountId);
}
