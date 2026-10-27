package io.fireflysoftware.core.banking.ledger.core.repositories;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.banking.ledger.core.mappers.core.TransactionMapper;
import io.fireflysoftware.core.banking.ledger.core.mappers.leg.TransactionLegMapper;
import io.fireflysoftware.core.banking.ledger.core.services.core.*;
import io.fireflysoftware.core.banking.ledger.core.services.core.impl.LedgerPostingServiceImpl;
import io.fireflysoftware.core.banking.ledger.core.services.funds.*;
import io.fireflysoftware.core.banking.ledger.core.services.funds.impl.FundsAuthorityImpl;
import io.fireflysoftware.core.banking.ledger.core.services.funds.impl.FundsDatabaseContext;
import io.fireflysoftware.core.banking.ledger.core.services.funds.impl.FundsMoneyServiceImpl;
import io.fireflysoftware.core.banking.ledger.core.services.funds.impl.FundsReceiptServiceImpl;
import io.fireflysoftware.core.banking.ledger.core.services.funds.impl.FundsReconciliationServiceImpl;
import io.fireflysoftware.core.banking.ledger.models.repositories.funds.*;
import io.r2dbc.spi.*;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.context.annotation.*;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import reactor.core.publisher.Mono;

class RepositoryAssemblyTest {
    @Configuration(proxyBeanMethods = false)
    @EnableR2dbcRepositories(
            basePackages = "io.fireflysoftware.core.banking.ledger.models.repositories")
    @EnableTransactionManagement
    static class Database extends AbstractR2dbcConfiguration {
        private final ConnectionFactory factory =
                new ConnectionFactory() {
                    public Publisher<? extends Connection> create() {
                        return Mono.error(
                                new AssertionError("Assembly must not connect to a database"));
                    }

                    public ConnectionFactoryMetadata getMetadata() {
                        return () -> "PostgreSQL";
                    }
                };

        @Bean
        public ConnectionFactory connectionFactory() {
            return factory;
        }

        @Bean
        R2dbcTransactionManager transactionManager() {
            return new R2dbcTransactionManager(factory);
        }

        @Bean
        TransactionMapper transactionMapper() {
            return mock(TransactionMapper.class);
        }

        @Bean
        TransactionLegMapper transactionLegMapper() {
            return mock(TransactionLegMapper.class);
        }

        @Bean
        AccountEnrollmentVerifier accountEnrollmentVerifier() {
            return mock(AccountEnrollmentVerifier.class);
        }
    }

    @Test
    void everyRepositoryAndFinancialInterfaceAssemblesAgainstOneFactoryWithoutConnecting() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(Database.class, FundsRepositories.class, FundsDatabaseContext.class);
            context.registerBean("fundsAuthority", FundsAuthorityImpl.class);
            context.registerBean("fundsMoneyService", FundsMoneyServiceImpl.class);
            context.registerBean("fundsReceiptService", FundsReceiptServiceImpl.class);
            context.registerBean(
                    "fundsReconciliationService", FundsReconciliationServiceImpl.class);
            context.registerBean("ledgerPostingService", LedgerPostingServiceImpl.class);
            context.refresh();
            assertThat(context.getBean(FundsAuthority.class))
                    .isSameAs(context.getBean("fundsAuthority"));
            assertThat(context.getBean(FundsMoneyService.class))
                    .isSameAs(context.getBean("fundsMoneyService"));
            assertThat(context.getBean(FundsReceiptService.class))
                    .isSameAs(context.getBean("fundsReceiptService"));
            assertThat(context.getBean(FundsReconciliationService.class))
                    .isSameAs(context.getBean("fundsReconciliationService"));
            assertThat(context.getBean(LedgerPostingService.class))
                    .isSameAs(context.getBean("ledgerPostingService"));
            var factory = context.getBean(ConnectionFactory.class);
            assertThat(
                            context.getBean(R2dbcEntityTemplate.class)
                                    .getDatabaseClient()
                                    .getConnectionFactory())
                    .isSameAs(factory);
            assertThat(context.getBean(R2dbcTransactionManager.class).getConnectionFactory())
                    .isSameAs(factory);
        }
    }
}
