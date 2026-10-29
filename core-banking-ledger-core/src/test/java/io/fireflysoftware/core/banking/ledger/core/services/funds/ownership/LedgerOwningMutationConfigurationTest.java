package io.fireflysoftware.core.banking.ledger.core.services.funds.ownership;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.banking.ledger.core.mappers.core.TransactionMapper;
import io.fireflysoftware.core.banking.ledger.core.mappers.leg.TransactionLegMapper;
import io.fireflysoftware.core.banking.ledger.core.services.core.LedgerPostingService;
import io.fireflysoftware.core.banking.ledger.core.services.core.impl.LedgerPostingServiceImpl;
import io.fireflysoftware.core.banking.ledger.core.services.funds.FundsMoneyService;
import io.fireflysoftware.core.banking.ledger.core.services.funds.impl.*;
import io.fireflysoftware.core.banking.ledger.models.config.*;
import io.fireflysoftware.core.banking.ledger.models.repositories.funds.FundsRepositories;
import io.r2dbc.spi.ConnectionFactory;
import java.time.*;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.fireflyframework.core.audit.*;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.kernel.audit.AuditEvent;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import reactor.core.publisher.Mono;

class LedgerOwningMutationConfigurationTest {
    @Configuration(proxyBeanMethods=false)
    @EnableTransactionManagement
    @EnableR2dbcRepositories(basePackages="io.fireflysoftware.core.banking.ledger.models.repositories")
    static class OriginalRepositoryAdvice {}
    static final class Graph implements AutoCloseable {
        final LedgerOwningMutationServiceTest.Driver driver=new LedgerOwningMutationServiceTest.Driver();
        final OwnedLeaseConnectionFactory factory=new OwnedLeaseConnectionFactory(driver);
        final DatabaseClient client=DatabaseClient.builder().connectionFactory(factory).build();
        final R2dbcEntityTemplate operations=new R2dbcEntityTemplate(client,PostgresDialect.INSTANCE);
        final R2dbcTransactionManager manager=new R2dbcTransactionManager(factory);
        final Clock clock=Clock.systemUTC(); final LongSupplier nanos=System::nanoTime;
        final AnnotationConfigApplicationContext context=new AnnotationConfigApplicationContext();
        Graph(boolean enabled,boolean capture,boolean authority) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("qualification",Map.of("firefly.ledger.owning-mutations.enabled",Boolean.toString(enabled))));
            context.registerBean("connectionFactory",ConnectionFactory.class,()->factory);
            context.registerBean("databaseClient",DatabaseClient.class,()->client);
            context.registerBean("r2dbcEntityTemplate",R2dbcEntityTemplate.class,()->operations);
            context.registerBean("transactionManager",R2dbcTransactionManager.class,()->manager);
            context.registerBean(TransactionMapper.class,()->mock(TransactionMapper.class));
            context.registerBean(TransactionLegMapper.class,()->mock(TransactionLegMapper.class));
            context.register(OriginalRepositoryAdvice.class,FundsRepositories.class,FundsDatabaseContext.class);
            context.registerBean("ledgerPostingService",LedgerPostingServiceImpl.class);
            context.registerBean("fundsMoneyService",FundsMoneyServiceImpl.class);
            context.registerBean("fundsExpiryCandidateService",FundsExpiryCandidateServiceImpl.class);
            context.registerBean(LedgerMutationTime.class,()->new LedgerMutationTime(clock,nanos));
            if(capture) context.registerBean(LedgerMutationAuditCapture.class,()->new LedgerMutationAuditCapture() {
                public Mono<AuditEvent> capture(RootTransactionScope scope,LedgerMutation mutation) { return Mono.empty(); }
                public Mono<UUID> pair(RootTransactionScope scope,LedgerMutation mutation,TransactionalAuditAppender.ProvisionalAppend append) { return Mono.empty(); }
            });
            if(authority) context.registerBean(AuditAppendAuthority.class,()->event->Mono.empty());
            context.register(LedgerOwningMutationConfiguration.class);
        }
        R2dbcOperationBudget budget() { var now=clock.instant(); return R2dbcOperationBudget.observed(now,now.plusSeconds(5),nanos.getAsLong(),Duration.ofSeconds(5),clock,nanos); }
        public void close() { context.close(); }
    }
    @Test void defaultOffLeavesOriginalRepositoriesServicesAndAdviceWithoutCreatingBridge() {
        try(var g=new Graph(false,false,false)) {
            g.context.refresh();
            assertTrue(g.context.getBeansOfType(LedgerOwningMutationService.class).isEmpty());
            assertTrue(g.context.getBeansOfType(LedgerOwningExpiryService.class).isEmpty());
            assertTrue(AopUtils.isAopProxy(g.context.getBean("ledgerPostingService")));
            assertTrue(AopUtils.isAopProxy(g.context.getBean("fundsMoneyService")));
            assertEquals(0,g.driver.creates);
        }
    }
    @Test void enabledWithoutOwnerCaptureRefusesAtConstructionBeforeAcquisition() {
        try(var g=new Graph(true,false,true)) {
            assertThrows(BeanCreationException.class,g.context::refresh); assertEquals(0,g.driver.creates);
        }
    }
    @Test void enabledWithoutSharedCurrentAuthorityRefusesAtConstructionBeforeAcquisition() {
        try(var g=new Graph(true,true,false)) {
            assertThrows(BeanCreationException.class,g.context::refresh); assertEquals(0,g.driver.creates);
        }
    }
    @Test void actualTypedRepositoryAndOriginalServiceGraphConstructsWithUnavailablePortsAndPreservesEveryInstance() {
        try(var g=new Graph(true,true,true)) {
            g.context.refresh();
            assertSame(g.client,g.context.getBean("databaseClient")); assertSame(g.operations,g.context.getBean("r2dbcEntityTemplate")); assertSame(g.manager,g.context.getBean("transactionManager"));
            assertSame(g.factory,g.context.getBean(LedgerOwningMutationResources.class).bounded().factory());
            assertSame(g.manager,g.context.getBean(LedgerOwningMutationResources.class).bounded().manager());
            assertSame(g.context.getBean("ledgerPostingService"),g.context.getBean(LedgerPostingService.class));
            assertSame(g.context.getBean("fundsMoneyService"),g.context.getBean(FundsMoneyService.class));
            assertEquals(0,g.driver.creates);
            assertNotNull(g.context.getBean(LedgerOwningExpiryService.class));
            var error=assertThrows(RootTransactionException.class,()->g.context.getBean(LedgerOwningMutationService.class).fund(g.budget(),null,"tenant",null).block());
            assertTrue(error.transactionFacts().rollbackAcknowledged());
            assertEquals(1,g.driver.creates); assertEquals(1,g.driver.begins); assertEquals(1,g.driver.rollbacks); assertEquals(0,g.driver.commits); assertEquals(1,g.driver.closes);
        }
    }
    @Test void originalBootManagerNameAndRequiredAdviceRemainUsableWithoutRenamingOrReplacingIt() {
        try(var g=new Graph(true,true,true)) {
            g.context.removeBeanDefinition("transactionManager");
            g.context.registerBean("connectionFactoryTransactionManager",R2dbcTransactionManager.class,()->g.manager);
            g.context.refresh();
            assertSame(g.manager,g.context.getBean(LedgerOwningMutationResources.class).bounded().manager());
            assertThrows(RootTransactionException.class,()->g.context.getBean(LedgerOwningMutationService.class).fund(g.budget(),null,"tenant",null).block());
            assertEquals(1,g.driver.begins); assertEquals(1,g.driver.rollbacks); assertEquals(0,g.driver.commits);
        }
    }
    @Test void secondManagerCannotReplaceTheOriginalAdviceManagerGraph() {
        try(var g=new Graph(true,true,true)) {
            g.context.registerBean("anotherManager",R2dbcTransactionManager.class,()->new R2dbcTransactionManager(g.factory));
            assertThrows(BeanCreationException.class,g.context::refresh); assertEquals(0,g.driver.creates);
        }
    }
    @Test void missingOriginalTimePairCannotBeReplacedByAFreshClock() {
        try(var g=new Graph(true,true,true)) {
            g.context.removeBeanDefinition("ledgerMutationTime");
            assertThrows(BeanCreationException.class,g.context::refresh); assertEquals(0,g.driver.creates);
        }
    }
    @Test void differentOriginalClientAndTemplateCannotConstructAQualifiedGraph() {
        try(var g=new Graph(false,false,false)) {
            var different=DatabaseClient.create(g.factory);
            assertThrows(IllegalArgumentException.class,()->new LedgerOwningMutationResources(g.factory,different,g.operations,g.manager));
            assertEquals(0,g.driver.creates);
        }
    }
}
