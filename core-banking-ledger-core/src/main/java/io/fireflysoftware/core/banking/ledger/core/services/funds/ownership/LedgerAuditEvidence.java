/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.core.services.funds.ownership;

import java.util.Objects;
import java.util.UUID;

/** Bounded durable-record witness. Retained business metadata is distinct from audit capture provenance. */
public record LedgerAuditEvidence(String action, String ownerTenant, String resourceType, UUID resourceId,
        UUID processId, String commandKey, String digest, Long retainedRecordTimeMillis,
        String retainedActorIssuer, String retainedActorSubject) {
    public LedgerAuditEvidence {
        Objects.requireNonNull(action); Objects.requireNonNull(ownerTenant); Objects.requireNonNull(resourceType);
        Objects.requireNonNull(resourceId); Objects.requireNonNull(processId); Objects.requireNonNull(commandKey);
        if (!action.matches("[A-Z_]{1,64}") || !ownerTenant.matches("[A-Za-z0-9_-]{1,64}")
                || !resourceType.matches("[a-z-]{1,64}") || resourceId.equals(new UUID(0,0))
                || processId.equals(new UUID(0,0)) || commandKey.isBlank() || commandKey.length()>100
                || digest==null || !digest.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Exact bounded Ledger durable witness required");
    }
}
