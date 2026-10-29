/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.core.services.funds.ownership;

import java.util.UUID;
import org.fireflyframework.core.transaction.RootTransactionScope;
import reactor.core.publisher.Mono;

/** Capture, append and native pair for one mutation on its original owning root. */
public interface LedgerMutationAuditAppender {
    Mono<UUID> append(RootTransactionScope scope, LedgerMutation mutation);
}
