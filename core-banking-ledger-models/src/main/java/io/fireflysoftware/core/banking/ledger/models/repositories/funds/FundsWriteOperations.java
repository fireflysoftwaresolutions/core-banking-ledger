package io.fireflysoftware.core.banking.ledger.models.repositories.funds;

import io.fireflysoftware.core.banking.ledger.models.entities.funds.*;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

public interface FundsWriteOperations {
    Mono<FundsAccount> insertAccount(FundsAccount entity);

    Mono<FundsReservation> insertReservation(FundsReservation entity);

    Mono<FundsOperation> insertOperation(FundsOperation entity);

    Mono<FundsAction> insertAction(FundsAction entity);

    Mono<FundsControl> insertControl(FundsControl entity);

    Mono<FundsEvent> insertEvent(FundsEvent entity);

    Mono<FundsOutbox> insertOutbox(FundsOutbox entity);

    Mono<LedgerPostingClaim> insertLedgerPostingClaim(LedgerPostingClaim entity);

    Mono<Long> updateMoney(
            String tenant,
            UUID account,
            long expectedVersion,
            String expectedState,
            BigDecimal booked,
            BigDecimal held);

    Mono<Long> updateAccountState(
            String tenant, UUID account, long expectedVersion, String expectedState, String state);

    Mono<Long> updateReservation(
            String tenant,
            UUID reservation,
            long expectedVersion,
            String expectedState,
            String state,
            UUID reversal);

    Mono<Long> completeOperation(FundsOperationId id, UUID reservation, UUID posting);

    Mono<Long> completePosting(String key, UUID posting);
}
