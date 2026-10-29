package io.fireflysoftware.core.banking.ledger.core.services.funds.ownership;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.banking.ledger.core.services.core.LedgerPostingService;
import io.fireflysoftware.core.banking.ledger.core.services.funds.*;
import io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.impl.*;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.*;
import io.r2dbc.spi.*;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.fireflyframework.core.audit.*;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.context.annotation.*;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.*;

class LedgerOwningExpiryServiceTest {
    static final UUID FIRST=new UUID(0,51),SECOND=new UUID(0,52);
    static final class Time extends Clock {
        final Instant started=Instant.now(); final AtomicLong elapsed=new AtomicLong(); final LongSupplier nanos=elapsed::get;
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return started.plusNanos(elapsed.get());}
        R2dbcOperationBudget budget(){return R2dbcOperationBudget.observed(started,started.plusSeconds(5),0,Duration.ofSeconds(5),this,nanos);}
        void expire(){elapsed.set(Duration.ofSeconds(6).toNanos());}
    }
    static final class Driver implements ConnectionFactory {
        int creates,begins,commits,rollbacks,closes,failCloseAt; Runnable afterClose=()->{};
        final Connection connection=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->switch(m.getName()){
            case "isAutoCommit" -> true;
            case "getTransactionIsolationLevel" -> IsolationLevel.READ_COMMITTED;
            case "beginTransaction" -> Mono.fromRunnable(()->begins++);
            case "commitTransaction" -> Mono.fromRunnable(()->commits++);
            case "rollbackTransaction" -> Mono.fromRunnable(()->rollbacks++);
            case "close" -> Mono.defer(()->{closes++;afterClose.run();return closes==failCloseAt?Mono.error(new IllegalStateException("owned close unavailable")):Mono.empty();});
            case "hashCode" -> System.identityHashCode(p);
            case "equals" -> p==a[0];
            default -> Mono.empty();
        });
        public Publisher<? extends Connection> create(){return Mono.defer(()->{creates++;return Mono.just(connection);});}
        public ConnectionFactoryMetadata getMetadata(){return ()->"PostgreSQL";}
    }
    static final class ControlledMoney implements FundsMoneyService {
        final List<UUID> ids=new ArrayList<>(); final List<Command> commands=new ArrayList<>();
        Function<UUID,Mono<Reservation>> behavior=id->Mono.error(terminal());
        @Transactional public Mono<Reservation> action(SecurityPrincipal p,String tenant,UUID id,Command request){return Mono.defer(()->{ids.add(id);commands.add(request);return behavior.apply(id);});}
        public Mono<FundingReceipt> fund(SecurityPrincipal p,String t,Funding i){return Mono.error(new UnsupportedOperationException());}
        public Mono<Reservation> reserve(SecurityPrincipal p,String t,Reserve i){return Mono.error(new UnsupportedOperationException());}
        public Mono<Reservation> reservation(SecurityPrincipal p,String t,UUID id){return Mono.error(new UnsupportedOperationException());}
        public Mono<Reservation> reservationByOperation(SecurityPrincipal p,String t,UUID id){return Mono.error(new UnsupportedOperationException());}
    }
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement static class Advice {}
    static ResponseStatusException terminal(){return new ResponseStatusException(HttpStatus.CONFLICT,"RESERVATION_IS_TERMINAL");}
    static Reservation provisional(UUID id){return new Reservation(id,id,FIRST,SECOND,BigDecimal.ONE,"USD",ReservationState.EXPIRED,Instant.now(),id,null,1);}
    static final class Fixture implements AutoCloseable {
        final Time time=new Time();final R2dbcOperationBudget original=time.budget();final Driver driver=new Driver();
        final OwnedLeaseConnectionFactory factory=new OwnedLeaseConnectionFactory(driver);final DatabaseClient client=DatabaseClient.create(factory);
        final R2dbcEntityTemplate operations=new R2dbcEntityTemplate(client,PostgresDialect.INSTANCE);final R2dbcTransactionManager manager=new R2dbcTransactionManager(factory);
        final BoundedR2dbcResources resources=BoundedR2dbcResources.verify(factory,client,operations,manager);
        final ControlledMoney controlled=new ControlledMoney();final FundsExpiryCandidateService candidates=mock(FundsExpiryCandidateService.class);
        final AnnotationConfigApplicationContext context=new AnnotationConfigApplicationContext();
        final AtomicInteger captures=new AtomicInteger(); final LedgerOwningMutationService mutations;final LedgerOwningExpiryService expiry;
        final SecurityPrincipal principal=SecurityPrincipal.builder().subject("expiry-unit").tenantId("owned").scopes(Set.of("funds:expire")).claims(Map.of("exp",Instant.now().plusSeconds(120),"funds_accounts",List.of(FIRST.toString(),SECOND.toString()))).build();
        Fixture(){
            context.registerBean("transactionManager",R2dbcTransactionManager.class,()->manager);context.registerBean("fundsMoneyService",FundsMoneyService.class,()->controlled);context.register(Advice.class);context.refresh();
            LedgerMutationAuditCapture capture=new LedgerMutationAuditCapture(){
                public Mono<org.fireflyframework.kernel.audit.AuditEvent> capture(RootTransactionScope scope,LedgerMutation mutation){return factory.requireBoundScope(resources,scope).then(Mono.defer(()->{captures.incrementAndGet();return Mono.error(terminal());}));}
                public Mono<UUID> pair(RootTransactionScope scope,LedgerMutation mutation,TransactionalAuditAppender.ProvisionalAppend append){return Mono.error(new IllegalStateException("No current native coupling provider"));}
            };
            mutations=spy(new LedgerOwningMutationServiceImpl(resources,mock(LedgerPostingService.class),context.getBean(FundsMoneyService.class),mock(TransactionalAuditAppender.class),capture,new CleanupPolicy(Duration.ofSeconds(2),Duration.ofSeconds(2))));
            expiry=new LedgerOwningExpiryServiceImpl(resources,candidates,mutations,new CleanupPolicy(Duration.ofSeconds(2),Duration.ofSeconds(2)));
            when(candidates.select(eq(principal),eq("owned"),any())).thenAnswer(invocation->org.springframework.transaction.reactive.TransactionSynchronizationManager.forCurrentTransaction().map(sync->{assertTrue(sync.isActualTransactionActive());return List.of(FIRST,SECOND);}));
        }
        public void close(){context.close();}
    }
    @Test void selectionCommitsBeforeTwoSeparateOriginalAdviceRollbacksAndSameBudgetReachesBothActions(){
        try(var f=new Fixture()){
            f.controlled.behavior=id->{assertEquals(1,f.driver.commits);assertEquals(f.controlled.ids.size(),f.driver.closes);return Mono.error(terminal());};
            assertEquals(List.of(),f.expiry.expire(f.original,f.principal,"owned",new ExpiryBatch(2)).block());
            assertEquals(List.of(FIRST,SECOND),f.controlled.ids);assertEquals(3,f.driver.begins);assertEquals(1,f.driver.commits);assertEquals(2,f.driver.rollbacks);assertEquals(3,f.driver.closes);assertEquals(0,f.captures.get());
            for(int index=0;index<2;index++){
                UUID id=f.controlled.ids.get(index),expected=UUID.nameUUIDFromBytes(("funds-expiry:owned:"+id).getBytes(StandardCharsets.UTF_8));
                assertEquals(new Command(expected,Action.EXPIRE),f.controlled.commands.get(index));
                verify(f.mutations).action(same(f.original),same(f.principal),eq("owned"),eq(id),eq(new Command(expected,Action.EXPIRE)));
            }
        }
    }
    @Test void ordinarySelectionFailureStopsBeforeAnyActionAndRollsBackSelection(){
        try(var f=new Fixture()){
            when(f.candidates.select(any(),any(),any())).thenReturn(Mono.error(new IllegalStateException("selection unavailable")));
            assertThrows(RootTransactionException.class,()->f.expiry.expire(f.original,f.principal,"owned",new ExpiryBatch(2)).block());
            assertEquals(List.of(),f.controlled.ids);assertEquals(0,f.driver.commits);assertEquals(1,f.driver.rollbacks);assertEquals(1,f.driver.closes);
        }
    }
    @Test void exhaustedOriginalBudgetRefusesBeforeSelectionAcquisition(){
        try(var f=new Fixture()){
            f.time.expire();assertThrows(RootTransactionException.class,()->f.expiry.expire(f.original,f.principal,"owned",new ExpiryBatch(2)).block());
            assertEquals(0,f.driver.creates);assertEquals(List.of(),f.controlled.ids);verifyNoInteractions(f.candidates);
        }
    }
    @Test void timeSpentOnFirstItemCannotRenewTheOriginalBudgetForTheSecondItem(){
        try(var f=new Fixture()){
            f.driver.afterClose=()->{if(f.driver.closes==2)f.time.expire();};
            assertThrows(RootTransactionException.class,()->f.expiry.expire(f.original,f.principal,"owned",new ExpiryBatch(2)).block());
            assertEquals(List.of(FIRST),f.controlled.ids);assertEquals(2,f.driver.creates);assertEquals(1,f.driver.commits);assertEquals(1,f.driver.rollbacks);
        }
    }
    @Test void originalBudgetStillMustBeLiveAfterTheLastSkippedTerminalItem(){
        try(var f=new Fixture()){
            when(f.candidates.select(any(),any(),any())).thenReturn(Mono.just(List.of(FIRST)));
            f.driver.afterClose=()->{if(f.driver.closes==2)f.time.expire();};
            var error=assertThrowsExactly(IllegalStateException.class,()->f.expiry.expire(f.original,f.principal,"owned",new ExpiryBatch(1)).block());
            assertEquals("Original operation budget exhausted",error.getMessage());
            assertEquals(List.of(FIRST),f.controlled.ids);assertEquals(1,f.driver.commits);assertEquals(1,f.driver.rollbacks);
        }
    }
    @Test void terminalBusinessRefusalWithIncompleteCleanupCannotBeSkipped(){
        try(var f=new Fixture()){
            f.driver.failCloseAt=2;
            var error=assertThrows(RootTransactionException.class,()->f.expiry.expire(f.original,f.principal,"owned",new ExpiryBatch(2)).block());
            assertEquals(CleanupFact.INCOMPLETE,error.cleanupFact());assertEquals(List.of(FIRST),f.controlled.ids);assertEquals(2,f.driver.creates);assertEquals(1,f.driver.commits);
        }
    }
    @Test void generic409CannotBeSkippedAsTerminal(){
        try(var f=new Fixture()){
            f.controlled.behavior=id->Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,"RESERVATION_NOT_DUE"));
            assertThrows(RootTransactionException.class,()->f.expiry.expire(f.original,f.principal,"owned",new ExpiryBatch(2)).block());
            assertEquals(List.of(FIRST),f.controlled.ids);assertEquals(1,f.driver.commits);assertEquals(1,f.driver.rollbacks);
        }
    }
    @Test void sameTerminalTextFromAuditCaptureCannotBecomeABusinessTerminalMarker(){
        try(var f=new Fixture()){
            f.controlled.behavior=id->Mono.just(provisional(id));
            assertThrows(RootTransactionException.class,()->f.expiry.expire(f.original,f.principal,"owned",new ExpiryBatch(2)).block());
            assertEquals(List.of(FIRST),f.controlled.ids);assertEquals(1,f.captures.get());assertEquals(1,f.driver.commits);assertEquals(1,f.driver.rollbacks);
        }
    }
    @Test void differentActionCannotBecomeAnExpiryTerminalMarker(){
        try(var f=new Fixture()){
            assertThrows(RootTransactionException.class,()->f.mutations.action(f.original,f.principal,"owned",FIRST,new Command(FIRST,Action.RELEASE)).block());
            assertEquals(0,f.driver.commits);assertEquals(1,f.driver.rollbacks);
        }
    }
    @Test void exactOriginalTerminalExpiryHasOnlyObservedRollbackAndCleanupFacts(){
        try(var f=new Fixture()){
            var error=assertThrows(LedgerTerminalExpiryException.class,()->f.mutations.action(f.original,f.principal,"owned",FIRST,new Command(SECOND,Action.EXPIRE)).block());
            assertEquals(FIRST,error.reservationId());assertEquals(SECOND,error.actionId());assertTrue(error.transactionFacts().rollbackAcknowledged());assertFalse(error.transactionFacts().commitAcknowledged());assertEquals(CleanupFact.COMPLETE,error.cleanupFact());assertNull(error.getCause());
        }
    }
    @Test void separateSubscriptionsCannotReuseAnEarlierTerminalBusinessFailure() {
        try(var f=new Fixture()) {
            var selected=f.mutations.action(f.original,f.principal,"owned",FIRST,new Command(SECOND,Action.EXPIRE));
            assertThrows(LedgerTerminalExpiryException.class,selected::block);
            f.controlled.behavior=id->Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,"RESERVATION_NOT_DUE"));
            assertThrows(RootTransactionException.class,selected::block);
            assertEquals(2,f.driver.begins);assertEquals(2,f.driver.rollbacks);assertEquals(0,f.driver.commits);assertEquals(0,f.captures.get());
        }
    }
}
