/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.impl;

import io.fireflysoftware.core.banking.ledger.core.services.core.LedgerAuditRecordService;
import io.fireflysoftware.core.banking.ledger.core.services.funds.impl.FundsPolicy;
import io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.*;
import io.fireflysoftware.core.banking.ledger.models.config.LedgerOwningMutationResources;
import io.fireflysoftware.core.banking.ledger.models.entities.funds.FundsCompletedReserveWitness;
import io.fireflysoftware.core.banking.ledger.models.repositories.funds.FundsReserveCompletedWitnessRepository;
import java.math.BigInteger;
import java.time.*;
import java.util.Objects;
import java.util.UUID;
import org.fireflyframework.core.transaction.*;
import reactor.core.publisher.Mono;

/** Reads real protected FIRST completion; it neither creates nor authorizes an audit anchor. */
public final class LedgerFundsReserveWitnessServiceImpl implements LedgerFundsReserveWitnessService {
    private final BoundedR2dbcResources resources;
    private final FundsReserveCompletedWitnessRepository nativeOps;
    private final LedgerAuditRecordService records;
    private final LedgerMutationTime time;
    public LedgerFundsReserveWitnessServiceImpl(LedgerOwningMutationResources resources,
            LedgerAuditRecordService records, LedgerMutationTime time) {
        Objects.requireNonNull(resources);
        this.resources=resources.bounded();this.nativeOps=resources.completedReserveWitnesses();
        this.records=Objects.requireNonNull(records);this.time=Objects.requireNonNull(time);
    }
    @Override public Mono<LedgerFundsFirstCaptureProvider.CompletedCommand> requireFirst(RootTransactionScope scope,
            LedgerFundsFirstCaptureProvider.Authorization authorization, LedgerMutation.Reserved mutation, LedgerAuditEvidence evidence) {
        return Mono.defer(()->{
            require(scope!=null&&authorization!=null&&authorization.mode()==LedgerFundsFirstCaptureProvider.Mode.FIRST&&mutation!=null&&evidence!=null);
            var command=authorization.command();var principal=command.principal();var request=command.intent();
            require(principal.equals(mutation.principal())&&command.tenant().equals(mutation.tenant())&&request.equals(mutation.request())
                    &&principal.issuer().equals(authorization.captureActor().issuer())&&principal.subject().equals(authorization.captureActor().subject())
                    &&command.tenant().equals(authorization.captureActor().platformTenantId()));
            FundsPolicy.require(principal,command.tenant(),"funds:reserve",request.sourceAccountId(),request.destinationAccountId());
            var now=time.clock().instant();var until=now.plus(scope.remaining());
            if(until.isAfter(now.plusSeconds(5)))until=now.plusSeconds(5);
            if(until.isAfter(authorization.validUntil()))until=authorization.validUntil();
            var token=principal.claim("exp",Instant.class);if(until.isAfter(token))until=token;
            if(request.notAfterEpochMillis()!=null&&until.toEpochMilli()>request.notAfterEpochMillis())until=Instant.ofEpochMilli(request.notAfterEpochMillis());
            var deadline=Instant.ofEpochMilli(until.toEpochMilli());require(deadline.isAfter(now));
            var remaining=Duration.between(now,deadline);long before=time.nanos().getAsLong();
            return resources.factory().requireBoundScope(resources,scope)
                .then(Mono.defer(()->records.observe(scope,mutation,command.tenant())))
                .filter(evidence::equals).switchIfEmpty(Mono.error(failure()))
                .then(Mono.defer(()->resources.factory().requireBoundScope(resources,scope).then(nativeOps.requireCompletedReserve(
                    command.tenant(),request.operationId(),mutation.result().reservationId(),request.sourceAccountId(),request.destinationAccountId(),
                    command.canonicalIntent(),command.intentDigest(),command.operationFingerprint(),principal.issuer(),principal.subject(),OffsetDateTime.ofInstant(deadline,ZoneOffset.UTC)))))
                .switchIfEmpty(Mono.error(failure())).map(row->{
                    validate(row,command,mutation,evidence,deadline,token);
                    scope.narrow(R2dbcOperationBudget.observed(Instant.ofEpochMilli(row.observedAtEpochMillis()),Instant.ofEpochMilli(row.readUntilEpochMillis()-1),
                            before,Duration.ofSeconds(5),time.clock(),time.nanos()));
                    scope.remaining();
                    return new LedgerFundsFirstCaptureProvider.CompletedCommand(scope,row.commandId(),row.reservationId(),LedgerFundsFirstCaptureProvider.Mode.FIRST,null,command.intentDigest(),evidence.digest());
                }).timeout(remaining);
        }).onErrorResume(error->resources.factory().markBoundScopeRollbackOnly(resources,scope)
                .onErrorResume(markFailure->Mono.error(error)).then(Mono.error(error)));
    }
    private static void validate(FundsCompletedReserveWitness row,LedgerFundsFirstCaptureProvider.Command command,
            LedgerMutation.Reserved mutation,LedgerAuditEvidence evidence,Instant until,Instant token) {
        require(row!=null&&row.commandId()!=null&&!row.commandId().equals(new UUID(0,0))
                &&mutation.result().reservationId().equals(row.reservationId())&&command.intent().operationId().equals(row.operationId())
                &&command.tenant().equals(row.ownerTenant())&&command.intent().sourceAccountId().equals(row.sourceAccountId())
                &&command.intent().destinationAccountId().equals(row.destinationAccountId())&&command.canonicalIntent().equals(row.originalIntentCanonical())
                &&command.intentDigest().equals(row.originalIntentDigest())&&command.operationFingerprint().equals(row.operationFingerprint())
                &&command.principal().issuer().equals(row.actorIssuer())&&command.principal().subject().equals(row.actorSubject())
                &&fullXid(row.createdXid())&&row.createdXid().equals(row.currentXid())&&row.executorRole()!=null&&!row.executorRole().isBlank()
                &&row.executorRole().equals(row.sessionRole())&&!row.sessionRole().equals("firefly_funds_receipt_guard")
                &&!row.sessionRole().equals("firefly_funds_policy_reader")&&!row.sessionRole().equals("firefly_funds_policy_writer")
                &&"firefly_funds_receipt_guard".equals(row.functionRole())&&row.backendId()!=null&&row.backendId()>0
                &&row.observedAtEpochMillis()!=null&&row.observedAtEpochMillis()>0&&row.observedAtEpochMillis()<=253402300799999L
                &&row.tokenExpiresEpochMillis()!=null&&row.tokenExpiresEpochMillis()==token.toEpochMilli()
                &&row.tokenExpiresEpochMillis()<=253402300799999L&&row.tokenExpiresEpochMillis()>row.observedAtEpochMillis()
                &&row.readUntilEpochMillis()!=null&&row.readUntilEpochMillis()>row.observedAtEpochMillis()
                &&row.readUntilEpochMillis()-1>row.observedAtEpochMillis()&&row.readUntilEpochMillis()<=until.toEpochMilli()
                &&row.readUntilEpochMillis()<=row.tokenExpiresEpochMillis()
                &&evidence.ownerTenant().equals(command.tenant())&&"FUNDS_RESERVE_CAPTURED".equals(evidence.action())&&"funds-reservation".equals(evidence.resourceType())
                &&evidence.resourceId().equals(row.reservationId())&&evidence.processId().equals(row.operationId())
                &&row.operationId().toString().equals(evidence.commandKey()));
    }
    private static boolean fullXid(String value) {
        return value!=null&&value.matches("[1-9][0-9]{0,19}")&&new BigInteger(value).bitLength()<=64;
    }
    private static void require(boolean value) {if(!value)throw failure();}
    private static IllegalStateException failure() {return new IllegalStateException("Protected completed FIRST reserve witness required");}
}
