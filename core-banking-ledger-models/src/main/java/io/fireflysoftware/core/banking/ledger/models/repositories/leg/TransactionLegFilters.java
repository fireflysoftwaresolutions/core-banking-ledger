package io.fireflysoftware.core.banking.ledger.models.repositories.leg;

import io.fireflysoftware.core.banking.ledger.models.entities.leg.TransactionLeg;

import org.springframework.data.domain.Pageable;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

public interface TransactionLegFilters {
    Flux<TransactionLeg> findByAccountIdAndBookingDateBetween(
            UUID accountId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    Mono<Long> countByAccountIdAndBookingDateBetween(
            UUID accountId, LocalDateTime startDate, LocalDateTime endDate);
}
