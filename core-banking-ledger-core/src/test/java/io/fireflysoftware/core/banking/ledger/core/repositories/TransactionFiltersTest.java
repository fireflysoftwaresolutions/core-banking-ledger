package io.fireflysoftware.core.banking.ledger.core.repositories;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.banking.ledger.interfaces.enums.core.TransactionStatusEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.core.Transaction;
import io.fireflysoftware.core.banking.ledger.models.repositories.core.TransactionFiltersImpl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Query;

import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.List;

class TransactionFiltersTest {
    private Query query(
            Boolean reversed,
            Boolean failed,
            Boolean pending,
            List<TransactionStatusEnum> statuses) {
        var operations = mock(R2dbcEntityOperations.class);
        when(operations.select(any(Query.class), eq(Transaction.class))).thenReturn(Flux.empty());
        new TransactionFiltersImpl(operations)
                .findTransactionsByFilter(
                        null,
                        null,
                        new BigDecimal("1.25"),
                        new BigDecimal("2.50"),
                        null,
                        null,
                        statuses,
                        null,
                        null,
                        "bank%ref",
                        null,
                        null,
                        reversed,
                        failed,
                        pending,
                        10,
                        20,
                        "transaction_date",
                        "ASC");
        var captured = ArgumentCaptor.forClass(Query.class);
        verify(operations).select(captured.capture(), eq(Transaction.class));
        return captured.getValue();
    }

    @Test
    void actualAmountAndReferenceColumnsAreUsed() {
        String criteria = query(null, null, null, null).getCriteria().orElseThrow().toString();
        assertThat(criteria)
                .contains(
                        "total_amount >= 1.25",
                        "total_amount <= 2.50",
                        "external_reference LIKE '%bank%ref%'")
                .doesNotContain("reference_number", "is_reversed");
    }

    @Test
    void absentAndTrueIncludeReversedDoNotInventStatusRestrictions() {
        assertThat(query(null, null, null, null).getCriteria().orElseThrow().toString())
                .doesNotContain("transaction_status");
        assertThat(query(true, null, null, null).getCriteria().orElseThrow().toString())
                .doesNotContain("transaction_status");
    }

    @Test
    void falseIncludeReversedExcludesOnlyExplicitReversedStatus() {
        assertThat(query(false, null, null, null).getCriteria().orElseThrow().toString())
                .contains("transaction_status != 'REVERSED'");
    }

    @Test
    void falseFlagsDoNotFilterRows() {
        assertThat(query(null, false, false, null).getCriteria().orElseThrow().toString())
                .doesNotContain("transaction_status");
    }

    @Test
    void trueFlagsIntersectExplicitStatusSelection() {
        assertThat(
                        query(false, true, true, List.of(TransactionStatusEnum.POSTED))
                                .getCriteria()
                                .orElseThrow()
                                .toString())
                .contains(
                        "transaction_status IN ('POSTED')",
                        "transaction_status = 'FAILED'",
                        "transaction_status = 'PENDING'",
                        "transaction_status != 'REVERSED'");
    }

    @Test
    void pagingAndSupportedLegacySortAreRetained() {
        var query = query(null, null, null, null);
        assertThat(query.getLimit()).isEqualTo(10);
        assertThat(query.getOffset()).isEqualTo(20);
        assertThat(query.getSort().getOrderFor("transaction_date").isAscending()).isTrue();
    }
}
