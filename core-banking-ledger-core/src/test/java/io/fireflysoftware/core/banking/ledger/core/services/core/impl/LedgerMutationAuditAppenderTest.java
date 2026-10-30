package io.fireflysoftware.core.banking.ledger.core.services.core.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.*;
import io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.impl.LedgerMutationAuditCaptureImpl;
import io.fireflysoftware.core.banking.ledger.models.config.LedgerOwningMutationResources;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.fireflyframework.core.audit.*;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.kernel.audit.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import reactor.core.publisher.Mono;

class LedgerMutationAuditAppenderTest {
    @SuppressWarnings("unchecked")
    private BiFunction<RootTransactionScope,LedgerMutation,Mono<UUID>> appender(
            LedgerMutationAuditCaptureTest.Fixture f,LedgerOwningMutationResources resources) {
        return appender(f,resources,null);
    }
    @SuppressWarnings("unchecked")
    private BiFunction<RootTransactionScope,LedgerMutation,Mono<UUID>> appender(
            LedgerMutationAuditCaptureTest.Fixture f,LedgerOwningMutationResources resources,AuditAppendAuthority supplied) {
        try {
            var type=Class.forName("io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.impl.LedgerMutationAuditAppenderImpl");
            assertTrue(Arrays.stream(type.getInterfaces()).anyMatch(value->value.getName().equals(
                    "io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.LedgerMutationAuditAppender")));
            var instance=supplied==null?type.getConstructor(LedgerOwningMutationResources.class,LedgerMutationAuditCaptureImpl.class,
                    Clock.class,LongSupplier.class).newInstance(resources,(LedgerMutationAuditCaptureImpl)f.capture,f.r.clock,f.r.nanos)
                    :type.getConstructor(LedgerOwningMutationResources.class,LedgerMutationAuditCaptureImpl.class,
                        Clock.class,LongSupplier.class,AuditAppendAuthority.class)
                        .newInstance(resources,(LedgerMutationAuditCaptureImpl)f.capture,f.r.clock,f.r.nanos,supplied);
            var append=type.getMethod("append",RootTransactionScope.class,LedgerMutation.class);
            return (scope,mutation)->{
                try{return (Mono<UUID>)append.invoke(instance,scope,mutation);}
                catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
            };
        }catch(ReflectiveOperationException error){throw new AssertionError("Concrete call-local Ledger audit append required",error);}
    }
    private LedgerOwningMutationResources controlledFactory(LedgerMutationAuditCaptureTest.Fixture f,
            AtomicReference<AuditAppendAuthority> retained,AtomicReference<AuditEvent> event,
            List<Instant> permits) {
        var resources=mock(LedgerOwningMutationResources.class);
        when(resources.bounded()).thenReturn(f.r.resources);
        when(resources.audit(any(),same(f.r.clock),same(f.r.nanos))).thenAnswer(invocation->{
            AuditAppendAuthority authority=invocation.getArgument(0);retained.set(authority);
            return (TransactionalAuditAppender)(scope,captured)->{
                event.set(captured);
                return authority.requireCurrent(captured).flatMap(before->{
                    permits.add(before.validUntil());
                    var record=AuditRecordHasher.record(AuditCheckpoint.genesis(captured.stream()),captured,Instant.now());
                    return authority.requireCurrent(captured).map(after->{permits.add(after.validUntil());
                        return new TransactionalAuditAppender.ProvisionalAppend(record,false);});
                });
            };
        });
        return resources;
    }
    @Test void currentPermissionIsCheckedBeforeAndAfterControlledAppendAndAtPairOnOnePhysicalRoot() {
        var f=new LedgerMutationAuditCaptureTest.Fixture();var permits=new ArrayList<Instant>();
        var audit=appender(f,controlledFactory(f,new AtomicReference<>(),new AtomicReference<>(),permits));
        assertEquals(LedgerMutationAuditCaptureTest.id(22),f.r.run(scope->audit.apply(scope,f.mutation)));
        verify(f.admission,times(4)).requireCurrent(f.retry,f.evidence);
        assertEquals(2,permits.size());assertFalse(permits.get(1).isAfter(permits.getFirst()));
        assertEquals(1,f.r.driver.creates);assertEquals(1,f.r.driver.begins);assertEquals(1,f.r.driver.commits);
        assertEquals(0,f.r.driver.rollbacks);assertEquals(1,f.r.driver.closes);
    }
    @Test void differentCurrentRetryExecutorNeverRewritesTheOriginalCapturedEvent() {
        var f=new LedgerMutationAuditCaptureTest.Fixture();var event=new AtomicReference<AuditEvent>();
        var audit=appender(f,controlledFactory(f,new AtomicReference<>(),event,new ArrayList<>()));
        f.r.run(scope->audit.apply(scope,f.mutation));
        assertEquals(f.original,event.get().actor());assertNotEquals(f.retry.subject(),event.get().actor().subject());
        assertEquals(Instant.ofEpochMilli(f.originalCapture),event.get().occurredAt());
        assertEquals("original-policy",event.get().policyRevision());
    }
    @Test void escapedAuthorityCannotReadOrAuthorizeAfterItsOriginalRootHasCompleted() {
        var f=new LedgerMutationAuditCaptureTest.Fixture();var authority=new AtomicReference<AuditAppendAuthority>();
        var event=new AtomicReference<AuditEvent>();var audit=appender(f,controlledFactory(f,authority,event,new ArrayList<>()));
        f.r.run(scope->audit.apply(scope,f.mutation));
        assertThrows(IllegalStateException.class,()->authority.get().requireCurrent(event.get()).block());
        verify(f.admission,times(4)).requireCurrent(f.retry,f.evidence);assertEquals(1,f.r.driver.creates);
    }
    @Test void missingOriginalAnchorRefusesBeforeAnySharedAppenderCanBeConstructed() {
        var f=new LedgerMutationAuditCaptureTest.Fixture();var factory=controlledFactory(f,new AtomicReference<>(),new AtomicReference<>(),new ArrayList<>());
        doReturn(Mono.empty()).when(f.nativeOps).requireAuditCapture(any(),any(),any(),any(),any(),any(),any(),any(),any());
        var audit=appender(f,factory);assertThrows(RootTransactionException.class,()->f.r.run(scope->audit.apply(scope,f.mutation)));
        verify(factory,never()).audit(any(),any(),any());assertEquals(0,f.r.driver.commits);assertEquals(1,f.r.driver.rollbacks);
    }
    @Test void changedDurableEvidenceCannotAuthorizeThePreviouslyCapturedEvent() {
        var f=new LedgerMutationAuditCaptureTest.Fixture();var changed=new LedgerAuditEvidence(f.evidence.action(),f.evidence.ownerTenant(),
                f.evidence.resourceType(),f.evidence.resourceId(),f.evidence.processId(),f.evidence.commandKey(),"b".repeat(64),null,null,null);
        when(f.records.observe(any(),same(f.mutation),any())).thenReturn(Mono.just(f.evidence),Mono.just(changed));
        var audit=appender(f,controlledFactory(f,new AtomicReference<>(),new AtomicReference<>(),new ArrayList<>()));
        assertThrows(RootTransactionException.class,()->f.r.run(scope->audit.apply(scope,f.mutation)));
        verify(f.nativeOps,never()).pairAuditCapture(any(),any(),anyLong(),any(),any(),anyBoolean());
        assertEquals(0,f.r.driver.commits);assertEquals(1,f.r.driver.rollbacks);
    }
    @Test void permissionRevokedAfterControlledAppendStillRollsBackBeforePairing() {
        var f=new LedgerMutationAuditCaptureTest.Fixture();var calls=new AtomicInteger();
        when(f.admission.requireCurrent(any(),any())).thenAnswer(i->calls.incrementAndGet()<3
                ?Mono.just(new LedgerAuditAdmission.Observation(f.retry,LedgerMutationAuditCaptureTest.id(20),LedgerMutationAuditCaptureTest.id(21),f.original,"original-policy",Instant.now().plusSeconds(3)))
                :Mono.empty());
        var audit=appender(f,controlledFactory(f,new AtomicReference<>(),new AtomicReference<>(),new ArrayList<>()));
        assertThrows(RootTransactionException.class,()->f.r.run(scope->audit.apply(scope,f.mutation)));
        verify(f.nativeOps,never()).pairAuditCapture(any(),any(),anyLong(),any(),any(),anyBoolean());
        assertEquals(3,calls.get());assertEquals(0,f.r.driver.commits);assertEquals(1,f.r.driver.rollbacks);
    }
    @Test void lateNativePairRefusalMarksTheSameRootRollbackOnlyEvenWhenCallerRecovers() {
        var f=new LedgerMutationAuditCaptureTest.Fixture();when(f.nativeOps.pairAuditCapture(any(),any(),anyLong(),any(),any(),anyBoolean())).thenReturn(Mono.empty());
        var audit=appender(f,controlledFactory(f,new AtomicReference<>(),new AtomicReference<>(),new ArrayList<>()));
        var now=f.r.clock.instant();var budget=R2dbcOperationBudget.observed(now,now.plusSeconds(5),f.r.nanos.getAsLong(),Duration.ofSeconds(5),f.r.clock,f.r.nanos);
        var completion=f.r.root.execute(budget,new DefaultTransactionDefinition(),scope->audit.apply(scope,f.mutation)
                .onErrorReturn(LedgerMutationAuditCaptureTest.id(99))).block();
        assertFalse(completion.transactionFacts().commitAcknowledged());assertTrue(completion.transactionFacts().rollbackAcknowledged());
        assertEquals(0,f.r.driver.commits);assertEquals(1,f.r.driver.rollbacks);
    }
    @Test void anotherOwningResourceGraphCannotUseThisAppender() {
        var f=new LedgerMutationAuditCaptureTest.Fixture();var other=new LedgerAuditRecordServiceTest.Resources();
        var audit=appender(f,controlledFactory(f,new AtomicReference<>(),new AtomicReference<>(),new ArrayList<>()));
        assertThrows(RootTransactionException.class,()->other.run(scope->audit.apply(scope,f.mutation)));
        verifyNoInteractions(f.records,f.admission,f.nativeOps);assertEquals(0,f.r.driver.creates);assertEquals(1,other.driver.rollbacks);
    }
    @Test void actualSharedAppenderUsesTheBoundCurrentPermissionAndRefusesBeforeNativeEnter() {
        var f=new LedgerMutationAuditCaptureTest.Fixture();var calls=new AtomicInteger();
        when(f.admission.requireCurrent(any(),any())).thenAnswer(i->calls.incrementAndGet()==1
                ?Mono.just(new LedgerAuditAdmission.Observation(f.retry,LedgerMutationAuditCaptureTest.id(20),LedgerMutationAuditCaptureTest.id(21),f.original,"original-policy",Instant.now().plusSeconds(3)))
                :Mono.empty());
        var resources=new LedgerOwningMutationResources(f.r.factory,f.r.client,f.r.template,f.r.manager);
        var audit=appender(f,resources);
        assertThrows(RootTransactionException.class,()->f.r.run(scope->audit.apply(scope,f.mutation)));
        assertEquals(2,calls.get());assertEquals(1,f.r.driver.begins);assertEquals(1,f.r.driver.rollbacks);assertEquals(0,f.r.driver.commits);
    }
    @Test void anotherEventIsRefusedBeforeAnyAuthorityReadsAndRecoveryCannotMakeItsRootCommit() {
        var f=new LedgerMutationAuditCaptureTest.Fixture();var resources=mock(LedgerOwningMutationResources.class);
        when(resources.bounded()).thenReturn(f.r.resources);
        when(resources.audit(any(),any(),any())).thenAnswer(i->{AuditAppendAuthority authority=i.getArgument(0);
            return (TransactionalAuditAppender)(scope,event)->{
                var other=new AuditEvent(LedgerMutationAuditCaptureTest.id(77),event.stream(),event.actor(),event.action(),event.outcome(),
                        event.processId(),event.correlationId(),event.causationId(),event.occurredAt(),event.evidenceDigest(),event.policyRevision());
                var original=new TransactionalAuditAppender.ProvisionalAppend(AuditRecordHasher.record(AuditCheckpoint.genesis(event.stream()),event,Instant.now()),false);
                return authority.requireCurrent(other).map(permit->original).onErrorReturn(original);
            };
        });
        var audit=appender(f,resources);f.r.run(scope->audit.apply(scope,f.mutation));
        verify(f.records,times(2)).observe(any(),same(f.mutation),any());
        assertEquals(0,f.r.driver.commits);assertEquals(1,f.r.driver.rollbacks);
    }
    @Test void anAdditionalSuppliedAuthorityCannotBeSilentlyBypassed() {
        var f=new LedgerMutationAuditCaptureTest.Fixture();var external=mock(AuditAppendAuthority.class);
        when(external.requireCurrent(any())).thenReturn(Mono.empty());
        var audit=appender(f,controlledFactory(f,new AtomicReference<>(),new AtomicReference<>(),new ArrayList<>()),external);
        assertThrows(RootTransactionException.class,()->f.r.run(scope->audit.apply(scope,f.mutation)));
        verify(external).requireCurrent(any());verify(f.nativeOps,never()).pairAuditCapture(any(),any(),anyLong(),any(),any(),anyBoolean());
        assertEquals(0,f.r.driver.commits);assertEquals(1,f.r.driver.rollbacks);
    }
    @Test void explicitlySuppliedAuthorityOnlyNarrowsTheVerifiedOriginalNativePermit() {
        var f=new LedgerMutationAuditCaptureTest.Fixture();var external=mock(AuditAppendAuthority.class);
        var deadline=Instant.now().plusMillis(1500);var permits=new ArrayList<Instant>();
        when(external.requireCurrent(any())).thenAnswer(i->{AuditEvent event=i.getArgument(0);
            return Mono.just(new AuditAppendAuthority.Permit(event.stream(),event.actor(),deadline));});
        var audit=appender(f,controlledFactory(f,new AtomicReference<>(),new AtomicReference<>(),permits),external);
        f.r.run(scope->audit.apply(scope,f.mutation));
        verify(external,times(2)).requireCurrent(any());assertEquals(List.of(deadline,deadline),permits);
        assertEquals(1,f.r.driver.commits);
    }
}
