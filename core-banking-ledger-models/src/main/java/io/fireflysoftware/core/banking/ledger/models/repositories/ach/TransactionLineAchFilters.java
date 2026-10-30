package io.fireflysoftware.core.banking.ledger.models.repositories.ach;

import io.fireflysoftware.core.banking.ledger.models.entities.ach.TransactionLineAch;

import org.springframework.data.domain.Pageable;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface TransactionLineAchFilters {
    Flux<TransactionLineAch> findByAchSourceAccountId(Long sourceAccountId, Pageable pageable);

    Mono<Long> countByAchSourceAccountId(Long sourceAccountId);

    Flux<TransactionLineAch> findByAchDestinationAccountId(
            Long destinationAccountId, Pageable pageable);

    Mono<Long> countByAchDestinationAccountId(Long destinationAccountId);

    Flux<TransactionLineAch> findByAchRoutingNumber(String routingNumber, Pageable pageable);

    Flux<TransactionLineAch> findByAchBatchNumber(String batchNumber, Pageable pageable);

    Flux<TransactionLineAch> findByCustomCriteria(
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
