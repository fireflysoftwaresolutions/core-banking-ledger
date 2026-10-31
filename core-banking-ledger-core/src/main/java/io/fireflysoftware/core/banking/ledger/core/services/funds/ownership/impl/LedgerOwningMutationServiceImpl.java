/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.impl;

import io.fireflysoftware.core.banking.ledger.core.services.core.LedgerPostingService;
import io.fireflysoftware.core.banking.ledger.core.services.core.LedgerPostingIntent;
import io.fireflysoftware.core.banking.ledger.core.services.core.LedgerAuditRecordService;
import io.fireflysoftware.core.banking.ledger.core.services.funds.FundsMoneyService;
import io.fireflysoftware.core.banking.ledger.core.services.funds.impl.FundsPolicy;
import io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.*;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.core.*;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.*;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.BiFunction;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import org.fireflyframework.core.audit.TransactionalAuditAppender;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.core.transaction.impl.DefaultBoundedRootTransaction;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.fireflyframework.kernel.audit.AuditActor;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** One outer root encloses all existing REQUIRED funds/posting work and its owner audit append. */
public final class LedgerOwningMutationServiceImpl implements LedgerOwningMutationService {
    private final BoundedR2dbcResources resources;
    private final BoundedRootTransaction root;
    private final LedgerPostingService postings;
    private final FundsMoneyService money;
    private final LedgerMutationAuditAppender audit;
    private final FirstCapture firstCapture;
    private record FirstCapture(LedgerFundsFirstCaptureProvider provider, SecurityContextPort principals,
            LedgerAuditRecordService records, LedgerMutationTime time) {
        FirstCapture {Objects.requireNonNull(provider);Objects.requireNonNull(principals);Objects.requireNonNull(records);Objects.requireNonNull(time);}
    }

