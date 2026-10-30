package io.fireflysoftware.core.banking.ledger.models.repositories.withdrawal;

import io.fireflysoftware.core.banking.ledger.models.entities.withdrawal.TransactionLineWithdrawal;

import org.springframework.data.domain.Pageable;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface TransactionLineWithdrawalFilters {
    Flux<TransactionLineWithdrawal> findByWithdrawalMethod(
            String withdrawalMethod, Pageable pageable);

    Mono<Long> countByWithdrawalMethod(String withdrawalMethod);

    Flux<TransactionLineWithdrawal> findByWithdrawalAtmId(String atmId, Pageable pageable);

    Mono<Long> countByWithdrawalAtmId(String atmId);

    Flux<TransactionLineWithdrawal> findByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            String withdrawalMethod,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Pageable pageable);

    Mono<Long> countByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            String withdrawalMethod,
            BigDecimal minAmount,
            BigDecimal maxAmount);
}
