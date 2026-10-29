/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.impl;

import io.fireflysoftware.core.banking.ledger.core.services.funds.FundsExpiryCandidateService;
import io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.*;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.core.transaction.impl.DefaultBoundedRootTransaction;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Selection completes before each separately atomic item; no batch-wide transaction or budget renewal. */
public final class LedgerOwningExpiryServiceImpl implements LedgerOwningExpiryService {
    private final BoundedR2dbcResources resources;
    private final BoundedRootTransaction selection;
    private final FundsExpiryCandidateService candidates;
    private final LedgerOwningMutationService mutations;

    public LedgerOwningExpiryServiceImpl(BoundedR2dbcResources resources, FundsExpiryCandidateService candidates,
            LedgerOwningMutationService mutations, CleanupPolicy cleanup) {
        this.resources = Objects.requireNonNull(resources);
        this.selection = new DefaultBoundedRootTransaction(resources, Objects.requireNonNull(cleanup));
        this.candidates = Objects.requireNonNull(candidates);
        this.mutations = Objects.requireNonNull(mutations);
    }

    public Mono<List<Reservation>> expire(R2dbcOperationBudget original, SecurityPrincipal principal,
            String tenant, ExpiryBatch input) {
        return selection.execute(original, new DefaultTransactionDefinition(), scope ->
                resources.factory().requireBoundScope(resources, scope)
                        .then(Mono.defer(() -> candidates.select(principal, tenant, input))))
                .flatMapMany(completion -> {
                    if (!completion.transactionFacts().commitAcknowledged()
                            || !completion.transactionFacts().boundaryCompleted()
                            || completion.cleanupFact() != CleanupFact.COMPLETE)
                        return Flux.error(new IllegalStateException("Complete owning expiry selection required"));
                    return Flux.fromIterable(completion.value()).concatMap(id -> expireOne(original, principal, tenant, id));
                })
                .collectList()
                .map(rows -> { original.remaining(); return rows; });
    }

    private Mono<Reservation> expireOne(R2dbcOperationBudget original, SecurityPrincipal principal, String tenant, UUID id) {
        UUID action = UUID.nameUUIDFromBytes(("funds-expiry:" + tenant + ":" + id).getBytes(StandardCharsets.UTF_8));
        return mutations.action(original, principal, tenant, id, new Command(action, Action.EXPIRE))
                .onErrorResume(LedgerTerminalExpiryException.class, error ->
                        id.equals(error.reservationId()) && action.equals(error.actionId())
                                ? Mono.empty() : Mono.error(error));
    }
}
