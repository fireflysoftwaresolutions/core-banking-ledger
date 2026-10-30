package io.fireflysoftware.core.banking.ledger.core.services.funds.ownership;

import static org.junit.jupiter.api.Assertions.*;
import io.fireflysoftware.core.banking.ledger.core.services.core.LedgerPostingService;
import io.fireflysoftware.core.banking.ledger.models.entities.funds.FundsAuditAnchor;
import org.fireflyframework.kernel.audit.AuditActor;
import org.fireflyframework.security.spi.SecurityContextPort;
import reactor.test.StepVerifier;
import io.fireflysoftware.core.banking.ledger.core.services.funds.FundsMoneyService;
import io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.impl.LedgerOwningMutationServiceImpl;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.annotation.*;
import reactor.core.publisher.Mono;

class LedgerFundsFirstCaptureTest {
    static final UUID ID=LedgerOwningMutationServiceTest.ID;
    static final String TENANT="platform_tenant";
    static final Reserve INPUT=new Reserve(ID,new UUID(0,2),new UUID(0,3),BigDecimal.ONE,"EUR",30);
    static final SecurityPrincipal PRINCIPAL=SecurityPrincipal.builder().issuer("unit-issuer").subject("unit-subject").tenantId(TENANT)
            .scopes(Set.of("funds:reserve")).claims(Map.of("exp",Instant.now().plusSeconds(3600),"funds_accounts",List.of(INPUT.sourceAccountId().toString(),INPUT.destinationAccountId().toString()))).build();
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement
    static class Advice {
        @Bean FundsMoneyService fundsMoneyService(LedgerPostingService postings,AtomicInteger calls){return new ReserveMoney(postings,calls);}
    }
    // Real REQUIRED advice and original posting proxy; controlled rows prove source composition only.
    static class ReserveMoney implements FundsMoneyService {
        final LedgerPostingService postings;final AtomicInteger calls;
        ReserveMoney(LedgerPostingService postings,AtomicInteger calls){this.postings=postings;this.calls=calls;}
        @Transactional public Mono<Reservation> reserve(SecurityPrincipal p,String tenant,Reserve input){return Mono.defer(()->{calls.incrementAndGet();return postings.confirm(ID).map(value->new Reservation(ID,input.operationId(),input.sourceAccountId(),input.destinationAccountId(),input.amount(),input.currency(),ReservationState.HELD,Instant.now().plusSeconds(30),ID,null,1));});}
        public Mono<FundingReceipt> fund(SecurityPrincipal p,String t,Funding i){return Mono.error(new UnsupportedOperationException());}
        public Mono<Reservation> action(SecurityPrincipal p,String t,UUID id,Command i){return Mono.error(new UnsupportedOperationException());}
        public Mono<Reservation> reservation(SecurityPrincipal p,String t,UUID id){return Mono.error(new UnsupportedOperationException());}
        public Mono<Reservation> reservationByOperation(SecurityPrincipal p,String t,UUID id){return Mono.error(new UnsupportedOperationException());}
    }
    static final class Fixture implements AutoCloseable {
        final LedgerOwningMutationServiceTest.Fixture original=new LedgerOwningMutationServiceTest.Fixture();
        final AnnotationConfigApplicationContext context=new AnnotationConfigApplicationContext();final AtomicInteger money=new AtomicInteger(),audit=new AtomicInteger();
        final BoundedR2dbcResources resources;final List<String> phases=new ArrayList<>();boolean auditFailure;
        Fixture(){context.registerBean(R2dbcTransactionManager.class,()->original.context.getBean(R2dbcTransactionManager.class));context.registerBean(LedgerPostingService.class,()->original.context.getBean(LedgerPostingService.class));context.registerBean(AtomicInteger.class,()->money);context.register(Advice.class);context.refresh();resources=BoundedR2dbcResources.verify(original.factory,original.client,original.operations,original.context.getBean(R2dbcTransactionManager.class));}
        LedgerOwningMutationService service(){return new LedgerOwningMutationServiceImpl(resources,original.context.getBean(LedgerPostingService.class),context.getBean(FundsMoneyService.class),(scope,mutation)->original.factory.requireBoundScope(resources,scope).then(Mono.fromRunnable(audit::incrementAndGet)).thenReturn(ID),new CleanupPolicy(Duration.ofSeconds(2),Duration.ofSeconds(2)));}
        LedgerOwningMutationService admitted(ControlledProvider provider,SecurityContextPort principals){return admitted(provider,principals,new LedgerMutationTime(original.clock,original.nanos));}
        LedgerOwningMutationService admitted(ControlledProvider provider,SecurityContextPort principals,LedgerMutationTime time){
            return new LedgerOwningMutationServiceImpl(resources,original.context.getBean(LedgerPostingService.class),context.getBean(FundsMoneyService.class),
                (scope,mutation)->original.factory.requireBoundScope(resources,scope).then(Mono.defer(()->{phases.add("audit");audit.incrementAndGet();return auditFailure?Mono.error(new IllegalStateException("CONTROLLED_LATE_AUDIT_REFUSAL")):Mono.just(ID);})),
                new CleanupPolicy(Duration.ofSeconds(2),Duration.ofSeconds(2)),provider,principals,
                (scope,mutation,tenant)->original.factory.requireBoundScope(resources,scope).then(Mono.defer(()->{phases.add("observe");assertEquals(1,money.get());return Mono.just(evidence());})),time);
        }
        R2dbcOperationBudget longBudget(){var now=original.clock.instant();return R2dbcOperationBudget.observed(now,now.plusSeconds(20),original.nanos.getAsLong(),Duration.ofSeconds(20),original.clock,original.nanos);}
        public void close(){context.close();original.close();}
    }
    @Test void firstCaptureCannotReachMoneyWithoutAdmission(){try(var f=new Fixture()){
        assertThrows(IllegalStateException.class,()->f.service().reserveFirstCapture(f.original.budget(),PRINCIPAL,TENANT,INPUT).block());
        assertEquals(0,f.money.get());assertEquals(0,f.original.driver.creates);assertEquals(0,f.audit.get());
    }}

