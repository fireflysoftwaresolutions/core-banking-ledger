package io.fireflysoftware.core.banking.ledger.models.repositories.funds;

import io.fireflysoftware.core.banking.ledger.models.entities.funds.*;

import org.springframework.data.repository.Repository;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FundsEventRepository extends Repository<FundsEvent, UUID> {
    Mono<FundsEvent> findById(UUID id);
}
