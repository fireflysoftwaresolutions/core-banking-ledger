package io.fireflysoftware.core.banking.ledger.sdk;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

class ManagedFundsContractTest {
    @Test
    void genuineProducerContractIncludesRecoveryAndNeverAsksForAnAuthenticatedPrincipal()
            throws Exception {
        String contract = Files.readString(Path.of("src/main/resources/openapi.yml"));
        assertThat(contract)
                .contains(
                        "operationId: getFundsReservationByOperation",
                        "operationId: controlLedgerFunds",
                        "operationId: reserveFunds",
                        "operationId: expireDueFunds",
                        "operationId: reconcileFundsReservation",
                        "notAfterEpochMillis:")
                .doesNotContain("name: jwt", "    Jwt:");
    }

    @Test
    void genuineProducerExposesTypedMonetaryEvidenceWithoutChangingTheActionResponse()
            throws Exception {
        JsonNode contract =
                new ObjectMapper(new YAMLFactory())
                        .readTree(Files.readString(Path.of("src/main/resources/openapi.yml")));
        JsonNode paths = contract.path("paths");
        JsonNode read =
                paths.path("/api/v1/tenants/{tenant}/funds/reservations/{id}/monetary-receipts")
                        .path("get");
        assertThat(read.path("operationId").asText()).isEqualTo("getFundsMonetaryReceipts");
        assertThat(read.path("security").toString()).contains("fundsBearer");
        var parameters = new java.util.HashSet<String>();
        read.path("parameters")
                .forEach(parameter -> parameters.add(parameter.path("name").asText()));
        assertThat(parameters).contains("tenant", "id").doesNotContain("jwt", "principal");
        JsonNode evidence = responseSchema(read);
        assertThat(evidence.path("$ref").asText()).isEqualTo("#/components/schemas/EvidenceView");
        JsonNode schemas = contract.path("components").path("schemas");
        JsonNode view = schemas.path("EvidenceView").path("properties");
        assertThat(view.path("evidenceStatus").path("enum").toString())
                .contains("RETAINED", "HISTORICAL_UNAVAILABLE");
        assertThat(view.path("acceptedReserve").path("$ref").asText())
                .isEqualTo("#/components/schemas/AcceptedReserveEnvelope");
        assertThat(view.path("commit").path("$ref").asText())
                .isEqualTo("#/components/schemas/MonetaryReceiptEnvelope");
        assertThat(view.path("reversal").path("$ref").asText())
                .isEqualTo("#/components/schemas/MonetaryReceiptEnvelope");
        assertThat(
                        schemas.path("AcceptedReserve")
                                .path("properties")
                                .path("intentRecordedAtEpochMillis")
                                .path("format")
                                .asText())
                .isEqualTo("int64");
        assertThat(
                        schemas.path("MonetaryReceipt")
                                .path("properties")
                                .path("effectRecordedAtEpochMillis")
                                .path("format")
                                .asText())
                .isEqualTo("int64");
        assertThat(
                        responseSchema(
                                        paths.path(
                                                        "/api/v1/tenants/{tenant}/funds/reservations/{id}/actions")
                                                .path("post"))
                                .path("$ref")
                                .asText())
                .isEqualTo("#/components/schemas/Reservation");
    }

    @Test
    void packagedCompatibilityAliasMatchesActualProducerSpec() throws Exception {
        try (var root = getClass().getResourceAsStream("/openapi.yml");
                var alias = getClass().getResourceAsStream("/api-spec/openapi.yml")) {
            assertThat(root).isNotNull();
            assertThat(alias).isNotNull();
            assertThat(alias.readAllBytes()).isEqualTo(root.readAllBytes());
        }
    }

    private JsonNode responseSchema(JsonNode operation) {
        JsonNode content = operation.path("responses").path("200").path("content");
        return content.path(content.has("application/json") ? "application/json" : "*/*")
                .path("schema");
    }
}
