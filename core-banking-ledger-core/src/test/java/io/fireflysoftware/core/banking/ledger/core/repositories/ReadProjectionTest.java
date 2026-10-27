package io.fireflysoftware.core.banking.ledger.core.repositories;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.mapping.R2dbcMappingContext;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

class ReadProjectionTest {
    private static final Map<String, String> TYPES =
            Map.of(
                    "ach",
                    "TransactionLineAch",
                    "card",
                    "TransactionLineCard",
                    "deposit",
                    "TransactionLineDeposit",
                    "directdebit",
                    "TransactionLineDirectDebit",
                    "fee",
                    "TransactionLineFee",
                    "sepa",
                    "TransactionLineSepaTransfer",
                    "standingorder",
                    "TransactionLineStandingOrder",
                    "transfer",
                    "TransactionLineTransfer",
                    "wire",
                    "TransactionLineWireTransfer",
                    "withdrawal",
                    "TransactionLineWithdrawal");

    private Set<String> columns(RelationalPersistentEntity<?> entity) {
        Set<String> columns = new HashSet<>();
        for (var property : entity) columns.add(property.getColumnName().getReference());
        return columns;
    }

    @Test
    void projectionsRetainEveryExistingMappedColumnAndIdentity() throws Exception {
        var mapping = new R2dbcMappingContext();
        for (var type : TYPES.entrySet()) {
            var base =
                    Class.forName(
                            "io.fireflysoftware.core.banking.ledger.models.entities."
                                    + type.getKey()
                                    + "."
                                    + type.getValue());
            var projection =
                    Class.forName(
                            "io.fireflysoftware.core.banking.ledger.models.entities.search."
                                    + type.getValue()
                                    + "Search");
            var original = mapping.getRequiredPersistentEntity(base);
            var read = mapping.getRequiredPersistentEntity(projection);
            assertThat(columns(read))
                    .containsAll(columns(original))
                    .contains("ledger_transaction_total_amount");
            assertThat(read.getRequiredIdProperty().getColumnName())
                    .isEqualTo(original.getRequiredIdProperty().getColumnName());
            assertThat(projection.getSuperclass()).isEqualTo(base);
        }
    }

    @Test
    void viewsAreExplicitInvokerBarrierInnerJoinsWithSelectOnlyPermissions() throws Exception {
        String ddl =
                Files.readString(
                        Path.of(System.getProperty("basedir"))
                                .getParent()
                                .resolve(
                                        "core-banking-ledger-models/src/main/resources/db/migration/V60__Read_only_transaction_line_search_projections.sql"));
        assertThat(ddl.split("security_invoker=true", -1).length - 1).isEqualTo(10);
        assertThat(ddl.split("security_barrier=true", -1).length - 1).isEqualTo(10);
        assertThat(ddl.split("INNER JOIN public.transaction", -1).length - 1).isEqualTo(10);
        assertThat(ddl.split("GRANT SELECT ON public.ledger_search_", -1).length - 1).isEqualTo(10);
        assertThat(ddl).doesNotContain("GRANT INSERT", "GRANT UPDATE", "GRANT DELETE", "TO PUBLIC");
    }
}
