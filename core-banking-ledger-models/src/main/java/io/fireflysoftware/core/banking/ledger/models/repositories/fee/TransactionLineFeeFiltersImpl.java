package io.fireflysoftware.core.banking.ledger.models.repositories.fee;

import io.fireflysoftware.core.banking.ledger.models.entities.fee.TransactionLineFee;
import io.fireflysoftware.core.banking.ledger.models.entities.search.TransactionLineFeeSearch;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransactionLineFeeFiltersImpl implements TransactionLineFeeFilters {
    private final R2dbcEntityOperations operations;

    public TransactionLineFeeFiltersImpl(R2dbcEntityOperations operations) {
        this.operations = operations;
    }

    @Override
    public Flux<TransactionLineFee> findByFeeType(String feeType, Pageable pageable) {
        if (feeType == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("fee_type").is(feeType));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        query = query.sort(Sort.by(Sort.Order.desc("fee_timestamp")));
        return operations.select(query, TransactionLineFee.class);
    }

    @Override
    public Mono<Long> countByFeeType(String feeType) {
        if (feeType == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("fee_type").is(feeType));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineFee.class);
    }

    @Override
    public Flux<TransactionLineFee> findByFeeRelatedTransactionId(
            Long relatedTransactionId, Pageable pageable) {
        if (relatedTransactionId == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria =
                criteria.and(Criteria.where("fee_related_transaction_id").is(relatedTransactionId));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        query = query.sort(Sort.by(Sort.Order.desc("fee_timestamp")));
        return operations.select(query, TransactionLineFee.class);
    }

    @Override
    public Mono<Long> countByFeeRelatedTransactionId(Long relatedTransactionId) {
        if (relatedTransactionId == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria =
                criteria.and(Criteria.where("fee_related_transaction_id").is(relatedTransactionId));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineFee.class);
    }

    @Override
    public Flux<TransactionLineFee> findByFeeWaived(Boolean waived, Pageable pageable) {
        if (waived == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("fee_waived").is(waived));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        query = query.sort(Sort.by(Sort.Order.desc("fee_timestamp")));
        return operations.select(query, TransactionLineFee.class);
    }

    @Override
    public Mono<Long> countByFeeWaived(Boolean waived) {
        if (waived == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("fee_waived").is(waived));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineFee.class);
    }

    @Override
    public Flux<TransactionLineFee> findByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            String feeType,
            Boolean waived,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Pageable pageable) {
        Criteria criteria = Criteria.empty();
        if (startDate != null)
            criteria = criteria.and(Criteria.where("fee_timestamp").greaterThanOrEquals(startDate));
        if (endDate != null)
            criteria = criteria.and(Criteria.where("fee_timestamp").lessThanOrEquals(endDate));
        if (feeType != null) criteria = criteria.and(Criteria.where("fee_type").is(feeType));
        if (waived != null) criteria = criteria.and(Criteria.where("fee_waived").is(waived));
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
        query = query.sort(Sort.by(Sort.Direction.DESC, "fee_timestamp"));
        return operations
                .select(query, TransactionLineFeeSearch.class)
                .map(row -> (TransactionLineFee) row);
    }

    @Override
    public Mono<Long> countByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            String feeType,
            Boolean waived,
            BigDecimal minAmount,
            BigDecimal maxAmount) {
        Criteria criteria = Criteria.empty();
        if (startDate != null)
            criteria = criteria.and(Criteria.where("fee_timestamp").greaterThanOrEquals(startDate));
        if (endDate != null)
            criteria = criteria.and(Criteria.where("fee_timestamp").lessThanOrEquals(endDate));
        if (feeType != null) criteria = criteria.and(Criteria.where("fee_type").is(feeType));
        if (waived != null) criteria = criteria.and(Criteria.where("fee_waived").is(waived));
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
        return operations.count(query, TransactionLineFeeSearch.class);
    }
}
