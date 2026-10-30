package io.fireflysoftware.core.banking.ledger.models.repositories.deposit;

import io.fireflysoftware.core.banking.ledger.models.entities.deposit.TransactionLineDeposit;
import io.fireflysoftware.core.banking.ledger.models.entities.search.TransactionLineDepositSearch;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransactionLineDepositFiltersImpl implements TransactionLineDepositFilters {
    private final R2dbcEntityOperations operations;

    public TransactionLineDepositFiltersImpl(R2dbcEntityOperations operations) {
        this.operations = operations;
    }

    @Override
    public Flux<TransactionLineDeposit> findByDepositMethod(
            String depositMethod, Pageable pageable) {
        if (depositMethod == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("deposit_method").is(depositMethod));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        query = query.sort(Sort.by(Sort.Order.desc("deposit_timestamp")));
        return operations.select(query, TransactionLineDeposit.class);
    }

    @Override
    public Mono<Long> countByDepositMethod(String depositMethod) {
        if (depositMethod == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("deposit_method").is(depositMethod));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineDeposit.class);
    }

    @Override
    public Flux<TransactionLineDeposit> findByDepositBranchId(String branchId, Pageable pageable) {
        if (branchId == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("deposit_branch_id").is(branchId));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        query = query.sort(Sort.by(Sort.Order.desc("deposit_timestamp")));
        return operations.select(query, TransactionLineDeposit.class);
    }

    @Override
    public Mono<Long> countByDepositBranchId(String branchId) {
        if (branchId == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("deposit_branch_id").is(branchId));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineDeposit.class);
    }

    @Override
    public Flux<TransactionLineDeposit> findByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            String depositMethod,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Pageable pageable) {
        Criteria criteria = Criteria.empty();
        if (startDate != null)
            criteria =
                    criteria.and(
                            Criteria.where("deposit_timestamp").greaterThanOrEquals(startDate));
        if (endDate != null)
            criteria = criteria.and(Criteria.where("deposit_timestamp").lessThanOrEquals(endDate));
        if (depositMethod != null)
            criteria = criteria.and(Criteria.where("deposit_method").is(depositMethod));
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
        query = query.sort(Sort.by(Sort.Direction.DESC, "deposit_timestamp"));
        return operations
                .select(query, TransactionLineDepositSearch.class)
                .map(row -> (TransactionLineDeposit) row);
    }

    @Override
    public Mono<Long> countByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            String depositMethod,
            BigDecimal minAmount,
            BigDecimal maxAmount) {
        Criteria criteria = Criteria.empty();
        if (startDate != null)
            criteria =
                    criteria.and(
                            Criteria.where("deposit_timestamp").greaterThanOrEquals(startDate));
        if (endDate != null)
            criteria = criteria.and(Criteria.where("deposit_timestamp").lessThanOrEquals(endDate));
        if (depositMethod != null)
            criteria = criteria.and(Criteria.where("deposit_method").is(depositMethod));
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
        return operations.count(query, TransactionLineDepositSearch.class);
    }
}
