package io.fireflysoftware.core.banking.ledger.models.repositories.ach;

import io.fireflysoftware.core.banking.ledger.models.entities.ach.TransactionLineAch;
import io.fireflysoftware.core.banking.ledger.models.entities.search.TransactionLineAchSearch;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransactionLineAchFiltersImpl implements TransactionLineAchFilters {
    private final R2dbcEntityOperations operations;

    public TransactionLineAchFiltersImpl(R2dbcEntityOperations operations) {
        this.operations = operations;
    }

    @Override
    public Flux<TransactionLineAch> findByAchSourceAccountId(
            Long sourceAccountId, Pageable pageable) {
        if (sourceAccountId == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("ach_source_account_id").is(sourceAccountId));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        query = query.sort(Sort.by(Sort.Order.desc("ach_timestamp")));
        return operations.select(query, TransactionLineAch.class);
    }

    @Override
    public Mono<Long> countByAchSourceAccountId(Long sourceAccountId) {
        if (sourceAccountId == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("ach_source_account_id").is(sourceAccountId));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineAch.class);
    }

    @Override
    public Flux<TransactionLineAch> findByAchDestinationAccountId(
            Long destinationAccountId, Pageable pageable) {
        if (destinationAccountId == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria =
                criteria.and(Criteria.where("ach_destination_account_id").is(destinationAccountId));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        query = query.sort(Sort.by(Sort.Order.desc("ach_timestamp")));
        return operations.select(query, TransactionLineAch.class);
    }

    @Override
    public Mono<Long> countByAchDestinationAccountId(Long destinationAccountId) {
        if (destinationAccountId == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria =
                criteria.and(Criteria.where("ach_destination_account_id").is(destinationAccountId));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineAch.class);
    }

    @Override
    public Flux<TransactionLineAch> findByAchRoutingNumber(
            String routingNumber, Pageable pageable) {
        if (routingNumber == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("ach_routing_number").is(routingNumber));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        query = query.sort(Sort.by(Sort.Order.desc("ach_timestamp")));
        return operations.select(query, TransactionLineAch.class);
    }

    @Override
    public Flux<TransactionLineAch> findByAchBatchNumber(String batchNumber, Pageable pageable) {
        if (batchNumber == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("ach_batch_number").is(batchNumber));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        query = query.sort(Sort.by(Sort.Order.desc("ach_timestamp")));
        return operations.select(query, TransactionLineAch.class);
    }

    @Override
    public Flux<TransactionLineAch> findByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            Long sourceAccountId,
            Long destinationAccountId,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Pageable pageable) {
        Criteria criteria = Criteria.empty();
        if (startDate != null)
            criteria = criteria.and(Criteria.where("ach_timestamp").greaterThanOrEquals(startDate));
        if (endDate != null)
            criteria = criteria.and(Criteria.where("ach_timestamp").lessThanOrEquals(endDate));
        if (sourceAccountId != null)
            criteria = criteria.and(Criteria.where("ach_source_account_id").is(sourceAccountId));
        if (destinationAccountId != null)
            criteria =
                    criteria.and(
                            Criteria.where("ach_destination_account_id").is(destinationAccountId));
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
        Query query = Query.query(criteria);
        query = query.with(pageable);
        query = query.sort(Sort.by(Sort.Direction.DESC, "ach_timestamp"));
        return operations
                .select(query, TransactionLineAchSearch.class)
                .map(row -> (TransactionLineAch) row);
    }

    @Override
    public Mono<Long> countByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            Long sourceAccountId,
            Long destinationAccountId,
            BigDecimal minAmount,
            BigDecimal maxAmount) {
        Criteria criteria = Criteria.empty();
        if (startDate != null)
            criteria = criteria.and(Criteria.where("ach_timestamp").greaterThanOrEquals(startDate));
        if (endDate != null)
            criteria = criteria.and(Criteria.where("ach_timestamp").lessThanOrEquals(endDate));
        if (sourceAccountId != null)
            criteria = criteria.and(Criteria.where("ach_source_account_id").is(sourceAccountId));
        if (destinationAccountId != null)
            criteria =
                    criteria.and(
                            Criteria.where("ach_destination_account_id").is(destinationAccountId));
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
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineAchSearch.class);
    }
}
