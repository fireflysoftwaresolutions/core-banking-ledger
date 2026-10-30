package io.fireflysoftware.core.banking.ledger.models.repositories.interest;

import io.fireflysoftware.core.banking.ledger.models.entities.interest.TransactionLineInterest;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class TransactionLineInterestFiltersImpl implements TransactionLineInterestFilters {
    private final R2dbcEntityOperations operations;

    public TransactionLineInterestFiltersImpl(R2dbcEntityOperations operations) {
        this.operations = operations;
    }

    @Override
    public Flux<TransactionLineInterest> findByInterestType(
            String interestType, Pageable pageable) {
        if (interestType == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("interest_type").is(interestType));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        query = query.sort(Sort.by(Sort.Order.desc("interest_timestamp")));
        return operations.select(query, TransactionLineInterest.class);
    }

    @Override
    public Mono<Long> countByInterestType(String interestType) {
        if (interestType == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("interest_type").is(interestType));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineInterest.class);
    }

    @Override
    public Flux<TransactionLineInterest> findByInterestRelatedAccountId(
            Long relatedAccountId, Pageable pageable) {
        if (relatedAccountId == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("interest_related_account_id").is(relatedAccountId));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        query = query.sort(Sort.by(Sort.Order.desc("interest_timestamp")));
        return operations.select(query, TransactionLineInterest.class);
    }

    @Override
    public Mono<Long> countByInterestRelatedAccountId(Long relatedAccountId) {
        if (relatedAccountId == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("interest_related_account_id").is(relatedAccountId));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineInterest.class);
    }

    @Override
    public Flux<TransactionLineInterest> findByAccrualPeriod(
            LocalDate startDate, LocalDate endDate, Pageable pageable) {
        if (endDate == null || startDate == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria =
                criteria.and(
                        Criteria.where("interest_accrual_start_date")
                                .greaterThanOrEquals(startDate));
        criteria =
                criteria.and(Criteria.where("interest_accrual_end_date").lessThanOrEquals(endDate));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        query = query.sort(Sort.by(Sort.Order.desc("interest_timestamp")));
        return operations.select(query, TransactionLineInterest.class);
    }

    @Override
    public Mono<Long> countByAccrualPeriod(LocalDate startDate, LocalDate endDate) {
        if (endDate == null || startDate == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria =
                criteria.and(
                        Criteria.where("interest_accrual_start_date")
                                .greaterThanOrEquals(startDate));
        criteria =
                criteria.and(Criteria.where("interest_accrual_end_date").lessThanOrEquals(endDate));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineInterest.class);
    }

    @Override
    public Flux<TransactionLineInterest> findByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            String interestType,
            Long relatedAccountId,
            BigDecimal minRate,
            BigDecimal maxRate,
            Pageable pageable) {
        Criteria criteria = Criteria.empty();
        if (startDate != null)
            criteria =
                    criteria.and(
                            Criteria.where("interest_timestamp").greaterThanOrEquals(startDate));
        if (endDate != null)
            criteria = criteria.and(Criteria.where("interest_timestamp").lessThanOrEquals(endDate));
        if (interestType != null)
            criteria = criteria.and(Criteria.where("interest_type").is(interestType));
        if (relatedAccountId != null)
            criteria =
                    criteria.and(
                            Criteria.where("interest_related_account_id").is(relatedAccountId));
        if (minRate != null)
            criteria =
                    criteria.and(
                            Criteria.where("interest_rate_percentage")
                                    .greaterThanOrEquals(minRate));
        if (maxRate != null)
            criteria =
                    criteria.and(
                            Criteria.where("interest_rate_percentage").lessThanOrEquals(maxRate));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        query = query.sort(Sort.by(Sort.Order.desc("interest_timestamp")));
        return operations.select(query, TransactionLineInterest.class);
    }

    @Override
    public Mono<Long> countByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            String interestType,
            Long relatedAccountId,
            BigDecimal minRate,
            BigDecimal maxRate) {
        Criteria criteria = Criteria.empty();
        if (startDate != null)
            criteria =
                    criteria.and(
                            Criteria.where("interest_timestamp").greaterThanOrEquals(startDate));
        if (endDate != null)
            criteria = criteria.and(Criteria.where("interest_timestamp").lessThanOrEquals(endDate));
        if (interestType != null)
            criteria = criteria.and(Criteria.where("interest_type").is(interestType));
        if (relatedAccountId != null)
            criteria =
                    criteria.and(
                            Criteria.where("interest_related_account_id").is(relatedAccountId));
        if (minRate != null)
            criteria =
                    criteria.and(
                            Criteria.where("interest_rate_percentage")
                                    .greaterThanOrEquals(minRate));
        if (maxRate != null)
            criteria =
                    criteria.and(
                            Criteria.where("interest_rate_percentage").lessThanOrEquals(maxRate));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineInterest.class);
    }
}
