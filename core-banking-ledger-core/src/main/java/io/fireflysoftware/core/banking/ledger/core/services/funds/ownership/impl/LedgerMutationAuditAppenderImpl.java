/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.impl;

import io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.*;
import io.fireflysoftware.core.banking.ledger.models.config.LedgerOwningMutationResources;
import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.fireflyframework.core.audit.AuditAppendAuthority;
import org.fireflyframework.core.transaction.RootTransactionScope;
import org.fireflyframework.kernel.audit.AuditEvent;
import reactor.core.publisher.Mono;

/** Call-local shared authority rechecks the exact original mutation and anchor without granting either. */
public final class LedgerMutationAuditAppenderImpl implements LedgerMutationAuditAppender {
    private final LedgerOwningMutationResources resources;
    private final LedgerMutationAuditCaptureImpl capture;
    private final Clock clock;
    private final LongSupplier nanos;
    private final AuditAppendAuthority additional;
    public LedgerMutationAuditAppenderImpl(LedgerOwningMutationResources resources,
            LedgerMutationAuditCaptureImpl capture, Clock clock, LongSupplier nanos) {
        this(resources,capture,clock,nanos,null);
    }
    public LedgerMutationAuditAppenderImpl(LedgerOwningMutationResources resources,
            LedgerMutationAuditCaptureImpl capture, Clock clock, LongSupplier nanos, AuditAppendAuthority additional) {
        this.resources=Objects.requireNonNull(resources);this.capture=Objects.requireNonNull(capture);
        this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);
        this.additional=additional;
    }
    @Override public Mono<UUID> append(RootTransactionScope scope, LedgerMutation mutation) {
        return guarded(scope,()->
            capture.capture(scope,mutation).switchIfEmpty(Mono.error(failure())).flatMap(event->{
                AuditAppendAuthority authority=retained->current(scope,mutation,event,retained);
                var appender=resources.audit(authority,clock,nanos);
                return appender.append(scope,event).switchIfEmpty(Mono.error(failure())).flatMap(append->{
                    if(append.record()==null||!event.equals(append.record().event()))return Mono.error(failure());
                    return capture.pair(scope,mutation,append).filter(event.eventId()::equals)
                            .switchIfEmpty(Mono.error(failure()));
                });
            }));
    }
    private Mono<AuditAppendAuthority.Permit> current(RootTransactionScope scope, LedgerMutation mutation,
            AuditEvent expected, AuditEvent retained) {
        return guarded(scope,()->capture.requireCurrentAppend(scope,mutation,expected,retained).flatMap(local->{
            if(additional==null)return Mono.just(local);
            return Mono.defer(()->additional.requireCurrent(retained))
                    .filter(value->value.stream().equals(expected.stream())&&value.actor().equals(expected.actor())
                            &&value.validUntil().isAfter(clock.instant()))
                    .switchIfEmpty(Mono.error(failure()))
                    .map(value->new AuditAppendAuthority.Permit(local.stream(),local.actor(),
                            Collections.min(List.of(local.validUntil(),value.validUntil()))));
        }));
    }
    private <T> Mono<T> guarded(RootTransactionScope scope, Supplier<Mono<T>> work) {
        var graph=resources.bounded();
        return graph.factory().requireBoundScope(graph,scope).then(Mono.defer(work))
                .onErrorResume(error->graph.factory().markBoundScopeRollbackOnly(graph,scope)
                .onErrorResume(markFailure->Mono.error(error)).then(Mono.error(error)));
    }
    private static IllegalStateException failure() { return new IllegalStateException("Exact call-local Ledger audit append required"); }
}
