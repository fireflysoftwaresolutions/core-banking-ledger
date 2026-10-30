package io.fireflysoftware.core.banking.ledger.web;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@org.springframework.context.annotation.Import(FundsTestRoles.MigrationFixture.class)
@Testcontainers
@SpringBootTest(classes = LedgerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.address=127.0.0.1", "firefly.eda.enabled=false"})
class LedgerRuntimeDatabaseTest {

    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("ledger_qualification")
            .withUsername("ledger_test")
            .withPassword("synthetic-disposable-test-password")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withPortBindings(
                    new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1", 0), ExposedPort.tcp(5432))));

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        FundsTestRoles.prepare(DATABASE);
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://" + DATABASE.getHost() + ":"
                + DATABASE.getMappedPort(5432) + "/" + DATABASE.getDatabaseName());
        registry.add("spring.r2dbc.username", DATABASE::getUsername);
        registry.add("spring.r2dbc.password", DATABASE::getPassword);
        registry.add("spring.flyway.url", DATABASE::getJdbcUrl);
        registry.add("spring.flyway.user", DATABASE::getUsername);
        registry.add("spring.flyway.password", DATABASE::getPassword);
    }

    @Autowired
    DatabaseClient database;

    @Autowired
    Flyway migrations;

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    io.fireflysoftware.core.banking.ledger.models.repositories.core.TransactionRepository transactionRepository;

    @Autowired
    io.fireflysoftware.core.banking.ledger.core.services.core.TransactionService transactionService;

    @Autowired
    io.fireflysoftware.core.banking.ledger.core.services.core.TransactionStatusHistoryService historyService;

    @Autowired
    io.fireflysoftware.core.banking.ledger.core.services.leg.TransactionLegService legService;

    @Test
    void storedTransactionEnumsMatchTheActualPostgresSchema() {
        for (var type : io.fireflysoftware.core.banking.ledger.interfaces.enums.core.TransactionTypeEnum.values()) {
            var transaction = new io.fireflysoftware.core.banking.ledger.models.entities.core.Transaction();
            transaction.setExternalReference(UUID.randomUUID().toString());
            transaction.setAccountId(UUID.randomUUID());
            transaction.setTransactionType(type);
            transaction.setTransactionStatus(io.fireflysoftware.core.banking.ledger.interfaces.enums.core.TransactionStatusEnum.POSTED);
            transaction.setTotalAmount(new java.math.BigDecimal("1.00"));
            transaction.setCurrency("USD");
            transaction.setTransactionDate(java.time.LocalDateTime.now());
            transaction.setValueDate(java.time.LocalDateTime.now());
            var saved = transactionRepository.save(transaction).block(Duration.ofSeconds(10));
            assertThat(saved.getTransactionId()).isNotNull();
            var loaded = transactionRepository.findById(saved.getTransactionId()).block(Duration.ofSeconds(10));
            assertThat(loaded.getTransactionType()).isEqualTo(transaction.getTransactionType());
            assertThat(loaded.getTransactionStatus()).isEqualTo(transaction.getTransactionStatus());
        }
    }

    @Test
    void actualLedgerMigrationsAndReactiveRuntimeUseTheSameDisposablePostgres() throws Exception {
        try (var connection = DriverManager.getConnection(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE success")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isPositive().isEqualTo(migrations.info().applied().length);
        }
        assertThat(migrations.info().pending()).isEmpty();
        String name = database.sql("SELECT current_database() AS name").map((row, metadata) -> row.get("name", String.class))
                .one().block(Duration.ofSeconds(10));
        assertThat(name).isEqualTo(DATABASE.getDatabaseName());
        Long transactions = database.sql("SELECT count(*) AS total FROM transaction")
                .map((row, metadata) -> row.get("total", Long.class)).one().block(Duration.ofSeconds(10));
        assertThat(transactions).isNotNegative();
    }

    @Test
    void balancedPostingAndReplayPersistOneCompleteAggregate() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = posting(key, "12.34", "12.34", "ordinary");
        var created = send("POST", "/api/v1/ledger-postings", body);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        String id = json.readTree(created.body()).path("transaction").path("transactionId").asText();
        assertThat(id).isNotBlank();
        var replay = send("POST", "/api/v1/ledger-postings", body);
        assertThat(replay.statusCode()).as(replay.body()).isEqualTo(200);
        assertThat(json.readTree(replay.body()).path("transaction").path("transactionId").asText()).isEqualTo(id);
        assertThat(json.readTree(replay.body()).path("legs")).hasSize(2);
        assertThat(count("SELECT count(*) AS total FROM transaction WHERE external_reference = :key", key)).isEqualTo(1);
        assertThat(count("SELECT count(*) AS total FROM transaction_leg WHERE transaction_id = CAST(:key AS uuid)", id)).isEqualTo(2);
        assertThat(count("SELECT count(*) AS total FROM transaction_status_history WHERE transaction_id = CAST(:key AS uuid)", id)).isEqualTo(1);
    }

    @Test
    void unbalancedPostingLeavesNoFinancialRows() throws Exception {
        String key = UUID.randomUUID().toString();
        var response = send("POST", "/api/v1/ledger-postings", posting(key, "12.34", "12.33", "ordinary"));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(count("SELECT count(*) AS total FROM transaction WHERE external_reference = :key", key)).isZero();
    }

    @Test
    void conflictingIdempotencyCannotReplaceTheOriginalMoney() throws Exception {
        String key = UUID.randomUUID().toString();
        var first = send("POST", "/api/v1/ledger-postings", posting(key, "12.34", "12.34", "ordinary"));
        assertThat(first.statusCode()).as(first.body()).isEqualTo(201);
        var conflict = send("POST", "/api/v1/ledger-postings", posting(key, "99.00", "99.00", "ordinary"));
        assertThat(conflict.statusCode()).as(conflict.body()).isEqualTo(409);
        assertThat(count("SELECT count(*) AS total FROM transaction WHERE external_reference = :key AND total_amount = 12.34", key)).isEqualTo(1);
    }

    @Test
    void concurrentIdenticalRequestsCreateExactlyOnePosting() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = posting(key, "12.34", "12.34", "ordinary");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var requests = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(index -> executor.submit(() -> send("POST", "/api/v1/ledger-postings", body))).toList();
            var responses = new java.util.ArrayList<HttpResponse<String>>();
            for (var request : requests) {
                responses.add(request.get(30, java.util.concurrent.TimeUnit.SECONDS));
            }
            assertThat(responses.stream().filter(response -> response.statusCode() == 201).count()).isEqualTo(1);
            assertThat(responses.stream().filter(response -> response.statusCode() == 200).count()).isEqualTo(7);
            var ids = new java.util.HashSet<String>();
            for (var response : responses) {
                ids.add(json.readTree(response.body()).path("transaction").path("transactionId").asText());
            }
            assertThat(ids).hasSize(1).doesNotContain("");
            String id = ids.iterator().next();
            assertThat(count("SELECT count(*) AS total FROM transaction WHERE external_reference = :key", key)).isEqualTo(1);
            assertThat(count("SELECT count(*) AS total FROM transaction_leg WHERE transaction_id = CAST(:key AS uuid)", id)).isEqualTo(2);
        }
    }

    @Test
    void aDatabaseFailureAfterHeaderCreationRollsBackTheWholePosting() throws Exception {
        String key = UUID.randomUUID().toString();
        String marker = "reject-" + UUID.randomUUID();
        String constraint = "test_" + UUID.randomUUID().toString().replace("-", "");
        database.sql("ALTER TABLE transaction_leg ADD CONSTRAINT " + constraint
                + " CHECK (description IS DISTINCT FROM '" + marker + "')").fetch().rowsUpdated().block(Duration.ofSeconds(10));
        try {
            var response = send("POST", "/api/v1/ledger-postings", posting(key, "12.34", "12.34", marker));
            assertThat(response.statusCode()).as(response.body()).isGreaterThanOrEqualTo(400);
            assertThat(count("SELECT count(*) AS total FROM transaction WHERE external_reference = :key", key)).isZero();
            assertThat(count("SELECT count(*) AS total FROM ledger_posting_request WHERE idempotency_key = :key", key)).isZero();
            assertThat(count("SELECT count(*) AS total FROM transaction_leg WHERE description = :key", marker)).isZero();
            var retried = send("POST", "/api/v1/ledger-postings", posting(key, "12.34", "12.34", "ordinary"));
            assertThat(retried.statusCode()).as(retried.body()).isEqualTo(201);
        } finally {
            database.sql("ALTER TABLE transaction_leg DROP CONSTRAINT " + constraint).fetch().rowsUpdated().block(Duration.ofSeconds(10));
        }
    }

    @Test
    void legacyCrudCannotAlterOrDeleteAManagedPostingOrAppendLegs() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = posting(key, "12.34", "12.34", "ordinary");
        var first = send("POST", "/api/v1/ledger-postings", body);
        assertThat(first.statusCode()).as(first.body()).isEqualTo(201);
        var header = json.readTree(first.body()).path("transaction");
        String id = header.path("transactionId").asText();
        var update = send("PUT", "/api/v1/transactions/" + id, json.readTree(body).path("transaction").toString());
        assertThat(update.statusCode()).as(update.body()).isEqualTo(409);
        assertThat(send("DELETE", "/api/v1/transactions/" + id, "").statusCode()).isEqualTo(409);
        var leg = json.readTree(body).path("legs").get(0);
        assertThat(send("POST", "/api/v1/transactions/" + id + "/legs", leg.toString()).statusCode()).isEqualTo(409);
        assertThat(count("SELECT count(*) AS total FROM transaction_leg WHERE transaction_id = CAST(:key AS uuid)", id)).isEqualTo(2);
    }

    @Test
    void pendingLifecycleIsDurableAndTerminalReleaseCannotErasePostedMoney() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = posting(key, "12.34", "12.34", "ordinary").replace("POSTED", "PENDING");
        var created = send("POST", "/api/v1/ledger-postings", body);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        String id = json.readTree(created.body()).path("transaction").path("transactionId").asText();
        for (int i = 0; i < 2; i++) {
            var confirmed = send("POST", "/api/v1/ledger-postings/" + id + "/confirm", "");
            assertThat(confirmed.statusCode()).as(confirmed.body()).isEqualTo(200);
            assertThat(json.readTree(confirmed.body()).path("transaction").path("transactionStatus").asText()).isEqualTo("POSTED");
            assertThat(json.readTree(confirmed.body()).path("legs")).hasSize(2);
        }
        assertThat(send("POST", "/api/v1/ledger-postings/" + id + "/release", "").statusCode()).isEqualTo(409);
        assertThat(send("PATCH", "/api/v1/transactions/" + id + "/status?newStatus=REVERSED&reason=unsafe", "").statusCode()).isEqualTo(409);
        assertThat(count("SELECT count(*) AS total FROM transaction_status_history WHERE transaction_id = CAST(:key AS uuid)", id)).isEqualTo(2);
    }

    @Test
    void releasedPendingPostingCannotBeConfirmedAndReplaysItsPersistedState() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = posting(key, "12.34", "12.34", "ordinary").replace("POSTED", "PENDING");
        var created = send("POST", "/api/v1/ledger-postings", body);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        String id = json.readTree(created.body()).path("transaction").path("transactionId").asText();
        assertThat(send("POST", "/api/v1/ledger-postings/" + id + "/release", "").statusCode()).isEqualTo(200);
        assertThat(send("POST", "/api/v1/ledger-postings/" + id + "/release", "").statusCode()).isEqualTo(200);
        assertThat(send("POST", "/api/v1/ledger-postings/" + id + "/confirm", "").statusCode()).isEqualTo(409);
        var replay = send("POST", "/api/v1/ledger-postings", body);
        assertThat(replay.statusCode()).as(replay.body()).isEqualTo(200);
        assertThat(json.readTree(replay.body()).path("transaction").path("transactionStatus").asText()).isEqualTo("REVERSED");
        assertThat(count("SELECT count(*) AS total FROM transaction_status_history WHERE transaction_id = CAST(:key AS uuid)", id)).isEqualTo(2);
    }

    @Test
    void reversalCopiesEveryPersistedOppositeLegAndCannotDuplicateUnderAnotherKey() throws Exception {
        String key = UUID.randomUUID().toString();
        var created = send("POST", "/api/v1/ledger-postings", posting(key, "12.34", "12.34", "ordinary"));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        String id = json.readTree(created.body()).path("transaction").path("transactionId").asText();
        String reverse = "{\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}";
        var reversed = send("POST", "/api/v1/ledger-postings/" + id + "/reversal", reverse);
        assertThat(reversed.statusCode()).as(reversed.body()).isEqualTo(201);
        var result = json.readTree(reversed.body());
        assertThat(result.path("transaction").path("relatedTransactionId").asText()).isEqualTo(id);
        assertThat(result.path("transaction").path("relationType").asText()).isEqualTo("REVERSAL");
        assertThat(count("SELECT count(*) AS total FROM transaction_leg WHERE transaction_id = CAST(:key AS uuid) AND account_id = '11111111-1111-1111-1111-111111111111' AND leg_type = 'CREDIT' AND amount = 12.34", result.path("transaction").path("transactionId").asText())).isEqualTo(1);
        var replay = send("POST", "/api/v1/ledger-postings/" + id + "/reversal", reverse);
        assertThat(replay.statusCode()).as(replay.body()).isEqualTo(200);
        assertThat(json.readTree(replay.body()).path("transaction").path("transactionId")).isEqualTo(result.path("transaction").path("transactionId"));
        assertThat(send("POST", "/api/v1/ledger-postings/" + id + "/reversal", "{\"idempotencyKey\":\"different-key\"}").statusCode()).isEqualTo(409);
        assertThat(count("SELECT count(*) AS total FROM transaction WHERE related_transaction_id = CAST(:key AS uuid) AND relation_type='REVERSAL'", id)).isEqualTo(1);
        assertThat(count("SELECT count(*) AS total FROM transaction WHERE transaction_id = CAST(:key AS uuid) AND transaction_status='posted'", id)).isEqualTo(1);
        assertThat(send("POST", "/api/v1/ledger-postings/" + id + "/release", "").statusCode()).isEqualTo(200);
    }

    @Test
    void legacyHistoryCannotBeForgedOrErasedForManagedPostings() throws Exception {
        String key = UUID.randomUUID().toString();
        var created = send("POST", "/api/v1/ledger-postings", posting(key, "12.34", "12.34", "ordinary"));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        String id = json.readTree(created.body()).path("transaction").path("transactionId").asText();
        String historyId = database.sql("SELECT transaction_status_history_id FROM transaction_status_history WHERE transaction_id=CAST(:key AS uuid)")
                .bind("key", id).map((row, metadata) -> row.get("transaction_status_history_id", UUID.class).toString()).one().block();
        String history = "{\"statusCode\":\"FAILED\",\"statusStartDatetime\":\"2026-10-22T10:00:00\"}";
        assertThat(send("POST", "/api/v1/transactions/" + id + "/status-history", history).statusCode()).isEqualTo(409);
        assertThat(send("PUT", "/api/v1/transactions/" + id + "/status-history/" + historyId, history).statusCode()).isEqualTo(409);
        assertThat(send("DELETE", "/api/v1/transactions/" + id + "/status-history/" + historyId, "").statusCode()).isEqualTo(409);
        assertThat(count("SELECT count(*) AS total FROM transaction_status_history WHERE transaction_id = CAST(:key AS uuid)", id)).isEqualTo(1);
    }

    @Test
    void populatedUnmappedLegacyHistoryBlocksUpgradeWithoutChangingEvidence() throws Exception {
        String databaseName = "legacy_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + databaseName);
        }
        String jdbcUrl = DATABASE.getJdbcUrl().replace(DATABASE.getDatabaseName(), databaseName);
        Flyway legacy = Flyway.configure().dataSource(jdbcUrl, DATABASE.getUsername(), DATABASE.getPassword()).target("52").load();
        legacy.migrate();
        try (var connection = DriverManager.getConnection(jdbcUrl, DATABASE.getUsername(), DATABASE.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("INSERT INTO transaction_status_history(transaction_status_history_id,transaction_id,status_code,status_start_datetime,reason) VALUES (71,99,'posted',TIMESTAMP '2026-10-22 10:00:00','legacy-evidence')");
            Flyway upgrade = Flyway.configure().dataSource(jdbcUrl, DATABASE.getUsername(), DATABASE.getPassword()).load();
            assertThatThrownBy(upgrade::migrate).hasStackTraceContaining("LEDGER_HISTORY_UUID_REPAIR_REQUIRED");
            try (var result = statement.executeQuery("SELECT transaction_status_history_id,transaction_id,reason FROM transaction_status_history")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isEqualTo(71);
                assertThat(result.getLong(2)).isEqualTo(99);
                assertThat(result.getString(3)).isEqualTo("legacy-evidence");
                assertThat(result.next()).isFalse();
            }
        }
    }

    @Test
    void exactMultiLegAmountsSurvivePersistenceAndEconomicReplay() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = posting(key, "123456789012345.670000000000000001", "123456789012345.660000000000000001", "ordinary")
                .replace("\"description\":\"ordinary\"}]", "\"description\":\"ordinary\"},{\"accountId\":\"33333333-3333-3333-3333-333333333333\",\"accountSpaceId\":\"44444444-4444-4444-4444-444444444444\",\"legType\":\"CREDIT\",\"amount\":0.01,\"currency\":\"USD\"}]");
        var created = send("POST", "/api/v1/ledger-postings", body);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        String id = json.readTree(created.body()).path("transaction").path("transactionId").asText();
        assertThat(count("SELECT count(*) AS total FROM transaction_leg WHERE transaction_id=CAST(:key AS uuid)", id)).isEqualTo(3);
        assertThat(count("SELECT count(*) AS total FROM transaction_leg WHERE transaction_id=CAST(:key AS uuid) AND leg_type='DEBIT' AND amount=123456789012345.670000000000000001", id)).isEqualTo(1);
        assertThat(count("SELECT count(*) AS total FROM transaction_leg WHERE transaction_id=CAST(:key AS uuid) AND amount=0.01 AND account_space_id='44444444-4444-4444-4444-444444444444'", id)).isEqualTo(1);
        var replay = send("POST", "/api/v1/ledger-postings", body.replace("0.01", "0.0100"));
        assertThat(replay.statusCode()).as(replay.body()).isEqualTo(200);
        assertThat(json.readTree(replay.body()).path("legs")).hasSize(3);
    }

    @Test
    void malformedMoneyNeverClaimsAnIdempotencyKey() throws Exception {
        for (String amount : java.util.List.of("0", "-0.01", "0.0000000000000000001", "100000000000000000000")) {
            String key = UUID.randomUUID().toString();
            var response = send("POST", "/api/v1/ledger-postings", posting(key, amount, amount, "ordinary"));
            assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
            assertThat(count("SELECT count(*) AS total FROM ledger_posting_request WHERE idempotency_key=:key", key)).isZero();
            assertThat(count("SELECT count(*) AS total FROM transaction WHERE external_reference=:key", key)).isZero();
        }
        for (String change : java.util.List.of("mixed-currency", "unknown-side", "missing-account")) {
            String key = UUID.randomUUID().toString();
            String body = posting(key, "12.34", "12.34", "ordinary");
            body = switch (change) {
                case "mixed-currency" -> body.replace("\"currency\":\"USD\",\"description\"", "\"currency\":\"EUR\",\"description\"");
                case "unknown-side" -> body.replace("\"DEBIT\"", "\"INVALID\"");
                default -> body.replace("\"accountId\":\"22222222-2222-2222-2222-222222222222\",", "");
            };
            var response = send("POST", "/api/v1/ledger-postings", body);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
            assertThat(count("SELECT count(*) AS total FROM ledger_posting_request WHERE idempotency_key=:key", key)).isZero();
        }
    }

    @Test
    void failedConfirmationRollsBackStatusAndHistoryBeforeRetry() throws Exception {
        String key = UUID.randomUUID().toString();
        var created = send("POST", "/api/v1/ledger-postings", posting(key, "12.34", "12.34", "ordinary").replace("POSTED", "PENDING"));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        String id = json.readTree(created.body()).path("transaction").path("transactionId").asText();
        String constraint = "test_" + UUID.randomUUID().toString().replace("-", "");
        database.sql("ALTER TABLE transaction_status_history ADD CONSTRAINT " + constraint + " CHECK (transaction_id <> '" + id + "' OR status_code <> 'posted')")
                .fetch().rowsUpdated().block();
        try {
            assertThat(send("POST", "/api/v1/ledger-postings/" + id + "/confirm", "").statusCode()).isGreaterThanOrEqualTo(400);
            assertThat(count("SELECT count(*) AS total FROM transaction WHERE transaction_id=CAST(:key AS uuid) AND transaction_status='pending'", id)).isEqualTo(1);
            assertThat(count("SELECT count(*) AS total FROM transaction_status_history WHERE transaction_id=CAST(:key AS uuid)", id)).isEqualTo(1);
        } finally {
            database.sql("ALTER TABLE transaction_status_history DROP CONSTRAINT " + constraint).fetch().rowsUpdated().block();
        }
        assertThat(send("POST", "/api/v1/ledger-postings/" + id + "/confirm", "").statusCode()).isEqualTo(200);
    }

    @Test
    void actualRuntimePublishesTypedAtomicPostingContract() throws Exception {
        var spec = send("GET", "/v3/api-docs.yaml", "");
        assertThat(spec.statusCode()).as(spec.body()).isEqualTo(200);
        assertThat(spec.body()).contains("createLedgerPosting", "confirmLedgerPosting", "releaseLedgerPosting", "reverseLedgerPosting",
                "LedgerPostingRequestDTO", "LedgerPostingDTO", "LedgerReversalRequestDTO");
        var schema = json.readTree(send("GET", "/v3/api-docs", "").body()).path("components").path("schemas").path("LedgerPostingHeaderDTO");
        assertThat(schema.path("required").toString()).contains("accountId", "transactionType", "transactionStatus", "totalAmount", "currency")
                .doesNotContain("transactionDate", "valueDate", "bookingDate");
        assertThat(schema.path("properties").path("transactionStatus").path("enum")).isEqualTo(json.readTree("[\"PENDING\",\"POSTED\"]"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("target", "qualified-openapi.yaml"), spec.body());
    }

    @Test
    void forgedLegacyReversalCannotAuthorizeReleaseOfPostedMoney() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = posting(key, "12.34", "12.34", "ordinary");
        var created = send("POST", "/api/v1/ledger-postings", body);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        String id = json.readTree(created.body()).path("transaction").path("transactionId").asText();
        var forged = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(body).path("transaction");
        forged.put("externalReference", UUID.randomUUID().toString());
        forged.put("relationType", "REVERSAL");
        forged.put("relatedTransactionId", id);
        var legacy = send("POST", "/api/v1/transactions", forged.toString());
        assertThat(legacy.statusCode()).as(legacy.body()).isBetween(200, 299);
        assertThat(send("POST", "/api/v1/ledger-postings/" + id + "/release", "").statusCode()).isEqualTo(409);
        var reversal = send("POST", "/api/v1/ledger-postings/" + id + "/reversal", "{\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}");
        assertThat(reversal.statusCode()).as(reversal.body()).isEqualTo(201);
        assertThat(send("POST", "/api/v1/ledger-postings/" + id + "/release", "").statusCode()).isEqualTo(200);
        assertThat(count("SELECT count(*) AS total FROM transaction WHERE transaction_id=CAST(:key AS uuid) AND transaction_status='posted'", id)).isEqualTo(1);
    }

    @Test
    void optionalHttpCacheKeyCannotBypassTheDurableInputClaimOrLifecycle() throws Exception {
        String key = UUID.randomUUID().toString();
        var created = sendCached("/api/v1/ledger-postings", posting(key, "12.34", "12.34", "ordinary").replace("POSTED", "PENDING"), key);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        String id = json.readTree(created.body()).path("transaction").path("transactionId").asText();
        var conflict = sendCached("/api/v1/ledger-postings", posting(key, "99.00", "99.00", "ordinary").replace("POSTED", "PENDING"), key);
        assertThat(conflict.statusCode()).as(conflict.body()).isEqualTo(409);
        var confirmed = sendCached("/api/v1/ledger-postings/" + id + "/confirm", "", key);
        assertThat(confirmed.statusCode()).as(confirmed.body()).isEqualTo(200);
        assertThat(json.readTree(confirmed.body()).path("transaction").path("transactionStatus").asText()).isEqualTo("POSTED");
    }

    @Test
    void concurrentConfirmAndReleaseHaveOneDurableWinner() throws Exception {
        String key = UUID.randomUUID().toString();
        var created = send("POST", "/api/v1/ledger-postings", posting(key, "12.34", "12.34", "ordinary").replace("POSTED", "PENDING"));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        String id = json.readTree(created.body()).path("transaction").path("transactionId").asText();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var barrier = new java.util.concurrent.CyclicBarrier(2);
            var confirm = executor.submit(() -> { barrier.await(); return send("POST", "/api/v1/ledger-postings/" + id + "/confirm", ""); });
            var release = executor.submit(() -> { barrier.await(); return send("POST", "/api/v1/ledger-postings/" + id + "/release", ""); });
            var statuses = java.util.List.of(confirm.get(30, java.util.concurrent.TimeUnit.SECONDS).statusCode(),
                    release.get(30, java.util.concurrent.TimeUnit.SECONDS).statusCode());
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(count("SELECT count(*) AS total FROM transaction WHERE transaction_id=CAST(:key AS uuid) AND transaction_status IN ('posted','reversed')", id)).isEqualTo(1);
        assertThat(count("SELECT count(*) AS total FROM transaction_status_history WHERE transaction_id=CAST(:key AS uuid)", id)).isEqualTo(2);
    }

    @Test
    void publicHeaderCreateCannotUpsertManagedMoneyOrRemoveItsProtection() throws Exception {
        UUID id = managedServiceFixture();
        String before = aggregateSnapshot(id);
        var supplied = transactionService.getTransaction(id).block(Duration.ofSeconds(10));
        assertThat(supplied.getTransactionId()).isEqualTo(id);
        assertThat(supplied.getRowVersion()).isNotNull();
        supplied.setTotalAmount(new java.math.BigDecimal("99.00"));
        assertThatThrownBy(() -> transactionService.createTransaction(supplied).block(Duration.ofSeconds(10)))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST));
        assertThat(aggregateSnapshot(id)).isEqualTo(before);
    }

    @Test
    void publicHistoryCreateCannotReparentManagedEvidenceToALegacyTransaction() throws Exception {
        UUID id = managedServiceFixture();
        UUID legacy = legacyServiceFixture();
        UUID historyId = database.sql("SELECT transaction_status_history_id FROM transaction_status_history WHERE transaction_id=:id")
                .bind("id", id).map((row, metadata) -> row.get("transaction_status_history_id", UUID.class)).one().block();
        var supplied = historyService.getStatusHistory(id, historyId).block(Duration.ofSeconds(10));
        supplied.setReason("forged");
        String before = aggregateSnapshot(id);
        String parentBefore = aggregateSnapshot(legacy);
        assertThatThrownBy(() -> historyService.createStatusHistory(legacy, supplied).block(Duration.ofSeconds(10)))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST));
        assertThat(aggregateSnapshot(id)).isEqualTo(before);
        assertThat(aggregateSnapshot(legacy)).isEqualTo(parentBefore);
    }

    @Test
    void publicLegCreateRejectsSuppliedManagedIdentityBeforeAnyDatabaseConstraint() throws Exception {
        UUID id = managedServiceFixture();
        UUID legacy = legacyServiceFixture();
        UUID legId = database.sql("SELECT transaction_leg_id FROM transaction_leg WHERE transaction_id=:id ORDER BY transaction_leg_id LIMIT 1")
                .bind("id", id).map((row, metadata) -> row.get("transaction_leg_id", UUID.class)).one().block();
        var supplied = legService.getTransactionLeg(id, legId).block(Duration.ofSeconds(10));
        supplied.setAmount(new java.math.BigDecimal("99.00"));
        String before = aggregateSnapshot(id);
        String parentBefore = aggregateSnapshot(legacy);
        assertThatThrownBy(() -> legService.createTransactionLeg(legacy, supplied).block(Duration.ofSeconds(10)))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST));
        assertThat(aggregateSnapshot(id)).isEqualTo(before);
        assertThat(aggregateSnapshot(legacy)).isEqualTo(parentBefore);
    }

    @Test
    void publicHeaderCreateRejectsCallerVersionEvenWithoutAnIdentifier() throws Exception {
        UUID id = managedServiceFixture();
        var supplied = transactionService.getTransaction(id).block(Duration.ofSeconds(10));
        supplied.setTransactionId(null);
        String key = UUID.randomUUID().toString();
        supplied.setExternalReference(key);
        String before = aggregateSnapshot(id);
        assertThatThrownBy(() -> transactionService.createTransaction(supplied).block(Duration.ofSeconds(10)))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST));
        assertThat(aggregateSnapshot(id)).isEqualTo(before);
        assertThat(count("SELECT count(*) AS total FROM transaction WHERE external_reference=:key", key)).isZero();
    }

    @Test
    void publicCreateMethodsStillPersistFreshServerAssignedLegacyRecords() throws Exception {
        UUID legacy = legacyServiceFixture();
        var leg = new io.fireflysoftware.core.banking.ledger.interfaces.dtos.leg.TransactionLegDTO();
        leg.setAccountId(UUID.randomUUID());
        leg.setLegType("DEBIT");
        leg.setAmount(new java.math.BigDecimal("1.00"));
        leg.setCurrency("USD");
        var createdLeg = legService.createTransactionLeg(legacy, leg).block(Duration.ofSeconds(10));
        assertThat(createdLeg.getTransactionLegId()).isNotNull();
        assertThat(createdLeg.getTransactionId()).isEqualTo(legacy);
        var history = new io.fireflysoftware.core.banking.ledger.interfaces.dtos.core.TransactionStatusHistoryDTO();
        history.setStatusCode(io.fireflysoftware.core.banking.ledger.interfaces.enums.core.TransactionStatusEnum.POSTED);
        history.setStatusStartDatetime(java.time.LocalDateTime.now());
        history.setReason("Fresh synthetic legacy history");
        var createdHistory = historyService.createStatusHistory(legacy, history).block(Duration.ofSeconds(10));
        assertThat(createdHistory.getTransactionStatusHistoryId()).isNotNull();
        assertThat(createdHistory.getTransactionId()).isEqualTo(legacy);
        assertThat(count("SELECT count(*) AS total FROM transaction_status_history WHERE transaction_id=CAST(:key AS uuid)", legacy.toString())).isEqualTo(2);
    }

    private UUID managedServiceFixture() throws Exception {
        String key = UUID.randomUUID().toString();
        var response = send("POST", "/api/v1/ledger-postings", posting(key, "12.34", "12.34", "ordinary"));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return UUID.fromString(json.readTree(response.body()).path("transaction").path("transactionId").asText());
    }

    private UUID legacyServiceFixture() {
        var header = new io.fireflysoftware.core.banking.ledger.interfaces.dtos.core.TransactionDTO();
        header.setExternalReference(UUID.randomUUID().toString());
        header.setAccountId(UUID.randomUUID());
        header.setTransactionDate(java.time.LocalDateTime.now());
        header.setValueDate(java.time.LocalDateTime.now());
        header.setTransactionType(io.fireflysoftware.core.banking.ledger.interfaces.enums.core.TransactionTypeEnum.TRANSFER);
        header.setTransactionStatus(io.fireflysoftware.core.banking.ledger.interfaces.enums.core.TransactionStatusEnum.POSTED);
        header.setTotalAmount(new java.math.BigDecimal("1.00"));
        header.setCurrency("USD");
        var created = transactionService.createTransaction(header).block(Duration.ofSeconds(10));
        assertThat(created.getTransactionId()).isNotNull();
        assertThat(created.getRowVersion()).isZero();
        return created.getTransactionId();
    }

    private String aggregateSnapshot(UUID id) {
        return database.sql("SELECT jsonb_build_object('header',to_jsonb(t),'legs',(SELECT jsonb_agg(to_jsonb(l) ORDER BY l.transaction_leg_id) FROM transaction_leg l WHERE l.transaction_id=t.transaction_id),'history',(SELECT jsonb_agg(to_jsonb(h) ORDER BY h.transaction_status_history_id) FROM transaction_status_history h WHERE h.transaction_id=t.transaction_id),'claim',(SELECT to_jsonb(c) FROM ledger_posting_request c WHERE c.transaction_id=t.transaction_id))::text AS snapshot FROM transaction t WHERE t.transaction_id=:id")
                .bind("id", id).map((row, metadata) -> row.get("snapshot", String.class)).one().block(Duration.ofSeconds(10));
    }

    private HttpResponse<String> sendCached(String path, String body, String key) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json").header("X-Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private long count(String sql, String key) {
        return database.sql(sql).bind("key", key).map((row, metadata) -> row.get("total", Long.class))
                .one().block(Duration.ofSeconds(10));
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private String posting(String key, String debit, String credit, String secondLegDescription) {
        return """
                {"idempotencyKey":"%s","transaction":{
                  "externalReference":"%s","transactionDate":"2026-10-22T10:00:00",
                  "valueDate":"2026-10-22T10:00:00","bookingDate":"2026-10-22T10:00:00",
                  "transactionType":"TRANSFER","transactionStatus":"POSTED","totalAmount":%s,
                  "currency":"USD","accountId":"11111111-1111-1111-1111-111111111111"},
                  "legs":[{"accountId":"11111111-1111-1111-1111-111111111111","legType":"DEBIT","amount":%s,"currency":"USD"},
                          {"accountId":"22222222-2222-2222-2222-222222222222","legType":"CREDIT","amount":%s,"currency":"USD","description":"%s"}]}
                """.formatted(key, key, debit, debit, credit, secondLegDescription);
    }
}
