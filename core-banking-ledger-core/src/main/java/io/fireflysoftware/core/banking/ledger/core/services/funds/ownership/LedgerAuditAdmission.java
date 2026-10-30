/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.core.services.funds.ownership;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.fireflyframework.kernel.audit.AuditActor;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import reactor.core.publisher.Mono;

/** Current permission to use an existing original capture anchor. There is no default authority provider. */
public interface LedgerAuditAdmission {
    record Observation(SecurityPrincipal currentCaller, UUID currentNativeAuthorityId, UUID resourceTenantId,
            AuditActor originalCaptureActor, String originalPolicyRevision, Instant validUntil) {
        public Observation {
            Objects.requireNonNull(currentCaller); Objects.requireNonNull(currentNativeAuthorityId);
            Objects.requireNonNull(resourceTenantId); Objects.requireNonNull(originalCaptureActor);
            Objects.requireNonNull(originalPolicyRevision); Objects.requireNonNull(validUntil);
            if(currentNativeAuthorityId.equals(new UUID(0,0))||resourceTenantId.equals(new UUID(0,0)))
                throw new IllegalArgumentException("Existing qualified native audit authority required");
        }
    }
    Mono<Observation> requireCurrent(SecurityPrincipal principal, LedgerAuditEvidence evidence);
}
