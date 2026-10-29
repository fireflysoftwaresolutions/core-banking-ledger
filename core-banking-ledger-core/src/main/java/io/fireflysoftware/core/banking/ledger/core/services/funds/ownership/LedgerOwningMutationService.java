/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.core.services.funds.ownership;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.core.*;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.*;
import java.util.UUID;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import reactor.core.publisher.Mono;

/** Explicit outer entrypoint. The caller retains the original budget observed before any I/O. */
public interface LedgerOwningMutationService {
    Mono<LedgerPostingDTO> create(R2dbcOperationBudget original, LedgerPostingRequestDTO request);
    Mono<LedgerPostingDTO> confirm(R2dbcOperationBudget original, UUID id);
    Mono<LedgerPostingDTO> release(R2dbcOperationBudget original, UUID id);
    Mono<LedgerPostingDTO> reverse(R2dbcOperationBudget original, UUID id, String key);
    Mono<FundingReceipt> fund(R2dbcOperationBudget original, SecurityPrincipal p, String tenant, Funding request);
    Mono<Reservation> reserve(R2dbcOperationBudget original, SecurityPrincipal p, String tenant, Reserve request);
    Mono<Reservation> action(R2dbcOperationBudget original, SecurityPrincipal p, String tenant, UUID id, Command request);
}
