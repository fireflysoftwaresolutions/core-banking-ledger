package io.fireflysoftware.core.banking.ledger.models.repositories.funds;

import io.fireflysoftware.core.banking.ledger.models.entities.funds.*;

import org.springframework.data.repository.Repository;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FundsOutboxRepository extends Repository<FundsOutbox, UUID> {
    Mono<FundsOutbox> findById(UUID id);
}
