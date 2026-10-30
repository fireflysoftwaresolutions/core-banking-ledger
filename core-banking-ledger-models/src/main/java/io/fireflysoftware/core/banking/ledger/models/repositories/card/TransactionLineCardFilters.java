package io.fireflysoftware.core.banking.ledger.models.repositories.card;

import io.fireflysoftware.core.banking.ledger.models.entities.card.TransactionLineCard;

import org.springframework.data.domain.Pageable;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface TransactionLineCardFilters {
    Flux<TransactionLineCard> findByCardMerchantCode(String merchantCode, Pageable pageable);

    Mono<Long> countByCardMerchantCode(String merchantCode);

    Flux<TransactionLineCard> findByCardTerminalId(String terminalId, Pageable pageable);

    Flux<TransactionLineCard> findByCardFraudFlag(Boolean fraudFlag, Pageable pageable);

    Flux<TransactionLineCard> findByMerchantNameContaining(String merchantName, Pageable pageable);

    Mono<Long> countByMerchantNameContaining(String merchantName);

    Flux<TransactionLineCard> findFraudulentTransactionsByCountry(
            String country, Pageable pageable);

    Mono<Long> countFraudulentTransactionsByCountry(String country);

    Flux<TransactionLineCard> findByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            String merchantCode,
            Boolean cardPresent,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Pageable pageable);

    Mono<Long> countByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            String merchantCode,
            Boolean cardPresent,
            BigDecimal minAmount,
            BigDecimal maxAmount);
}
