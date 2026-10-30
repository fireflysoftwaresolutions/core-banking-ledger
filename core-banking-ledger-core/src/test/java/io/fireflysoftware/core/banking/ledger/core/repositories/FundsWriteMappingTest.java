package io.fireflysoftware.core.banking.ledger.core.repositories;

import io.fireflysoftware.core.banking.ledger.models.repositories.funds.*;

import static org.junit.jupiter.api.Assertions.*;

import io.fireflysoftware.core.banking.ledger.models.entities.funds.*;
import io.r2dbc.spi.*;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.context.annotation.*;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Supplier;

class FundsWriteMappingTest {
    static final UUID ACCOUNT = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID RESERVATION = UUID.fromString("20000000-0000-0000-0000-000000000002");
    static final UUID POSTING = UUID.fromString("30000000-0000-0000-0000-000000000003");

    @Test
    void implementationUsesImplSubpackageAndRealSpringDataFragmentDiscovery() throws Exception {
        Class<?> implementation = Class.forName(
                "io.fireflysoftware.core.banking.ledger.models.repositories.funds.impl.FundsWriteOperationsImpl");
        assertTrue(FundsWriteOperations.class.isAssignableFrom(implementation));
        try (var context = new AnnotationConfigApplicationContext(Database.class)) {
            var operations = context.getBean(FundsMutationRepository.class);
            Database.driver.counts = () -> Flux.just(1L);
            assertEquals(1L, operations.completePosting("posting-key", POSTING).block());
            assertSame(context.getBean(R2dbcEntityTemplate.class).getDatabaseClient().getConnectionFactory(),
                    context.getBean(ConnectionFactory.class));
        }
    }

    @Test
    void accountMoneyGeneratedSqlRetainsTenantIdentityVersionStateAndExactSignedAmounts() {
        var driver = new Driver();
        var operations = writes(driver);
        assertEquals(1L, operations.updateMoney("tenant-one", ACCOUNT, 7, "ACTIVE",
                new BigDecimal("-10.1200"), new BigDecimal("0.0000")).block());
        assertEquals("UPDATE \"ledger_funds_account\" SET \"booked\" = $1, \"held\" = $2, \"version\" = $3 WHERE \"ledger_funds_account\".tenant_id = $4 AND \"ledger_funds_account\".account_id = $5 AND \"ledger_funds_account\".\"version\" = $6 AND \"ledger_funds_account\".\"state\" = $7", driver.sql);
        assertEquals(List.of(new BigDecimal("-10.1200"), new BigDecimal("0.0000"), 8L,
                "tenant-one", ACCOUNT, 7L, "ACTIVE"), driver.values());
        assertEquals(1, driver.closed);
    }

    @Test
    void accountStateCasRetainsExpectedStateAndVersion() {
        var driver = new Driver();
        writes(driver).updateAccountState("tenant-one", ACCOUNT, 3, "BLOCKED", "ACTIVE").block();
        assertTrue(driver.sql.contains("tenant_id = $3"));
        assertTrue(driver.sql.contains("account_id = $4"));
        assertTrue(driver.sql.contains("\"version\" = $5"));
        assertTrue(driver.sql.contains("\"state\" = $6"));
        assertEquals(List.of("ACTIVE", 4L, "tenant-one", ACCOUNT, 3L, "BLOCKED"), driver.values());
    }

    @Test
    void reservationCasIncludesReversalOnlyWhenProvided() {
        var driver = new Driver();
        writes(driver).updateReservation("tenant-one", RESERVATION, 8, "COMMITTED", "REVERSED", POSTING).block();
        assertEquals(List.of("REVERSED", 9L, POSTING, "tenant-one", RESERVATION, 8L, "COMMITTED"), driver.values());
        assertTrue(driver.sql.contains("reversal_id = $3"));
        writes(driver).updateReservation("tenant-one", RESERVATION, 8, "ACTIVE", "EXPIRED", null).block();
        assertFalse(driver.sql.contains("reversal_id"));
        assertEquals(List.of("EXPIRED", 9L, "tenant-one", RESERVATION, 8L, "ACTIVE"), driver.values());
    }

    @Test
    void operationCompletionRetainsCompositeTenantIdentityAndOriginalPointerSelection() {
        var driver = new Driver();
        writes(driver).completeOperation(new FundsOperationId("tenant-one", ACCOUNT), RESERVATION, null).block();
        assertTrue(driver.sql.contains("reservation_id = $1"));
        assertEquals(List.of(RESERVATION, "tenant-one", ACCOUNT), driver.values());
        writes(driver).completeOperation(new FundsOperationId("tenant-one", ACCOUNT), null, POSTING).block();
        assertTrue(driver.sql.contains("posting_id = $1"));
        assertEquals(List.of(POSTING, "tenant-one", ACCOUNT), driver.values());
    }

