package io.fireflysoftware.core.banking.ledger.models.repositories.funds;

import io.fireflysoftware.core.banking.ledger.models.entities.funds.*;

import org.springframework.data.repository.Repository;

import reactor.core.publisher.Mono;

public interface LedgerPostingClaimRepository extends Repository<LedgerPostingClaim, String> {
    Mono<LedgerPostingClaim> findById(String id);
}
