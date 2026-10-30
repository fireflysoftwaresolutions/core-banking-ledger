/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.fireflysoftware.core.banking.ledger.core.services.deposit.impl;

import io.fireflysoftware.core.banking.ledger.core.mappers.deposit.TransactionLineDepositMapper;
import io.fireflysoftware.core.banking.ledger.core.services.deposit.TransactionLineDepositService;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.deposit.TransactionLineDepositDTO;
import io.fireflysoftware.core.banking.ledger.models.entities.deposit.TransactionLineDeposit;
import io.fireflysoftware.core.banking.ledger.models.repositories.deposit.TransactionLineDepositRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.util.UUID;



/**
 * Implementation of the TransactionLineDepositService interface.
 * Provides functionality for managing deposit transaction lines.
 */
@Service
@Transactional
public class TransactionLineDepositServiceImpl implements TransactionLineDepositService {

    @Autowired
    private TransactionLineDepositRepository repository;

    @Autowired
    private TransactionLineDepositMapper mapper;

    @Override
    public Mono<TransactionLineDepositDTO> getDepositLine(UUID transactionId) {
        return repository.findByTransactionId(transactionId)
                .map(mapper::toDTO)
                .switchIfEmpty(Mono.error(new RuntimeException("Transaction Line Deposit not found")));
    }

    @Override
    public Mono<TransactionLineDepositDTO> createDepositLine(UUID transactionId, TransactionLineDepositDTO depositDTO) {
        depositDTO.setTransactionId(transactionId);
        TransactionLineDeposit entity = mapper.toEntity(depositDTO);
        return repository.save(entity)
                .map(mapper::toDTO)
                .onErrorResume(e -> Mono.error(new RuntimeException("Failed to create Transaction Line Deposit", e)));
    }

    @Override
    public Mono<TransactionLineDepositDTO> updateDepositLine(UUID transactionId, TransactionLineDepositDTO depositDTO) {
        return repository.findByTransactionId(transactionId)
                .switchIfEmpty(Mono.error(new RuntimeException("Failed to update Transaction Line Deposit: Entity not found")))
                .flatMap(existingEntity -> {
                    TransactionLineDeposit updatedEntity = mapper.toEntity(depositDTO);
                    updatedEntity.setTransactionLineDepositId(existingEntity.getTransactionLineDepositId());
                    updatedEntity.setTransactionId(transactionId);
                    return repository.save(updatedEntity);
                })
                .map(mapper::toDTO)
                .onErrorResume(e -> Mono.error(new RuntimeException("Failed to update Transaction Line Deposit", e)));
    }

    @Override
    public Mono<Void> deleteDepositLine(UUID transactionId) {
        return repository.findByTransactionId(transactionId)
                .switchIfEmpty(Mono.error(new RuntimeException("Transaction Line Deposit not found")))
                .flatMap(repository::delete);
    }
}