    @Test
    void postingCompletionRetainsIdempotencyKeyAndUncompletedPredicate() {
        var driver = new Driver();
        writes(driver).completePosting("posting-key", POSTING).block();
        assertEquals("UPDATE \"ledger_posting_request\" SET transaction_id = $1 WHERE \"ledger_posting_request\".idempotency_key = $2 AND \"ledger_posting_request\".transaction_id IS NULL", driver.sql);
        assertEquals(List.of(POSTING, "posting-key"), driver.values());
    }

    @Test
    void actualDriverCountsAreNeverReplacedWithSingularSuccess() {
        var driver = new Driver();
        var operations = writes(driver);
        for (long count : new long[]{0L, 1L, 2L}) {
            driver.counts = () -> Flux.just(count);
            assertEquals(count, operations.updateMoney("tenant-one", ACCOUNT, 1, "ACTIVE", BigDecimal.ZERO, BigDecimal.ZERO).block());
            assertEquals(count, operations.updateAccountState("tenant-one", ACCOUNT, 1, "ACTIVE", "BLOCKED").block());
            assertEquals(count, operations.updateReservation("tenant-one", RESERVATION, 1, "ACTIVE", "RELEASED", null).block());
            assertEquals(count, operations.completeOperation(new FundsOperationId("tenant-one", ACCOUNT), RESERVATION, null).block());
            assertEquals(count, operations.completePosting("posting-key", POSTING).block());
        }
        driver.counts = Flux::empty;
        assertEquals(0L, operations.completePosting("posting-key", POSTING).block());
        driver.counts = () -> Flux.just(1L, 1L);
        assertEquals(2L, operations.completePosting("posting-key", POSTING).block());
    }

    @Test
    void driverErrorPropagatesAndReleasesOnlyTheCreatedConnection() {
        var driver = new Driver();
        var failure = new IllegalStateException("driver unavailable");
        driver.counts = () -> Flux.error(failure);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> writes(driver).completePosting("posting-key", POSTING).block()));
        assertEquals(1, driver.opened);
        assertEquals(1, driver.closed);
    }

    private static FundsWriteOperations writes(Driver driver) {
        // Reflect only to keep the same maintained test usable before and after the package move.
        try {
            Class<?> type;
            try {
                type = Class.forName("io.fireflysoftware.core.banking.ledger.models.repositories.funds.impl.FundsWriteOperationsImpl");
            } catch (ClassNotFoundException oldPackage) {
                type = Class.forName("io.fireflysoftware.core.banking.ledger.models.repositories.funds.FundsWriteOperationsImpl");
            }
            return (FundsWriteOperations) type.getConstructor(org.springframework.data.r2dbc.core.R2dbcEntityOperations.class)
                    .newInstance(new R2dbcEntityTemplate(driver));
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableR2dbcRepositories(basePackageClasses = FundsMutationRepository.class)
    static class Database extends AbstractR2dbcConfiguration {
        static final Driver driver = new Driver();
        @Bean public ConnectionFactory connectionFactory() { return driver; }
    }

    static final class Driver implements ConnectionFactory {
        String sql;
        final Map<Integer, Object> bindings = new TreeMap<>();
        Supplier<Flux<Long>> counts = () -> Flux.just(1L);
        int opened;
        int closed;
        public ConnectionFactoryMetadata getMetadata() { return () -> "PostgreSQL"; }
        public Publisher<? extends Connection> create() {
            return Mono.fromSupplier(() -> {
                opened++;
                return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "createStatement" -> statement((String) args[0]);
                        case "close" -> Mono.fromRunnable(() -> closed++);
                        case "getMetadata" -> (ConnectionMetadata) Proxy.newProxyInstance(ConnectionMetadata.class.getClassLoader(), new Class<?>[]{ConnectionMetadata.class}, (p, m, a) -> m.getName().equals("getDatabaseProductName") ? "PostgreSQL" : "17");
                        case "toString" -> "NetworklessOwnedDriverConnection";
                        default -> throw new AssertionError("Unexpected driver method: " + method.getName());
                    };
                });
            });
        }
        List<Object> values() { return List.copyOf(bindings.values()); }
        Statement statement(String query) {
            sql = query;
            bindings.clear();
            final Statement[] self = new Statement[1];
            self[0] = (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(), new Class<?>[]{Statement.class}, (proxy, method, args) -> {
                return switch (method.getName()) {
                    case "bind" -> { bindings.put((Integer) args[0], args[1]); yield self[0]; }
                    case "bindNull" -> throw new AssertionError("Unexpected null binding");
                    case "execute" -> Flux.just(result());
                    case "returnGeneratedValues", "fetchSize" -> self[0];
                    default -> throw new AssertionError("Unexpected statement method: " + method.getName());
                };
            });
            return self[0];
        }
        Result result() {
            return (Result) Proxy.newProxyInstance(Result.class.getClassLoader(), new Class<?>[]{Result.class}, (proxy, method, args) -> {
                if (method.getName().equals("getRowsUpdated")) return counts.get();
                throw new AssertionError("Unexpected result method: " + method.getName());
            });
        }
    }
}
