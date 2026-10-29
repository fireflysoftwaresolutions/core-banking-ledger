/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.core.services.funds.ownership;

import java.util.Objects;
import java.util.UUID;
import org.fireflyframework.core.transaction.*;

/** Original terminal expiry refusal after observed technical rollback; never business no-effect proof. */
public final class LedgerTerminalExpiryException extends IllegalStateException {
    private final UUID reservationId;
    private final UUID actionId;
    private final TransactionFacts facts;
    private final CleanupFact cleanup;

    public LedgerTerminalExpiryException(UUID reservationId, UUID actionId, RootTransactionException failure) {
        super("TERMINAL_EXPIRY_REFUSED_AFTER_ACKNOWLEDGED_ROLLBACK");
        this.reservationId = Objects.requireNonNull(reservationId);
        this.actionId = Objects.requireNonNull(actionId);
        this.facts = Objects.requireNonNull(failure).transactionFacts();
        this.cleanup = failure.cleanupFact();
        if (!facts.nativeAttempted() || !facts.rollbackAcknowledged() || facts.commitAcknowledged() || facts.boundaryCompleted()
                || cleanup != CleanupFact.COMPLETE)
            throw new IllegalArgumentException("Acknowledged expiry rollback and complete cleanup required");
    }

    public UUID reservationId() { return reservationId; }
    public UUID actionId() { return actionId; }
    public TransactionFacts transactionFacts() { return facts; }
    public CleanupFact cleanupFact() { return cleanup; }
}
