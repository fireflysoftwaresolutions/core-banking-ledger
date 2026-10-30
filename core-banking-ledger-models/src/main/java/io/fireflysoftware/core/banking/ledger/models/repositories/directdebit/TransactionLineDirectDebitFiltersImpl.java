package io.fireflysoftware.core.banking.ledger.models.repositories.directdebit;

import io.fireflysoftware.core.banking.ledger.interfaces.enums.directdebit.DirectDebitProcessingStatusEnum;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.directdebit.DirectDebitSequenceTypeEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.directdebit.TransactionLineDirectDebit;
import io.fireflysoftware.core.banking.ledger.models.entities.search.TransactionLineDirectDebitSearch;

import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;

public class TransactionLineDirectDebitFiltersImpl implements TransactionLineDirectDebitFilters {
    private final R2dbcEntityOperations operations;

    public TransactionLineDirectDebitFiltersImpl(R2dbcEntityOperations operations) {
        this.operations = operations;
    }

    @Override
    public Flux<TransactionLineDirectDebit> findByDirectDebitMandateId(
            String mandateId, Pageable pageable) {
        if (mandateId == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("direct_debit_mandate_id").is(mandateId));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        return operations.select(query, TransactionLineDirectDebit.class);
    }

    @Override
    public Mono<Long> countByDirectDebitMandateId(String mandateId) {
        if (mandateId == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("direct_debit_mandate_id").is(mandateId));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineDirectDebit.class);
    }

    @Override
    public Flux<TransactionLineDirectDebit> findByDirectDebitCreditorId(
            String creditorId, Pageable pageable) {
        if (creditorId == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("direct_debit_creditor_id").is(creditorId));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        return operations.select(query, TransactionLineDirectDebit.class);
    }

    @Override
    public Mono<Long> countByDirectDebitCreditorId(String creditorId) {
        if (creditorId == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("direct_debit_creditor_id").is(creditorId));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineDirectDebit.class);
    }

    @Override
    public Flux<TransactionLineDirectDebit> findByDirectDebitProcessingStatus(
            DirectDebitProcessingStatusEnum status, Pageable pageable) {
        if (status == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("direct_debit_processing_status").is(status));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        return operations.select(query, TransactionLineDirectDebit.class);
    }

    @Override
    public Mono<Long> countByDirectDebitProcessingStatus(DirectDebitProcessingStatusEnum status) {
        if (status == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("direct_debit_processing_status").is(status));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineDirectDebit.class);
    }

    @Override
    public Flux<TransactionLineDirectDebit> findBySequenceTypeAndStatus(
            DirectDebitSequenceTypeEnum sequenceType,
            DirectDebitProcessingStatusEnum status,
            Pageable pageable) {
        if (sequenceType == null || status == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("direct_debit_sequence_type").is(sequenceType));
        criteria = criteria.and(Criteria.where("direct_debit_processing_status").is(status));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        return operations.select(query, TransactionLineDirectDebit.class);
    }

    @Override
    public Mono<Long> countBySequenceTypeAndStatus(
            DirectDebitSequenceTypeEnum sequenceType, DirectDebitProcessingStatusEnum status) {
        if (sequenceType == null || status == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("direct_debit_sequence_type").is(sequenceType));
        criteria = criteria.and(Criteria.where("direct_debit_processing_status").is(status));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineDirectDebit.class);
    }

    @Override
    public Flux<TransactionLineDirectDebit> findPendingDirectDebitsForProcessing(
            LocalDate date, Pageable pageable) {
        if (date == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("direct_debit_processing_status").is("INITIATED"));
        criteria = criteria.and(Criteria.where("direct_debit_due_date").lessThanOrEquals(date));
        criteria = criteria.and(Criteria.where("direct_debit_revocation_date").isNull());
        Query query = Query.query(criteria);
        query = query.with(pageable);
        return operations.select(query, TransactionLineDirectDebit.class);
    }

    @Override
    public Mono<Long> countPendingDirectDebitsForProcessing(LocalDate date) {
        if (date == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("direct_debit_processing_status").is("INITIATED"));
        criteria = criteria.and(Criteria.where("direct_debit_due_date").lessThanOrEquals(date));
        criteria = criteria.and(Criteria.where("direct_debit_revocation_date").isNull());
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineDirectDebit.class);
    }

    @Override
    public Flux<TransactionLineDirectDebit> findByDebtorNameContaining(
            String debtorName, Pageable pageable) {
        if (debtorName == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria =
                criteria.and(
                        Criteria.where("direct_debit_debtor_name")
                                .like("%" + debtorName + "%")
                                .ignoreCase(true));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        return operations.select(query, TransactionLineDirectDebit.class);
    }

    @Override
    public Mono<Long> countByDebtorNameContaining(String debtorName) {
        if (debtorName == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria =
                criteria.and(
                        Criteria.where("direct_debit_debtor_name")
                                .like("%" + debtorName + "%")
                                .ignoreCase(true));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineDirectDebit.class);
    }

    @Override
    public Flux<TransactionLineDirectDebit> findCompletedRecurringByMandate(
            String mandateId, Pageable pageable) {
        if (mandateId == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("direct_debit_mandate_id").is(mandateId));
        criteria = criteria.and(Criteria.where("direct_debit_sequence_type").is("RCUR"));
        criteria = criteria.and(Criteria.where("direct_debit_processing_status").is("COMPLETED"));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        return operations.select(query, TransactionLineDirectDebit.class);
    }

    @Override
    public Mono<Long> countCompletedRecurringByMandate(String mandateId) {
        if (mandateId == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("direct_debit_mandate_id").is(mandateId));
        criteria = criteria.and(Criteria.where("direct_debit_sequence_type").is("RCUR"));
        criteria = criteria.and(Criteria.where("direct_debit_processing_status").is("COMPLETED"));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineDirectDebit.class);
    }

    @Override
    public Flux<TransactionLineDirectDebit> findByCustomCriteria(
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String mandateId,
            String creditorId,
            String debtorName,
            DirectDebitProcessingStatusEnum processingStatus,
            DirectDebitSequenceTypeEnum sequenceType,
            LocalDate dueDateStart,
            LocalDate dueDateEnd,
            Boolean isRevoked,
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
        if (mandateId != null)
            criteria = criteria.and(Criteria.where("direct_debit_mandate_id").is(mandateId));
        if (creditorId != null)
            criteria = criteria.and(Criteria.where("direct_debit_creditor_id").is(creditorId));
        if (debtorName != null)
            criteria =
                    criteria.and(
                            Criteria.where("direct_debit_debtor_name")
                                    .like("%" + debtorName + "%")
                                    .ignoreCase(true));
        if (processingStatus != null)
            criteria =
                    criteria.and(
                            Criteria.where("direct_debit_processing_status").is(processingStatus));
        if (sequenceType != null)
            criteria = criteria.and(Criteria.where("direct_debit_sequence_type").is(sequenceType));
        if (dueDateStart != null)
            criteria =
                    criteria.and(
                            Criteria.where("direct_debit_due_date")
                                    .greaterThanOrEquals(dueDateStart));
        if (dueDateEnd != null)
            criteria =
                    criteria.and(
                            Criteria.where("direct_debit_due_date").lessThanOrEquals(dueDateEnd));
        if (isRevoked != null)
            criteria =
                    criteria.and(
                            Boolean.TRUE.equals(isRevoked)
                                    ? Criteria.where("direct_debit_revocation_date").isNotNull()
                                    : Criteria.where("direct_debit_revocation_date").isNull());
        Query query = Query.query(criteria);
        query = query.with(pageable);
        return operations
                .select(query, TransactionLineDirectDebitSearch.class)
                .map(row -> (TransactionLineDirectDebit) row);
    }

    @Override
    public Mono<Long> countByCustomCriteria(
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String mandateId,
            String creditorId,
            String debtorName,
            DirectDebitProcessingStatusEnum processingStatus,
            DirectDebitSequenceTypeEnum sequenceType,
            LocalDate dueDateStart,
            LocalDate dueDateEnd,
            Boolean isRevoked) {
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
        if (mandateId != null)
            criteria = criteria.and(Criteria.where("direct_debit_mandate_id").is(mandateId));
        if (creditorId != null)
            criteria = criteria.and(Criteria.where("direct_debit_creditor_id").is(creditorId));
        if (debtorName != null)
            criteria =
                    criteria.and(
                            Criteria.where("direct_debit_debtor_name")
                                    .like("%" + debtorName + "%")
                                    .ignoreCase(true));
        if (processingStatus != null)
            criteria =
                    criteria.and(
                            Criteria.where("direct_debit_processing_status").is(processingStatus));
        if (sequenceType != null)
            criteria = criteria.and(Criteria.where("direct_debit_sequence_type").is(sequenceType));
        if (dueDateStart != null)
            criteria =
                    criteria.and(
                            Criteria.where("direct_debit_due_date")
                                    .greaterThanOrEquals(dueDateStart));
        if (dueDateEnd != null)
            criteria =
                    criteria.and(
                            Criteria.where("direct_debit_due_date").lessThanOrEquals(dueDateEnd));
        if (isRevoked != null)
            criteria =
                    criteria.and(
                            Boolean.TRUE.equals(isRevoked)
                                    ? Criteria.where("direct_debit_revocation_date").isNotNull()
                                    : Criteria.where("direct_debit_revocation_date").isNull());
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineDirectDebitSearch.class);
    }
}
