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


package io.fireflysoftware.core.banking.ledger.core.services.interest;

import java.util.UUID;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.interest.TransactionLineInterestDTO;
import reactor.core.publisher.Mono;

/**
 * Service interface for managing interest transaction lines.
 */
public interface TransactionLineInterestService {

    /**
     * Retrieve the interest line for the specified transaction.
     *
     * @param transactionId The transaction ID
     * @return A Mono containing the interest transaction line if found
     */
    Mono<TransactionLineInterestDTO> getInterestLine(UUID transactionId);

    /**
     * Create a new interest line record for the specified transaction.
     *
     * @param transactionId The transaction ID
     * @param interestDTO The interest transaction line data
     * @return A Mono containing the created interest transaction line
     */
    Mono<TransactionLineInterestDTO> createInterestLine(UUID transactionId, TransactionLineInterestDTO interestDTO);

    /**
     * Update an existing interest line for the specified transaction.
     *
     * @param transactionId The transaction ID
     * @param interestDTO The updated interest transaction line data
     * @return A Mono containing the updated interest transaction line
     */
    Mono<TransactionLineInterestDTO> updateInterestLine(UUID transactionId, TransactionLineInterestDTO interestDTO);

    /**
     * Delete the interest line record for the specified transaction.
     *
     * @param transactionId The transaction ID
     * @return A Mono that completes when the deletion is done
     */
    Mono<Void> deleteInterestLine(UUID transactionId);
}
