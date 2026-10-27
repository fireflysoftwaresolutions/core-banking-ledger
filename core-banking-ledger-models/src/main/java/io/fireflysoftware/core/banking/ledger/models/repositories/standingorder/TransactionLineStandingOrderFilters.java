package io.fireflysoftware.core.banking.ledger.models.repositories.standingorder;

import io.fireflysoftware.core.banking.ledger.interfaces.enums.standingorder.StandingOrderFrequencyEnum;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.standingorder.StandingOrderStatusEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.standingorder.TransactionLineStandingOrder;

import org.springframework.data.domain.Pageable;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface TransactionLineStandingOrderFilters {
    Mono<Long> countByStandingOrderStatus(StandingOrderStatusEnum status);

    Mono<Long> countByStandingOrderFrequency(StandingOrderFrequencyEnum frequency);

    Flux<TransactionLineStandingOrder> findActiveStandingOrders(
            LocalDate currentDate, Pageable pageable);

    Mono<Long> countActiveStandingOrders(LocalDate currentDate);

    Flux<TransactionLineStandingOrder> findStandingOrdersForExecution(
            LocalDate executionDate, Pageable pageable);

    Mono<Long> countStandingOrdersForExecution(LocalDate executionDate);

    Flux<TransactionLineStandingOrder> findByRecipientNameContaining(
            String recipientName, Pageable pageable);

    Mono<Long> countByRecipientNameContaining(String recipientName);

    Flux<TransactionLineStandingOrder> findActiveStandingOrdersByRecipientIban(
            String iban, Pageable pageable);

    Mono<Long> countActiveStandingOrdersByRecipientIban(String iban);

    Flux<TransactionLineStandingOrder> findExpiredSuspensions(LocalDate date, Pageable pageable);

    Mono<Long> countExpiredSuspensions(LocalDate date);

    Flux<TransactionLineStandingOrder> findByCustomCriteria(
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String standingOrderId,
            String recipientName,
            String recipientIban,
            String recipientBic,
            StandingOrderStatusEnum status,
            StandingOrderFrequencyEnum frequency,
            LocalDate startDateFrom,
            LocalDate startDateTo,
            LocalDate endDateFrom,
            LocalDate endDateTo,
            LocalDate nextExecutionDateFrom,
            LocalDate nextExecutionDateTo,
            Boolean isSuspended,
            Integer minTotalExecutions,
            Integer maxTotalExecutions,
            String createdBy,
            String updatedBy,
            Pageable pageable);

    Mono<Long> countByCustomCriteria(
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String standingOrderId,
            String recipientName,
            String recipientIban,
            String recipientBic,
            StandingOrderStatusEnum status,
            StandingOrderFrequencyEnum frequency,
            LocalDate startDateFrom,
            LocalDate startDateTo,
            LocalDate endDateFrom,
            LocalDate endDateTo,
            LocalDate nextExecutionDateFrom,
            LocalDate nextExecutionDateTo,
            Boolean isSuspended,
            Integer minTotalExecutions,
            Integer maxTotalExecutions,
            String createdBy,
            String updatedBy);
}
