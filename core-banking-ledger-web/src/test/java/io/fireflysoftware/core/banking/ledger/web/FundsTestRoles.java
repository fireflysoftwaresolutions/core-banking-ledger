package io.fireflysoftware.core.banking.ledger.web;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;
import java.util.UUID;

final class FundsTestRoles {
    static final String RUNTIME_PASSWORD = UUID.randomUUID().toString();

    private FundsTestRoles() {}

    static void prepare(PostgreSQLContainer database) {
        try (var connection =
                        DriverManager.getConnection(
                                database.getJdbcUrl(),
                                database.getUsername(),
                                database.getPassword());
                var statement = connection.createStatement()) {
            statement.execute(
                    "CREATE ROLE funds_runtime LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD '"
                            + RUNTIME_PASSWORD
                            + "'");
            for (String role :
                    new String[] {
                        "firefly_funds_policy_reader", "firefly_funds_policy_writer",
                        "firefly_funds_receipt_guard", "firefly_funds_receipt_api"
                    }) {
                statement.execute("CREATE ROLE " + role + " NOLOGIN NOSUPERUSER NOBYPASSRLS");
            }
            statement.execute("GRANT firefly_funds_receipt_api TO funds_runtime");
            statement.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
            statement.execute("GRANT USAGE ON SCHEMA public TO funds_runtime");
        } catch (Exception error) {
            throw new IllegalStateException("Cannot provision owned funds test roles", error);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MigrationFixture {
        @Bean
        FlywayMigrationStrategy fundsTestMigrationStrategy() {
            return flyway -> {
                flyway.migrate();
                try (var connection = flyway.getConfiguration().getDataSource().getConnection();
                        var statement = connection.createStatement()) {
                    statement.execute(
                            """
                            DO $$ DECLARE relation RECORD; BEGIN
                              FOR relation IN SELECT c.relname FROM pg_catalog.pg_class c
                                JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
                                WHERE n.nspname='public' AND c.relkind IN ('r','p')
                              LOOP
                                EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE ON TABLE public.%I TO funds_runtime',relation.relname);
                              END LOOP;
                            END $$
                            """);
                    statement.execute(
                            "GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO"
                                + " funds_runtime");
                } catch (Exception error) {
                    throw new IllegalStateException(
                            "Cannot grant owned test runtime table privileges", error);
                }
            };
        }
    }
}
