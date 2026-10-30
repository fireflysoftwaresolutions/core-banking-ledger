package io.fireflysoftware.core.banking.ledger.models.repositories.transfer;

import io.fireflysoftware.core.banking.ledger.models.entities.search.TransactionLineTransferSearch;
import io.fireflysoftware.core.banking.ledger.models.entities.transfer.TransactionLineTransfer;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransactionLineTransferFiltersImpl implements TransactionLineTransferFilters {
    private final R2dbcEntityOperations operations;

    public TransactionLineTransferFiltersImpl(R2dbcEntityOperations operations) {
        this.operations = operations;
    }

    @Override
    public Flux<TransactionLineTransfer> findByTransferSourceAccountId(
            Long sourceAccountId, Pageable pageable) {
        if (sourceAccountId == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("transfer_source_account_id").is(sourceAccountId));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        query = query.sort(Sort.by(Sort.Order.desc("transfer_timestamp")));
        return operations.select(query, TransactionLineTransfer.class);
    }

    @Override
    public Mono<Long> countByTransferSourceAccountId(Long sourceAccountId) {
        if (sourceAccountId == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("transfer_source_account_id").is(sourceAccountId));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineTransfer.class);
    }

    @Override
    public Flux<TransactionLineTransfer> findByTransferDestinationAccountId(
            Long destinationAccountId, Pageable pageable) {
        if (destinationAccountId == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria =
                criteria.and(
                        Criteria.where("transfer_destination_account_id").is(destinationAccountId));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        query = query.sort(Sort.by(Sort.Order.desc("transfer_timestamp")));
        return operations.select(query, TransactionLineTransfer.class);
    }

    @Override
    public Mono<Long> countByTransferDestinationAccountId(Long destinationAccountId) {
        if (destinationAccountId == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria =
                criteria.and(
                        Criteria.where("transfer_destination_account_id").is(destinationAccountId));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineTransfer.class);
    }

    @Override
    public Flux<TransactionLineTransfer> findByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            Long sourceAccountId,
            Long destinationAccountId,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Pageable pageable) {
        Criteria criteria = Criteria.empty();
        if (startDate != null)
            criteria =
                    criteria.and(
                            Criteria.where("transfer_timestamp").greaterThanOrEquals(startDate));
        if (endDate != null)
            criteria = criteria.and(Criteria.where("transfer_timestamp").lessThanOrEquals(endDate));
        if (sourceAccountId != null)
            criteria =
                    criteria.and(Criteria.where("transfer_source_account_id").is(sourceAccountId));
        if (destinationAccountId != null)
            criteria =
                    criteria.and(
                            Criteria.where("transfer_destination_account_id")
                                    .is(destinationAccountId));
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
        query = query.sort(Sort.by(Sort.Direction.DESC, "transfer_timestamp"));
        return operations
                .select(query, TransactionLineTransferSearch.class)
                .map(row -> (TransactionLineTransfer) row);
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
            criteria =
                    criteria.and(
                            Criteria.where("transfer_timestamp").greaterThanOrEquals(startDate));
        if (endDate != null)
            criteria = criteria.and(Criteria.where("transfer_timestamp").lessThanOrEquals(endDate));
        if (sourceAccountId != null)
            criteria =
                    criteria.and(Criteria.where("transfer_source_account_id").is(sourceAccountId));
        if (destinationAccountId != null)
            criteria =
                    criteria.and(
                            Criteria.where("transfer_destination_account_id")
                                    .is(destinationAccountId));
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
        return operations.count(query, TransactionLineTransferSearch.class);
    }
}