    static LedgerAuditEvidence evidence(){return new LedgerAuditEvidence("FUNDS_RESERVE_CAPTURED",TENANT,"funds-reservation",ID,INPUT.operationId(),INPUT.operationId().toString(),"a".repeat(64),null,null,null);}
    // Source-only provider control. It does not implement Config/PDP/native issuance or prove financial effects.
    static class ControlledProvider implements LedgerFundsFirstCaptureProvider {
        final Fixture f;R2dbcOperationBudget received;Authorization authorization;boolean absentAdmission,neverAdmission,absentWitness,badWitness,absentAnchor,badAnchor,expired;Command commandOverride;Mode mode=Mode.FIRST;int witnesses,anchors;Long nativeUntilOverride;int nativeUntilOffset=1000;CompletedCommand retainedCompleted;
        final AuditActor firstActor=new AuditActor("unit-issuer","unit-subject","unit-client",TENANT,null,null),oldActor=new AuditActor("original-issuer","original-subject","original-client",TENANT,null,null);
        ControlledProvider(Fixture f){this.f=f;}
        public Mono<Authorization> authorize(R2dbcOperationBudget original,Command command){return Mono.defer(()->{received=original;f.phases.add("authorize");if(neverAdmission)return Mono.never();if(absentAdmission)return Mono.empty();authorization=new Authorization(commandOverride==null?command:commandOverride,new UUID(0,91),mode,mode==Mode.FIRST?firstActor:oldActor,mode==Mode.FIRST?"controlled-current-policy":"controlled-original-policy","controlled-current-policy","controlled-decision",f.original.clock.instant().plusSeconds(expired?-1:3));return Mono.just(authorization);});}
        public Mono<CompletedCommand> requireCompleted(RootTransactionScope scope,Authorization authorization,LedgerMutation.Reserved mutation,LedgerAuditEvidence evidence){return f.original.factory.requireBoundScope(f.resources,scope).then(Mono.defer(()->{witnesses++;f.phases.add("witness");assertEquals(1,f.money.get());return absentWitness?Mono.empty():Mono.just(retainedCompleted=new CompletedCommand(scope,new UUID(0,92),ID,mode,mode==Mode.REPLAY?new UUID(0,93):null,badWitness?"b".repeat(64):authorization.command().intentDigest(),evidence.digest()));}));}
        public Mono<FundsAuditAnchor> requireOriginalAnchor(RootTransactionScope scope,Authorization authorization,CompletedCommand completed,LedgerAuditEvidence evidence){return f.original.factory.requireBoundScope(f.resources,scope).then(Mono.defer(()->{anchors++;f.phases.add("anchor");assertEquals(1,witnesses);assertSame(retainedCompleted,completed);assertEquals(new UUID(0,92),completed.commandId());if(absentAnchor)return Mono.empty();var a=authorization.captureActor();long now=f.original.clock.millis();return Mono.just(new FundsAuditAnchor(new UUID(0,94),new UUID(0,95),new UUID(0,93),badAnchor?new UUID(0,96):authorization.resourceTenant(),TENANT,evidence.action(),ID,evidence.processId(),evidence.commandKey(),evidence.digest(),mode==Mode.REPLAY?now-86400000:now,now,nativeUntilOverride==null?now+nativeUntilOffset:nativeUntilOverride,a.issuer(),a.subject(),a.clientId(),a.platformTenantId(),a.staffRealm(),a.staffId(),authorization.originalPolicyRevision(),null));}));}
    }
    @Test void defaultLegacyPathRemainsUnchangedWithoutFirstCaptureClaim(){try(var f=new Fixture()){assertNotNull(f.service().reserve(f.original.budget(),PRINCIPAL,TENANT,INPUT).block());assertEquals(1,f.money.get());assertEquals(1,f.original.driver.commits);}}
    @Test void missingVerifiedPrincipalRefusesBeforeAuthorityOrAcquisition(){try(var f=new Fixture()){var p=new ControlledProvider(f);assertThrows(IllegalStateException.class,()->f.admitted(p,Mono::empty).reserveFirstCapture(f.longBudget(),PRINCIPAL,TENANT,INPUT).block());assertNull(p.received);assertEquals(0,f.money.get());assertEquals(0,f.original.driver.creates);}}
    @Test void suppliedPrincipalCannotReplaceTheExactSpiPrincipal(){try(var f=new Fixture()){var p=new ControlledProvider(f);var other=PRINCIPAL.toBuilder().authorities(Set.of("different")).build();assertThrows(IllegalStateException.class,()->f.admitted(p,()->Mono.just(other)).reserveFirstCapture(f.longBudget(),PRINCIPAL,TENANT,INPUT).block());assertNull(p.received);assertEquals(0,f.original.driver.creates);}}
    @Test void originalBudgetAndCanonicalCommandReachAdmissionUnchanged(){try(var f=new Fixture()){var p=new ControlledProvider(f);var budget=f.longBudget();assertNotNull(f.admitted(p,()->Mono.just(PRINCIPAL)).reserveFirstCapture(budget,PRINCIPAL,TENANT,INPUT).block());assertSame(budget,p.received);assertEquals(io.fireflysoftware.core.banking.ledger.interfaces.protocol.receipt.FundsReceiptCodec.canonicalIntent(TENANT,INPUT).canonical(),p.authorization.command().canonicalIntent());assertEquals(io.fireflysoftware.core.banking.ledger.interfaces.protocol.receipt.FundsReceiptCodec.reserveOperationFingerprint(INPUT),p.authorization.command().operationFingerprint());assertEquals(List.of("authorize","observe","witness","anchor","audit"),f.phases);assertEquals(1,f.original.driver.begins);assertEquals(1,f.original.driver.commits);assertEquals(0,f.original.driver.rollbacks);}}
    @Test void neverReturningPrincipalCannotConsumeTheLongOriginalBudget(){try(var f=new Fixture()){var p=new ControlledProvider(f);StepVerifier.withVirtualTime(()->f.admitted(p,Mono::never).reserveFirstCapture(f.longBudget(),PRINCIPAL,TENANT,INPUT)).thenAwait(Duration.ofSeconds(6)).expectError(java.util.concurrent.TimeoutException.class).verify();assertNull(p.received);assertEquals(0,f.original.driver.creates);}}
    @Test void neverReturningAdmissionSharesTheOriginalFiveSecondCap(){try(var f=new Fixture()){var p=new ControlledProvider(f);p.neverAdmission=true;StepVerifier.withVirtualTime(()->f.admitted(p,()->Mono.just(PRINCIPAL)).reserveFirstCapture(f.longBudget(),PRINCIPAL,TENANT,INPUT)).thenAwait(Duration.ofSeconds(6)).expectError(java.util.concurrent.TimeoutException.class).verify();assertNotNull(p.received);assertEquals(0,f.money.get());assertEquals(0,f.original.driver.creates);}}
    @Test void absentCurrentAuthorityRefusesBeforeBusiness(){try(var f=new Fixture()){var p=new ControlledProvider(f);p.absentAdmission=true;assertThrows(IllegalStateException.class,()->f.admitted(p,()->Mono.just(PRINCIPAL)).reserveFirstCapture(f.longBudget(),PRINCIPAL,TENANT,INPUT).block());assertEquals(0,f.money.get());assertEquals(0,f.original.driver.creates);}}
    @Test void changedCanonicalCommandCannotReuseAdmission(){try(var f=new Fixture()){var p=new ControlledProvider(f);p.commandOverride=LedgerFundsFirstCaptureProvider.Command.reserve(PRINCIPAL,TENANT,new Reserve(INPUT.operationId(),INPUT.sourceAccountId(),INPUT.destinationAccountId(),BigDecimal.TEN,"EUR",30));assertThrows(IllegalStateException.class,()->f.admitted(p,()->Mono.just(PRINCIPAL)).reserveFirstCapture(f.longBudget(),PRINCIPAL,TENANT,INPUT).block());assertEquals(0,f.original.driver.creates);}}
    @Test void expiredAuthorizationCannotRenewAnOriginalBudget(){try(var f=new Fixture()){var p=new ControlledProvider(f);p.expired=true;assertThrows(IllegalArgumentException.class,()->f.admitted(p,()->Mono.just(PRINCIPAL)).reserveFirstCapture(f.longBudget(),PRINCIPAL,TENANT,INPUT).block());assertEquals(0,f.original.driver.creates);}}
    static void rolledBack(Fixture f){assertEquals(1,f.money.get());assertEquals(2,f.original.writes.get());assertEquals(1,f.original.driver.begins);assertEquals(0,f.original.driver.commits);assertEquals(1,f.original.driver.rollbacks);assertEquals(1,f.original.driver.closes);}
    @Test void absentCompletedWitnessRollsBackBeforeAnchorOrAudit(){try(var f=new Fixture()){var p=new ControlledProvider(f);p.absentWitness=true;assertThrows(RootTransactionException.class,()->f.admitted(p,()->Mono.just(PRINCIPAL)).reserveFirstCapture(f.longBudget(),PRINCIPAL,TENANT,INPUT).block());rolledBack(f);assertEquals(0,p.anchors);assertEquals(0,f.audit.get());}}
    @Test void contradictoryWitnessRollsBackBeforeAnyAnchor(){try(var f=new Fixture()){var p=new ControlledProvider(f);p.badWitness=true;assertThrows(RootTransactionException.class,()->f.admitted(p,()->Mono.just(PRINCIPAL)).reserveFirstCapture(f.longBudget(),PRINCIPAL,TENANT,INPUT).block());rolledBack(f);assertEquals(0,p.anchors);}}
    @Test void missingOriginalAnchorCannotBecomeSuccessfulCapture(){try(var f=new Fixture()){var p=new ControlledProvider(f);p.absentAnchor=true;assertThrows(RootTransactionException.class,()->f.admitted(p,()->Mono.just(PRINCIPAL)).reserveFirstCapture(f.longBudget(),PRINCIPAL,TENANT,INPUT).block());rolledBack(f);assertEquals(0,f.audit.get());}}
    @Test void resourceTenantIsSeparateAndContradictoryAnchorRefuses(){try(var f=new Fixture()){var p=new ControlledProvider(f);p.badAnchor=true;assertThrows(RootTransactionException.class,()->f.admitted(p,()->Mono.just(PRINCIPAL)).reserveFirstCapture(f.longBudget(),PRINCIPAL,TENANT,INPUT).block());rolledBack(f);assertEquals(0,f.audit.get());}}
    @Test void auditRefusalAfterWitnessAndAnchorStillRollsBackRequiredMoney(){try(var f=new Fixture()){var p=new ControlledProvider(f);f.auditFailure=true;assertThrows(RootTransactionException.class,()->f.admitted(p,()->Mono.just(PRINCIPAL)).reserveFirstCapture(f.longBudget(),PRINCIPAL,TENANT,INPUT).block());rolledBack(f);assertEquals(1,p.anchors);assertEquals(1,f.audit.get());}}
    @Test void authorizedRetryPreservesOriginalActorPolicyAndEvent(){try(var f=new Fixture()){var p=new ControlledProvider(f);p.mode=LedgerFundsFirstCaptureProvider.Mode.REPLAY;assertNotNull(f.admitted(p,()->Mono.just(PRINCIPAL)).reserveFirstCapture(f.longBudget(),PRINCIPAL,TENANT,INPUT).block());assertEquals(p.oldActor,p.authorization.captureActor());assertNotEquals(PRINCIPAL.subject(),p.authorization.captureActor().subject());assertEquals("controlled-original-policy",p.authorization.originalPolicyRevision());assertEquals(1,f.original.driver.commits);}}
    @Test void replayWithoutAnOriginalAnchorStillRefuses(){try(var f=new Fixture()){var p=new ControlledProvider(f);p.mode=LedgerFundsFirstCaptureProvider.Mode.REPLAY;p.absentAnchor=true;assertThrows(RootTransactionException.class,()->f.admitted(p,()->Mono.just(PRINCIPAL)).reserveFirstCapture(f.longBudget(),PRINCIPAL,TENANT,INPUT).block());rolledBack(f);}}
    @Test void delayedSubscriptionCannotRefreshThePreIoTechnicalCap(){try(var f=new Fixture()){
        var now=new java.util.concurrent.atomic.AtomicReference<>(Instant.now());var ticks=new java.util.concurrent.atomic.AtomicLong();
        Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now.get();}};
        java.util.function.LongSupplier nanos=ticks::get;var time=new LedgerMutationTime(clock,nanos);var budget=R2dbcOperationBudget.observed(now.get(),now.get().plusSeconds(20),0,Duration.ofSeconds(20),clock,nanos);
        var reads=new AtomicInteger();var provider=new ControlledProvider(f);var pending=f.admitted(provider,()->Mono.defer(()->{reads.incrementAndGet();return Mono.never();}),time).reserveFirstCapture(budget,PRINCIPAL,TENANT,INPUT);
        now.set(now.get().plusSeconds(6));ticks.set(Duration.ofSeconds(6).toNanos());
        assertThrows(IllegalStateException.class,()->pending.block(Duration.ofMillis(50)));assertEquals(0,reads.get());assertEquals(0,f.original.driver.creates);
    }}

    @ParameterizedTest @ValueSource(longs={Long.MIN_VALUE,0,-1,-2})
    void malformedNativeHorizonCannotReachAudit(long horizon){try(var f=new Fixture()){var p=new ControlledProvider(f);p.nativeUntilOverride=horizon;
        assertThrows(RootTransactionException.class,()->f.admitted(p,()->Mono.just(PRINCIPAL)).reserveFirstCapture(f.longBudget(),PRINCIPAL,TENANT,INPUT).block());rolledBack(f);assertEquals(0,f.audit.get());
    }}
    @Test void nonpositiveNativeMarginRefusesBeforeAudit(){for(int offset:new int[]{-1,0,1})try(var f=new Fixture()){var p=new ControlledProvider(f);p.nativeUntilOffset=offset;
        assertThrows(RootTransactionException.class,()->f.admitted(p,()->Mono.just(PRINCIPAL)).reserveFirstCapture(f.longBudget(),PRINCIPAL,TENANT,INPUT).block());rolledBack(f);assertEquals(0,f.audit.get());
    }}
    @Test void exactProtectedCommandObjectAndDistinctIdReachAnchorProvider(){try(var f=new Fixture()){var p=new ControlledProvider(f);
        assertNotNull(f.admitted(p,()->Mono.just(PRINCIPAL)).reserveFirstCapture(f.longBudget(),PRINCIPAL,TENANT,INPUT).block());assertNotEquals(INPUT.operationId(),p.retainedCompleted.commandId());assertEquals(1,p.anchors);
    }}

}
