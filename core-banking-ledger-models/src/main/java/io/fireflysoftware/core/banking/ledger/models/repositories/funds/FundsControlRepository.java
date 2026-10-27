package io.fireflysoftware.core.banking.ledger.models.repositories.funds;

import io.fireflysoftware.core.banking.ledger.models.entities.funds.*;

import org.springframework.data.repository.Repository;

import reactor.core.publisher.Mono;

public interface FundsControlRepository extends Repository<FundsControl, FundsControlId> {
    Mono<FundsControl> findById(FundsControlId id);
}
