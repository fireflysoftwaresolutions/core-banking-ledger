package io.fireflysoftware.core.banking.ledger.models.repositories.transfer;

import io.fireflysoftware.core.banking.ledger.models.entities.transfer.TransactionLineTransfer;

import org.springframework.data.domain.Pageable;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface TransactionLineTransferFilters {
    Flux<TransactionLineTransfer> findByTransferSourceAccountId(
            Long sourceAccountId, Pageable pageable);

    Mono<Long> countByTransferSourceAccountId(Long sourceAccountId);

    Flux<TransactionLineTransfer> findByTransferDestinationAccountId(
            Long destinationAccountId, Pageable pageable);

    Mono<Long> countByTransferDestinationAccountId(Long destinationAccountId);

    Flux<TransactionLineTransfer> findByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            Long sourceAccountId,
            Long destinationAccountId,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Pageable pageable);

    Mono<Long> countByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            Long sourceAccountId,
            Long destinationAccountId,
            BigDecimal minAmount,
            BigDecimal maxAmount);
}
