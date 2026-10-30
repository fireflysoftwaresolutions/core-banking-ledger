/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.core.services.funds.ownership;

import org.fireflyframework.core.transaction.RootTransactionScope;
import reactor.core.publisher.Mono;

/** FIRST completed-command reader only. Replay admission and anchor/grant issuance are separate. */
public interface LedgerFundsReserveWitnessService {
    Mono<LedgerFundsFirstCaptureProvider.CompletedCommand> requireFirst(RootTransactionScope scope,
            LedgerFundsFirstCaptureProvider.Authorization authorization,
            LedgerMutation.Reserved mutation, LedgerAuditEvidence evidence);
}
