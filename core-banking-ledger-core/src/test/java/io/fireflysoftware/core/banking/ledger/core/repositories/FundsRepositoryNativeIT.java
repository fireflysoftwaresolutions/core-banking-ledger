package io.fireflysoftware.core.banking.ledger.core.repositories;

import io.fireflysoftware.core.banking.ledger.interfaces.protocol.receipt.FundsReceiptCodec;

import static org.assertj.core.api.Assertions.*;

import io.fireflysoftware.core.banking.ledger.core.mappers.core.TransactionMapper;
import io.fireflysoftware.core.banking.ledger.core.mappers.leg.TransactionLegMapper;
import io.fireflysoftware.core.banking.ledger.core.services.core.*;
import io.fireflysoftware.core.banking.ledger.core.services.core.impl.LedgerPostingServiceImpl;
import io.fireflysoftware.core.banking.ledger.core.services.funds.*;
import io.fireflysoftware.core.banking.ledger.core.services.funds.impl.FundsDatabaseContext;
import io.fireflysoftware.core.banking.ledger.core.services.funds.impl.FundsMoneyServiceImpl;
import io.fireflysoftware.core.banking.ledger.core.services.funds.impl.FundsReceiptServiceImpl;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.*;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsReceiptContracts.*;
import io.fireflysoftware.core.banking.ledger.models.config.LedgerR2dbcConversions;
import io.fireflysoftware.core.banking.ledger.models.repositories.funds.*;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.context.annotation.*;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

class FundsRepositoryNativeIT {
    static final UUID SOURCE = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID DESTINATION = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Configuration(proxyBeanMethods = false)
    @EnableR2dbcRepositories(
            basePackages = "io.fireflysoftware.core.banking.ledger.models.repositories")
    @EnableTransactionManagement
    @org.springframework.data.r2dbc.config.EnableR2dbcAuditing
    static class Database extends AbstractR2dbcConfiguration {
        private final ConnectionFactory factory = factory();

        private static ConnectionFactory factory() {
            var builder =
                    PostgresqlConnectionConfiguration.builder()
                            .username(System.getProperty("ledger.native.user", "funds_runtime"))
                            .database("postgres")
                            .connectTimeout(Duration.ofSeconds(3));
            String port = System.getProperty("ledger.native.port");
            if (port == null) builder.socket(System.getProperty("ledger.native.socket"));
            else
                builder.host("127.0.0.1")
                        .port(Integer.parseInt(port))
                        .password(System.getenv("LEDGER_NATIVE_PASSWORD"));
            return new PostgresqlConnectionFactory(builder.build());
        }

        @Bean
        public ConnectionFactory connectionFactory() {
            return factory;
        }

        @Bean
        public R2dbcCustomConversions r2dbcCustomConversions() {
            return new LedgerR2dbcConversions().r2dbcCustomConversions();
        }

        @Bean
        R2dbcTransactionManager transactionManager() {
            return new R2dbcTransactionManager(factory);
        }

        @Bean
        TransactionMapper transactionMapper() {
            return Mappers.getMapper(TransactionMapper.class);
        }

        @Bean
        TransactionLegMapper transactionLegMapper() {
            return Mappers.getMapper(TransactionLegMapper.class);
        }
    }

    private SecurityPrincipal principal() {
        return SecurityPrincipal.builder()
                .subject("synthetic-native-java")
                .issuer("https://native-java-fixture.invalid")
                .tenantId("owned")
                .scopes(
                        Set.of(
                                "funds:reserve",
                                "funds:commit",
                                "funds:reverse",
                                "funds:read",
                                "funds:tenant-bind"))
                .claims(
                        Map.of(
                                "exp",
                                Instant.now().plusSeconds(120),
                                "funds_accounts",
                                List.of(SOURCE.toString(), DESTINATION.toString())))
                .build();
    }

    private AnnotationConfigApplicationContext context() {
        var context = new AnnotationConfigApplicationContext();
        context.register(Database.class, FundsRepositories.class, FundsDatabaseContext.class);
        context.registerBean("fundsMoneyService", FundsMoneyServiceImpl.class);
        context.registerBean("fundsReceiptService", FundsReceiptServiceImpl.class);
        context.registerBean("ledgerPostingService", LedgerPostingServiceImpl.class);
        context.refresh();
        return context;
    }

