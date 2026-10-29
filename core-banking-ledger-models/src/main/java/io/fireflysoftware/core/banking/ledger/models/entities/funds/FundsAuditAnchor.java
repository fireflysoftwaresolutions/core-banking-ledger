/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.models.entities.funds;

import java.util.UUID;

/** Existing original capture metadata plus separately refreshed current permission/context. Never a legacy business timestamp. */
public record FundsAuditAnchor(UUID currentAuthorityId, UUID captureNonce, UUID eventId,
        UUID resourceTenantId, String ownerTenant, String action, UUID resourceId, UUID processId,
        String commandKey, String evidenceDigest, Long captureObservedAtEpochMillis,
        Long dbObservedAtEpochMillis, Long nativeUntilEpochMillis, String actorIssuer, String actorSubject,
        String actorClientId, String actorPlatformTenantId, String actorStaffRealm, UUID actorStaffId,
        String originalPolicyRevision, UUID causationId) {}
