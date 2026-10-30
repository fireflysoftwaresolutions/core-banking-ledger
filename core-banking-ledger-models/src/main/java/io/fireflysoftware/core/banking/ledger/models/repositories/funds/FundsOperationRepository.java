package io.fireflysoftware.core.banking.ledger.models.repositories.funds;

import io.fireflysoftware.core.banking.ledger.models.entities.funds.*;

import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.data.relational.repository.Lock;
import org.springframework.data.repository.Repository;

import reactor.core.publisher.Mono;

public interface FundsOperationRepository extends Repository<FundsOperation, FundsOperationId> {
    @Lock(LockMode.PESSIMISTIC_WRITE)
    Mono<FundsOperation> findLockedById(FundsOperationId id);

    Mono<FundsOperation> findById(FundsOperationId id);
}
