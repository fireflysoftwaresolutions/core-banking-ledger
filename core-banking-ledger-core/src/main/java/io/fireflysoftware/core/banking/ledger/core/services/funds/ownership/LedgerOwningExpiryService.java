/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.core.services.funds.ownership;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.ExpiryBatch;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.Reservation;
import java.util.List;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import reactor.core.publisher.Mono;

/** Explicit expiry entrypoint retaining one original budget across separate selection and item roots. */
public interface LedgerOwningExpiryService {
    Mono<List<Reservation>> expire(R2dbcOperationBudget original, SecurityPrincipal principal,
            String tenant, ExpiryBatch input);
}
