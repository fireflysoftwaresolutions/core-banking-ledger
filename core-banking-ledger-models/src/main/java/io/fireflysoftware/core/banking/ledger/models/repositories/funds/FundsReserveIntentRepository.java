package io.fireflysoftware.core.banking.ledger.models.repositories.funds;

import io.fireflysoftware.core.banking.ledger.models.entities.funds.*;

import org.springframework.data.repository.Repository;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FundsReserveIntentRepository extends Repository<FundsReserveIntent, UUID> {
    Mono<FundsReserveIntent> findByTenantIdAndReservationId(String tenantId, UUID reservationId);

    Mono<FundsReserveIntent> findById(UUID id);
}
