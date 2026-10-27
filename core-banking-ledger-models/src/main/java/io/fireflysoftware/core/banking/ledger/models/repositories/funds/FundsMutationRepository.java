package io.fireflysoftware.core.banking.ledger.models.repositories.funds;

import io.fireflysoftware.core.banking.ledger.models.entities.funds.FundsAccount;

import org.springframework.data.repository.Repository;

import java.util.UUID;

public interface FundsMutationRepository
        extends Repository<FundsAccount, UUID>, FundsWriteOperations {}
