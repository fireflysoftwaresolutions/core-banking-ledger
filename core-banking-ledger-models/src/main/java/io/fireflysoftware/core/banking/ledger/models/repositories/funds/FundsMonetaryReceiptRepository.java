package io.fireflysoftware.core.banking.ledger.models.repositories.funds;

import io.fireflysoftware.core.banking.ledger.models.entities.funds.*;

import org.springframework.data.repository.Repository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FundsMonetaryReceiptRepository extends Repository<FundsMonetaryReceipt, UUID> {
    Flux<FundsMonetaryReceipt> findTop3ByTenantIdAndReservationIdOrderByEffectKindAsc(
            String tenantId, UUID reservationId);

    Mono<FundsMonetaryReceipt> findById(UUID id);
}
