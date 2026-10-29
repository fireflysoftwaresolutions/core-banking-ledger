/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.impl;

import io.fireflysoftware.core.banking.ledger.core.services.core.LedgerPostingService;
import io.fireflysoftware.core.banking.ledger.core.services.funds.FundsMoneyService;
import io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.*;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.core.*;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.*;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicReference;
import org.fireflyframework.core.audit.TransactionalAuditAppender;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.core.transaction.impl.DefaultBoundedRootTransaction;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** One outer root encloses all existing REQUIRED funds/posting work and its owner audit append. */
public final class LedgerOwningMutationServiceImpl implements LedgerOwningMutationService {
    private final BoundedR2dbcResources resources;
    private final BoundedRootTransaction root;
    private final LedgerPostingService postings;
    private final FundsMoneyService money;
    private final TransactionalAuditAppender audit;
    private final LedgerMutationAuditCapture capture;

    public LedgerOwningMutationServiceImpl(BoundedR2dbcResources resources, LedgerPostingService postings,
            FundsMoneyService money, TransactionalAuditAppender audit, LedgerMutationAuditCapture capture,
            CleanupPolicy cleanup) {
        this.resources=Objects.requireNonNull(resources);
        this.root=new DefaultBoundedRootTransaction(resources,Objects.requireNonNull(cleanup));
        this.postings=Objects.requireNonNull(postings); this.money=Objects.requireNonNull(money);
        this.audit=Objects.requireNonNull(audit); this.capture=Objects.requireNonNull(capture);
    }
    public Mono<LedgerPostingDTO> create(R2dbcOperationBudget original, LedgerPostingRequestDTO request) {
        return execute(original,()->postings.create(request),result->new LedgerMutation.Created(request,result));
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
    public Mono<Reservation> action(R2dbcOperationBudget original, SecurityPrincipal p, String tenant, UUID id, Command request) {
        return execute(original,()->money.action(p,tenant,id,request),result->new LedgerMutation.Acted(p,tenant,id,request,result),
                request!=null&&request.action()==Action.EXPIRE?id:null,request==null?null:request.actionId());
    }
    private <T> Mono<T> execute(R2dbcOperationBudget original, Supplier<Mono<T>> work, Function<T,LedgerMutation> mutation) {
        return execute(original,work,mutation,null,null);
    }
    private <T> Mono<T> execute(R2dbcOperationBudget original, Supplier<Mono<T>> work, Function<T,LedgerMutation> mutation,
            UUID expiryReservation, UUID expiryAction) {
        return Mono.defer(()->{
            var workFailure=new AtomicReference<Throwable>();
            return root.execute(original,new DefaultTransactionDefinition(),scope->
                resources.factory().requireBoundScope(resources,scope)
                    .then(Mono.defer(work).doOnError(error->workFailure.compareAndSet(null,error)))
                    .flatMap(value->{
                        var selected=mutation.apply(value);
                        return Mono.defer(()->capture.capture(scope,selected)).switchIfEmpty(Mono.error(unavailable()))
                            .flatMap(event->audit.append(scope,event).switchIfEmpty(Mono.error(unavailable()))
                                .flatMap(append->Mono.defer(()->capture.pair(scope,selected,append))
                                    .filter(event.eventId()::equals).switchIfEmpty(Mono.error(unavailable()))))
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
