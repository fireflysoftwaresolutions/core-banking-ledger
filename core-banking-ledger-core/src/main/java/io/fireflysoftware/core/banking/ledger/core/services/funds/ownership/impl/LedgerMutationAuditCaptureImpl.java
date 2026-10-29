/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.impl;

import io.fireflysoftware.core.banking.ledger.core.services.core.LedgerAuditRecordService;
import io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.*;
import io.fireflysoftware.core.banking.ledger.models.entities.funds.FundsAuditAnchor;
import io.fireflysoftware.core.banking.ledger.models.repositories.funds.FundsNativeRepository;
import java.time.*;
import java.util.*;
import java.util.function.*;
import org.fireflyframework.core.audit.TransactionalAuditAppender.ProvisionalAppend;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.kernel.audit.*;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.SecurityContextPort;
import reactor.core.publisher.Mono;

/** Reads an existing genuine capture anchor and pairs the actual shared append on the original graph. */
public final class LedgerMutationAuditCaptureImpl implements LedgerMutationAuditCapture {
    private final BoundedR2dbcResources resources;
    private final LedgerAuditRecordService records;
    private final FundsNativeRepository nativeOps;
    private final SecurityContextPort principals;
    private final LedgerAuditAdmission admission;
    private final Clock clock;
    private final LongSupplier nanos;
    public LedgerMutationAuditCaptureImpl(BoundedR2dbcResources resources, LedgerAuditRecordService records,
            FundsNativeRepository nativeOps, SecurityContextPort principals, LedgerAuditAdmission admission,
            Clock clock, LongSupplier nanos) {
        this.resources=Objects.requireNonNull(resources);this.records=Objects.requireNonNull(records);
        this.nativeOps=Objects.requireNonNull(nativeOps);this.principals=Objects.requireNonNull(principals);
        this.admission=Objects.requireNonNull(admission);this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);
    }
    private record Frame(AuditEvent event, UUID nonce) {}
    public Mono<AuditEvent> capture(RootTransactionScope scope, LedgerMutation mutation) {
        return guarded(scope,()->frame(scope,mutation).map(Frame::event));
    }
    public Mono<UUID> pair(RootTransactionScope scope, LedgerMutation mutation, ProvisionalAppend append) {
        return guarded(scope,()->frame(scope,mutation).flatMap(frame->{
            if(append==null||append.record()==null||!frame.event().equals(append.record().event()))return Mono.error(failure());
            return resources.factory().requireBoundScope(resources,scope).then(nativeOps.pairAuditCapture(frame.nonce(),
                    frame.event().eventId(),append.record().sequence(),append.record().recordHash(),
                    frame.event().evidenceDigest(),append.replay())).filter(frame.event().eventId()::equals)
                    .switchIfEmpty(Mono.error(failure())).timeout(scope.remaining());
        }));
    }
    private Mono<Frame> frame(RootTransactionScope scope, LedgerMutation mutation) {
        return Mono.defer(principals::currentPrincipal).switchIfEmpty(Mono.error(failure())).flatMap(principal->{
            SecurityPrincipal supplied=mutation instanceof LedgerMutation.Funded m?m.principal():mutation instanceof LedgerMutation.Reserved m?m.principal():mutation instanceof LedgerMutation.Acted m?m.principal():null;
            if((mutation instanceof LedgerMutation.Funded||mutation instanceof LedgerMutation.Reserved||mutation instanceof LedgerMutation.Acted)&&!principal.equals(supplied))return Mono.error(failure());
            return records.observe(scope,mutation,principal.tenantId()).flatMap(evidence->Mono.defer(()->admission.requireCurrent(principal,evidence))
                .switchIfEmpty(Mono.error(failure())).flatMap(current->{
                    if(!principal.equals(current.currentCaller())||!current.validUntil().isAfter(clock.instant()))return Mono.error(failure());
                    Instant now=clock.instant();Instant until=Collections.min(List.of(current.validUntil(),now.plus(scope.remaining()),now.plusSeconds(5)));
                    long before=nanos.getAsLong();
                    return resources.factory().requireBoundScope(resources,scope).then(nativeOps.requireAuditCapture(
                            current.currentNativeAuthorityId(),evidence.ownerTenant(),current.resourceTenantId(),evidence.action(),
                            evidence.resourceId(),evidence.processId(),evidence.commandKey(),evidence.digest(),OffsetDateTime.ofInstant(until,ZoneOffset.UTC)))
                        .switchIfEmpty(Mono.error(failure())).timeout(scope.remaining()).map(anchor->{
                            validate(anchor,current,evidence,until);
                            var cap=R2dbcOperationBudget.observed(Instant.ofEpochMilli(anchor.dbObservedAtEpochMillis()),Instant.ofEpochMilli(anchor.nativeUntilEpochMillis()-1),before,Duration.ofSeconds(5),clock,nanos);scope.narrow(cap);
                            var actor=new AuditActor(anchor.actorIssuer(),anchor.actorSubject(),anchor.actorClientId(),anchor.actorPlatformTenantId(),anchor.actorStaffRealm(),anchor.actorStaffId());
                            var stream=new AuditStream(anchor.resourceTenantId(),"core-banking-ledger",evidence.resourceType(),anchor.resourceId());
                            var event=new AuditEvent(anchor.eventId(),stream,actor,evidence.action(),AuditEvent.Outcome.ACCEPTED,
                                    evidence.processId(),evidence.processId(),anchor.causationId(),Instant.ofEpochMilli(anchor.captureObservedAtEpochMillis()),evidence.digest(),anchor.originalPolicyRevision());
                            return new Frame(event,anchor.captureNonce());
                        });
                }));
        });
    }
    private static void validate(FundsAuditAnchor a, LedgerAuditAdmission.Observation current, LedgerAuditEvidence e,Instant until) {
        if(a==null||a.captureNonce()==null||a.captureNonce().equals(new UUID(0,0))||a.eventId()==null||a.eventId().equals(new UUID(0,0))
                ||!current.currentNativeAuthorityId().equals(a.currentAuthorityId())||!current.resourceTenantId().equals(a.resourceTenantId())
                ||!e.ownerTenant().equals(a.ownerTenant())||!e.action().equals(a.action())||!e.resourceId().equals(a.resourceId())
                ||!e.processId().equals(a.processId())||!e.commandKey().equals(a.commandKey())||!e.digest().equals(a.evidenceDigest())
                ||a.captureObservedAtEpochMillis()==null||a.captureObservedAtEpochMillis()<=0||a.dbObservedAtEpochMillis()==null
                ||a.dbObservedAtEpochMillis()<a.captureObservedAtEpochMillis()||a.nativeUntilEpochMillis()==null
                ||a.nativeUntilEpochMillis()<=a.dbObservedAtEpochMillis()||a.nativeUntilEpochMillis()-1<=a.dbObservedAtEpochMillis()||a.nativeUntilEpochMillis()>until.toEpochMilli()
                ||!current.originalPolicyRevision().equals(a.originalPolicyRevision()))throw failure();
        var original=new AuditActor(a.actorIssuer(),a.actorSubject(),a.actorClientId(),a.actorPlatformTenantId(),a.actorStaffRealm(),a.actorStaffId());
        if(!current.originalCaptureActor().equals(original))throw failure();
        boolean root=Set.of("POSTING_CREATE_CAPTURED","FUNDS_FUND_CAPTURED","FUNDS_RESERVE_CAPTURED").contains(e.action());
        if((!root&&a.causationId()==null)||(a.causationId()!=null&&a.causationId().equals(new UUID(0,0))))throw failure();
    }
    private <T> Mono<T> guarded(RootTransactionScope scope,Supplier<Mono<T>> work) {
        return Mono.defer(()->resources.factory().requireBoundScope(resources,scope).then(Mono.defer(work))
            .onErrorResume(error->resources.factory().markBoundScopeRollbackOnly(resources,scope).onErrorResume(markFailure->Mono.error(error)).then(Mono.error(error))));
    }
    private static IllegalStateException failure() {return new IllegalStateException("Existing original Ledger capture and current native authority required");}
}
