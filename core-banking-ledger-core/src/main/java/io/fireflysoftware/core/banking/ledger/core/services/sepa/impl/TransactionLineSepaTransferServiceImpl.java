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


package io.fireflysoftware.core.banking.ledger.core.services.sepa.impl;

import io.fireflysoftware.core.banking.ledger.core.mappers.sepa.TransactionLineSepaTransferMapper;
import io.fireflysoftware.core.banking.ledger.core.services.sepa.TransactionLineSepaTransferService;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.sepa.TransactionLineSepaTransferDTO;
import io.fireflysoftware.core.banking.ledger.models.entities.sepa.TransactionLineSepaTransfer;
import io.fireflysoftware.core.banking.ledger.models.repositories.sepa.TransactionLineSepaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.util.UUID;



@Service
@Transactional
public class TransactionLineSepaTransferServiceImpl implements TransactionLineSepaTransferService {

    @Autowired
    private TransactionLineSepaRepository repository;

    @Autowired
    private TransactionLineSepaTransferMapper mapper;

    @Override
    public Mono<TransactionLineSepaTransferDTO> getSepaTransferLine(UUID transactionId) {
        return repository.findByTransactionId(transactionId)
                .map(mapper::toDTO)
                .switchIfEmpty(Mono.error(new RuntimeException("SEPA transfer line not found for transactionId: " + transactionId)));
    }

    @Override
    public Mono<TransactionLineSepaTransferDTO> createSepaTransferLine(UUID transactionId, TransactionLineSepaTransferDTO sepaDTO) {
        sepaDTO.setTransactionId(transactionId);
        TransactionLineSepaTransfer entity = mapper.toEntity(sepaDTO);
        return repository.save(entity)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<TransactionLineSepaTransferDTO> updateSepaTransferLine(UUID transactionId, TransactionLineSepaTransferDTO sepaDTO) {
        return repository.findByTransactionId(transactionId)
                .switchIfEmpty(Mono.error(new RuntimeException("SEPA transfer line not found for transactionId: " + transactionId)))
                .flatMap(existingEntity -> {
                    TransactionLineSepaTransfer updatedEntity = mapper.toEntity(sepaDTO);
                    updatedEntity.setTransactionLineSepaId(existingEntity.getTransactionLineSepaId());
                    return repository.save(updatedEntity)
                            .map(mapper::toDTO);
                });
    }

    @Override
    public Mono<Void> deleteSepaTransferLine(UUID transactionId) {
        return repository.findByTransactionId(transactionId)
                .switchIfEmpty(Mono.error(new RuntimeException("SEPA transfer line not found for transactionId: " + transactionId)))
                .flatMap(repository::delete);
    }
}
