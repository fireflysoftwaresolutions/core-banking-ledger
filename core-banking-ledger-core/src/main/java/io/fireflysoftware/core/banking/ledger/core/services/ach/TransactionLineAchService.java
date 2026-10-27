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


package io.fireflysoftware.core.banking.ledger.core.services.ach;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.ach.TransactionLineAchDTO;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Service interface for managing ACH transaction lines.
 */
public interface TransactionLineAchService {

    /**
     * Retrieve the ACH line for the specified transaction.
     *
     * @param transactionId The transaction ID
     * @return A Mono containing the ACH transaction line if found
     */
    Mono<TransactionLineAchDTO> getAchLine(UUID transactionId);

    /**
     * Create a new ACH line record for the specified transaction.
     *
     * @param transactionId The transaction ID
     * @param achDTO The ACH transaction line data
     * @return A Mono containing the created ACH transaction line
     */
    Mono<TransactionLineAchDTO> createAchLine(UUID transactionId, TransactionLineAchDTO achDTO);

    /**
     * Update an existing ACH line for the specified transaction.
     *
     * @param transactionId The transaction ID
     * @param achDTO The updated ACH transaction line data
     * @return A Mono containing the updated ACH transaction line
     */
    Mono<TransactionLineAchDTO> updateAchLine(UUID transactionId, TransactionLineAchDTO achDTO);

    /**
     * Delete the ACH line record for the specified transaction.
     *
     * @param transactionId The transaction ID
     * @return A Mono that completes when the ACH line is deleted
     */
    Mono<Void> deleteAchLine(UUID transactionId);
}
