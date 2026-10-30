package io.fireflysoftware.core.banking.ledger.models.repositories.withdrawal;

import io.fireflysoftware.core.banking.ledger.models.entities.search.TransactionLineWithdrawalSearch;
import io.fireflysoftware.core.banking.ledger.models.entities.withdrawal.TransactionLineWithdrawal;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransactionLineWithdrawalFiltersImpl implements TransactionLineWithdrawalFilters {
    private final R2dbcEntityOperations operations;

    public TransactionLineWithdrawalFiltersImpl(R2dbcEntityOperations operations) {
        this.operations = operations;
    }

    @Override
    public Flux<TransactionLineWithdrawal> findByWithdrawalMethod(
            String withdrawalMethod, Pageable pageable) {
        if (withdrawalMethod == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("withdrawal_method").is(withdrawalMethod));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        query = query.sort(Sort.by(Sort.Order.desc("withdrawal_timestamp")));
        return operations.select(query, TransactionLineWithdrawal.class);
    }

    @Override
    public Mono<Long> countByWithdrawalMethod(String withdrawalMethod) {
        if (withdrawalMethod == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("withdrawal_method").is(withdrawalMethod));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineWithdrawal.class);
    }

    @Override
    public Flux<TransactionLineWithdrawal> findByWithdrawalAtmId(String atmId, Pageable pageable) {
        if (atmId == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("withdrawal_atm_id").is(atmId));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        query = query.sort(Sort.by(Sort.Order.desc("withdrawal_timestamp")));
        return operations.select(query, TransactionLineWithdrawal.class);
    }

    @Override
    public Mono<Long> countByWithdrawalAtmId(String atmId) {
        if (atmId == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("withdrawal_atm_id").is(atmId));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineWithdrawal.class);
    }

    @Override
    public Flux<TransactionLineWithdrawal> findByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            String withdrawalMethod,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Pageable pageable) {
        Criteria criteria = Criteria.empty();
        if (startDate != null)
            criteria =
                    criteria.and(
                            Criteria.where("withdrawal_timestamp").greaterThanOrEquals(startDate));
        if (endDate != null)
            criteria =
                    criteria.and(Criteria.where("withdrawal_timestamp").lessThanOrEquals(endDate));
        if (withdrawalMethod != null)
            criteria = criteria.and(Criteria.where("withdrawal_method").is(withdrawalMethod));
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
        query = query.sort(Sort.by(Sort.Direction.DESC, "withdrawal_timestamp"));
        return operations
                .select(query, TransactionLineWithdrawalSearch.class)
                .map(row -> (TransactionLineWithdrawal) row);
    }

    @Override
    public Mono<Long> countByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            String withdrawalMethod,
            BigDecimal minAmount,
            BigDecimal maxAmount) {
        Criteria criteria = Criteria.empty();
        if (startDate != null)
            criteria =
                    criteria.and(
                            Criteria.where("withdrawal_timestamp").greaterThanOrEquals(startDate));
        if (endDate != null)
            criteria =
                    criteria.and(Criteria.where("withdrawal_timestamp").lessThanOrEquals(endDate));
        if (withdrawalMethod != null)
            criteria = criteria.and(Criteria.where("withdrawal_method").is(withdrawalMethod));
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
        return operations.count(query, TransactionLineWithdrawalSearch.class);
    }
}
