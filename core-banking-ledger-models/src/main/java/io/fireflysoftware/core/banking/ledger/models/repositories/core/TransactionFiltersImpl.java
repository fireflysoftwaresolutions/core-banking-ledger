package io.fireflysoftware.core.banking.ledger.models.repositories.core;

import io.fireflysoftware.core.banking.ledger.interfaces.enums.core.TransactionStatusEnum;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.core.TransactionTypeEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.core.Transaction;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class TransactionFiltersImpl implements TransactionFilters {
    private final R2dbcEntityOperations operations;

    public TransactionFiltersImpl(R2dbcEntityOperations operations) {
        this.operations = operations;
    }

    @Override
    public Flux<Transaction> findByDateRangeAndCurrency(
            LocalDateTime startDate, LocalDateTime endDate, String currency, Pageable pageable) {
        Criteria criteria = Criteria.empty();
        if (startDate != null)
            criteria =
                    criteria.and(Criteria.where("transaction_date").greaterThanOrEquals(startDate));
        if (endDate != null)
            criteria = criteria.and(Criteria.where("transaction_date").lessThanOrEquals(endDate));
        if (currency != null) criteria = criteria.and(Criteria.where("currency").is(currency));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        return operations.select(query, Transaction.class);
    }

    @Override
    public Mono<Long> countByDateRangeAndCurrency(
            LocalDateTime startDate, LocalDateTime endDate, String currency) {
        Criteria criteria = Criteria.empty();
        if (startDate != null)
            criteria =
                    criteria.and(Criteria.where("transaction_date").greaterThanOrEquals(startDate));
        if (endDate != null)
            criteria = criteria.and(Criteria.where("transaction_date").lessThanOrEquals(endDate));
        if (currency != null) criteria = criteria.and(Criteria.where("currency").is(currency));
        Query query = Query.query(criteria);
        return operations.count(query, Transaction.class);
    }

    @Override
    public Flux<Transaction> findTransactionsByFilter(
            LocalDateTime startDate,
            LocalDateTime endDate,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            List<String> currencies,
            List<TransactionTypeEnum> types,
            List<TransactionStatusEnum> statuses,
            List<UUID> categoryIds,
            List<UUID> accountIds,
            String referenceNumber,
            String description,
            String initiatingParty,
            Boolean includeReversed,
            Boolean onlyFailed,
            Boolean onlyPending,
            Integer pageSize,
            Integer pageOffset,
            String sortBy,
            String sortDirection) {
        Criteria criteria = Criteria.empty();
        if (startDate != null)
            criteria = criteria.and("transaction_date").greaterThanOrEquals(startDate);
        if (endDate != null) criteria = criteria.and("transaction_date").lessThanOrEquals(endDate);
        if (minAmount != null)
            criteria = criteria.and("total_amount").greaterThanOrEquals(minAmount);
        if (maxAmount != null) criteria = criteria.and("total_amount").lessThanOrEquals(maxAmount);
        if (currencies != null) criteria = criteria.and("currency").in(currencies);
        if (types != null) criteria = criteria.and("transaction_type").in(types);
        if (statuses != null) criteria = criteria.and("transaction_status").in(statuses);
        if (categoryIds != null) criteria = criteria.and("transaction_category_id").in(categoryIds);
        if (accountIds != null) criteria = criteria.and("account_id").in(accountIds);
        if (referenceNumber != null)
            criteria = criteria.and("external_reference").like("%" + referenceNumber + "%");
        if (description != null)
            criteria = criteria.and("description").like("%" + description + "%");
        if (initiatingParty != null)
            criteria = criteria.and("initiating_party").like("%" + initiatingParty + "%");
        if (Boolean.FALSE.equals(includeReversed))
            criteria = criteria.and("transaction_status").not(TransactionStatusEnum.REVERSED);
        if (Boolean.TRUE.equals(onlyFailed))
            criteria = criteria.and("transaction_status").is(TransactionStatusEnum.FAILED);
        if (Boolean.TRUE.equals(onlyPending))
            criteria = criteria.and("transaction_status").is(TransactionStatusEnum.PENDING);
        Query query = Query.query(criteria);
        query = query.limit(pageSize).offset(pageOffset);
        if ("ASC".equals(sortDirection) && "transaction_date".equals(sortBy))
            query = query.sort(Sort.by(Sort.Direction.ASC, "transaction_date"));
        return operations.select(query, Transaction.class);
    }

    @Override
    public Mono<Long> countTransactionsByFilter(
            LocalDateTime startDate,
            LocalDateTime endDate,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            List<String> currencies,
            List<TransactionTypeEnum> types,
            List<TransactionStatusEnum> statuses,
            List<UUID> categoryIds,
            List<UUID> accountIds,
            String referenceNumber,
            String description,
            String initiatingParty,
            Boolean includeReversed,
            Boolean onlyFailed,
            Boolean onlyPending) {
        Criteria criteria = Criteria.empty();
        if (startDate != null)
            criteria = criteria.and("transaction_date").greaterThanOrEquals(startDate);
        if (endDate != null) criteria = criteria.and("transaction_date").lessThanOrEquals(endDate);
        if (minAmount != null)
            criteria = criteria.and("total_amount").greaterThanOrEquals(minAmount);
        if (maxAmount != null) criteria = criteria.and("total_amount").lessThanOrEquals(maxAmount);
        if (currencies != null) criteria = criteria.and("currency").in(currencies);
        if (types != null) criteria = criteria.and("transaction_type").in(types);
        if (statuses != null) criteria = criteria.and("transaction_status").in(statuses);
        if (categoryIds != null) criteria = criteria.and("transaction_category_id").in(categoryIds);
        if (accountIds != null) criteria = criteria.and("account_id").in(accountIds);
        if (referenceNumber != null)
            criteria = criteria.and("external_reference").like("%" + referenceNumber + "%");
        if (description != null)
            criteria = criteria.and("description").like("%" + description + "%");
        if (initiatingParty != null)
            criteria = criteria.and("initiating_party").like("%" + initiatingParty + "%");
        if (Boolean.FALSE.equals(includeReversed))
            criteria = criteria.and("transaction_status").not(TransactionStatusEnum.REVERSED);
        if (Boolean.TRUE.equals(onlyFailed))
            criteria = criteria.and("transaction_status").is(TransactionStatusEnum.FAILED);
        if (Boolean.TRUE.equals(onlyPending))
            criteria = criteria.and("transaction_status").is(TransactionStatusEnum.PENDING);
        Query query = Query.query(criteria);
        return operations.count(query, Transaction.class);
    }
}
