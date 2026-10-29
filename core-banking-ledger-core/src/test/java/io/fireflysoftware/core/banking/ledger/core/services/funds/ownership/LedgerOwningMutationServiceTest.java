package io.fireflysoftware.core.banking.ledger.core.services.funds.ownership;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.banking.ledger.core.mappers.core.TransactionMapper;
import io.fireflysoftware.core.banking.ledger.core.mappers.leg.TransactionLegMapper;
import io.fireflysoftware.core.banking.ledger.core.services.core.LedgerPostingService;
import io.fireflysoftware.core.banking.ledger.core.services.core.impl.LedgerPostingServiceImpl;
import io.fireflysoftware.core.banking.ledger.core.services.funds.FundsMoneyService;
import io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.impl.LedgerOwningMutationServiceImpl;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.core.*;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.*;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.core.TransactionStatusEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.core.Transaction;
import io.fireflysoftware.core.banking.ledger.models.repositories.core.*;
import io.fireflysoftware.core.banking.ledger.models.repositories.funds.*;
import io.fireflysoftware.core.banking.ledger.models.repositories.leg.TransactionLegRepository;
import io.r2dbc.spi.*;
import java.lang.reflect.Proxy;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import org.fireflyframework.core.audit.*;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.*;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.*;

class LedgerOwningMutationServiceTest {
    static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000123");
    static final class Driver implements ConnectionFactory {
        int creates, begins, commits, rollbacks, closes;
        final Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (p,m,a) -> switch(m.getName()) {
            case "isAutoCommit" -> true;
            case "getTransactionIsolationLevel" -> IsolationLevel.READ_COMMITTED;
            case "beginTransaction" -> Mono.fromRunnable(() -> begins++);
            case "commitTransaction" -> Mono.fromRunnable(() -> commits++);
            case "rollbackTransaction" -> Mono.fromRunnable(() -> rollbacks++);
            case "close" -> Mono.fromRunnable(() -> closes++);
            case "hashCode" -> System.identityHashCode(p);
            case "equals" -> p == a[0];
            default -> Mono.empty();
        });
        public Publisher<? extends Connection> create() { return Mono.defer(() -> { creates++; return Mono.just(connection); }); }
        public ConnectionFactoryMetadata getMetadata() { return () -> "PostgreSQL"; }
    }
    @Configuration(proxyBeanMethods=false)
    @EnableTransactionManagement
    static class Advice {
        @Bean R2dbcTransactionManager transactionManager(OwnedLeaseConnectionFactory f) { return new R2dbcTransactionManager(f); }
        @Bean LedgerPostingService ledgerPostingService(FundsRepositories f, TransactionRepository t, TransactionLegRepository l, TransactionStatusHistoryRepository h, TransactionMapper tm, TransactionLegMapper lm) {
            return new LedgerPostingServiceImpl(f,t,l,h,tm,lm);
        }
        @Bean FundsMoneyService fundsMoneyService(LedgerPostingService postings) { return new JoiningMoney(postings); }
    }
    // Controlled enclosing caller proves REQUIRED composition, not financial persistence.
    static class JoiningMoney implements FundsMoneyService {
        final LedgerPostingService postings;
        JoiningMoney(LedgerPostingService postings) { this.postings=postings; }
        @Transactional public Mono<FundingReceipt> fund(SecurityPrincipal p,String tenant,Funding input) { return postings.confirm(ID).map(x -> new FundingReceipt(ID,ID)); }
        public Mono<Reservation> reserve(SecurityPrincipal p,String t,Reserve i) { return Mono.error(new UnsupportedOperationException()); }
        public Mono<Reservation> action(SecurityPrincipal p,String t,UUID id,Command i) { return Mono.error(new UnsupportedOperationException()); }
        public Mono<Reservation> reservation(SecurityPrincipal p,String t,UUID id) { return Mono.error(new UnsupportedOperationException()); }
        public Mono<Reservation> reservationByOperation(SecurityPrincipal p,String t,UUID id) { return Mono.error(new UnsupportedOperationException()); }
    }
    static final class Fixture implements AutoCloseable {
        final Driver driver=new Driver(); final OwnedLeaseConnectionFactory factory=new OwnedLeaseConnectionFactory(driver);
        final DatabaseClient client=DatabaseClient.create(factory); final R2dbcEntityTemplate operations=new R2dbcEntityTemplate(client,PostgresDialect.INSTANCE);
        final Clock clock=Clock.systemUTC(); final LongSupplier nanos=System::nanoTime;
        final AnnotationConfigApplicationContext context=new AnnotationConfigApplicationContext();
        final AtomicInteger writes=new AtomicInteger(), captures=new AtomicInteger(), currentChecks=new AtomicInteger(), pairs=new AtomicInteger();
        final LedgerOwningMutationService service;
        Fixture() { this(false); }
        Fixture(boolean actualSharedAppender) {
            var nativeOps=mock(FundsNativeRepository.class); when(nativeOps.postingAllowed(ID)).thenReturn(Mono.just(true));
            var funds=mock(FundsRepositories.class); when(funds.nativeOps()).thenReturn(nativeOps);
            var transaction=new Transaction(); transaction.setTransactionId(ID); transaction.setManagedPosting(true); transaction.setTransactionStatus(TransactionStatusEnum.PENDING);
            var transactions=mock(TransactionRepository.class); when(transactions.findByIdForUpdate(ID)).thenReturn(Mono.just(transaction));
            when(transactions.save(any())).thenAnswer(i -> Mono.fromSupplier(() -> { writes.incrementAndGet(); return i.getArgument(0); }));
            when(transactions.findById(ID)).thenReturn(Mono.just(transaction));
            var history=mock(TransactionStatusHistoryRepository.class); when(history.save(any())).thenAnswer(i -> Mono.fromSupplier(() -> { writes.incrementAndGet(); return i.getArgument(0); }));
            var legs=mock(TransactionLegRepository.class); when(legs.findAllByTransactionIdOrderByTransactionLegId(ID)).thenReturn(Flux.empty());
            var mapper=mock(TransactionMapper.class); when(mapper.toDTO(any())).thenReturn(new TransactionDTO());
            context.registerBean(OwnedLeaseConnectionFactory.class, () -> factory); context.registerBean(FundsRepositories.class, () -> funds);
            context.registerBean(TransactionRepository.class, () -> transactions); context.registerBean(TransactionStatusHistoryRepository.class, () -> history);
            context.registerBean(TransactionLegRepository.class, () -> legs); context.registerBean(TransactionMapper.class, () -> mapper);
            context.registerBean(TransactionLegMapper.class, () -> mock(TransactionLegMapper.class)); context.register(Advice.class); context.refresh();
            var manager=context.getBean(R2dbcTransactionManager.class); var resources=BoundedR2dbcResources.verify(factory,client,operations,manager);
            LedgerMutationAuditCapture capture=new LedgerMutationAuditCapture() {
                public Mono<org.fireflyframework.kernel.audit.AuditEvent> capture(RootTransactionScope scope,LedgerMutation mutation) { return factory.requireBoundScope(resources,scope).then(Mono.defer(() -> { captures.incrementAndGet(); return actualSharedAppender?Mono.just(new org.fireflyframework.kernel.audit.AuditEvent(ID,new org.fireflyframework.kernel.audit.AuditStream(ID,"ledger","posting",ID),new org.fireflyframework.kernel.audit.AuditActor("unit-issuer","unit-subject","unit-client","unit-tenant",null,null),"CONFIRM",org.fireflyframework.kernel.audit.AuditEvent.Outcome.COMPLETED,ID,ID,null,clock.instant(),"a".repeat(64),"unit-policy")):Mono.error(new IllegalStateException("CURRENT_OWNER_AUDIT_CAPTURE_REQUIRED")); })); }
                public Mono<UUID> pair(RootTransactionScope scope,LedgerMutation mutation,TransactionalAuditAppender.ProvisionalAppend append) { pairs.incrementAndGet(); return Mono.error(new IllegalStateException("OWNER_NATIVE_PAIR_REQUIRED")); }
            };
            TransactionalAuditAppender appender=actualSharedAppender?new org.fireflyframework.core.audit.impl.SpringDataTransactionalAuditAppender(resources,client,operations,manager,event -> Mono.defer(() -> { currentChecks.incrementAndGet(); return Mono.empty(); }),clock,nanos,UUID::randomUUID):mock(TransactionalAuditAppender.class);
            service=new LedgerOwningMutationServiceImpl(resources,context.getBean(LedgerPostingService.class),context.getBean(FundsMoneyService.class),appender,capture,new CleanupPolicy(Duration.ofSeconds(2),Duration.ofSeconds(2)));
        }
        R2dbcOperationBudget budget() { var now=clock.instant(); return R2dbcOperationBudget.observed(now,now.plusSeconds(5),nanos.getAsLong(),Duration.ofSeconds(5),clock,nanos); }
        public void close() { context.close(); }
    }
    @Test void realOriginalRequiredPostingProxyJoinsOuterRootAndLateCaptureRefusalRollsBackOnce() {
        try(var f=new Fixture()) {
            assertTrue(AopUtils.isAopProxy(f.context.getBean("ledgerPostingService")));
            assertTrue(AopUtils.isAopProxy(f.context.getBean("fundsMoneyService")));
            var error=assertThrows(RootTransactionException.class, () -> f.service.fund(f.budget(),null,"tenant",null).block());
            assertEquals(2,f.writes.get()); assertEquals(1,f.captures.get());
            assertEquals(1,f.driver.creates); assertEquals(1,f.driver.begins); assertEquals(0,f.driver.commits); assertEquals(1,f.driver.rollbacks); assertEquals(1,f.driver.closes);
            assertTrue(error.transactionFacts().rollbackAcknowledged());
        }
    }
    @Test void originalRequiredProxyChainCommitsOnceInsideTheActualSharedRootWithoutAuditClaim() {
        try(var f=new Fixture()) {
            var manager=f.context.getBean(R2dbcTransactionManager.class);
            var resources=BoundedR2dbcResources.verify(f.factory,f.client,f.operations,manager);
            var root=new org.fireflyframework.core.transaction.impl.DefaultBoundedRootTransaction(resources,new CleanupPolicy(Duration.ofSeconds(2),Duration.ofSeconds(2)));
            var result=root.execute(f.budget(),new org.springframework.transaction.support.DefaultTransactionDefinition(),scope ->
                f.factory.requireBoundScope(resources,scope).then(f.context.getBean(FundsMoneyService.class).fund(null,"tenant",null))).block();
            assertNotNull(result); assertTrue(result.transactionFacts().commitAcknowledged()); assertTrue(result.transactionFacts().boundaryCompleted());
            assertEquals(2,f.writes.get()); assertEquals(0,f.captures.get());
            assertEquals(1,f.driver.creates); assertEquals(1,f.driver.begins); assertEquals(1,f.driver.commits); assertEquals(0,f.driver.rollbacks); assertEquals(1,f.driver.closes);
        }
    }
    @Test void realSharedAppenderReceivesTheOriginalScopeAndUnavailableCurrentAuthorityRollsBackAllProvisionalEffects() {
        try(var f=new Fixture(true)) {
            assertThrows(RootTransactionException.class,()->f.service.fund(f.budget(),null,"tenant",null).block());
            assertEquals(2,f.writes.get()); assertEquals(1,f.captures.get()); assertEquals(1,f.currentChecks.get()); assertEquals(0,f.pairs.get());
            assertEquals(1,f.driver.creates); assertEquals(1,f.driver.begins); assertEquals(0,f.driver.commits); assertEquals(1,f.driver.rollbacks); assertEquals(1,f.driver.closes);
        }
    }
    @Test void exhaustedOriginalBudgetRefusesBeforeAcquisitionAndBusinessMutation() {
        try(var f=new Fixture()) {
            Clock old=Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneOffset.UTC);
            var expired=R2dbcOperationBudget.observed(old.instant(),old.instant().plusSeconds(1),System.nanoTime()-Duration.ofSeconds(2).toNanos(),Duration.ofSeconds(1),old,System::nanoTime);
            assertThrows(RootTransactionException.class, () -> f.service.confirm(expired,ID).block());
            assertEquals(0,f.driver.creates); assertEquals(0,f.writes.get()); assertEquals(0,f.captures.get());
        }
    }
    @Test void preexistingSpringTransactionCannotBecomeTheOwningRoot() {
        try(var f=new Fixture()) {
            var foreign=new Driver();
            assertThrows(RootTransactionException.class, () -> TransactionalOperator.create(new R2dbcTransactionManager(foreign)).transactional(f.service.confirm(f.budget(),ID)).block());
            assertEquals(0,f.driver.creates); assertEquals(0,f.writes.get()); assertEquals(1,foreign.rollbacks);
        }
    }
}
