/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.core.services.core;

import io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.*;
import org.fireflyframework.core.transaction.RootTransactionScope;
import reactor.core.publisher.Mono;

public interface LedgerAuditRecordService {
    Mono<LedgerAuditEvidence> observe(RootTransactionScope scope, LedgerMutation mutation, String ownerTenant);
}
