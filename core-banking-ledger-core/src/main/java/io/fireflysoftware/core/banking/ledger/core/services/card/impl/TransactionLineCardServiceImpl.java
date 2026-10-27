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


package io.fireflysoftware.core.banking.ledger.core.services.card.impl;

import io.fireflysoftware.core.banking.ledger.core.mappers.card.TransactionLineCardMapper;
import io.fireflysoftware.core.banking.ledger.core.services.card.TransactionLineCardService;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.card.TransactionLineCardDTO;
import io.fireflysoftware.core.banking.ledger.models.entities.card.TransactionLineCard;
import io.fireflysoftware.core.banking.ledger.models.repositories.card.TransactionLineCardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.util.UUID;



@Service
@Transactional
public class TransactionLineCardServiceImpl implements TransactionLineCardService {

    @Autowired
    private TransactionLineCardRepository repository;

    @Autowired
    private TransactionLineCardMapper mapper;

    @Override
    public Mono<TransactionLineCardDTO> getCardLine(UUID transactionId) {
        return repository.findByTransactionId(transactionId)
                .map(mapper::toDTO)
                .switchIfEmpty(Mono.error(new RuntimeException("Transaction Line Card not found")));
    }

    @Override
    public Mono<TransactionLineCardDTO> createCardLine(UUID transactionId, TransactionLineCardDTO cardDTO) {
        cardDTO.setTransactionId(transactionId);
        TransactionLineCard entity = mapper.toEntity(cardDTO);
        return repository.save(entity)
                .map(mapper::toDTO)
                .onErrorResume(e -> Mono.error(new RuntimeException("Failed to create Transaction Line Card", e)));
    }

    @Override
    public Mono<TransactionLineCardDTO> updateCardLine(UUID transactionId, TransactionLineCardDTO cardDTO) {
        return repository.findByTransactionId(transactionId)
                .switchIfEmpty(Mono.error(new RuntimeException("Transaction Line Card not found")))
                .flatMap(existingEntity -> {
                    cardDTO.setTransactionLineCardId(existingEntity.getTransactionLineCardId());
                    cardDTO.setTransactionId(transactionId);
                    TransactionLineCard updatedEntity = mapper.toEntity(cardDTO);
                    return repository.save(updatedEntity);
                })
                .map(mapper::toDTO)
                .onErrorResume(e -> {
                    if (e.getMessage() != null && e.getMessage().equals("Transaction Line Card not found")) {
                        return Mono.error(e);
                    }
                    return Mono.error(new RuntimeException("Failed to update Transaction Line Card", e));
                });
    }

    @Override
    public Mono<Void> deleteCardLine(UUID transactionId) {
        return repository.findByTransactionId(transactionId)
                .switchIfEmpty(Mono.error(new RuntimeException("Transaction Line Card not found")))
                .flatMap(repository::delete);
    }
}
