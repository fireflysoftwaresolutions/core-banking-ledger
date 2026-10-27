package io.fireflysoftware.core.banking.ledger.models.repositories.sepa;

import io.fireflysoftware.core.banking.ledger.interfaces.enums.sepa.SepaTransactionStatusEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.sepa.TransactionLineSepaTransfer;

import org.springframework.data.domain.Pageable;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface TransactionLineSepaFilters {
    Flux<TransactionLineSepaTransfer> findOutgoingTransfersByIban(String iban, Pageable pageable);

    Mono<Long> countOutgoingTransfersByIban(String iban);

    Flux<TransactionLineSepaTransfer> findIncomingTransfersByIban(String iban, Pageable pageable);

    Mono<Long> countIncomingTransfersByIban(String iban);

    Flux<TransactionLineSepaTransfer> findPendingTransfersForExecution(
            LocalDateTime date, Pageable pageable);

    Mono<Long> countPendingTransfersForExecution(LocalDateTime date);

    Flux<TransactionLineSepaTransfer> findByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            SepaTransactionStatusEnum status,
            String creditorId,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Pageable pageable);

    Mono<Long> countByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            SepaTransactionStatusEnum status,
            String creditorId,
            BigDecimal minAmount,
            BigDecimal maxAmount);
}
