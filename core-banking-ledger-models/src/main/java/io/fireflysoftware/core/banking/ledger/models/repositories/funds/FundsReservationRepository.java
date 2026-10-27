package io.fireflysoftware.core.banking.ledger.models.repositories.funds;

import io.fireflysoftware.core.banking.ledger.models.entities.funds.*;

import org.springframework.data.domain.Pageable;
import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.data.relational.repository.Lock;
import org.springframework.data.repository.Repository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FundsReservationRepository extends Repository<FundsReservation, UUID> {
    Mono<FundsReservation> findByTenantIdAndReservationId(String tenantId, UUID reservationId);

    @Lock(LockMode.PESSIMISTIC_WRITE)
    Mono<FundsReservation> findLockedByTenantIdAndReservationId(
            String tenantId, UUID reservationId);

    Flux<FundsReservation>
            findByTenantIdAndStateAndExpiresAtLessThanEqualOrderByExpiresAtAscReservationIdAsc(
                    String tenantId, String state, java.time.OffsetDateTime now, Pageable page);

    Mono<FundsReservation> findById(UUID id);
}
