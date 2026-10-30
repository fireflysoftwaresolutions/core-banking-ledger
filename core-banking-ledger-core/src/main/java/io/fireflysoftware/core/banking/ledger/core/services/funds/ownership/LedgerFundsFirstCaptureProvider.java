/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.core.services.funds.ownership;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.Reserve;
import io.fireflysoftware.core.banking.ledger.interfaces.protocol.receipt.FundsReceiptCodec;
import io.fireflysoftware.core.banking.ledger.models.entities.funds.FundsAuditAnchor;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import org.fireflyframework.core.transaction.RootTransactionScope;
import org.fireflyframework.kernel.audit.AuditActor;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import reactor.core.publisher.Mono;

/** Required real adapters only. Mapping and PDP evidence do not themselves issue native authority. */
public interface LedgerFundsFirstCaptureProvider {
    enum Mode { FIRST, REPLAY }
    record Command(SecurityPrincipal principal, String tenant, Reserve intent,
            String canonicalIntent, String intentDigest, String operationFingerprint) {
        public Command {
            Objects.requireNonNull(principal);
            var canonical=FundsReceiptCodec.canonicalIntent(tenant,intent);
            if(!canonical.canonical().equals(canonicalIntent)||!canonical.digest().equals(intentDigest)
                    ||!FundsReceiptCodec.reserveOperationFingerprint(intent).equals(operationFingerprint))
                throw new IllegalArgumentException("Exact original funds command required");
        }
        public static Command reserve(SecurityPrincipal principal,String tenant,Reserve intent) {
            var canonical=FundsReceiptCodec.canonicalIntent(tenant,intent);
            return new Command(principal,tenant,intent,canonical.canonical(),canonical.digest(),
                    FundsReceiptCodec.reserveOperationFingerprint(intent));
        }
    }
    /** Same-response real policy provenance and an independently qualified current owner mapping. */
    record Authorization(Command command, UUID resourceTenant, Mode mode, AuditActor captureActor,
            String originalPolicyRevision, String currentPolicyRevision, String decisionId, Instant validUntil) {
        public Authorization {
            Objects.requireNonNull(command);Objects.requireNonNull(mode);Objects.requireNonNull(captureActor);Objects.requireNonNull(validUntil);
            if(resourceTenant==null||resourceTenant.equals(new UUID(0,0))||originalPolicyRevision==null||originalPolicyRevision.isBlank()
                    ||currentPolicyRevision==null||currentPolicyRevision.isBlank()
                    ||mode==Mode.FIRST&&!originalPolicyRevision.equals(currentPolicyRevision)
                    ||decisionId==null||decisionId.isBlank())throw new IllegalArgumentException("Real current mapping and policy evidence required");
        }
    }
    /** A port response, never Java-issued authority. The native adapter must attest the actual protected completed row. */
    record CompletedCommand(RootTransactionScope scope, UUID commandId, UUID reservationId, Mode mode,
            UUID originalEventId, String originalIntentDigest, String evidenceDigest) {
        public CompletedCommand {
            Objects.requireNonNull(scope);Objects.requireNonNull(mode);
            if(commandId==null||commandId.equals(new UUID(0,0))||reservationId==null||reservationId.equals(new UUID(0,0))
                    ||mode==Mode.FIRST&&originalEventId!=null
                    ||mode==Mode.REPLAY&&(originalEventId==null||originalEventId.equals(new UUID(0,0)))
                    ||originalIntentDigest==null||!originalIntentDigest.matches("[0-9a-f]{64}")
                    ||evidenceDigest==null||!evidenceDigest.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("Protected original completed command required");
        }
    }
    /** Must use the exact verified caller, current owner projection and same-response PDP metadata within original.
     * The real adapter must authenticate the complete captureActor, including client/staff provenance, using
     * its qualified issuer contract; SecurityPrincipal has no universal typed client/staff projection. */
    Mono<Authorization> authorize(R2dbcOperationBudget original, Command command);

    /** FIRST requires the protected completed V59 command in this same physical session/full XID.
     * REPLAY requires its retained original command and anchor under genuine current replay permission,
     * never a replacement command, actor, time or grant. Java scope identity alone proves neither. */
    Mono<CompletedCommand> requireCompleted(RootTransactionScope scope, Authorization authorization,
            LedgerMutation.Reserved mutation, LedgerAuditEvidence evidence);

    /** Must require the protected witness before creation; replay without an original anchor must refuse. */
    Mono<FundsAuditAnchor> requireOriginalAnchor(RootTransactionScope scope, Authorization authorization,
            CompletedCommand completed, LedgerAuditEvidence evidence);
}
