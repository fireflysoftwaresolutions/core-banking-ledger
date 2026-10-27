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


package io.fireflysoftware.core.banking.ledger.core.services.card;

import java.util.UUID;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.card.TransactionLineCardDTO;
import reactor.core.publisher.Mono;

public interface TransactionLineCardService {

    /**
     * Retrieve the transaction line card for the specified transaction.
     */
    Mono<TransactionLineCardDTO> getCardLine(UUID transactionId);

    /**
     * Create a new transaction line card record for the specified transaction.
     */
    Mono<TransactionLineCardDTO> createCardLine(UUID transactionId, TransactionLineCardDTO cardDTO);

    /**
     * Update an existing transaction line card for the specified transaction.
     */
    Mono<TransactionLineCardDTO> updateCardLine(UUID transactionId, TransactionLineCardDTO cardDTO);

    /**
     * Delete the transaction line card record for the specified transaction.
     */
    Mono<Void> deleteCardLine(UUID transactionId);
}
