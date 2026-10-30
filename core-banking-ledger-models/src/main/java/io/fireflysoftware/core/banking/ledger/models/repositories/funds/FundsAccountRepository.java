package io.fireflysoftware.core.banking.ledger.models.repositories.funds;

import io.fireflysoftware.core.banking.ledger.models.entities.funds.*;

import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.data.relational.repository.Lock;
import org.springframework.data.repository.Repository;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FundsAccountRepository extends Repository<FundsAccount, UUID> {
    Mono<FundsAccount> findByTenantIdAndAccountId(String tenantId, UUID accountId);

    @Lock(LockMode.PESSIMISTIC_WRITE)
    Mono<FundsAccount> findLockedByTenantIdAndAccountId(String tenantId, UUID accountId);

    Mono<FundsAccount> findById(UUID id);
}