    @Test
    void actualSpringDataProxyExecutesContextGatesAdmissionFinishAndReadInFinancialTransactions() {
        try (var context = context()) {
            var p = principal();
            var money = context.getBean(FundsMoneyService.class);
            var evidence = context.getBean(FundsReceiptService.class);
            var reserve =
                    money.reserve(
                                    p,
                                    "owned",
                                    new Reserve(
                                            UUID.randomUUID(),
                                            SOURCE,
                                            DESTINATION,
                                            new BigDecimal("1.00"),
                                            "USD",
                                            300))
                            .block(Duration.ofSeconds(10));
            assertThat(reserve.state()).isEqualTo(ReservationState.HELD);
            var held =
                    evidence.read(p, "owned", reserve.reservationId())
                            .block(Duration.ofSeconds(10));
            assertThat(held.evidenceStatus()).isEqualTo(EvidenceStatus.RETAINED);
            assertThat(held.commit()).isNull();
            var committed =
                    money.action(
                                    p,
                                    "owned",
                                    reserve.reservationId(),
                                    new Command(UUID.randomUUID(), Action.COMMIT))
                            .block(Duration.ofSeconds(10));
            assertThat(committed.state()).isEqualTo(ReservationState.COMMITTED);
            var commit =
                    evidence.read(p, "owned", reserve.reservationId())
                            .block(Duration.ofSeconds(10));
            assertThat(commit.commit()).isNotNull();
            assertThat(commit.reversal()).isNull();
            var reversed =
                    money.action(
                                    p,
                                    "owned",
                                    reserve.reservationId(),
                                    new Command(UUID.randomUUID(), Action.REVERSE))
                            .block(Duration.ofSeconds(10));
            assertThat(reversed.state()).isEqualTo(ReservationState.REVERSED);
            var reverse =
                    evidence.read(p, "owned", reserve.reservationId())
                            .block(Duration.ofSeconds(10));
            assertThat(reverse.commit()).isNotNull();
            assertThat(reverse.reversal()).isNotNull();
            FundsReceiptCodec.validateReversal(
                    reverse.reversal().value(), reverse.commit().value());
        }
    }

    @Test
    void hostilePrivateOwnerMembersAreDeniedByActualRuntimeCatalogueProxy() {
        for (String user :
                List.of("hostile_runtime", "hostile_policy_reader", "hostile_policy_writer")) {
            System.setProperty("ledger.native.user", user);
            try (var context = context()) {
                assertThat(
                                context.getBean(FundsNativeRepository.class)
                                        .runtimeAllowed()
                                        .block(Duration.ofSeconds(5)))
                        .isFalse();
                var request =
                        new Reserve(
                                UUID.randomUUID(), SOURCE, DESTINATION, BigDecimal.ONE, "USD", 300);
                assertThatThrownBy(
                                () ->
                                        context.getBean(FundsMoneyService.class)
                                                .reserve(principal(), "owned", request)
                                                .block(Duration.ofSeconds(5)))
                        .isInstanceOfSatisfying(
                                org.springframework.web.server.ResponseStatusException.class,
                                error -> assertThat(error.getStatusCode().value()).isEqualTo(503));
            } finally {
                System.clearProperty("ledger.native.user");
            }
        }
    }

    @Test
    void actualProjectionRepositoryRetainsJoinAmountCountPagingAndZeroResultSemantics() {
        try (var context = context()) {
            var nativeOps = context.getBean(FundsNativeRepository.class);
            var deposits =
                    context.getBean(
                            io.fireflysoftware.core.banking.ledger.models.repositories.deposit
                                    .TransactionLineDepositRepository.class);
            var tx =
                    org.springframework.transaction.reactive.TransactionalOperator.create(
                            context.getBean(R2dbcTransactionManager.class));
            java.util.function.Supplier<reactor.core.publisher.Mono<Void>> scope =
                    () ->
                            nativeOps.establish(
                                    "owned",
                                    "[\"" + SOURCE + "\",\"" + DESTINATION + "\"]",
                                    "READ",
                                    "https://native-java-fixture.invalid",
                                    "synthetic-native-java",
                                    Long.toString(Instant.now().plusSeconds(30).toEpochMilli()));
            var count =
                    scope.get()
                            .then(
                                    deposits.countByCustomCriteria(
                                            null, null, "NATIVE", BigDecimal.ONE, BigDecimal.ONE))
                            .as(tx::transactional)
                            .block(Duration.ofSeconds(5));
            assertThat(count).isEqualTo(3);
            var rows =
                    scope.get()
                            .thenMany(
                                    deposits.findByCustomCriteria(
                                            null,
                                            null,
                                            "NATIVE",
                                            BigDecimal.ONE,
                                            BigDecimal.ONE,
                                            org.springframework.data.domain.PageRequest.of(1, 1)))
                            .collectList()
                            .as(tx::transactional)
                            .block(Duration.ofSeconds(5));
            assertThat(rows).hasSize(1);
            assertThat(rows.getFirst().getDepositTimestamp())
                    .isEqualTo(LocalDateTime.of(2026, 1, 2, 0, 0));
            var zero =
                    scope.get()
                            .then(
                                    deposits.countByCustomCriteria(
                                            null, null, "NATIVE", new BigDecimal("2"), null))
                            .as(tx::transactional)
                            .block(Duration.ofSeconds(5));
            assertThat(zero).isZero();
        }
    }

