package io.fireflysoftware.core.banking.ledger.models.repositories.leg;

import io.fireflysoftware.core.banking.ledger.models.entities.leg.TransactionLeg;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

public class TransactionLegFiltersImpl implements TransactionLegFilters {
    private final R2dbcEntityOperations operations;

    public TransactionLegFiltersImpl(R2dbcEntityOperations operations) {
        this.operations = operations;
    }

    @Override
    public Flux<TransactionLeg> findByAccountIdAndBookingDateBetween(
            UUID accountId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        if (accountId == null || endDate == null || startDate == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("account_id").is(accountId));
        criteria = criteria.and(Criteria.where("booking_date").greaterThanOrEquals(startDate));
        criteria = criteria.and(Criteria.where("booking_date").lessThanOrEquals(endDate));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        query = query.sort(Sort.by(Sort.Order.desc("booking_date")));
        return operations.select(query, TransactionLeg.class);
    }

    @Override
    public Mono<Long> countByAccountIdAndBookingDateBetween(
            UUID accountId, LocalDateTime startDate, LocalDateTime endDate) {
        if (accountId == null || endDate == null || startDate == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("account_id").is(accountId));
        criteria = criteria.and(Criteria.where("booking_date").greaterThanOrEquals(startDate));
        criteria = criteria.and(Criteria.where("booking_date").lessThanOrEquals(endDate));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLeg.class);
    }
}