    public LedgerOwningMutationServiceImpl(BoundedR2dbcResources resources, LedgerPostingService postings,
            FundsMoneyService money, TransactionalAuditAppender audit, LedgerMutationAuditCapture capture,
            CleanupPolicy cleanup) {
        this(resources,postings,money,legacyAudit(audit,capture),cleanup);
    }
    public LedgerOwningMutationServiceImpl(BoundedR2dbcResources resources, LedgerPostingService postings,
            FundsMoneyService money, LedgerMutationAuditAppender audit, CleanupPolicy cleanup) {
        this(resources,postings,money,audit,cleanup,null);
    }
    /** Explicit construction only; there is no default successful provider or automatic configuration. */
    public LedgerOwningMutationServiceImpl(BoundedR2dbcResources resources, LedgerPostingService postings,
            FundsMoneyService money, LedgerMutationAuditAppender audit, CleanupPolicy cleanup,
            LedgerFundsFirstCaptureProvider provider, SecurityContextPort principals,
            LedgerAuditRecordService records, LedgerMutationTime time) {
        this(resources,postings,money,audit,cleanup,new FirstCapture(provider,principals,records,time));
    }
    private LedgerOwningMutationServiceImpl(BoundedR2dbcResources resources, LedgerPostingService postings,
            FundsMoneyService money, LedgerMutationAuditAppender audit, CleanupPolicy cleanup, FirstCapture firstCapture) {
        this.resources=Objects.requireNonNull(resources);
        this.root=new DefaultBoundedRootTransaction(resources,Objects.requireNonNull(cleanup));
        this.postings=Objects.requireNonNull(postings); this.money=Objects.requireNonNull(money);
        this.audit=Objects.requireNonNull(audit);
        this.firstCapture=firstCapture;
    }
    private static LedgerMutationAuditAppender legacyAudit(TransactionalAuditAppender audit, LedgerMutationAuditCapture capture) {
        Objects.requireNonNull(audit);Objects.requireNonNull(capture);
        return (scope,mutation)->Mono.defer(()->capture.capture(scope,mutation)).switchIfEmpty(Mono.error(unavailable()))
                .flatMap(event->audit.append(scope,event).switchIfEmpty(Mono.error(unavailable()))
                    .flatMap(append->Mono.defer(()->capture.pair(scope,mutation,append))
                        .filter(event.eventId()::equals).switchIfEmpty(Mono.error(unavailable()))));
    }
    public Mono<LedgerPostingDTO> create(R2dbcOperationBudget original, LedgerPostingRequestDTO request) {
        return Mono.defer(()->{
            var intent=LedgerPostingIntent.capture(request);
            return execute(original,()->postings.create(intent.request()),
                    result->new LedgerMutation.Created(intent.request(),result));
        });
    }
    public Mono<LedgerPostingDTO> confirm(R2dbcOperationBudget original, UUID id) {
        return execute(original,()->postings.confirm(id),result->new LedgerMutation.Confirmed(id,result));
    }
    public Mono<LedgerPostingDTO> release(R2dbcOperationBudget original, UUID id) {
        return execute(original,()->postings.release(id),result->new LedgerMutation.Released(id,result));
    }
    public Mono<LedgerPostingDTO> reverse(R2dbcOperationBudget original, UUID id, String key) {
        return execute(original,()->postings.reverse(id,key),result->new LedgerMutation.Reversed(id,key,result));
    }
    public Mono<FundingReceipt> fund(R2dbcOperationBudget original, SecurityPrincipal p, String tenant, Funding request) {
        return execute(original,()->money.fund(p,tenant,request),result->new LedgerMutation.Funded(p,tenant,request,result));
    }
    public Mono<Reservation> reserve(R2dbcOperationBudget original, SecurityPrincipal p, String tenant, Reserve request) {
        return execute(original,()->money.reserve(p,tenant,request),result->new LedgerMutation.Reserved(p,tenant,request,result));
    }
    @Override public Mono<Reservation> reserveFirstCapture(R2dbcOperationBudget original, SecurityPrincipal p, String tenant, Reserve request) {
        if(firstCapture==null)return Mono.error(unavailable());
        final LedgerFundsFirstCaptureProvider.Command command;
        final R2dbcOperationBudget ingress;
        try {
            Objects.requireNonNull(original);original.remaining();
            FundsPolicy.require(p,tenant,"funds:reserve",request==null?null:request.sourceAccountId(),request==null?null:request.destinationAccountId());
            command=LedgerFundsFirstCaptureProvider.Command.reserve(p,tenant,request);
            var until=firstCapture.time().clock().instant().plusSeconds(5);
            var expiration=p.claim("exp",Instant.class);
            if(expiration.isBefore(until))until=expiration;
            if(request.notAfterEpochMillis()!=null&&Instant.ofEpochMilli(request.notAfterEpochMillis()).isBefore(until))until=Instant.ofEpochMilli(request.notAfterEpochMillis());
            ingress=cap(original,until);
        } catch(RuntimeException error) {return Mono.error(error);}
        return Mono.defer(()->{
            return Mono.defer(firstCapture.principals()::currentPrincipal).filter(p::equals).switchIfEmpty(Mono.error(unavailable()))
                .flatMap(current->Mono.defer(()->firstCapture.provider().authorize(original,command)).switchIfEmpty(Mono.error(unavailable())))
                .timeout(ingress.remaining()).flatMap(authorization->{
                    if(!command.equals(authorization.command())||!tenant.equals(authorization.captureActor().platformTenantId())
                            ||authorization.mode()==LedgerFundsFirstCaptureProvider.Mode.FIRST&&(!p.issuer().equals(authorization.captureActor().issuer())
                            ||!p.subject().equals(authorization.captureActor().subject())))return Mono.error(unavailable());
                    var effective=cap(ingress,authorization.validUntil());
                    return execute(effective,()->money.reserve(p,tenant,request),value->new LedgerMutation.Reserved(p,tenant,request,value),null,null,
                        (scope,mutation)->firstAnchor(scope,authorization,(LedgerMutation.Reserved)mutation));
                });
        });
    }
    private R2dbcOperationBudget cap(R2dbcOperationBudget inherited,Instant until) {
        var time=firstCapture.time();long before=time.nanos().getAsLong();var now=time.clock().instant();
        var maximum=now.plus(inherited.remaining());if(until.isAfter(maximum))until=maximum;
        if(until.isAfter(now.plusSeconds(5)))until=now.plusSeconds(5);
        return inherited.intersect(R2dbcOperationBudget.observed(now,until,before,Duration.ofSeconds(5),time.clock(),time.nanos()));
    }
    private Mono<Void> firstAnchor(RootTransactionScope scope,LedgerFundsFirstCaptureProvider.Authorization authorization,LedgerMutation.Reserved mutation) {
        return resources.factory().requireBoundScope(resources,scope).then(firstCapture.records().observe(scope,mutation,authorization.command().tenant()))
            .switchIfEmpty(Mono.error(unavailable())).flatMap(evidence->{
                if(!"FUNDS_RESERVE_CAPTURED".equals(evidence.action())||!mutation.result().reservationId().equals(evidence.resourceId())
                        ||!mutation.request().operationId().equals(evidence.processId())||!mutation.request().operationId().toString().equals(evidence.commandKey())
                        ||!authorization.command().tenant().equals(evidence.ownerTenant()))return Mono.error(unavailable());
                return Mono.defer(()->firstCapture.provider().requireCompleted(scope,authorization,mutation,evidence))
                    .switchIfEmpty(Mono.error(unavailable())).flatMap(completed->{
                        if(completed.scope()!=scope||completed.mode()!=authorization.mode()||!evidence.resourceId().equals(completed.reservationId())
                                ||!authorization.command().intentDigest().equals(completed.originalIntentDigest())||!evidence.digest().equals(completed.evidenceDigest()))return Mono.error(unavailable());
                        long before=firstCapture.time().nanos().getAsLong();
                        return Mono.defer(()->firstCapture.provider().requireOriginalAnchor(scope,authorization,completed,evidence))
                            .switchIfEmpty(Mono.error(unavailable())).flatMap(anchor->{
                                var actor=new AuditActor(anchor.actorIssuer(),anchor.actorSubject(),anchor.actorClientId(),anchor.actorPlatformTenantId(),anchor.actorStaffRealm(),anchor.actorStaffId());
                                if(anchor.currentAuthorityId()==null||anchor.currentAuthorityId().equals(new UUID(0,0))||anchor.captureNonce()==null||anchor.captureNonce().equals(new UUID(0,0))
                                        ||anchor.eventId()==null||anchor.eventId().equals(new UUID(0,0))||!authorization.resourceTenant().equals(anchor.resourceTenantId())
                                        ||!authorization.captureActor().equals(actor)||!authorization.originalPolicyRevision().equals(anchor.originalPolicyRevision())
                                        ||completed.mode()==LedgerFundsFirstCaptureProvider.Mode.REPLAY&&!completed.originalEventId().equals(anchor.eventId())
                                        ||!evidence.ownerTenant().equals(anchor.ownerTenant())||!evidence.action().equals(anchor.action())||!evidence.resourceId().equals(anchor.resourceId())
                                        ||!evidence.processId().equals(anchor.processId())||!evidence.commandKey().equals(anchor.commandKey())||!evidence.digest().equals(anchor.evidenceDigest())
                                        ||anchor.causationId()!=null||anchor.captureObservedAtEpochMillis()==null||anchor.captureObservedAtEpochMillis()<=0
                                        ||anchor.dbObservedAtEpochMillis()==null||anchor.dbObservedAtEpochMillis()<anchor.captureObservedAtEpochMillis()
                                        ||anchor.nativeUntilEpochMillis()==null||anchor.nativeUntilEpochMillis()<=0
                                        ||anchor.nativeUntilEpochMillis()<=anchor.dbObservedAtEpochMillis()
                                        ||anchor.nativeUntilEpochMillis()-1<=anchor.dbObservedAtEpochMillis()
                                        ||anchor.nativeUntilEpochMillis()>authorization.validUntil().toEpochMilli()
                                        ||anchor.nativeUntilEpochMillis()>firstCapture.time().clock().instant().plus(scope.remaining()).toEpochMilli())return Mono.error(unavailable());
                                scope.narrow(R2dbcOperationBudget.observed(Instant.ofEpochMilli(anchor.dbObservedAtEpochMillis()),Instant.ofEpochMilli(anchor.nativeUntilEpochMillis()-1),before,Duration.ofSeconds(5),firstCapture.time().clock(),firstCapture.time().nanos()));
                                return Mono.<Void>empty();
                            });
                    });
            });
    }
    public Mono<Reservation> action(R2dbcOperationBudget original, SecurityPrincipal p, String tenant, UUID id, Command request) {
        return execute(original,()->money.action(p,tenant,id,request),result->new LedgerMutation.Acted(p,tenant,id,request,result),
                request!=null&&request.action()==Action.EXPIRE?id:null,request==null?null:request.actionId());
    }
    private <T> Mono<T> execute(R2dbcOperationBudget original, Supplier<Mono<T>> work, Function<T,LedgerMutation> mutation) {
        return execute(original,work,mutation,null,null);
    }
    private <T> Mono<T> execute(R2dbcOperationBudget original, Supplier<Mono<T>> work, Function<T,LedgerMutation> mutation,
            UUID expiryReservation, UUID expiryAction) {
        return execute(original,work,mutation,expiryReservation,expiryAction,(scope,selected)->Mono.empty());
    }
    private <T> Mono<T> execute(R2dbcOperationBudget original, Supplier<Mono<T>> work, Function<T,LedgerMutation> mutation,
            UUID expiryReservation, UUID expiryAction, BiFunction<RootTransactionScope,LedgerMutation,Mono<Void>> beforeAudit) {
        return Mono.defer(()->{
            var workFailure=new AtomicReference<Throwable>();
            return root.execute(original,new DefaultTransactionDefinition(),scope->
                resources.factory().requireBoundScope(resources,scope)
                    .then(Mono.defer(work).doOnError(error->workFailure.compareAndSet(null,error)))
                    .flatMap(value->{
                        var selected=mutation.apply(value);
                        return Mono.defer(()->beforeAudit.apply(scope,selected)).then(Mono.defer(()->audit.append(scope,selected))).switchIfEmpty(Mono.error(unavailable()))
                            .thenReturn(value);
                    }))
                .onErrorMap(RootTransactionException.class,error->
                    expiryReservation!=null&&expiryAction!=null&&terminalRollback(error,workFailure.get())
                        ?new LedgerTerminalExpiryException(expiryReservation,expiryAction,error):error)
                .flatMap(completion->{
                    if(!completion.transactionFacts().commitAcknowledged()||!completion.transactionFacts().boundaryCompleted()
                            ||completion.cleanupFact()!=CleanupFact.COMPLETE)return Mono.error(unavailable());
                    return Mono.just(completion.value());
                });
        });
    }
    private static boolean terminalRollback(RootTransactionException error,Throwable workFailure) {
        var facts=error.transactionFacts();
        return workFailure instanceof ResponseStatusException refusal&&refusal.getStatusCode().value()==409
                &&"RESERVATION_IS_TERMINAL".equals(refusal.getReason())&&facts.nativeAttempted()
                &&facts.rollbackAcknowledged()&&!facts.commitAcknowledged()&&!facts.boundaryCompleted()
                &&error.cleanupFact()==CleanupFact.COMPLETE;
    }
    private static IllegalStateException unavailable() { return new IllegalStateException("Exact current Ledger owner audit capture and native pairing required"); }
}