    @Test
    void actualTransactionFiltersUseNativeEnumBindingAndIntersectStatusSelection() {
        try (var context = context()) {
            var transactions =
                    context.getBean(
                            io.fireflysoftware.core.banking.ledger.models.repositories.core
                                    .TransactionRepository.class);
            var all =
                    transactions
                            .countTransactionsByFilter(
                                    null,
                                    null,
                                    BigDecimal.ONE,
                                    new BigDecimal("3"),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    "native-filter-",
                                    null,
                                    null,
                                    true,
                                    false,
                                    false)
                            .block(Duration.ofSeconds(5));
            assertThat(all).isEqualTo(3);
            var excluded =
                    transactions
                            .countTransactionsByFilter(
                                    null,
                                    null,
                                    BigDecimal.ONE,
                                    new BigDecimal("3"),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    "native-filter-",
                                    null,
                                    null,
                                    false,
                                    false,
                                    false)
                            .block(Duration.ofSeconds(5));
            assertThat(excluded).isEqualTo(2);
            var conflict =
                    transactions
                            .countTransactionsByFilter(
                                    null,
                                    null,
                                    BigDecimal.ONE,
                                    new BigDecimal("3"),
                                    null,
                                    null,
                                    List.of(
                                            io.fireflysoftware.core.banking.ledger.interfaces.enums
                                                    .core.TransactionStatusEnum.POSTED),
                                    null,
                                    null,
                                    "native-filter-",
                                    null,
                                    null,
                                    null,
                                    true,
                                    null)
                            .block(Duration.ofSeconds(5));
            assertThat(conflict).isZero();
            var failed =
                    transactions
                            .countTransactionsByFilter(
                                    null,
                                    null,
                                    BigDecimal.ONE,
                                    new BigDecimal("3"),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    "native-filter-",
                                    null,
                                    null,
                                    null,
                                    true,
                                    null)
                            .block(Duration.ofSeconds(5));
            assertThat(failed).isEqualTo(1);
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(
            named = "ledger.native.extended",
            matches = "true")
    void upgradedLegacyDrainExposesOnlyExplicitUnavailableHistoricalEvidence() {
        try (var context = context()) {
            var evidence = context.getBean(FundsReceiptService.class);
            for (String suffix : List.of("101", "102", "103")) {
                var id = UUID.fromString("00000000-0000-0000-0000-000000000" + suffix);
                var view = evidence.read(principal(), "owned", id).block(Duration.ofSeconds(5));
                assertThat(view.evidenceStatus()).isEqualTo(EvidenceStatus.HISTORICAL_UNAVAILABLE);
                assertThat(view.acceptedReserve()).isNull();
                assertThat(view.commit()).isNull();
                assertThat(view.reversal()).isNull();
            }
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(
            named = "ledger.native.extended",
            matches = "true")
    void concurrentExistingFundingPreservesSignedBankControlAndCustomerMoney() throws Exception {
        try (var context = context();
                var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            UUID bank = UUID.fromString("00000000-0000-0000-0000-000000000001");
            var principal =
                    SecurityPrincipal.builder()
                            .subject("native-funding")
                            .issuer("https://native-java-fixture.invalid")
                            .tenantId("owned")
                            .scopes(Set.of("funds:fund", "funds:read"))
                            .claims(
                                    Map.of(
                                            "exp",
                                            Instant.now().plusSeconds(60),
                                            "funds_accounts",
                                            List.of(bank.toString(), SOURCE.toString())))
                            .build();
            var money = context.getBean(FundsMoneyService.class);
            var start = new java.util.concurrent.CountDownLatch(1);
            var jobs = new java.util.ArrayList<java.util.concurrent.Future<FundingReceipt>>();
            for (int n = 0; n < 2; n++)
                jobs.add(
                        workers.submit(
                                () -> {
                                    start.await();
                                    return money.fund(
                                                    principal,
                                                    "owned",
                                                    new Funding(
                                                            UUID.randomUUID(),
                                                            bank,
                                                            SOURCE,
                                                            new BigDecimal("2.00"),
                                                            "USD"))
                                            .block(Duration.ofSeconds(10));
                                }));
            start.countDown();
            for (var job : jobs)
                assertThat(job.get(15, java.util.concurrent.TimeUnit.SECONDS).postingId())
                        .isNotNull();
            var nativeOps = context.getBean(FundsNativeRepository.class);
            var accounts = context.getBean(FundsAccountRepository.class);
            var tx =
                    org.springframework.transaction.reactive.TransactionalOperator.create(
                            context.getBean(R2dbcTransactionManager.class));
            var rows =
                    nativeOps
                            .establish(
                                    "owned",
                                    "[\"" + bank + "\",\"" + SOURCE + "\"]",
                                    "READ",
                                    "https://native-java-fixture.invalid",
                                    "native-funding",
                                    Long.toString(Instant.now().plusSeconds(30).toEpochMilli()))
                            .then(
                                    reactor.core.publisher.Mono.zip(
                                            accounts.findByTenantIdAndAccountId("owned", bank),
                                            accounts.findByTenantIdAndAccountId("owned", SOURCE)))
                            .as(tx::transactional)
                            .block(Duration.ofSeconds(5));
            assertThat(rows.getT1().getBooked()).isEqualByComparingTo("-4");
            assertThat(rows.getT1().getHeld()).isZero();
            assertThat(rows.getT2().getBooked()).isEqualByComparingTo("104");
            assertThat(rows.getT2().getHeld()).isZero();
        }
    }
}
