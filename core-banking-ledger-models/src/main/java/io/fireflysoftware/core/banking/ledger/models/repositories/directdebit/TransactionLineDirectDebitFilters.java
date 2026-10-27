package io.fireflysoftware.core.banking.ledger.models.repositories.directdebit;

import io.fireflysoftware.core.banking.ledger.interfaces.enums.directdebit.DirectDebitProcessingStatusEnum;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.directdebit.DirectDebitSequenceTypeEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.directdebit.TransactionLineDirectDebit;

import org.springframework.data.domain.Pageable;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface TransactionLineDirectDebitFilters {
    Flux<TransactionLineDirectDebit> findByDirectDebitMandateId(
            String mandateId, Pageable pageable);

    Mono<Long> countByDirectDebitMandateId(String mandateId);

    Flux<TransactionLineDirectDebit> findByDirectDebitCreditorId(
            String creditorId, Pageable pageable);

    Mono<Long> countByDirectDebitCreditorId(String creditorId);

    Flux<TransactionLineDirectDebit> findByDirectDebitProcessingStatus(
            DirectDebitProcessingStatusEnum status, Pageable pageable);

    Mono<Long> countByDirectDebitProcessingStatus(DirectDebitProcessingStatusEnum status);

    Flux<TransactionLineDirectDebit> findBySequenceTypeAndStatus(
            DirectDebitSequenceTypeEnum sequenceType,
            DirectDebitProcessingStatusEnum status,
            Pageable pageable);

    Mono<Long> countBySequenceTypeAndStatus(
            DirectDebitSequenceTypeEnum sequenceType, DirectDebitProcessingStatusEnum status);

    Flux<TransactionLineDirectDebit> findPendingDirectDebitsForProcessing(
            LocalDate date, Pageable pageable);

    Mono<Long> countPendingDirectDebitsForProcessing(LocalDate date);

    Flux<TransactionLineDirectDebit> findByDebtorNameContaining(
            String debtorName, Pageable pageable);

    Mono<Long> countByDebtorNameContaining(String debtorName);

    Flux<TransactionLineDirectDebit> findCompletedRecurringByMandate(
            String mandateId, Pageable pageable);

    Mono<Long> countCompletedRecurringByMandate(String mandateId);

    Flux<TransactionLineDirectDebit> findByCustomCriteria(
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
            Pageable pageable);

    Mono<Long> countByCustomCriteria(
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String mandateId,
            String creditorId,
            String debtorName,
            DirectDebitProcessingStatusEnum processingStatus,
            DirectDebitSequenceTypeEnum sequenceType,
            LocalDate dueDateStart,
            LocalDate dueDateEnd,
            Boolean isRevoked);
}
