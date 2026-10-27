package io.fireflysoftware.core.banking.ledger.models.repositories.deposit;

import io.fireflysoftware.core.banking.ledger.models.entities.deposit.TransactionLineDeposit;

import org.springframework.data.domain.Pageable;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface TransactionLineDepositFilters {
    Flux<TransactionLineDeposit> findByDepositMethod(String depositMethod, Pageable pageable);

    Mono<Long> countByDepositMethod(String depositMethod);

    Flux<TransactionLineDeposit> findByDepositBranchId(String branchId, Pageable pageable);

    Mono<Long> countByDepositBranchId(String branchId);

    Flux<TransactionLineDeposit> findByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            String depositMethod,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Pageable pageable);

    Mono<Long> countByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            String depositMethod,
            BigDecimal minAmount,
            BigDecimal maxAmount);
}
