package io.fireflysoftware.core.banking.ledger.models.repositories.wire;

import io.fireflysoftware.core.banking.ledger.interfaces.enums.wire.WireTransferPriorityEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.wire.TransactionLineWireTransfer;

import org.springframework.data.domain.Pageable;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface TransactionLineWireFilters {
    Flux<TransactionLineWireTransfer> findByWireCancelledFlag(Boolean cancelled, Pageable pageable);

    Mono<Long> countByWireCancelledFlag(Boolean cancelled);

    Flux<TransactionLineWireTransfer> findByWireTransferPriority(
            WireTransferPriorityEnum priority, Pageable pageable);

    Mono<Long> countByWireTransferPriority(WireTransferPriorityEnum priority);

    Flux<TransactionLineWireTransfer> findOutgoingTransfersBySwift(
            String swiftCode, Pageable pageable);

    Mono<Long> countOutgoingTransfersBySwift(String swiftCode);

    Flux<TransactionLineWireTransfer> findIncomingTransfersBySwift(
            String swiftCode, Pageable pageable);

    Mono<Long> countIncomingTransfersBySwift(String swiftCode);

    Flux<TransactionLineWireTransfer> findByBeneficiaryNameContaining(
            String beneficiaryName, Pageable pageable);

    Mono<Long> countByBeneficiaryNameContaining(String beneficiaryName);

    Flux<TransactionLineWireTransfer> findPendingTransfers(Pageable pageable);

    Mono<Long> countPendingTransfers();

    Flux<TransactionLineWireTransfer> findByExchangeRateRange(
            Double minRate, Double maxRate, Pageable pageable);

    Mono<Long> countByExchangeRateRange(Double minRate, Double maxRate);

    Flux<TransactionLineWireTransfer> findByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            WireTransferPriorityEnum priority,
            Boolean cancelled,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String beneficiaryName,
            String originSwiftCode,
            String destinationSwiftCode,
            BigDecimal minExchangeRate,
            BigDecimal maxExchangeRate,
            BigDecimal minFeeAmount,
            BigDecimal maxFeeAmount,
            Pageable pageable);

    Mono<Long> countByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            WireTransferPriorityEnum priority,
            Boolean cancelled,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String beneficiaryName,
            String originSwiftCode,
            String destinationSwiftCode,
            BigDecimal minExchangeRate,
            BigDecimal maxExchangeRate,
            BigDecimal minFeeAmount,
            BigDecimal maxFeeAmount);
}
