/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.core.services.funds.impl;

import static io.fireflysoftware.core.banking.ledger.core.services.funds.impl.FundsErrors.fail;

import io.fireflysoftware.core.banking.ledger.core.services.funds.FundsExpiryCandidateService;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.ExpiryBatch;
import io.fireflysoftware.core.banking.ledger.models.entities.funds.FundsReservation;
import io.fireflysoftware.core.banking.ledger.models.repositories.funds.FundsRepositories;
import java.util.List;
import java.util.UUID;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service("fundsExpiryCandidateService")
public final class FundsExpiryCandidateServiceImpl implements FundsExpiryCandidateService {
    private final FundsRepositories repos;
    private final FundsDatabaseContext context;

    public FundsExpiryCandidateServiceImpl(FundsRepositories repos, FundsDatabaseContext context) {
        this.repos = repos;
        this.context = context;
    }

    public Mono<List<UUID>> select(SecurityPrincipal principal, String tenant, ExpiryBatch input) {
        return Mono.defer(() -> {
            FundsPolicy.require(principal, tenant, "funds:expire");
            if (input == null || input.limit() == null || input.limit() < 1 || input.limit() > 100)
                return Mono.error(fail(400, "BOUNDED_EXPIRY_BATCH_REQUIRED"));
            return context.establish(principal, tenant, "EXPIRE", FundsPolicy.grantedAccounts(principal))
                    .then(repos.nativeOps().databaseNow())
                    .flatMapMany(now -> repos.reservations()
                            .findByTenantIdAndStateAndExpiresAtLessThanEqualOrderByExpiresAtAscReservationIdAsc(
                                    tenant, "HELD", now, PageRequest.of(0, input.limit()))
                            .map(FundsReservation::getReservationId))
                    .collectList();
        });
    }
}
