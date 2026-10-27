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

@org.springframework.context.annotation.Import({FundsRuntimeDatabaseTest.ClockFixture.class,FundsTestRoles.MigrationFixture.class})
@Testcontainers
@SpringBootTest(classes = LedgerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.address=127.0.0.1", "firefly.eda.enabled=false"})
class FundsRuntimeDatabaseTest {
    static final java.util.Map<UUID,java.util.Map<String,Object>> APPROVALS=new java.util.concurrent.ConcurrentHashMap<>();
    static final com.nimbusds.jose.jwk.RSAKey KEY;
    static final com.sun.net.httpserver.HttpServer JWKS;
    static {
        try {
            KEY = new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).keyID("owned-funds-fixture").generate();
            JWKS = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            JWKS.createContext("/jwks", exchange -> {
                byte[] body = new com.nimbusds.jose.jwk.JWKSet(KEY.toPublicJWK()).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            JWKS.start();
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    static String token(String tenant, java.util.List<UUID> accounts, String... scopes) throws Exception {
        var claims = new com.nimbusds.jwt.JWTClaimsSet.Builder().issuer("https://owned-funds-fixture.invalid")
                .audience("ledger-funds").subject("synthetic-operator").claim("tenant_id", tenant)
                .claim("scope", String.join(" ", scopes)).claim("funds_accounts", accounts.stream().map(UUID::toString).toList())
                .claim("funds_enrollments",accounts.stream().map(APPROVALS::get).filter(java.util.Objects::nonNull).toList())
                .expirationTime(java.util.Date.from(java.time.Instant.now().plusSeconds(300))).build();
        var signed = new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(), claims);
        signed.sign(new com.nimbusds.jose.crypto.RSASSASigner(KEY));
        return signed.serialize();
    }


    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("ledger_qualification")
            .withUsername("ledger_test")
            .withPassword("synthetic-disposable-test-password")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withPortBindings(
                    new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1", 0), ExposedPort.tcp(5432))));

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        prepareRuntimeRole();
        registry.add("funds.security.issuer", () -> "https://owned-funds-fixture.invalid");
        registry.add("funds.security.audience", () -> "ledger-funds");
        registry.add("funds.security.jwk-set-uri", () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
        registry.add("funds.currency-policy.version", () -> "owned-test-policy-v1");
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://" + DATABASE.getHost() + ":"
                + DATABASE.getMappedPort(5432) + "/" + DATABASE.getDatabaseName());
        registry.add("spring.r2dbc.username", ()->"funds_runtime");
        registry.add("spring.r2dbc.password", ()->FundsTestRoles.RUNTIME_PASSWORD);
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
    void managedFundsRoutesRequireTrustedIdentityBeforeEnrollment() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                + "/api/v1/tenants/synthetic-tenant/funds/accounts/" + UUID.randomUUID() + "/enrollment"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        assertThat(http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
    }
    @Test
    void trustedEnrollmentCreatesOnlyBlockedZeroFunds() throws Exception {
        UUID account = UUID.randomUUID();
        String body = "{\"enrollmentId\":\"" + UUID.randomUUID() + "\",\"contractId\":\"" + UUID.randomUUID()
                + "\",\"currency\":\"USD\",\"kind\":\"CUSTOMER\"}";
        approve(account,body);
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                + "/api/v1/tenants/owned/funds/accounts/" + account + "/enrollment"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token("owned", java.util.List.of(account), "funds:enroll","funds:tenant-bind"))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        var view = json.readTree(response.body());
        assertThat(view.path("state").asText()).isEqualTo("BLOCKED");
        assertThat(view.path("booked").decimalValue()).isEqualByComparingTo("0");
        assertThat(view.path("held").decimalValue()).isEqualByComparingTo("0");
        assertThat(view.path("available").decimalValue()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> database.sql("INSERT INTO transaction(account_id,external_reference,transaction_type,transaction_status,total_amount,currency,transaction_date) VALUES (:a,:r,'transfer','posted',1,'USD',CURRENT_TIMESTAMP)")
                .bind("a",account).bind("r",UUID.randomUUID().toString()).fetch().rowsUpdated().block())
                .hasMessageContaining("FND_NATIVE_COMMAND_REQUIRED");
        assertThat(adminCount("SELECT count(*) FROM transaction WHERE account_id=?",account)).isZero();
        assertThat(adminCount("SELECT count(*) FROM ledger_funds_account WHERE account_id=? AND state='BLOCKED' AND booked=0 AND held=0",account)).isEqualTo(1);
    }

    String path(UUID account) { return "/api/v1/tenants/owned/funds/accounts/"+account; }
    HttpResponse<String> call(String method,String path,String body,java.util.List<UUID> accounts,String... scopes) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type","application/json")
                .header("Authorization","Bearer "+token("owned",accounts,scopes));
        builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
        return http.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    UUID fixtureAccount(String kind) throws Exception {
        UUID account=UUID.randomUUID();
        String body="{\"enrollmentId\":\""+UUID.randomUUID()+"\",\"contractId\":\""+UUID.randomUUID()+"\",\"currency\":\"USD\",\"kind\":\""+kind+"\"}";
        approve(account,body);
        var response=call("POST",path(account)+"/enrollment",body,java.util.List.of(account),"funds:enroll","funds:bank-enroll","funds:tenant-bind");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        // This ledger-only transaction fixture isolates money invariants. A separate real-peer test qualifies activation.
        admin("UPDATE ledger_funds_account SET state='ACTIVE' WHERE account_id=?",account);
        return account;
    }
    @Test
    void reservationCommitMovesExactBookedMoneyAndConsumesHeldFundsOnce() throws Exception {
        UUID bank=fixtureAccount("BANK_CONTROL"), source=fixtureAccount("CUSTOMER"), destination=fixtureAccount("CUSTOMER");
        var grants=java.util.List.of(bank,source,destination);
        var funded=call("POST","/api/v1/tenants/owned/funds/funding","{\"operationId\":\""+UUID.randomUUID()+"\",\"bankControlAccountId\":\""+bank+"\",\"customerAccountId\":\""+source+"\",\"amount\":100.00,\"currency\":\"USD\"}",grants,"funds:fund");
        assertThat(funded.statusCode()).as(funded.body()).isEqualTo(201);
        UUID operation=UUID.randomUUID();
        String intent="{\"operationId\":\""+operation+"\",\"sourceAccountId\":\""+source+"\",\"destinationAccountId\":\""+destination+"\",\"amount\":25.00,\"currency\":\"USD\",\"ttlSeconds\":300}";
        var reserved=call("POST","/api/v1/tenants/owned/funds/reservations",intent,grants,"funds:reserve");
        assertThat(reserved.statusCode()).as(reserved.body()).isEqualTo(201);
        String id=json.readTree(reserved.body()).path("reservationId").asText();
        var balance=json.readTree(call("GET",path(source),null,grants,"funds:read").body());
        assertThat(balance.path("booked").decimalValue()).isEqualByComparingTo("100");
        assertThat(balance.path("held").decimalValue()).isEqualByComparingTo("25");
        assertThat(balance.path("available").decimalValue()).isEqualByComparingTo("75");
        var duplicate=call("POST","/api/v1/tenants/owned/funds/reservations",intent,grants,"funds:reserve");
        assertThat(json.readTree(duplicate.body()).path("reservationId").asText()).isEqualTo(id);
        assertThat(call("POST","/api/v1/tenants/owned/funds/reservations",intent.replace("25.00","26.00"),grants,"funds:reserve").statusCode()).isEqualTo(409);
        String command="{\"actionId\":\""+UUID.randomUUID()+"\",\"action\":\"COMMIT\"}";
        var committed=call("POST","/api/v1/tenants/owned/funds/reservations/"+id+"/actions",command,grants,"funds:commit");
        assertThat(committed.statusCode()).as(committed.body()).isEqualTo(200);
        assertThat(json.readTree(committed.body()).path("state").asText()).isEqualTo("COMMITTED");
        assertThat(call("POST","/api/v1/tenants/owned/funds/reservations/"+id+"/actions",command,grants,"funds:commit").statusCode()).isEqualTo(200);
        balance=json.readTree(call("GET",path(source),null,grants,"funds:read").body());
        assertThat(balance.path("booked").decimalValue()).isEqualByComparingTo("75");
        assertThat(balance.path("held").decimalValue()).isEqualByComparingTo("0");
        var credited=json.readTree(call("GET",path(destination),null,grants,"funds:read").body());
        assertThat(credited.path("booked").decimalValue()).isEqualByComparingTo("25");
    }
    static final MutableClock CLOCK=new MutableClock();
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    static class ClockFixture {
        @org.springframework.context.annotation.Bean java.time.Clock fundsClock() { return CLOCK; }
    }
    static final class MutableClock extends java.time.Clock {
        final java.util.concurrent.atomic.AtomicReference<java.time.Instant> now=new java.util.concurrent.atomic.AtomicReference<>(java.time.Instant.now());
        public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
        public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
        public java.time.Instant instant() { return now.get(); }
    }
    @org.junit.jupiter.api.BeforeEach
    void resetReconciliationObservationClock() { CLOCK.now.set(java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS)); }
    UUID fundedCustomer(String amount) throws Exception {
        UUID bank=fixtureAccount("BANK_CONTROL"),customer=fixtureAccount("CUSTOMER");
        var response=call("POST","/api/v1/tenants/owned/funds/funding",json.writeValueAsString(java.util.Map.of(
                "operationId",UUID.randomUUID(),"bankControlAccountId",bank,"customerAccountId",customer,"amount",new java.math.BigDecimal(amount),"currency","USD")),
                java.util.List.of(bank,customer),"funds:fund");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);return customer;
    }
    String reserveBody(UUID operation,UUID source,UUID destination,String amount,int ttl) throws Exception {
        return json.writeValueAsString(java.util.Map.of("operationId",operation,"sourceAccountId",source,"destinationAccountId",destination,
                "amount",new java.math.BigDecimal(amount),"currency","USD","ttlSeconds",ttl));
    }
    String reservation(UUID source,UUID destination,String amount,int ttl) throws Exception {
        var response=call("POST","/api/v1/tenants/owned/funds/reservations",reserveBody(UUID.randomUUID(),source,destination,amount,ttl),java.util.List.of(source,destination),"funds:reserve");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);return json.readTree(response.body()).path("reservationId").asText();
    }
    HttpResponse<String> action(String id,String action,java.util.List<UUID> accounts) throws Exception {
        return call("POST","/api/v1/tenants/owned/funds/reservations/"+id+"/actions",json.writeValueAsString(java.util.Map.of("actionId",UUID.randomUUID(),"action",action)),accounts,"funds:"+action.toLowerCase());
    }
    @Test
    void fundingCannotIntroduceAnotherCurrencyIntoTheManagedBook() throws Exception {
        UUID bank=fixtureAccount("BANK_CONTROL"),customer=fixtureAccount("CUSTOMER");
        var response=call("POST","/api/v1/tenants/owned/funds/funding",json.writeValueAsString(java.util.Map.of(
                "operationId",UUID.randomUUID(),"bankControlAccountId",bank,"customerAccountId",customer,"amount",1,"currency","EUR")),java.util.List.of(bank,customer),"funds:fund");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
        assertThat(json.readTree(call("GET",path(customer),null,java.util.List.of(customer),"funds:read").body()).path("booked").decimalValue()).isEqualByComparingTo("0");
    }
    @Test
    void currentResourceGrantIsRequiredEvenForAnIdenticalReservationReplay() throws Exception {
        UUID source=fundedCustomer("10"),destination=fixtureAccount("CUSTOMER"),operation=UUID.randomUUID();
        String body=reserveBody(operation,source,destination,"3",300);
        var granted=call("POST","/api/v1/tenants/owned/funds/reservations",body,java.util.List.of(source,destination),"funds:reserve");
        assertThat(granted.statusCode()).isEqualTo(201);
        assertThat(call("POST","/api/v1/tenants/owned/funds/reservations",body,java.util.List.of(destination),"funds:reserve").statusCode()).isEqualTo(403);
    }
    @Test
    void concurrentDistinctSpendsCannotReserveMoreThanAvailableMoney() throws Exception {
        UUID source=fundedCustomer("100"),destination=fixtureAccount("CUSTOMER");
        var grants=java.util.List.of(source,destination);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var futures=java.util.stream.IntStream.range(0,100).mapToObj(i->executor.submit(()->call("POST","/api/v1/tenants/owned/funds/reservations",
                    reserveBody(UUID.randomUUID(),source,destination,"2",300),grants,"funds:reserve"))).toList();
            int accepted=0,denied=0;
            for(var future:futures) { var response=future.get(90,java.util.concurrent.TimeUnit.SECONDS);if(response.statusCode()==201) accepted++;else { assertThat(response.statusCode()).as(response.body()).isEqualTo(409);denied++; } }
            assertThat(accepted).isEqualTo(50);assertThat(denied).isEqualTo(50);
        }
        var balance=json.readTree(call("GET",path(source),null,grants,"funds:read").body());
        assertThat(balance.path("held").decimalValue()).isEqualByComparingTo("100");
        assertThat(balance.path("available").decimalValue()).isEqualByComparingTo("0");
    }
    @Test
    void databaseExpiredReservationRejectsCommitAndReleasesTheSameHoldOnce() throws Exception {
        UUID source=fundedCustomer("10"),destination=fixtureAccount("CUSTOMER");var grants=java.util.List.of(source,destination);
        String id=reservation(source,destination,"4",1);awaitReservationExpiry(id);
        assertThat(action(id,"COMMIT",grants).statusCode()).isEqualTo(409);
        var expired=action(id,"EXPIRE",grants);assertThat(expired.statusCode()).as(expired.body()).isEqualTo(200);
        assertThat(json.readTree(expired.body()).path("state").asText()).isEqualTo("EXPIRED");
        assertThat(action(id,"EXPIRE",grants).statusCode()).isEqualTo(200);
        var balance=json.readTree(call("GET",path(source),null,grants,"funds:read").body());
        assertThat(balance.path("booked").decimalValue()).isEqualByComparingTo("10");assertThat(balance.path("held").decimalValue()).isEqualByComparingTo("0");
    }
    @Test
    void confirmAndReleaseHaveOneDurableWinner() throws Exception {
        UUID source=fundedCustomer("10"),destination=fixtureAccount("CUSTOMER");var grants=java.util.List.of(source,destination);
        String id=reservation(source,destination,"4",300);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var confirm=executor.submit(()->action(id,"COMMIT",grants));var release=executor.submit(()->action(id,"RELEASE",grants));
            assertThat(java.util.List.of(confirm.get().statusCode(),release.get().statusCode())).containsExactlyInAnyOrder(200,409);
        }
        var balance=json.readTree(call("GET",path(source),null,grants,"funds:read").body());
        assertThat(balance.path("held").decimalValue()).isEqualByComparingTo("0");
        assertThat(balance.path("booked").decimalValue()).satisfiesAnyOf(
                value->assertThat(value).isEqualByComparingTo("6"),value->assertThat(value).isEqualByComparingTo("10"));
    }
    @Test
    void aFailedAuditWriteRollsBackPostingFundsAndTheOperationClaim() throws Exception {
        UUID source=fundedCustomer("10"),destination=fixtureAccount("CUSTOMER"),operation=UUID.randomUUID();var grants=java.util.List.of(source,destination);
        String body=reserveBody(operation,source,destination,"4",300),constraint="fault_"+UUID.randomUUID().toString().replace("-","");
        admin("ALTER TABLE ledger_funds_event ADD CONSTRAINT "+constraint+" CHECK(event_type<>'FUNDS_RESERVED')");
        try {
            var failed=call("POST","/api/v1/tenants/owned/funds/reservations",body,grants,"funds:reserve");assertThat(failed.statusCode()).isGreaterThanOrEqualTo(400);
            assertThat(adminCount("SELECT COUNT(*) FROM ledger_funds_operation WHERE operation_id=?",operation)).isZero();
            assertThat(json.readTree(call("GET",path(source),null,grants,"funds:read").body()).path("held").decimalValue()).isEqualByComparingTo("0");
        } finally { admin("ALTER TABLE ledger_funds_event DROP CONSTRAINT "+constraint); }
        assertThat(call("POST","/api/v1/tenants/owned/funds/reservations",body,grants,"funds:reserve").statusCode()).isEqualTo(201);
    }
    @Test
    void fullLinkedReversalPreservesOriginalPostingAndNeverDuplicatesMoney() throws Exception {
        UUID source=fundedCustomer("10"),destination=fixtureAccount("CUSTOMER");var grants=java.util.List.of(source,destination);
        String id=reservation(source,destination,"4",300);assertThat(action(id,"COMMIT",grants).statusCode()).isEqualTo(200);
        var reversed=action(id,"REVERSE",grants);assertThat(reversed.statusCode()).as(reversed.body()).isEqualTo(200);
        var result=json.readTree(reversed.body());assertThat(result.path("state").asText()).isEqualTo("REVERSED");assertThat(result.path("reversalId").asText()).isNotBlank();
        assertThat(action(id,"REVERSE",grants).statusCode()).isEqualTo(200);
        assertThat(json.readTree(call("GET",path(source),null,grants,"funds:read").body()).path("booked").decimalValue()).isEqualByComparingTo("10");
        assertThat(adminString("SELECT transaction_status::text FROM transaction WHERE transaction_id=?",UUID.fromString(result.path("postingId").asText()))).isEqualTo("posted");
        String retainedAudit=adminString("SELECT jsonb_agg(to_jsonb(e) ORDER BY e.event_id)::text FROM ledger_funds_event e WHERE resource_id=?",UUID.fromString(id));
        String retainedReceipts=adminString("SELECT jsonb_agg(to_jsonb(r) ORDER BY r.receipt_id)::text FROM ledger_funds_monetary_receipt r WHERE reservation_id=?",UUID.fromString(id));
        assertThatThrownBy(()->admin("UPDATE ledger_funds_event SET actor='forged' WHERE resource_id=?",UUID.fromString(id))).hasMessageContaining("FND_NATIVE_COMMAND_REQUIRED");
        assertThat(adminString("SELECT jsonb_agg(to_jsonb(e) ORDER BY e.event_id)::text FROM ledger_funds_event e WHERE resource_id=?",UUID.fromString(id))).isEqualTo(retainedAudit);
        assertThat(adminString("SELECT jsonb_agg(to_jsonb(r) ORDER BY r.receipt_id)::text FROM ledger_funds_monetary_receipt r WHERE reservation_id=?",UUID.fromString(id))).isEqualTo(retainedReceipts);
        assertThat(adminCount("SELECT count(*) FROM ledger_funds_account WHERE (account_id=? AND booked=10 OR account_id=? AND booked=0) AND held=0",source,destination)).isEqualTo(2);
    }

    @Test
    void httpMonetaryEvidenceRetainsOriginalIntentExactEffectsAndCommitBytesAfterReversal() throws Exception {
        UUID source=fundedCustomer("10"),destination=fixtureAccount("CUSTOMER");
        var grants=java.util.List.of(source,destination);
        String id=reservation(source,destination,"2.50",300);
        String url="/api/v1/tenants/owned/funds/reservations/"+id+"/monetary-receipts";
        var heldResponse=call("GET",url,null,grants,"funds:read");
        assertThat(heldResponse.statusCode()).as(heldResponse.body()).isEqualTo(200);
        var held=json.readTree(heldResponse.body());
        assertThat(held.path("evidenceStatus").asText()).isEqualTo("RETAINED");
        assertThat(held.path("commit").isNull()).isTrue();
        assertThat(held.path("reversal").isNull()).isTrue();
        var accepted=json.treeToValue(held.path("acceptedReserve"),io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsReceiptContracts.AcceptedReserveEnvelope.class);
        io.fireflysoftware.core.banking.ledger.interfaces.protocol.receipt.FundsReceiptCodec.verify(accepted);
        assertThat(accepted.value().intent().amount()).isEqualByComparingTo("2.50");
        assertThat(accepted.value().expiresAtEpochMillis()-accepted.value().intentRecordedAtEpochMillis()).isEqualTo(300000);
        assertThat(accepted.value().intentRecordedAtEpochMillis()).isEqualTo(Long.parseLong(adminString("SELECT intent_recorded_at_epoch_millis FROM ledger_funds_reserve_intent WHERE reservation_id=?",UUID.fromString(id))));
        assertThat(action(id,"COMMIT",grants).statusCode()).isEqualTo(200);
        var committedResponse=call("GET",url,null,grants,"funds:read");
        assertThat(committedResponse.statusCode()).as(committedResponse.body()).isEqualTo(200);
        var committed=json.readTree(committedResponse.body());
        var commit=json.treeToValue(committed.path("commit"),io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsReceiptContracts.MonetaryReceiptEnvelope.class);
        io.fireflysoftware.core.banking.ledger.interfaces.protocol.receipt.FundsReceiptCodec.verify(commit);
        assertThat(commit.value().effect().sourceMinorUnits()).isEqualTo("250");
        assertThat(commit.value().effect().destinationMinorUnits()).isEqualTo("250");
        assertThat(commit.value().effect().sourceBalances().bookedAfter()).isEqualByComparingTo("7.50");
        assertThat(commit.value().effect().sourceBalances().heldAfter()).isEqualByComparingTo("0");
        assertThat(commit.value().effectRecordedAtEpochMillis()).isGreaterThanOrEqualTo(accepted.value().intentRecordedAtEpochMillis());
        assertThat(action(id,"REVERSE",grants).statusCode()).isEqualTo(200);
        var reversedResponse=call("GET",url,null,grants,"funds:read");
        assertThat(reversedResponse.statusCode()).as(reversedResponse.body()).isEqualTo(200);
        var reversed=json.readTree(reversedResponse.body());
        assertThat(reversed.path("acceptedReserve")).isEqualTo(held.path("acceptedReserve"));
        assertThat(reversed.path("commit")).isEqualTo(committed.path("commit"));
        var reversal=json.treeToValue(reversed.path("reversal"),io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsReceiptContracts.MonetaryReceiptEnvelope.class);
        io.fireflysoftware.core.banking.ledger.interfaces.protocol.receipt.FundsReceiptCodec.verify(reversal);
        io.fireflysoftware.core.banking.ledger.interfaces.protocol.receipt.FundsReceiptCodec.validateReversal(reversal.value(),commit.value());
        assertThat(call("GET",url,null,java.util.List.of(source),"funds:read").statusCode()).isEqualTo(404);
    }
    @Test
    void invalidPrecisionAndOverflowLeaveNoOperationClaim() throws Exception {
        UUID source=fundedCustomer("10"),destination=fixtureAccount("CUSTOMER");var grants=java.util.List.of(source,destination);
        for(String amount:java.util.List.of("1.001","100000000000000000000")) {
            UUID operation=UUID.randomUUID();var response=call("POST","/api/v1/tenants/owned/funds/reservations",reserveBody(operation,source,destination,amount,300),grants,"funds:reserve");
            assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
            assertThat(adminCount("SELECT COUNT(*) FROM ledger_funds_operation WHERE operation_id=?",operation)).isZero();
        }
    }

    @Test
    void activationCannotTrustCallerClaimsWithoutCommittedAccountsPeerVerification() throws Exception {
        UUID account=UUID.randomUUID();
        String begin=json.writeValueAsString(java.util.Map.of("enrollmentId",UUID.randomUUID(),"contractId",UUID.randomUUID(),"currency","USD","kind","CUSTOMER"));
        approve(account,begin);
        assertThat(call("POST",path(account)+"/enrollment",begin,java.util.List.of(account),"funds:enroll","funds:tenant-bind").statusCode()).isEqualTo(201);
        var response=call("POST",path(account)+"/controls",json.writeValueAsString(java.util.Map.of("actionId",UUID.randomUUID(),"state","ACTIVE")),java.util.List.of(account),"funds:control");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(503);
        assertThat(json.readTree(call("GET",path(account),null,java.util.List.of(account),"funds:read").body()).path("state").asText()).isEqualTo("BLOCKED");
    }

    @Test
    void financialRuntimeMustNotUseOwnerSuperuserOrBypassRls() {
        var privileges=database.sql("SELECT rolsuper,rolbypassrls,(SELECT pg_get_userbyid(relowner)=current_user FROM pg_class WHERE oid='transaction'::regclass) AS owner FROM pg_roles WHERE rolname=current_user")
            .map((r,m)->java.util.List.of(r.get("rolsuper",Boolean.class),r.get("rolbypassrls",Boolean.class),r.get("owner",Boolean.class))).one().block();
        assertThat(privileges).containsExactly(false,false,false);
        assertThat(database.sql("SELECT count(*) AS n FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relname LIKE 'ledger_search_%' AND (has_table_privilege(current_user,c.oid,'INSERT') OR has_table_privilege(current_user,c.oid,'UPDATE') OR has_table_privilege(current_user,c.oid,'DELETE'))")
            .map((r,m)->r.get("n",Long.class)).one().block()).isZero();
    }
    @Test
    void genericLifecycleReplayAndRepositoryReadCannotExposeManagedMoney() throws Exception {
        UUID source=fundedCustomer("10"),destination=fixtureAccount("CUSTOMER");var grants=java.util.List.of(source,destination);
        String id=reservation(source,destination,"4",300);var committed=action(id,"COMMIT",grants);assertThat(committed.statusCode()).isEqualTo(200);
        UUID posting=UUID.fromString(json.readTree(committed.body()).path("postingId").asText());
        var legacy=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/ledger-postings/"+posting+"/confirm"))
            .POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(legacy.statusCode()).as(legacy.body()).isEqualTo(403);
        assertThat(database.sql("SELECT transaction_id FROM transaction WHERE transaction_id=:id").bind("id",posting).map((r,m)->r.get("transaction_id",UUID.class)).one().block()).isNull();
    }

    static void prepareRuntimeRole() {
        FundsTestRoles.prepare(DATABASE);
    }
    static void admin(String sql,Object... arguments) throws Exception {
        try(var connection=DriverManager.getConnection(DATABASE.getJdbcUrl(),DATABASE.getUsername(),DATABASE.getPassword());var statement=connection.prepareStatement(sql)) {
            for(int i=0;i<arguments.length;i++) statement.setObject(i+1,arguments[i]);statement.execute();
        }
    }
    static long adminCount(String sql,Object... arguments) throws Exception {
        try(var connection=DriverManager.getConnection(DATABASE.getJdbcUrl(),DATABASE.getUsername(),DATABASE.getPassword());var statement=connection.prepareStatement(sql)) {
            for(int i=0;i<arguments.length;i++) statement.setObject(i+1,arguments[i]);try(var result=statement.executeQuery()) { assertThat(result.next()).isTrue();return result.getLong(1); }
        }
    }
    static String adminString(String sql,Object... arguments) throws Exception {
        try(var connection=DriverManager.getConnection(DATABASE.getJdbcUrl(),DATABASE.getUsername(),DATABASE.getPassword());var statement=connection.prepareStatement(sql)) {
            for(int i=0;i<arguments.length;i++) statement.setObject(i+1,arguments[i]);try(var result=statement.executeQuery()) { assertThat(result.next()).isTrue();return result.getString(1); }
        }
    }
    @Autowired io.fireflysoftware.core.banking.ledger.core.services.funds.FundsMoneyService fundsMoney;
    @Test
    void forcedPoliciesAndPrivatePredicatePrivilegesAreActuallyEnforced() {
        assertThat(database.sql("SELECT bool_and(c.relrowsecurity AND c.relforcerowsecurity) AS enforced FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relname IN ('transaction','transaction_leg','transaction_status_history','ledger_posting_request','ledger_funds_account','ledger_funds_reservation','ledger_funds_operation','ledger_funds_action','ledger_funds_control','ledger_funds_event','event_outbox')")
                .map((r,m)->r.get("enforced",Boolean.class)).one().block()).isTrue();
        assertThatThrownBy(()->database.sql("SELECT * FROM funds_private.visibility").fetch().all().collectList().block()).hasMessageContaining("permission denied");
        assertThatThrownBy(()->database.sql("CREATE OR REPLACE FUNCTION public.funds_reference_allowed(TEXT,UUID,BOOLEAN) RETURNS BOOLEAN LANGUAGE SQL AS 'SELECT TRUE'")
                .fetch().rowsUpdated().block()).isInstanceOf(org.springframework.dao.PermissionDeniedDataAccessException.class);
        assertThatThrownBy(()->database.sql("DROP POLICY funds_select ON public.transaction").fetch().rowsUpdated().block()).hasMessageContaining("owner");
        assertThatThrownBy(()->database.sql("CREATE FUNCTION public.owned_forbidden() RETURNS BOOLEAN LANGUAGE SQL AS 'SELECT TRUE'").fetch().rowsUpdated().block()).hasMessageContaining("permission denied");
    }
    void assertPoolHasNoInheritedFundsAuthority() {
        var rows=reactor.core.publisher.Flux.range(0,20).flatMap(i->database.sql("SELECT pg_backend_pid() AS pid,pg_sleep(0.05),COALESCE(NULLIF(current_setting('firefly.funds.tenant',true),''),'none') AS tenant,COALESCE(NULLIF(current_setting('firefly.funds.accounts',true),''),'none') AS accounts,COALESCE(NULLIF(current_setting('firefly.funds.purpose',true),''),'none') AS purpose")
                .map((r,m)->java.util.List.of(r.get("pid",Integer.class).toString(),r.get("tenant",String.class),r.get("accounts",String.class),r.get("purpose",String.class))).one(),10)
                .collectList().block(Duration.ofSeconds(10));
        assertThat(rows).hasSize(20);
        assertThat(rows.stream().map(row->row.getFirst()).distinct().count()).isGreaterThan(1);
        rows.forEach(row->assertThat(row.subList(1,4)).containsExactly("none","none","none"));
    }
    @Test
    void committedFundsContextNeverSurvivesConnectionPoolReuse() throws Exception {
        UUID source=fundedCustomer("10"),destination=fixtureAccount("CUSTOMER");reservation(source,destination,"2",300);
        assertPoolHasNoInheritedFundsAuthority();
        assertThat(database.sql("SELECT COUNT(*) AS n FROM ledger_funds_account").map((r,m)->r.get("n",Long.class)).one().block()).isZero();
    }
    @Test
    void cancelledReactiveTransactionRollsBackAndClearsResourceAuthority() throws Exception {
        UUID source=fundedCustomer("10"),destination=fixtureAccount("CUSTOMER"),operation=UUID.randomUUID();
        String suffix=UUID.randomUUID().toString().replace("-","");
        admin("CREATE FUNCTION public.wait_"+suffix+"() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN IF NEW.event_type='FUNDS_RESERVED' THEN PERFORM pg_sleep(5); END IF; RETURN NEW; END $$");
        admin("CREATE TRIGGER wait_"+suffix+" BEFORE INSERT ON ledger_funds_event FOR EACH ROW EXECUTE FUNCTION public.wait_"+suffix+"()");
        var principal=org.fireflyframework.security.api.domain.SecurityPrincipal.builder().subject("owned-cancellation-fixture").issuer("https://owned-funds-fixture.invalid").tenantId("owned")
                .scopes(java.util.Set.of("funds:reserve")).claims(java.util.Map.of("exp",java.time.Instant.now().plusSeconds(300),"funds_accounts",java.util.List.of(source.toString(),destination.toString()))).build();
        var input=new io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.Reserve(operation,source,destination,new java.math.BigDecimal("3"),"USD",300);
        var subscription=fundsMoney.reserve(principal,"owned",input).subscribe(ignored->{},ignored->{});
        try {
            boolean waiting=false;
            for(int attempt=0;attempt<100;attempt++) {
                if(adminCount("SELECT COUNT(*) FROM pg_stat_activity WHERE usename='funds_runtime' AND wait_event='PgSleep'")>0) { waiting=true;break; }
                Thread.sleep(25);
            }
            assertThat(waiting).isTrue();subscription.dispose();
            boolean rolledBack=false;
            for(int attempt=0;attempt<100;attempt++) {
                if(adminCount("SELECT COUNT(*) FROM pg_stat_activity WHERE usename='funds_runtime' AND state='idle in transaction'")==0
                        && adminCount("SELECT COUNT(*) FROM ledger_funds_operation WHERE operation_id=?",operation)==0) { rolledBack=true;break; }
                Thread.sleep(25);
            }
            assertThat(rolledBack).isTrue();
        } finally {
            subscription.dispose();admin("DROP TRIGGER wait_"+suffix+" ON ledger_funds_event");admin("DROP FUNCTION public.wait_"+suffix+"()");
        }
        assertPoolHasNoInheritedFundsAuthority();
        assertThat(adminCount("SELECT COUNT(*) FROM ledger_funds_operation WHERE operation_id=?",operation)).isZero();
        var balance=json.readTree(call("GET",path(source),null,java.util.List.of(source),"funds:read").body());
        assertThat(balance.path("held").decimalValue()).isEqualByComparingTo("0");
    }
    @Test
    void oneAccountGrantCannotReadAWholeTwoAccountReservation() throws Exception {
        UUID source=fundedCustomer("10"),destination=fixtureAccount("CUSTOMER");String id=reservation(source,destination,"2",300);
        var response=call("GET","/api/v1/tenants/owned/funds/reservations/"+id,null,java.util.List.of(source),"funds:read");
        assertThat(response.statusCode()).isEqualTo(404);
        var wrongTenant=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path(source)))
                .header("Authorization","Bearer "+token("other",java.util.List.of(source),"funds:read")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(wrongTenant.statusCode()).isEqualTo(403);
    }

    @Test
    void boundedExpiryWorkerDiscoversDurableDueReservationsWithoutAnInMemoryTimer() throws Exception {
        UUID source=fundedCustomer("10"),destination=fixtureAccount("CUSTOMER");var grants=java.util.List.of(source,destination);
        String id=reservation(source,destination,"4",1);awaitReservationExpiry(id);
        var response=call("POST","/api/v1/tenants/owned/funds/reservations/expiry","{\"limit\":10}",grants,"funds:expire");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(json.readTree(response.body()).toString()).contains(id,"EXPIRED");
        assertThat(call("POST","/api/v1/tenants/owned/funds/reservations/expiry","{\"limit\":101}",grants,"funds:expire").statusCode()).isEqualTo(400);
        assertThat(json.readTree(call("GET",path(source),null,grants,"funds:read").body()).path("held").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void blockedLedgerEnrollmentStillRequiresExplicitIssuerApprovedTenantBinding() throws Exception {
        UUID account=UUID.randomUUID();
        String input=json.writeValueAsString(java.util.Map.of("enrollmentId",UUID.randomUUID(),"contractId",UUID.randomUUID(),"currency","USD","kind","CUSTOMER"));
        var response=call("POST",path(account)+"/enrollment",input,java.util.List.of(account),"funds:enroll","funds:tenant-bind");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(403);
        assertThat(adminCount("SELECT COUNT(*) FROM ledger_funds_account WHERE account_id=?",account)).isZero();
    }

    void approve(UUID account,String body) throws Exception {
        var input=json.readTree(body);
        APPROVALS.put(account,java.util.Map.of("tenantId","owned","accountId",account.toString(),"contractId",input.path("contractId").asText(),"currency",input.path("currency").asText(),"kind",input.path("kind").asText()));
    }
    @Test
    void hiddenOperationCollisionIsAnExplicitConflictWithoutEmptyCreatedResponse() throws Exception {
        UUID bankA=fixtureAccount("BANK_CONTROL"),accountA=fixtureAccount("CUSTOMER"),bankB=fixtureAccount("BANK_CONTROL"),accountB=fixtureAccount("CUSTOMER"),operation=UUID.randomUUID();
        String first=json.writeValueAsString(java.util.Map.of("operationId",operation,"bankControlAccountId",bankA,"customerAccountId",accountA,"amount",1,"currency","USD"));
        String second=json.writeValueAsString(java.util.Map.of("operationId",operation,"bankControlAccountId",bankB,"customerAccountId",accountB,"amount",1,"currency","USD"));
        assertThat(call("POST","/api/v1/tenants/owned/funds/funding",first,java.util.List.of(bankA,accountA),"funds:fund").statusCode()).isEqualTo(201);
        var conflict=call("POST","/api/v1/tenants/owned/funds/funding",second,java.util.List.of(bankB,accountB),"funds:fund");
        assertThat(conflict.statusCode()).as(conflict.body()).isEqualTo(409);assertThat(conflict.body()).isNotBlank();
        assertThat(json.readTree(call("GET",path(accountB),null,java.util.List.of(accountB),"funds:read").body()).path("booked").decimalValue()).isEqualByComparingTo("0");
    }
    @Test
    void hiddenActionCollisionCannotReturnEmptySuccessOrCommitAnotherReservation() throws Exception {
        UUID a=fundedCustomer("10"),b=fixtureAccount("CUSTOMER"),c=fundedCustomer("10"),d=fixtureAccount("CUSTOMER"),action=UUID.randomUUID();
        String first=reservation(a,b,"2",300),second=reservation(c,d,"2",300);
        String command=json.writeValueAsString(java.util.Map.of("actionId",action,"action","COMMIT"));
        assertThat(call("POST","/api/v1/tenants/owned/funds/reservations/"+first+"/actions",command,java.util.List.of(a,b),"funds:commit").statusCode()).isEqualTo(200);
        var conflict=call("POST","/api/v1/tenants/owned/funds/reservations/"+second+"/actions",command,java.util.List.of(c,d),"funds:commit");
        assertThat(conflict.statusCode()).as(conflict.body()).isEqualTo(409);assertThat(conflict.body()).isNotBlank();
        assertThat(json.readTree(call("GET",path(c),null,java.util.List.of(c),"funds:read").body()).path("held").decimalValue()).isEqualByComparingTo("2");
    }
    @Test
    void hiddenControlCollisionCannotReturnEmptySuccessOrFreezeAnotherAccount() throws Exception {
        UUID first=fixtureAccount("CUSTOMER"),second=fixtureAccount("CUSTOMER"),action=UUID.randomUUID();
        String command=json.writeValueAsString(java.util.Map.of("actionId",action,"state","BLOCKED"));
        assertThat(call("POST",path(first)+"/controls",command,java.util.List.of(first),"funds:control").statusCode()).isEqualTo(200);
        var conflict=call("POST",path(second)+"/controls",command,java.util.List.of(second),"funds:control");
        assertThat(conflict.statusCode()).as(conflict.body()).isEqualTo(409);assertThat(conflict.body()).isNotBlank();
        assertThat(json.readTree(call("GET",path(second),null,java.util.List.of(second),"funds:read").body()).path("state").asText()).isEqualTo("ACTIVE");
    }
    @Test
    void partialOwnershipOfAnyProtectedRelationDisablesManagedFundsAuthority() throws Exception {
        UUID account=fixtureAccount("CUSTOMER");
        admin("ALTER TABLE public.transaction_leg OWNER TO funds_runtime");
        try { assertThat(call("GET",path(account),null,java.util.List.of(account),"funds:read").statusCode()).isEqualTo(503); }
        finally { admin("ALTER TABLE public.transaction_leg OWNER TO ledger_test"); admin("GRANT SELECT,INSERT,UPDATE,DELETE ON public.transaction_leg TO funds_runtime"); }
    }

    @Test
    void legacyOppositePairPostingRequestsPrelockEveryAccountInOneOrder() throws Exception {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();String marker="legacy_gate_"+UUID.randomUUID().toString().replace("-","");
        admin("CREATE FUNCTION public."+marker+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.description='"+marker+"' THEN PERFORM pg_sleep(0.5); END IF; RETURN NEW; END $$");
        admin("CREATE TRIGGER "+marker+" AFTER INSERT ON public.transaction FOR EACH ROW EXECUTE FUNCTION public."+marker+"()");
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            for(int iteration=0;iteration<4;iteration++) {
                var start=new java.util.concurrent.CountDownLatch(1);
                var left=executor.submit(()->{start.await();return legacyPosting(a,b,marker);});
                var right=executor.submit(()->{start.await();return legacyPosting(b,a,marker);});
                start.countDown();
                var l=left.get(15,java.util.concurrent.TimeUnit.SECONDS);var r=right.get(15,java.util.concurrent.TimeUnit.SECONDS);
                assertThat(l.statusCode()).as(l.body()).isEqualTo(201);assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
            }
        } finally {admin("DROP TRIGGER "+marker+" ON public.transaction");admin("DROP FUNCTION public."+marker+"()");}
    }
    HttpResponse<String> legacyPosting(UUID debit,UUID credit,String marker) throws Exception {
        String body=json.writeValueAsString(java.util.Map.of("idempotencyKey",UUID.randomUUID(),"transaction",
            java.util.Map.of("accountId",debit,"transactionType","TRANSFER","transactionStatus","POSTED","totalAmount",1,"currency","USD","description",marker),
            "legs",java.util.List.of(java.util.Map.of("accountId",debit,"legType","DEBIT","amount",1,"currency","USD"),java.util.Map.of("accountId",credit,"legType","CREDIT","amount",1,"currency","USD"))));
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/ledger-postings"))
            .timeout(Duration.ofSeconds(12)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void pinnedDeadlineBoundsNewHoldsAndRemainsPartOfImmutableReplayIntent() throws Exception {
        UUID source=fundedCustomer("10"),destination=fixtureAccount("CUSTOMER"),operation=UUID.randomUUID();var grants=java.util.List.of(source,destination);
        long deadline=databaseMillis()+2000;
        var body=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(reserveBody(operation,source,destination,"2",300));body.put("notAfterEpochMillis",deadline);
        var created=call("POST","/api/v1/tenants/owned/funds/reservations",body.toString(),grants,"funds:reserve");
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        var result=json.readTree(created.body());
        assertThat(java.time.Instant.parse(result.path("expiresAt").asText()).toEpochMilli()).isEqualTo(deadline);
        awaitDatabaseTime(deadline);
        var replay=call("POST","/api/v1/tenants/owned/funds/reservations",body.toString(),grants,"funds:reserve");
        assertThat(replay.statusCode()).as(replay.body()).isEqualTo(201);assertThat(json.readTree(replay.body()).path("reservationId")).isEqualTo(result.path("reservationId"));
        body.put("notAfterEpochMillis",deadline+1);
        assertThat(call("POST","/api/v1/tenants/owned/funds/reservations",body.toString(),grants,"funds:reserve").statusCode()).isEqualTo(409);
        body.put("operationId",UUID.randomUUID().toString());body.put("notAfterEpochMillis",deadline);
        assertThat(call("POST","/api/v1/tenants/owned/funds/reservations",body.toString(),grants,"funds:reserve").statusCode()).isEqualTo(409);
        assertThat(adminCount("SELECT count(*) FROM ledger_funds_operation WHERE operation_id=?",UUID.fromString(body.path("operationId").asText()))).isZero();
        assertThat(call("POST","/api/v1/tenants/owned/funds/reservations",body.toString(),grants,"funds:reserve").statusCode()).isEqualTo(409);
    }
    @Test
    void deadlineMustBeAnExactBoundedIntegerInsteadOfAStringFloatOrOverflow() throws Exception {
        UUID source=fundedCustomer("10"),destination=fixtureAccount("CUSTOMER");var grants=java.util.List.of(source,destination);
        var body=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(reserveBody(UUID.randomUUID(),source,destination,"2",300));
        for(String value:java.util.List.of("1.1","\"12345\"","9223372036854775808","-1","253402300800000")) {
            String input=body.toString().replace("}",",\"notAfterEpochMillis\":"+value+"}");
            var denied=call("POST","/api/v1/tenants/owned/funds/reservations",input,grants,"funds:reserve");
            assertThat(denied.statusCode()).as(value+": "+denied.body()).isEqualTo(400);
        }
    }
    @Test
    void byOperationRecoveryLookupRequiresCurrentExactPairPermissionsAndExplicitAbsence() throws Exception {
        UUID source=fundedCustomer("10"),destination=fixtureAccount("CUSTOMER"),operation=UUID.randomUUID(),other=fixtureAccount("CUSTOMER");var grants=java.util.List.of(source,destination);
        var created=call("POST","/api/v1/tenants/owned/funds/reservations",reserveBody(operation,source,destination,"2",300),grants,"funds:reserve");
        assertThat(created.statusCode()).isEqualTo(201);
        String url="/api/v1/tenants/owned/funds/reservations/by-operation/"+operation;
        var found=call("GET",url,null,grants,"funds:read");assertThat(found.statusCode()).as(found.body()).isEqualTo(200);
        assertThat(json.readTree(found.body()).path("reservationId")).isEqualTo(json.readTree(created.body()).path("reservationId"));
        assertThat(call("GET",url,null,java.util.List.of(source),"funds:read").statusCode()).isEqualTo(404);
        assertThat(call("GET",url,null,java.util.List.of(other),"funds:read").statusCode()).isEqualTo(404);
        assertThat(call("GET",url,null,grants,"funds:reserve").statusCode()).isEqualTo(403);
        assertThat(call("GET",url.replace("owned","different"),null,grants,"funds:read").statusCode()).isEqualTo(403);
        assertThat(call("GET",url.replace(operation.toString(),UUID.randomUUID().toString()),null,grants,"funds:read").statusCode()).isEqualTo(404);
    }

    @Test
    void operationLookupWaitsForAnInflightReservationInsteadOfReportingFalseAbsence() throws Exception {
        UUID source=fundedCustomer("10"),destination=fixtureAccount("CUSTOMER"),operation=UUID.randomUUID();var grants=java.util.List.of(source,destination);
        String marker="lookup_pause_"+UUID.randomUUID().toString().replace("-","");
        admin("CREATE FUNCTION public."+marker+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.event_type='FUNDS_RESERVED' THEN PERFORM pg_sleep(2); END IF; RETURN NEW; END $$");
        admin("CREATE TRIGGER "+marker+" BEFORE INSERT ON ledger_funds_event FOR EACH ROW EXECUTE FUNCTION public."+marker+"()");
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var creating=executor.submit(()->call("POST","/api/v1/tenants/owned/funds/reservations",reserveBody(operation,source,destination,"2",300),grants,"funds:reserve"));
            long end=System.nanoTime()+Duration.ofSeconds(10).toNanos();
            while(adminScalar("SELECT COUNT(*) FROM pg_stat_activity WHERE usename='funds_runtime' AND wait_event='PgSleep'")<1&&System.nanoTime()<end) Thread.sleep(10);
            assertThat(adminScalar("SELECT COUNT(*) FROM pg_stat_activity WHERE usename='funds_runtime' AND wait_event='PgSleep'")).isPositive();
            var lookup=executor.submit(()->call("GET","/api/v1/tenants/owned/funds/reservations/by-operation/"+operation,null,grants,"funds:read"));
            var observed=lookup.get(8,java.util.concurrent.TimeUnit.SECONDS);var created=creating.get(8,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
            assertThat(observed.statusCode()).as(observed.body()).isEqualTo(200);
            assertThat(json.readTree(observed.body()).path("reservationId")).isEqualTo(json.readTree(created.body()).path("reservationId"));
        } finally {admin("DROP TRIGGER "+marker+" ON ledger_funds_event");admin("DROP FUNCTION public."+marker+"()");}
    }
    long adminScalar(String sql) throws Exception {
        try(var c=DriverManager.getConnection(DATABASE.getJdbcUrl(),DATABASE.getUsername(),DATABASE.getPassword());var st=c.createStatement();var rows=st.executeQuery(sql)) {rows.next();return rows.getLong(1);}
    }

    long databaseMillis() throws Exception {
        return adminScalar("SELECT floor(extract(epoch FROM clock_timestamp())*1000)::bigint");
    }

    void awaitDatabaseTime(long deadline) throws Exception {
        long stop=System.nanoTime()+Duration.ofSeconds(10).toNanos();
        while(databaseMillis()<deadline) {
            assertThat(System.nanoTime()).as("owned database did not reach deadline").isLessThan(stop);
            Thread.sleep(10);
        }
    }

    void awaitReservationExpiry(String reservationId) throws Exception {
        long deadline=Long.parseLong(adminString("SELECT floor(extract(epoch FROM expires_at)*1000)::bigint FROM ledger_funds_reservation WHERE reservation_id=?",UUID.fromString(reservationId)));
        awaitDatabaseTime(deadline);
    }

    @Test
    void reconciliationClosesExactAbsentIntentDurablyAndPreventsLateReserveAfterClockRollback() throws Exception {
        UUID source=fundedCustomer("10"),destination=fixtureAccount("CUSTOMER"),operation=UUID.randomUUID();var grants=java.util.List.of(source,destination);
        long deadline=databaseMillis()+2000;
        CLOCK.now.set(java.time.Instant.ofEpochMilli(databaseMillis()));
        var body=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(reserveBody(operation,source,destination,"2",300));body.put("notAfterEpochMillis",deadline);
        String url="/api/v1/tenants/owned/funds/reservations/reconciliation";
        assertThat(call("POST",url,body.toString(),grants,"funds:reconcile").statusCode()).isEqualTo(409);
        awaitDatabaseTime(deadline);
        CLOCK.now.set(java.time.Instant.ofEpochMilli(databaseMillis()));
        var response=call("POST",url,body.toString(),grants,"funds:reconcile");assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        var receipt=json.readTree(response.body());assertThat(receipt.path("outcome").asText()).isEqualTo("ABSENT_CLOSED");
        assertThat(receipt.path("receiptId").asText()).isNotBlank();assertThat(receipt.path("reservation").isNull()).isTrue();
        assertThat(receipt.path("intent").path("operationId").asText()).isEqualTo(operation.toString());
        assertThat(receipt.path("notAfterEpochMillis").asLong()).isEqualTo(deadline);
        var replay=call("POST",url,body.toString(),grants,"funds:reconcile");assertThat(json.readTree(replay.body()).path("receiptId")).isEqualTo(receipt.path("receiptId"));
        CLOCK.now.set(java.time.Instant.ofEpochMilli(deadline-10000));
        assertThat(call("POST","/api/v1/tenants/owned/funds/reservations",body.toString(),grants,"funds:reserve").statusCode()).isEqualTo(409);
        body.put("amount",3);assertThat(call("POST",url,body.toString(),grants,"funds:reconcile").statusCode()).isEqualTo(409);
        assertThat(adminCount("SELECT COUNT(*) FROM ledger_funds_reservation WHERE operation_id=?",operation)).isZero();
        assertThat(adminCount("SELECT COUNT(*) FROM ledger_funds_event WHERE resource_id=? AND event_type='FUNDS_ABSENT_CLOSED'",operation)).isEqualTo(1);
        assertThat(call("POST",url,body.toString(),grants,"funds:read").statusCode()).isEqualTo(403);
    }
    @Test
    void reconciliationFindsAnExistingReservationAndRejectsDisjointHiddenIntent() throws Exception {
        UUID source=fundedCustomer("10"),destination=fixtureAccount("CUSTOMER"),operation=UUID.randomUUID();var grants=java.util.List.of(source,destination);
        long deadline=databaseMillis()+2000;
        var body=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(reserveBody(operation,source,destination,"2",300));body.put("notAfterEpochMillis",deadline);
        var created=call("POST","/api/v1/tenants/owned/funds/reservations",body.toString(),grants,"funds:reserve");assertThat(created.statusCode()).isEqualTo(201);
        awaitDatabaseTime(deadline);
        CLOCK.now.set(java.time.Instant.ofEpochMilli(databaseMillis()));
        String url="/api/v1/tenants/owned/funds/reservations/reconciliation";
        var found=call("POST",url,body.toString(),grants,"funds:reconcile");assertThat(found.statusCode()).as(found.body()).isEqualTo(200);
        assertThat(json.readTree(found.body()).path("outcome").asText()).isEqualTo("FOUND");
        assertThat(json.readTree(found.body()).path("reservation").path("reservationId")).isEqualTo(json.readTree(created.body()).path("reservationId"));
        UUID c=fixtureAccount("CUSTOMER"),d=fixtureAccount("CUSTOMER");body.put("sourceAccountId",c.toString());body.put("destinationAccountId",d.toString());
        assertThat(call("POST",url,body.toString(),java.util.List.of(c,d),"funds:reconcile").statusCode()).isEqualTo(409);
        assertThat(call("POST",url,body.toString(),java.util.List.of(c),"funds:reconcile").statusCode()).isEqualTo(403);
    }
    @Test
    void reconciliationAuditFailureRollsBackClosureRatherThanReturningAnUnsafeReceipt() throws Exception {
        UUID source=fixtureAccount("CUSTOMER"),destination=fixtureAccount("CUSTOMER"),operation=UUID.randomUUID();var grants=java.util.List.of(source,destination);
        var body=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(reserveBody(operation,source,destination,"2",300));body.put("notAfterEpochMillis",CLOCK.instant().toEpochMilli());
        String constraint="deny_closure_"+UUID.randomUUID().toString().replace("-","");
        admin("ALTER TABLE ledger_funds_event ADD CONSTRAINT "+constraint+" CHECK(event_type<>'FUNDS_ABSENT_CLOSED') NOT VALID");
        try {
            assertThat(call("POST","/api/v1/tenants/owned/funds/reservations/reconciliation",body.toString(),grants,"funds:reconcile").statusCode()).isEqualTo(503);
            assertThat(adminCount("SELECT COUNT(*) FROM ledger_funds_operation WHERE operation_id=?",operation)).isZero();
            assertThat(adminCount("SELECT COUNT(*) FROM ledger_funds_event WHERE resource_id=?",operation)).isZero();
        } finally {admin("ALTER TABLE ledger_funds_event DROP CONSTRAINT "+constraint);}
        var retained=call("POST","/api/v1/tenants/owned/funds/reservations/reconciliation",body.toString(),grants,"funds:reconcile");assertThat(retained.statusCode()).as(retained.body()).isEqualTo(200);
    }

    @Test
    void committedNoEffectReceiptIsImmutableAndFencesDirectLatePostingAndReservationRows() throws Exception {
        UUID source=fixtureAccount("CUSTOMER"),destination=fixtureAccount("CUSTOMER"),operation=UUID.randomUUID();var grants=java.util.List.of(source,destination);
        var body=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(reserveBody(operation,source,destination,"2",300));body.put("notAfterEpochMillis",CLOCK.instant().toEpochMilli());
        var closed=call("POST","/api/v1/tenants/owned/funds/reservations/reconciliation",body.toString(),grants,"funds:reconcile");assertThat(closed.statusCode()).as(closed.body()).isEqualTo(200);
        String key=adminString("SELECT closed_posting_key FROM ledger_funds_operation WHERE operation_id=?",operation);
        String retainedClosure=adminString("SELECT to_jsonb(o)::text FROM ledger_funds_operation o WHERE operation_id=?",operation);
        assertThatThrownBy(()->admin("UPDATE ledger_funds_operation SET fingerprint=repeat('0',64) WHERE operation_id='"+operation+"'")).hasMessageContaining("IMMUTABLE_FUNDS_NO_EFFECT_RECEIPT");
        assertThatThrownBy(()->admin("DELETE FROM ledger_funds_operation WHERE operation_id='"+operation+"'")).hasMessageContaining("IMMUTABLE_FUNDS_NO_EFFECT_RECEIPT");
        assertThatThrownBy(()->database.sql("INSERT INTO ledger_posting_request(idempotency_key,input_fingerprint) VALUES(:key,repeat('0',64))").bind("key",key).fetch().rowsUpdated().block()).hasMessageContaining("FND_NATIVE_COMMAND_REQUIRED");
        assertThatThrownBy(()->admin("INSERT INTO transaction(account_id,external_reference,transaction_type,transaction_status,total_amount,currency,transaction_date) VALUES('"+UUID.randomUUID()+"','"+key+"','transfer','posted',2,'USD',CURRENT_TIMESTAMP)")).hasMessageContaining("FUNDS_OPERATION_CLOSED_WITHOUT_EFFECT");
        var unrelated=legacyPosting(UUID.randomUUID(),UUID.randomUUID(),"deferred-closed-operation-backstop");assertThat(unrelated.statusCode()).as(unrelated.body()).isEqualTo(201);
        UUID posting=UUID.fromString(json.readTree(unrelated.body()).path("transaction").path("transactionId").asText());
        assertThatThrownBy(()->admin("INSERT INTO ledger_funds_reservation(reservation_id,tenant_id,operation_id,source_account_id,destination_account_id,amount,currency,state,expires_at,posting_id) VALUES('"+UUID.randomUUID()+"','owned','"+operation+"','"+source+"','"+destination+"',2,'USD','HELD',CURRENT_TIMESTAMP,'"+posting+"')")).hasMessageContaining("FND_NATIVE_COMMAND_REQUIRED");
        assertThat(adminCount("SELECT COUNT(*) FROM ledger_funds_reservation WHERE operation_id=?",operation)).isZero();
        assertThat(adminCount("SELECT count(*) FROM ledger_posting_request WHERE idempotency_key=?",key)).isZero();
        assertThat(adminString("SELECT to_jsonb(o)::text FROM ledger_funds_operation o WHERE operation_id=?",operation)).isEqualTo(retainedClosure);
        assertThat(adminCount("SELECT count(*) FROM ledger_funds_account WHERE account_id IN(?,?) AND booked=0 AND held=0",source,destination)).isEqualTo(2);
        assertThatThrownBy(()->database.sql("SELECT posting_key FROM funds_private.closed_posting").fetch().all().collectList().block()).isInstanceOf(org.springframework.dao.PermissionDeniedDataAccessException.class);
    }
    @Autowired org.springframework.transaction.ReactiveTransactionManager fundsTransactionManager;
    @Test
    void reconciliationContextCannotMutateBookedFundsOrCreateReservations() throws Exception {
        UUID account=fixtureAccount("CUSTOMER");
        var tx=org.springframework.transaction.reactive.TransactionalOperator.create(fundsTransactionManager);
        assertThat(database.sql("SELECT set_config('firefly.funds.tenant','owned',true),set_config('firefly.funds.purpose','RECONCILE',true),set_config('firefly.funds.accounts',:accounts,true)")
            .bind("accounts","[\""+account+"\"]").fetch().rowsUpdated().then(database.sql("UPDATE ledger_funds_account SET booked=booked+1 WHERE account_id=:a").bind("a",account).fetch().rowsUpdated()).as(tx::transactional).block()).isZero();
        assertThat(adminString("SELECT booked::text FROM ledger_funds_account WHERE account_id=?",account)).startsWith("0");
    }

    @Test
    void concurrentNoEffectClosuresRetainOneReceiptAndOneAtomicAuditEvent() throws Exception {
        UUID source=fixtureAccount("CUSTOMER"),destination=fixtureAccount("CUSTOMER"),operation=UUID.randomUUID();var grants=java.util.List.of(source,destination);
        var body=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(reserveBody(operation,source,destination,"2",300));body.put("notAfterEpochMillis",CLOCK.instant().toEpochMilli());
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var first=executor.submit(()->call("POST","/api/v1/tenants/owned/funds/reservations/reconciliation",body.toString(),grants,"funds:reconcile"));
            var second=executor.submit(()->call("POST","/api/v1/tenants/owned/funds/reservations/reconciliation",body.toString(),grants,"funds:reconcile"));
            var a=first.get();var b=second.get();assertThat(a.statusCode()).as(a.body()).isEqualTo(200);assertThat(b.statusCode()).as(b.body()).isEqualTo(200);
            assertThat(json.readTree(a.body()).path("receiptId")).isEqualTo(json.readTree(b.body()).path("receiptId"));
        }
        assertThat(adminCount("SELECT COUNT(*) FROM ledger_funds_event WHERE resource_id=?",operation)).isEqualTo(1);
        assertThat(adminCount("SELECT COUNT(*) FROM event_outbox WHERE aggregate_id=CAST(? AS text)",operation)).isEqualTo(1);
    }
    @Test
    void noEffectOutboxFailureRollsBackReceiptAuditAndPrivatePostingFence() throws Exception {
        UUID source=fixtureAccount("CUSTOMER"),destination=fixtureAccount("CUSTOMER"),operation=UUID.randomUUID();var grants=java.util.List.of(source,destination);
        var body=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(reserveBody(operation,source,destination,"2",300));body.put("notAfterEpochMillis",CLOCK.instant().toEpochMilli());
        String constraint="outbox_deny_closure_"+UUID.randomUUID().toString().replace("-","");
        admin("ALTER TABLE event_outbox ADD CONSTRAINT "+constraint+" CHECK(event_type<>'FUNDS_ABSENT_CLOSED') NOT VALID");
        try {
            var failed=call("POST","/api/v1/tenants/owned/funds/reservations/reconciliation",body.toString(),grants,"funds:reconcile");assertThat(failed.statusCode()).as(failed.body()).isEqualTo(503);
            assertThat(adminCount("SELECT COUNT(*) FROM ledger_funds_operation WHERE operation_id=?",operation)).isZero();
            assertThat(adminCount("SELECT COUNT(*) FROM ledger_funds_event WHERE resource_id=?",operation)).isZero();
            assertThat(adminScalar("SELECT COUNT(*) FROM funds_private.closed_posting WHERE posting_key='fnd:'||encode(sha256(convert_to('owned|"+operation+"|RESERVE','UTF8')),'hex')")).isZero();
        } finally {admin("ALTER TABLE event_outbox DROP CONSTRAINT "+constraint);}
    }

    @Test
    void managedOppositePairReservationsAndCommitsSerializeWithoutDeadlockOrMoneyDrift() throws Exception {
        UUID a=fundedCustomer("100"),b=fundedCustomer("100");var grants=java.util.List.of(a,b);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var creates=new java.util.ArrayList<java.util.concurrent.Future<HttpResponse<String>>>();
            var start=new java.util.concurrent.CountDownLatch(1);
            for(int i=0;i<20;i++) {
                UUID source=i%2==0?a:b,destination=i%2==0?b:a;
                String input=reserveBody(UUID.randomUUID(),source,destination,"1",300);
                creates.add(executor.submit(()->{start.await();return call("POST","/api/v1/tenants/owned/funds/reservations",input,grants,"funds:reserve");}));
            }
            start.countDown();var ids=new java.util.ArrayList<String>();
            for(var future:creates) {var response=future.get(20,java.util.concurrent.TimeUnit.SECONDS);assertThat(response.statusCode()).as(response.body()).isEqualTo(201);ids.add(json.readTree(response.body()).path("reservationId").asText());}
            var commits=new java.util.ArrayList<java.util.concurrent.Future<HttpResponse<String>>>();
            for(String id:ids) commits.add(executor.submit(()->action(id,"COMMIT",grants)));
            for(var future:commits) {var response=future.get(20,java.util.concurrent.TimeUnit.SECONDS);assertThat(response.statusCode()).as(response.body()).isEqualTo(200);}
        }
        for(UUID account:grants) {var snapshot=json.readTree(call("GET",path(account),null,grants,"funds:read").body());assertThat(snapshot.path("booked").decimalValue()).isEqualByComparingTo("100");assertThat(snapshot.path("held").decimalValue()).isEqualByComparingTo("0");}
    }

}
