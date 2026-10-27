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


package io.fireflysoftware.core.banking.ledger.core.services.fee.impl;

import io.fireflysoftware.core.banking.ledger.core.mappers.fee.TransactionLineFeeMapper;
import io.fireflysoftware.core.banking.ledger.core.services.fee.TransactionLineFeeService;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.fee.TransactionLineFeeDTO;
import io.fireflysoftware.core.banking.ledger.models.entities.fee.TransactionLineFee;
import io.fireflysoftware.core.banking.ledger.models.repositories.fee.TransactionLineFeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.util.UUID;



/**
 * Implementation of the TransactionLineFeeService interface.
 * Provides functionality for managing fee transaction lines.
 */
@Service
@Transactional
public class TransactionLineFeeServiceImpl implements TransactionLineFeeService {

    @Autowired
    private TransactionLineFeeRepository repository;

    @Autowired
    private TransactionLineFeeMapper mapper;

    @Override
    public Mono<TransactionLineFeeDTO> getFeeLine(UUID transactionId) {
        return repository.findByTransactionId(transactionId)
                .map(mapper::toDTO)
                .switchIfEmpty(Mono.error(new RuntimeException("Transaction Line Fee not found")));
    }

    @Override
    public Mono<TransactionLineFeeDTO> createFeeLine(UUID transactionId, TransactionLineFeeDTO feeDTO) {
        feeDTO.setTransactionId(transactionId);
        TransactionLineFee entity = mapper.toEntity(feeDTO);
        return repository.save(entity)
                .map(mapper::toDTO)
                .onErrorResume(e -> Mono.error(new RuntimeException("Failed to create Transaction Line Fee", e)));
    }

    @Override
    public Mono<TransactionLineFeeDTO> updateFeeLine(UUID transactionId, TransactionLineFeeDTO feeDTO) {
        return repository.findByTransactionId(transactionId)
                .flatMap(existingEntity -> {
                    TransactionLineFee updatedEntity = mapper.toEntity(feeDTO);
                    updatedEntity.setTransactionLineFeeId(existingEntity.getTransactionLineFeeId());
                    updatedEntity.setTransactionId(transactionId);
                    return repository.save(updatedEntity);
                })
                .map(mapper::toDTO)
                .switchIfEmpty(Mono.error(new RuntimeException("Failed to update Transaction Line Fee")));
    }

    @Override
    public Mono<Void> deleteFeeLine(UUID transactionId) {
        return repository.findByTransactionId(transactionId)
                .flatMap(entity -> repository.delete(entity))
                .switchIfEmpty(Mono.empty());
    }
}
