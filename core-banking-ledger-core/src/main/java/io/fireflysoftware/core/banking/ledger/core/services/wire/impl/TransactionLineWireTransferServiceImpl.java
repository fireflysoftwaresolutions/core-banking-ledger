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


package io.fireflysoftware.core.banking.ledger.core.services.wire.impl;

import io.fireflysoftware.core.banking.ledger.core.mappers.wire.TransactionLineWireTransferMapper;
import io.fireflysoftware.core.banking.ledger.core.services.wire.TransactionLineWireTransferService;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.wire.TransactionLineWireTransferDTO;
import io.fireflysoftware.core.banking.ledger.models.entities.wire.TransactionLineWireTransfer;
import io.fireflysoftware.core.banking.ledger.models.repositories.wire.TransactionLineWireRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.util.UUID;



@Service
@Transactional
public class TransactionLineWireTransferServiceImpl implements TransactionLineWireTransferService {

    @Autowired
    private TransactionLineWireRepository repository;

    @Autowired
    private TransactionLineWireTransferMapper mapper;

    @Override
    public Mono<TransactionLineWireTransferDTO> getWireTransferLine(UUID transactionId) {
        return repository.findByTransactionId(transactionId)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<TransactionLineWireTransferDTO> createWireTransferLine(UUID transactionId, TransactionLineWireTransferDTO wireDTO) {
        wireDTO.setTransactionId(transactionId);
        TransactionLineWireTransfer entity = mapper.toEntity(wireDTO);
        return repository.save(entity)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<TransactionLineWireTransferDTO> updateWireTransferLine(UUID transactionId, TransactionLineWireTransferDTO wireDTO) {
        return repository.findByTransactionId(transactionId)
                .flatMap(existingEntity -> {
                    TransactionLineWireTransfer updatedEntity = mapper.toEntity(wireDTO);
                    updatedEntity.setTransactionLineWireTransferId(existingEntity.getTransactionLineWireTransferId());
                    return repository.save(updatedEntity);
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> deleteWireTransferLine(UUID transactionId) {
        return repository.findByTransactionId(transactionId)
                .flatMap(repository::delete);
    }
}
