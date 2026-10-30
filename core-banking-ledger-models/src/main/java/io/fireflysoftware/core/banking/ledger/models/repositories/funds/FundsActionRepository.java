package io.fireflysoftware.core.banking.ledger.models.repositories.funds;

import io.fireflysoftware.core.banking.ledger.models.entities.funds.*;

import org.springframework.data.repository.Repository;

import reactor.core.publisher.Mono;

public interface FundsActionRepository extends Repository<FundsAction, FundsActionId> {
    Mono<FundsAction> findById(FundsActionId id);
}
