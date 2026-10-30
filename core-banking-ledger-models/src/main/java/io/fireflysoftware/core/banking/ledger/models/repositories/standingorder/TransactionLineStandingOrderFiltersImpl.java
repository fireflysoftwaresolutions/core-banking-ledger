package io.fireflysoftware.core.banking.ledger.models.repositories.standingorder;

import io.fireflysoftware.core.banking.ledger.interfaces.enums.standingorder.StandingOrderFrequencyEnum;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.standingorder.StandingOrderStatusEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.search.TransactionLineStandingOrderSearch;
import io.fireflysoftware.core.banking.ledger.models.entities.standingorder.TransactionLineStandingOrder;

import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;

public class TransactionLineStandingOrderFiltersImpl
        implements TransactionLineStandingOrderFilters {
    private final R2dbcEntityOperations operations;

    public TransactionLineStandingOrderFiltersImpl(R2dbcEntityOperations operations) {
        this.operations = operations;
    }

    @Override
    public Mono<Long> countByStandingOrderStatus(StandingOrderStatusEnum status) {
        if (status == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("standing_order_status").is(status));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineStandingOrder.class);
    }

    @Override
    public Mono<Long> countByStandingOrderFrequency(StandingOrderFrequencyEnum frequency) {
        if (frequency == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("standing_order_frequency").is(frequency));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineStandingOrder.class);
    }

    @Override
    public Flux<TransactionLineStandingOrder> findActiveStandingOrders(
            LocalDate currentDate, Pageable pageable) {
        if (currentDate == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("standing_order_status").is("ACTIVE"));
        criteria =
                criteria.and(
                        Criteria.where("standing_order_suspended_until_date")
                                .isNull()
                                .or(
                                        Criteria.where("standing_order_suspended_until_date")
                                                .lessThanOrEquals(currentDate)));
        criteria =
                criteria.and(
                        Criteria.where("standing_order_end_date")
                                .isNull()
                                .or(
                                        Criteria.where("standing_order_end_date")
                                                .greaterThanOrEquals(currentDate)));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        return operations.select(query, TransactionLineStandingOrder.class);
    }

    @Override
    public Mono<Long> countActiveStandingOrders(LocalDate currentDate) {
        if (currentDate == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("standing_order_status").is("ACTIVE"));
        criteria =
                criteria.and(
                        Criteria.where("standing_order_suspended_until_date")
                                .isNull()
                                .or(
                                        Criteria.where("standing_order_suspended_until_date")
                                                .lessThanOrEquals(currentDate)));
        criteria =
                criteria.and(
                        Criteria.where("standing_order_end_date")
                                .isNull()
                                .or(
                                        Criteria.where("standing_order_end_date")
                                                .greaterThanOrEquals(currentDate)));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineStandingOrder.class);
    }

    @Override
    public Flux<TransactionLineStandingOrder> findStandingOrdersForExecution(
            LocalDate executionDate, Pageable pageable) {
        if (executionDate == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("standing_order_status").is("ACTIVE"));
        criteria =
                criteria.and(
                        Criteria.where("standing_order_next_execution_date").is(executionDate));
        criteria =
                criteria.and(
                        Criteria.where("standing_order_suspended_until_date")
                                .isNull()
                                .or(
                                        Criteria.where("standing_order_suspended_until_date")
                                                .lessThanOrEquals(executionDate)));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        return operations.select(query, TransactionLineStandingOrder.class);
    }

    @Override
    public Mono<Long> countStandingOrdersForExecution(LocalDate executionDate) {
        if (executionDate == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("standing_order_status").is("ACTIVE"));
        criteria =
                criteria.and(
                        Criteria.where("standing_order_next_execution_date").is(executionDate));
        criteria =
                criteria.and(
                        Criteria.where("standing_order_suspended_until_date")
                                .isNull()
                                .or(
                                        Criteria.where("standing_order_suspended_until_date")
                                                .lessThanOrEquals(executionDate)));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineStandingOrder.class);
    }

    @Override
    public Flux<TransactionLineStandingOrder> findByRecipientNameContaining(
            String recipientName, Pageable pageable) {
        if (recipientName == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria =
                criteria.and(
                        Criteria.where("standing_order_recipient_name")
                                .like("%" + recipientName + "%")
                                .ignoreCase(true));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        return operations.select(query, TransactionLineStandingOrder.class);
    }

    @Override
    public Mono<Long> countByRecipientNameContaining(String recipientName) {
        if (recipientName == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria =
                criteria.and(
                        Criteria.where("standing_order_recipient_name")
                                .like("%" + recipientName + "%")
                                .ignoreCase(true));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineStandingOrder.class);
    }

    @Override
    public Flux<TransactionLineStandingOrder> findActiveStandingOrdersByRecipientIban(
            String iban, Pageable pageable) {
        if (iban == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("standing_order_recipient_iban").is(iban));
        criteria = criteria.and(Criteria.where("standing_order_status").is("ACTIVE"));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        return operations.select(query, TransactionLineStandingOrder.class);
    }

    @Override
    public Mono<Long> countActiveStandingOrdersByRecipientIban(String iban) {
        if (iban == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("standing_order_recipient_iban").is(iban));
        criteria = criteria.and(Criteria.where("standing_order_status").is("ACTIVE"));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineStandingOrder.class);
    }

    @Override
    public Flux<TransactionLineStandingOrder> findExpiredSuspensions(
            LocalDate date, Pageable pageable) {
        if (date == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("standing_order_status").is("SUSPENDED"));
        criteria =
                criteria.and(Criteria.where("standing_order_suspended_until_date").lessThan(date));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        return operations.select(query, TransactionLineStandingOrder.class);
    }

    @Override
    public Mono<Long> countExpiredSuspensions(LocalDate date) {
        if (date == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("standing_order_status").is("SUSPENDED"));
        criteria =
                criteria.and(Criteria.where("standing_order_suspended_until_date").lessThan(date));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineStandingOrder.class);
    }

    @Override
    public Flux<TransactionLineStandingOrder> findByCustomCriteria(
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String standingOrderId,
            String recipientName,
            String recipientIban,
            String recipientBic,
            StandingOrderStatusEnum status,
            StandingOrderFrequencyEnum frequency,
            LocalDate startDateFrom,
            LocalDate startDateTo,
            LocalDate endDateFrom,
            LocalDate endDateTo,
            LocalDate nextExecutionDateFrom,
            LocalDate nextExecutionDateTo,
            Boolean isSuspended,
            Integer minTotalExecutions,
            Integer maxTotalExecutions,
            String createdBy,
            String updatedBy,
            Pageable pageable) {
        Criteria criteria = Criteria.empty();
        if (minAmount != null)
            criteria =
                    criteria.and(
                            Criteria.where("ledger_transaction_total_amount")
                                    .greaterThanOrEquals(minAmount));
        if (maxAmount != null)
            criteria =
                    criteria.and(
                            Criteria.where("ledger_transaction_total_amount")
                                    .lessThanOrEquals(maxAmount));
        if (standingOrderId != null)
            criteria = criteria.and(Criteria.where("standing_order_id").is(standingOrderId));
        if (recipientName != null)
            criteria =
                    criteria.and(
                            Criteria.where("standing_order_recipient_name")
                                    .like("%" + recipientName + "%")
                                    .ignoreCase(true));
        if (recipientIban != null)
            criteria =
                    criteria.and(Criteria.where("standing_order_recipient_iban").is(recipientIban));
        if (recipientBic != null)
            criteria =
                    criteria.and(Criteria.where("standing_order_recipient_bic").is(recipientBic));
        if (status != null)
            criteria = criteria.and(Criteria.where("standing_order_status").is(status));
        if (frequency != null)
            criteria = criteria.and(Criteria.where("standing_order_frequency").is(frequency));
        if (startDateFrom != null)
            criteria =
                    criteria.and(
                            Criteria.where("standing_order_start_date")
                                    .greaterThanOrEquals(startDateFrom));
        if (startDateTo != null)
            criteria =
                    criteria.and(
                            Criteria.where("standing_order_start_date")
                                    .lessThanOrEquals(startDateTo));
        if (endDateFrom != null)
            criteria =
                    criteria.and(
                            Criteria.where("standing_order_end_date")
                                    .greaterThanOrEquals(endDateFrom));
        if (endDateTo != null)
            criteria =
                    criteria.and(
                            Criteria.where("standing_order_end_date").lessThanOrEquals(endDateTo));
        if (nextExecutionDateFrom != null)
            criteria =
                    criteria.and(
                            Criteria.where("standing_order_next_execution_date")
                                    .greaterThanOrEquals(nextExecutionDateFrom));
        if (nextExecutionDateTo != null)
            criteria =
                    criteria.and(
                            Criteria.where("standing_order_next_execution_date")
                                    .lessThanOrEquals(nextExecutionDateTo));
        if (isSuspended != null)
            criteria =
                    criteria.and(
                            Boolean.TRUE.equals(isSuspended)
                                    ? Criteria.where("ledger_suspended_on_database_date").is(true)
                                    : Criteria.where("ledger_suspended_on_database_date")
                                            .isNull()
                                            .or(
                                                    Criteria.where(
                                                                    "ledger_suspended_on_database_date")
                                                            .is(false)));
        if (minTotalExecutions != null)
            criteria =
                    criteria.and(
                            Criteria.where("standing_order_total_executions")
                                    .greaterThanOrEquals(minTotalExecutions));
        if (maxTotalExecutions != null)
            criteria =
                    criteria.and(
                            Criteria.where("standing_order_total_executions")
                                    .lessThanOrEquals(maxTotalExecutions));
        if (createdBy != null)
            criteria = criteria.and(Criteria.where("standing_order_created_by").is(createdBy));
        if (updatedBy != null)
            criteria = criteria.and(Criteria.where("standing_order_updated_by").is(updatedBy));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        return operations
                .select(query, TransactionLineStandingOrderSearch.class)
                .map(row -> (TransactionLineStandingOrder) row);
    }

    @Override
    public Mono<Long> countByCustomCriteria(
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String standingOrderId,
            String recipientName,
            String recipientIban,
            String recipientBic,
            StandingOrderStatusEnum status,
            StandingOrderFrequencyEnum frequency,
            LocalDate startDateFrom,
            LocalDate startDateTo,
            LocalDate endDateFrom,
            LocalDate endDateTo,
            LocalDate nextExecutionDateFrom,
            LocalDate nextExecutionDateTo,
            Boolean isSuspended,
            Integer minTotalExecutions,
            Integer maxTotalExecutions,
            String createdBy,
            String updatedBy) {
        Criteria criteria = Criteria.empty();
        if (minAmount != null)
            criteria =
                    criteria.and(
                            Criteria.where("ledger_transaction_total_amount")
                                    .greaterThanOrEquals(minAmount));
        if (maxAmount != null)
            criteria =
                    criteria.and(
                            Criteria.where("ledger_transaction_total_amount")
                                    .lessThanOrEquals(maxAmount));
        if (standingOrderId != null)
            criteria = criteria.and(Criteria.where("standing_order_id").is(standingOrderId));
        if (recipientName != null)
            criteria =
                    criteria.and(
                            Criteria.where("standing_order_recipient_name")
                                    .like("%" + recipientName + "%")
                                    .ignoreCase(true));
        if (recipientIban != null)
            criteria =
                    criteria.and(Criteria.where("standing_order_recipient_iban").is(recipientIban));
        if (recipientBic != null)
            criteria =
                    criteria.and(Criteria.where("standing_order_recipient_bic").is(recipientBic));
        if (status != null)
            criteria = criteria.and(Criteria.where("standing_order_status").is(status));
        if (frequency != null)
            criteria = criteria.and(Criteria.where("standing_order_frequency").is(frequency));
        if (startDateFrom != null)
            criteria =
                    criteria.and(
                            Criteria.where("standing_order_start_date")
                                    .greaterThanOrEquals(startDateFrom));
        if (startDateTo != null)
            criteria =
                    criteria.and(
                            Criteria.where("standing_order_start_date")
                                    .lessThanOrEquals(startDateTo));
        if (endDateFrom != null)
            criteria =
                    criteria.and(
                            Criteria.where("standing_order_end_date")
                                    .greaterThanOrEquals(endDateFrom));
        if (endDateTo != null)
            criteria =
                    criteria.and(
                            Criteria.where("standing_order_end_date").lessThanOrEquals(endDateTo));
        if (nextExecutionDateFrom != null)
            criteria =
                    criteria.and(
                            Criteria.where("standing_order_next_execution_date")
                                    .greaterThanOrEquals(nextExecutionDateFrom));
        if (nextExecutionDateTo != null)
            criteria =
                    criteria.and(
                            Criteria.where("standing_order_next_execution_date")
                                    .lessThanOrEquals(nextExecutionDateTo));
        if (isSuspended != null)
            criteria =
                    criteria.and(
                            Boolean.TRUE.equals(isSuspended)
                                    ? Criteria.where("ledger_suspended_on_database_date").is(true)
                                    : Criteria.where("ledger_suspended_on_database_date")
                                            .isNull()
                                            .or(
                                                    Criteria.where(
                                                                    "ledger_suspended_on_database_date")
                                                            .is(false)));
        if (minTotalExecutions != null)
            criteria =
                    criteria.and(
                            Criteria.where("standing_order_total_executions")
                                    .greaterThanOrEquals(minTotalExecutions));
        if (maxTotalExecutions != null)
            criteria =
                    criteria.and(
                            Criteria.where("standing_order_total_executions")
                                    .lessThanOrEquals(maxTotalExecutions));
        if (createdBy != null)
            criteria = criteria.and(Criteria.where("standing_order_created_by").is(createdBy));
        if (updatedBy != null)
            criteria = criteria.and(Criteria.where("standing_order_updated_by").is(updatedBy));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineStandingOrderSearch.class);
    }
}
