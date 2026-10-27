package io.fireflysoftware.core.banking.ledger.core.services.funds;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.*;

import org.fireflyframework.security.api.domain.SecurityPrincipal;

import reactor.core.publisher.Mono;

public interface FundsReconciliationService {
    Mono<Reconciliation> reconcile(SecurityPrincipal p, String tenant, Reserve input);
}
