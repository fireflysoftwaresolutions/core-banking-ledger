/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.core.services.funds;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.ExpiryBatch;
import java.util.List;
import java.util.UUID;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import reactor.core.publisher.Mono;

/** Database-clock selector; the caller supplies its existing transaction boundary. */
public interface FundsExpiryCandidateService {
    Mono<List<UUID>> select(SecurityPrincipal principal, String tenant, ExpiryBatch input);
}
