package io.fireflysoftware.core.banking.ledger.models.repositories.funds.impl;

import io.fireflysoftware.core.banking.ledger.models.repositories.funds.FundsWriteOperations;

import io.fireflysoftware.core.banking.ledger.models.entities.funds.*;

import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

public class FundsWriteOperationsImpl implements FundsWriteOperations {
    private final R2dbcEntityOperations operations;

    public FundsWriteOperationsImpl(R2dbcEntityOperations operations) {
        this.operations = operations;
    }

    public Mono<FundsAccount> insertAccount(FundsAccount entity) {
        java.util.Objects.requireNonNull(entity.getAccountId());
        return operations.insert(entity);
    }

    public Mono<FundsReservation> insertReservation(FundsReservation entity) {
        java.util.Objects.requireNonNull(entity.getReservationId());
        return operations.insert(entity);
    }

    public Mono<FundsOperation> insertOperation(FundsOperation entity) {
        java.util.Objects.requireNonNull(entity.getId());
        return operations.insert(entity);
    }

    public Mono<FundsAction> insertAction(FundsAction entity) {
        java.util.Objects.requireNonNull(entity.getId());
        return operations.insert(entity);
    }

    public Mono<FundsControl> insertControl(FundsControl entity) {
        java.util.Objects.requireNonNull(entity.getId());
        return operations.insert(entity);
    }

    public Mono<FundsEvent> insertEvent(FundsEvent entity) {
        java.util.Objects.requireNonNull(entity.getEventId());
        return operations.insert(entity);
    }

    public Mono<FundsOutbox> insertOutbox(FundsOutbox entity) {
        java.util.Objects.requireNonNull(entity.getEventId());
        return operations.insert(entity);
    }

    public Mono<LedgerPostingClaim> insertLedgerPostingClaim(LedgerPostingClaim entity) {
        java.util.Objects.requireNonNull(entity.getIdempotencyKey());
        return operations.insert(entity);
    }

    public Mono<Long> updateMoney(
            String tenant,
            UUID account,
            long expectedVersion,
            String expectedState,
            BigDecimal booked,
            BigDecimal held) {
        return operations.update(
                Query.query(
                        Criteria.where("tenant_id")
                                .is(tenant)
                                .and("account_id")
                                .is(account)
                                .and("version")
                                .is(expectedVersion)
                                .and("state")
                                .is(expectedState)),
                Update.update("booked", booked)
                        .set("held", held)
                        .set("version", expectedVersion + 1),
                FundsAccount.class);
    }

    public Mono<Long> updateAccountState(
            String tenant, UUID account, long expectedVersion, String expectedState, String state) {
        return operations.update(
                Query.query(
                        Criteria.where("tenant_id")
                                .is(tenant)
                                .and("account_id")
                                .is(account)
                                .and("version")
                                .is(expectedVersion)
                                .and("state")
                                .is(expectedState)),
                Update.update("state", state).set("version", expectedVersion + 1),
                FundsAccount.class);
    }

    public Mono<Long> updateReservation(
            String tenant,
            UUID reservation,
            long expectedVersion,
            String expectedState,
            String state,
            UUID reversal) {
        var update = Update.update("state", state).set("version", expectedVersion + 1);
        if (reversal != null) update = update.set("reversal_id", reversal);
        return operations.update(
                Query.query(
                        Criteria.where("tenant_id")
                                .is(tenant)
                                .and("reservation_id")
                                .is(reservation)
                                .and("version")
                                .is(expectedVersion)
                                .and("state")
                                .is(expectedState)),
                update,
                FundsReservation.class);
    }

    public Mono<Long> completeOperation(FundsOperationId id, UUID reservation, UUID posting) {
        return operations.update(
                Query.query(
                        Criteria.where("tenant_id")
                                .is(id.tenantId())
                                .and("operation_id")
                                .is(id.operationId())),
                reservation != null
                        ? Update.update("reservation_id", reservation)
                        : Update.update("posting_id", posting),
                FundsOperation.class);
    }

    public Mono<Long> completePosting(String key, UUID posting) {
        return operations.update(
                Query.query(
                        Criteria.where("idempotency_key").is(key).and("transaction_id").isNull()),
                Update.update("transaction_id", posting),
                LedgerPostingClaim.class);
    }
}
