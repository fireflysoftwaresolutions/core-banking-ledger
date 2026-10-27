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


package io.fireflysoftware.core.banking.ledger.core.services.withdrawal.impl;

import io.fireflysoftware.core.banking.ledger.core.mappers.withdrawal.TransactionLineWithdrawalMapper;
import io.fireflysoftware.core.banking.ledger.core.services.withdrawal.TransactionLineWithdrawalService;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.withdrawal.TransactionLineWithdrawalDTO;
import io.fireflysoftware.core.banking.ledger.models.entities.withdrawal.TransactionLineWithdrawal;
import io.fireflysoftware.core.banking.ledger.models.repositories.withdrawal.TransactionLineWithdrawalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.util.UUID;



/**
 * Implementation of the TransactionLineWithdrawalService interface.
 * Provides functionality for managing withdrawal transaction lines.
 */
@Service
@Transactional
public class TransactionLineWithdrawalServiceImpl implements TransactionLineWithdrawalService {

    @Autowired
    private TransactionLineWithdrawalRepository repository;

    @Autowired
    private TransactionLineWithdrawalMapper mapper;

    @Override
    public Mono<TransactionLineWithdrawalDTO> getWithdrawalLine(UUID transactionId) {
        return repository.findByTransactionId(transactionId)
                .map(mapper::toDTO)
                .switchIfEmpty(Mono.error(new RuntimeException("Transaction Line Withdrawal not found")));
    }

    @Override
    public Mono<TransactionLineWithdrawalDTO> createWithdrawalLine(UUID transactionId, TransactionLineWithdrawalDTO withdrawalDTO) {
        withdrawalDTO.setTransactionId(transactionId);
        TransactionLineWithdrawal entity = mapper.toEntity(withdrawalDTO);
        return repository.save(entity)
                .map(mapper::toDTO)
                .onErrorResume(e -> Mono.error(new RuntimeException("Failed to create Transaction Line Withdrawal", e)));
    }

    @Override
    public Mono<TransactionLineWithdrawalDTO> updateWithdrawalLine(UUID transactionId, TransactionLineWithdrawalDTO withdrawalDTO) {
        return repository.findByTransactionId(transactionId)
                .flatMap(existingEntity -> {
                    TransactionLineWithdrawal updatedEntity = mapper.toEntity(withdrawalDTO);
                    updatedEntity.setTransactionLineWithdrawalId(existingEntity.getTransactionLineWithdrawalId());
                    updatedEntity.setTransactionId(transactionId);
                    return repository.save(updatedEntity);
                })
                .map(mapper::toDTO)
                .switchIfEmpty(Mono.error(new RuntimeException("Failed to update Transaction Line Withdrawal")));
    }

    @Override
    public Mono<Void> deleteWithdrawalLine(UUID transactionId) {
        return repository.findByTransactionId(transactionId)
                .flatMap(entity -> repository.delete(entity))
                .switchIfEmpty(Mono.empty());
    }
}
