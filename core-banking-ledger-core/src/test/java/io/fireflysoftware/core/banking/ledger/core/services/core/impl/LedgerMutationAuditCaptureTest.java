package io.fireflysoftware.core.banking.ledger.core.services.core.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.banking.ledger.core.services.core.LedgerAuditRecordService;
import io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.*;
import io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.impl.LedgerMutationAuditCaptureImpl;
import io.fireflysoftware.core.banking.ledger.models.entities.funds.FundsAuditAnchor;
import io.fireflysoftware.core.banking.ledger.models.repositories.funds.FundsNativeRepository;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.fireflyframework.core.audit.TransactionalAuditAppender.ProvisionalAppend;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.kernel.audit.*;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;

class LedgerMutationAuditCaptureTest {
    static UUID id(int n){return new UUID(0,n);}
    static final class Fixture {
        final LedgerAuditRecordServiceTest.Resources r=new LedgerAuditRecordServiceTest.Resources();
        final LedgerAuditRecordService records=mock(LedgerAuditRecordService.class);final FundsNativeRepository nativeOps=mock(FundsNativeRepository.class);final SecurityContextPort principals=mock(SecurityContextPort.class);final LedgerAuditAdmission admission=mock(LedgerAuditAdmission.class);
        final SecurityPrincipal retry=SecurityPrincipal.builder().issuer("current-issuer").subject("current-retry-executor").tenantId("platform-bank-A").claims(Map.of("exp",Instant.now().plusSeconds(60))).build();
        final AuditActor original=new AuditActor("original-capture-issuer","original-capture-actor","original-client","platform-bank-A",null,null);
        final LedgerAuditEvidence evidence=new LedgerAuditEvidence("POSTING_CREATE_CAPTURED","platform-bank-A","posting",id(5),id(5),"request-key","a".repeat(64),null,null,null);
        final LedgerMutation mutation=new LedgerMutation.Created(null,null);
        final LedgerMutationAuditCapture capture=new LedgerMutationAuditCaptureImpl(r.resources,records,nativeOps,principals,admission,r.clock,r.nanos);
        final long originalCapture=1700000000000L;
        Fixture(){
            when(principals.currentPrincipal()).thenReturn(Mono.just(retry));when(records.observe(any(),same(mutation),eq("platform-bank-A"))).thenReturn(Mono.just(evidence));
            when(admission.requireCurrent(eq(retry),eq(evidence))).thenAnswer(i->Mono.just(new LedgerAuditAdmission.Observation(retry,id(20),id(21),original,"original-policy",Instant.now().plusSeconds(3))));
            when(nativeOps.requireAuditCapture(any(),any(),any(),any(),any(),any(),any(),any(),any())).thenAnswer(i->{long now=Instant.now().toEpochMilli();return Mono.just(anchor(Math.min(now+2000,((OffsetDateTime)i.getArgument(8)).toInstant().toEpochMilli()),original));});
            when(nativeOps.pairAuditCapture(any(),any(),anyLong(),any(),any(),anyBoolean())).thenReturn(Mono.just(id(22)));
        }
        FundsAuditAnchor anchor(long until,AuditActor actor){return new FundsAuditAnchor(id(20),id(23),id(22),id(21),evidence.ownerTenant(),evidence.action(),evidence.resourceId(),evidence.processId(),evidence.commandKey(),evidence.digest(),originalCapture,Instant.now().toEpochMilli(),until,actor.issuer(),actor.subject(),actor.clientId(),actor.platformTenantId(),actor.staffRealm(),actor.staffId(),"original-policy",null);}
    }
    @Test void permittedDifferentRetryExecutorPreservesTheExistingOriginalCaptureActorPolicyAndTime(){
        var f=new Fixture();var event=f.r.run(scope->f.capture.capture(scope,f.mutation));
        assertEquals(f.original,event.actor());assertNotEquals(f.retry.subject(),event.actor().subject());assertEquals("original-policy",event.policyRevision());assertEquals(Instant.ofEpochMilli(f.originalCapture),event.occurredAt());assertEquals("platform-bank-A",event.actor().platformTenantId());assertEquals(id(21),event.stream().resourceTenantId());assertEquals(1,f.r.driver.commits);
    }
    @Test void actualSharedCanonicalRecordIsRevalidatedAndPairedOnTheOriginalRoot(){
        var f=new Fixture();UUID paired=f.r.run(scope->f.capture.capture(scope,f.mutation).flatMap(event->{var record=AuditRecordHasher.record(AuditCheckpoint.genesis(event.stream()),event,Instant.now());return f.capture.pair(scope,f.mutation,new ProvisionalAppend(record,false));}));
        assertEquals(id(22),paired);verify(f.admission,times(2)).requireCurrent(f.retry,f.evidence);verify(f.nativeOps).pairAuditCapture(eq(id(23)),eq(id(22)),eq(1L),matches("[0-9a-f]{64}"),eq(f.evidence.digest()),eq(false));assertEquals(1,f.r.driver.creates);assertEquals(1,f.r.driver.commits);
    }
    @Test void captureAndPairKeepTheSameOriginalBudgetAndNeverRenewTheNativeWindow(){
        var f=new Fixture();var windows=new ArrayList<Instant>();
        doAnswer(i->{Instant until=((OffsetDateTime)i.getArgument(8)).toInstant();windows.add(until);return Mono.just(f.anchor(Math.min(Instant.now().toEpochMilli()+2000,until.toEpochMilli()),f.original));}).when(f.nativeOps).requireAuditCapture(any(),any(),any(),any(),any(),any(),any(),any(),any());
        f.r.run(scope->{var before=scope.remaining();return f.capture.capture(scope,f.mutation).flatMap(event->{assertTrue(scope.remaining().compareTo(before)<0);return f.capture.pair(scope,f.mutation,new ProvisionalAppend(AuditRecordHasher.record(AuditCheckpoint.genesis(event.stream()),event,Instant.now()),true));});});
        assertEquals(2,windows.size());assertFalse(windows.get(1).isAfter(windows.getFirst()));verify(f.nativeOps).pairAuditCapture(any(),any(),anyLong(),any(),any(),eq(true));
    }
    @Test void missingCurrentPrincipalRefusesBeforeRecordOrNativeReads(){var f=new Fixture();when(f.principals.currentPrincipal()).thenReturn(Mono.empty());assertThrows(RootTransactionException.class,()->f.r.run(scope->f.capture.capture(scope,f.mutation)));verifyNoInteractions(f.records,f.admission,f.nativeOps);assertEquals(1,f.r.driver.rollbacks);}
    @Test void absentCurrentPermissionRefusesWithoutNativeAnchorLookup(){var f=new Fixture();when(f.admission.requireCurrent(any(),any())).thenReturn(Mono.empty());assertThrows(RootTransactionException.class,()->f.r.run(scope->f.capture.capture(scope,f.mutation)));verifyNoInteractions(f.nativeOps);assertEquals(0,f.r.driver.commits);}
    @Test void missingOriginalAnchorCannotBeReplacedByCurrentActorOrClock(){var f=new Fixture();doReturn(Mono.empty()).when(f.nativeOps).requireAuditCapture(any(),any(),any(),any(),any(),any(),any(),any(),any());assertThrows(RootTransactionException.class,()->f.r.run(scope->f.capture.capture(scope,f.mutation)));verify(f.nativeOps,never()).pairAuditCapture(any(),any(),anyLong(),any(),any(),anyBoolean());assertEquals(1,f.r.driver.rollbacks);}
    @Test void absentNativeFunctionOrAuthorityErrorRemainsARefusal(){var f=new Fixture();doReturn(Mono.error(new IllegalStateException("required owner function unavailable"))).when(f.nativeOps).requireAuditCapture(any(),any(),any(),any(),any(),any(),any(),any(),any());assertThrows(RootTransactionException.class,()->f.r.run(scope->f.capture.capture(scope,f.mutation)));assertEquals(1,f.r.driver.rollbacks);}
    @Test void emptyNativePairRollsBackInsteadOfReturningAProvisionalSuccess(){var f=new Fixture();when(f.nativeOps.pairAuditCapture(any(),any(),anyLong(),any(),any(),anyBoolean())).thenReturn(Mono.empty());assertThrows(RootTransactionException.class,()->f.r.run(scope->f.capture.capture(scope,f.mutation).flatMap(event->f.capture.pair(scope,f.mutation,new ProvisionalAppend(AuditRecordHasher.record(AuditCheckpoint.genesis(event.stream()),event,Instant.now()),false)))));assertEquals(0,f.r.driver.commits);assertEquals(1,f.r.driver.rollbacks);}
    @Test void currentPermissionMustStillExistAtPairTime(){var f=new Fixture();var calls=new AtomicInteger();when(f.admission.requireCurrent(any(),any())).thenAnswer(i->calls.incrementAndGet()==1?Mono.just(new LedgerAuditAdmission.Observation(f.retry,id(20),id(21),f.original,"original-policy",Instant.now().plusSeconds(3))):Mono.empty());assertThrows(RootTransactionException.class,()->f.r.run(scope->f.capture.capture(scope,f.mutation).flatMap(event->f.capture.pair(scope,f.mutation,new ProvisionalAppend(AuditRecordHasher.record(AuditCheckpoint.genesis(event.stream()),event,Instant.now()),false)))));verify(f.nativeOps,never()).pairAuditCapture(any(),any(),anyLong(),any(),any(),anyBoolean());}
}
