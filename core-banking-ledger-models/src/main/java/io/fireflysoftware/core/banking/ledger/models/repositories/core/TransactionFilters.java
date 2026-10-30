package io.fireflysoftware.core.banking.ledger.models.repositories.core;

import io.fireflysoftware.core.banking.ledger.interfaces.enums.core.TransactionStatusEnum;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.core.TransactionTypeEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.core.Transaction;

import org.springframework.data.domain.Pageable;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TransactionFilters {
    Flux<Transaction> findByDateRangeAndCurrency(
            LocalDateTime startDate, LocalDateTime endDate, String currency, Pageable pageable);

    Mono<Long> countByDateRangeAndCurrency(
            LocalDateTime startDate, LocalDateTime endDate, String currency);

    Flux<Transaction> findTransactionsByFilter(
            LocalDateTime startDate,
            LocalDateTime endDate,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            List<String> currencies,
            List<TransactionTypeEnum> types,
            List<TransactionStatusEnum> statuses,
            List<UUID> categoryIds,
            List<UUID> accountIds,
            String referenceNumber,
            String description,
            String initiatingParty,
            Boolean includeReversed,
            Boolean onlyFailed,
            Boolean onlyPending,
            Integer pageSize,
            Integer pageOffset,
            String sortBy,
            String sortDirection);

    Mono<Long> countTransactionsByFilter(
            LocalDateTime startDate,
            LocalDateTime endDate,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            List<String> currencies,
            List<TransactionTypeEnum> types,
            List<TransactionStatusEnum> statuses,
            List<UUID> categoryIds,
            List<UUID> accountIds,
            String referenceNumber,
            String description,
            String initiatingParty,
            Boolean includeReversed,
            Boolean onlyFailed,
            Boolean onlyPending);
}
