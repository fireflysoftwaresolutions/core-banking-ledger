/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.models.entities.funds;

import java.util.UUID;

/** Fixed native integrity projection, not an authority grant or a new business timestamp. */
public record FundsCompletedReserveWitness(UUID commandId, UUID reservationId, UUID operationId,
        String ownerTenant, UUID sourceAccountId, UUID destinationAccountId,
        String originalIntentCanonical, String originalIntentDigest, String operationFingerprint,
        String actorIssuer, String actorSubject, String createdXid, String currentXid,
        String executorRole, String sessionRole, String functionRole, Integer backendId,
        Long observedAtEpochMillis, Long tokenExpiresEpochMillis, Long readUntilEpochMillis) {}
