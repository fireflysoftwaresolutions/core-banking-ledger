package io.fireflysoftware.core.banking.ledger.models.repositories.interest;

import io.fireflysoftware.core.banking.ledger.models.entities.interest.TransactionLineInterest;

import org.springframework.data.domain.Pageable;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public interface TransactionLineInterestFilters {
    Flux<TransactionLineInterest> findByInterestType(String interestType, Pageable pageable);

    Mono<Long> countByInterestType(String interestType);

    Flux<TransactionLineInterest> findByInterestRelatedAccountId(
            Long relatedAccountId, Pageable pageable);

    Mono<Long> countByInterestRelatedAccountId(Long relatedAccountId);

    Flux<TransactionLineInterest> findByAccrualPeriod(
            LocalDate startDate, LocalDate endDate, Pageable pageable);

    Mono<Long> countByAccrualPeriod(LocalDate startDate, LocalDate endDate);

    Flux<TransactionLineInterest> findByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            String interestType,
            Long relatedAccountId,
            BigDecimal minRate,
            BigDecimal maxRate,
            Pageable pageable);

    Mono<Long> countByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            String interestType,
            Long relatedAccountId,
            BigDecimal minRate,
            BigDecimal maxRate);
}
