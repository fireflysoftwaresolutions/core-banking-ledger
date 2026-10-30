package io.fireflysoftware.core.banking.ledger.models.repositories.fee;

import io.fireflysoftware.core.banking.ledger.models.entities.fee.TransactionLineFee;

import org.springframework.data.domain.Pageable;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface TransactionLineFeeFilters {
    Flux<TransactionLineFee> findByFeeType(String feeType, Pageable pageable);

    Mono<Long> countByFeeType(String feeType);

    Flux<TransactionLineFee> findByFeeRelatedTransactionId(
            Long relatedTransactionId, Pageable pageable);

    Mono<Long> countByFeeRelatedTransactionId(Long relatedTransactionId);

    Flux<TransactionLineFee> findByFeeWaived(Boolean waived, Pageable pageable);

    Mono<Long> countByFeeWaived(Boolean waived);

    Flux<TransactionLineFee> findByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            String feeType,
            Boolean waived,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Pageable pageable);

    Mono<Long> countByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            String feeType,
            Boolean waived,
            BigDecimal minAmount,
            BigDecimal maxAmount);
}
