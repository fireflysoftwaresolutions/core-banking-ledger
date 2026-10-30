package io.fireflysoftware.core.banking.ledger.core.repositories;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.banking.ledger.models.entities.funds.*;
import io.fireflysoftware.core.banking.ledger.models.repositories.funds.*;
import io.fireflysoftware.core.banking.ledger.models.repositories.funds.impl.FundsWriteOperationsImpl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

class FundsRepositoryContractTest {
    @Test
    void retainedFactsClaimsAndActionsExposeNoGenericMutations() {
        for (var type :
                List.of(
                        FundsMonetaryReceiptRepository.class,
                        FundsReserveIntentRepository.class,
                        FundsEventRepository.class,
                        FundsOutboxRepository.class,
                        FundsActionRepository.class,
                        FundsControlRepository.class,
                        FundsOperationRepository.class,
                        LedgerPostingClaimRepository.class)) {
            assertThat(type.getMethods())
                    .noneMatch(
                            method ->
                                    method.getName().startsWith("save")
                                            || method.getName().startsWith("delete"));
        }
        assertThat(FundsWriteOperations.class.getMethods())
                .noneMatch(
                        method ->
                                method.getName().contains("Receipt")
                                        || method.getName().contains("Intent"));
    }

    @Test
    void accountMoneyCasRetainsTenantStateVersionAndSignedZeroValues() {
        var operations = mock(R2dbcEntityOperations.class);
        when(operations.update(any(Query.class), any(Update.class), eq(FundsAccount.class)))
                .thenReturn(Mono.just(1L));
        UUID account = UUID.randomUUID();
        new FundsWriteOperationsImpl(operations)
                .updateMoney("tenant", account, 7, "ACTIVE", new BigDecimal("-10"), BigDecimal.ZERO)
                .block();
        var query = ArgumentCaptor.forClass(Query.class);
        var update = ArgumentCaptor.forClass(Update.class);
        verify(operations).update(query.capture(), update.capture(), eq(FundsAccount.class));
        assertThat(query.getValue().getCriteria().orElseThrow().toString())
                .contains(
                        "tenant_id = 'tenant'",
                        "account_id = '" + account + "'",
                        "version = 7",
                        "state = 'ACTIVE'");
        assertThat(update.getValue().toString())
                .contains("booked = -10", "held = 0", "version = 8");
    }

    @Test
    void accountControlCasRetainsTenantObservedStateAndVersion() {
        var operations = mock(R2dbcEntityOperations.class);
        when(operations.update(any(Query.class), any(Update.class), eq(FundsAccount.class)))
                .thenReturn(Mono.just(0L));
        new FundsWriteOperationsImpl(operations)
                .updateAccountState("tenant", UUID.randomUUID(), 3, "BLOCKED", "ACTIVE")
                .block();
        var query = ArgumentCaptor.forClass(Query.class);
        verify(operations).update(query.capture(), any(Update.class), eq(FundsAccount.class));
        assertThat(query.getValue().getCriteria().orElseThrow().toString())
                .contains("tenant_id = 'tenant'", "version = 3", "state = 'BLOCKED'");
    }

    @Test
    void reservationCasRetainsTenantObservedStateAndVersion() {
        var operations = mock(R2dbcEntityOperations.class);
        when(operations.update(any(Query.class), any(Update.class), eq(FundsReservation.class)))
                .thenReturn(Mono.just(1L));
        new FundsWriteOperationsImpl(operations)
                .updateReservation(
                        "tenant", UUID.randomUUID(), 8, "COMMITTED", "REVERSED", UUID.randomUUID())
                .block();
        var query = ArgumentCaptor.forClass(Query.class);
        var update = ArgumentCaptor.forClass(Update.class);
        verify(operations).update(query.capture(), update.capture(), eq(FundsReservation.class));
        assertThat(query.getValue().getCriteria().orElseThrow().toString())
                .contains("tenant_id = 'tenant'", "version = 8", "state = 'COMMITTED'");
        assertThat(update.getValue().toString())
                .contains("state = 'REVERSED'", "version = 9", "reversal_id =");
    }

    @Test
    void assignedInsertRejectsMissingIdentityBeforePersistence() {
        var operations = mock(R2dbcEntityOperations.class);
        var writes = new FundsWriteOperationsImpl(operations);
        assertThatNullPointerException()
                .isThrownBy(() -> writes.insertAccount(FundsAccount.builder().build()));
        assertThatNullPointerException()
                .isThrownBy(() -> writes.insertReservation(FundsReservation.builder().build()));
        assertThatNullPointerException()
                .isThrownBy(() -> writes.insertEvent(FundsEvent.builder().build()));
        verifyNoInteractions(operations);
    }
}
