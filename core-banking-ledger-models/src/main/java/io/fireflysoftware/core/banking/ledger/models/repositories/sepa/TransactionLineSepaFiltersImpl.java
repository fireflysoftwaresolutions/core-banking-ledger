package io.fireflysoftware.core.banking.ledger.models.repositories.sepa;

import io.fireflysoftware.core.banking.ledger.interfaces.enums.sepa.SepaTransactionStatusEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.search.TransactionLineSepaTransferSearch;
import io.fireflysoftware.core.banking.ledger.models.entities.sepa.TransactionLineSepaTransfer;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransactionLineSepaFiltersImpl implements TransactionLineSepaFilters {
    private final R2dbcEntityOperations operations;

    public TransactionLineSepaFiltersImpl(R2dbcEntityOperations operations) {
        this.operations = operations;
    }

    @Override
    public Flux<TransactionLineSepaTransfer> findOutgoingTransfersByIban(
            String iban, Pageable pageable) {
        if (iban == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("sepa_origin_iban").is(iban));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        query = query.sort(Sort.by(Sort.Order.desc("sepa_processing_date")));
        return operations.select(query, TransactionLineSepaTransfer.class);
    }

    @Override
    public Mono<Long> countOutgoingTransfersByIban(String iban) {
        if (iban == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("sepa_origin_iban").is(iban));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineSepaTransfer.class);
    }

    @Override
    public Flux<TransactionLineSepaTransfer> findIncomingTransfersByIban(
            String iban, Pageable pageable) {
        if (iban == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("sepa_destination_iban").is(iban));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        query = query.sort(Sort.by(Sort.Order.desc("sepa_processing_date")));
        return operations.select(query, TransactionLineSepaTransfer.class);
    }

    @Override
    public Mono<Long> countIncomingTransfersByIban(String iban) {
        if (iban == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("sepa_destination_iban").is(iban));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineSepaTransfer.class);
    }

    @Override
    public Flux<TransactionLineSepaTransfer> findPendingTransfersForExecution(
            LocalDateTime date, Pageable pageable) {
        if (date == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("sepa_transaction_status").is("PDNG"));
        criteria =
                criteria.and(
                        Criteria.where("sepa_requested_execution_date").lessThanOrEquals(date));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        query = query.sort(Sort.by(Sort.Order.asc("sepa_requested_execution_date")));
        return operations.select(query, TransactionLineSepaTransfer.class);
    }

    @Override
    public Mono<Long> countPendingTransfersForExecution(LocalDateTime date) {
        if (date == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("sepa_transaction_status").is("PDNG"));
        criteria =
                criteria.and(
                        Criteria.where("sepa_requested_execution_date").lessThanOrEquals(date));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineSepaTransfer.class);
    }

    @Override
    public Flux<TransactionLineSepaTransfer> findByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            SepaTransactionStatusEnum status,
            String creditorId,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Pageable pageable) {
        Criteria criteria = Criteria.empty();
        if (startDate != null)
            criteria =
                    criteria.and(
                            Criteria.where("sepa_processing_date").greaterThanOrEquals(startDate));
        if (endDate != null)
            criteria =
                    criteria.and(Criteria.where("sepa_processing_date").lessThanOrEquals(endDate));
        if (status != null)
            criteria = criteria.and(Criteria.where("sepa_transaction_status").is(status));
        if (creditorId != null)
            criteria = criteria.and(Criteria.where("sepa_creditor_id").is(creditorId));
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
        return operations
                .select(query, TransactionLineSepaTransferSearch.class)
                .map(row -> (TransactionLineSepaTransfer) row);
    }

    @Override
    public Mono<Long> countByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            SepaTransactionStatusEnum status,
            String creditorId,
            BigDecimal minAmount,
            BigDecimal maxAmount) {
        Criteria criteria = Criteria.empty();
        if (startDate != null)
            criteria =
                    criteria.and(
                            Criteria.where("sepa_processing_date").greaterThanOrEquals(startDate));
        if (endDate != null)
            criteria =
                    criteria.and(Criteria.where("sepa_processing_date").lessThanOrEquals(endDate));
        if (status != null)
            criteria = criteria.and(Criteria.where("sepa_transaction_status").is(status));
        if (creditorId != null)
            criteria = criteria.and(Criteria.where("sepa_creditor_id").is(creditorId));
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
        return operations.count(query, TransactionLineSepaTransferSearch.class);
    }
}
