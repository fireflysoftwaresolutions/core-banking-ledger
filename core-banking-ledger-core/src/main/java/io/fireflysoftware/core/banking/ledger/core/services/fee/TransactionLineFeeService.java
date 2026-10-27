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


package io.fireflysoftware.core.banking.ledger.core.services.fee;

import java.util.UUID;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.fee.TransactionLineFeeDTO;
import reactor.core.publisher.Mono;

/**
 * Service interface for managing fee transaction lines.
 */
public interface TransactionLineFeeService {

    /**
     * Retrieve the fee line for the specified transaction.
     *
     * @param transactionId The transaction ID
     * @return A Mono containing the fee transaction line if found
     */
    Mono<TransactionLineFeeDTO> getFeeLine(UUID transactionId);

    /**
     * Create a new fee line record for the specified transaction.
     *
     * @param transactionId The transaction ID
     * @param feeDTO The fee transaction line data
     * @return A Mono containing the created fee transaction line
     */
    Mono<TransactionLineFeeDTO> createFeeLine(UUID transactionId, TransactionLineFeeDTO feeDTO);

    /**
     * Update an existing fee line for the specified transaction.
     *
     * @param transactionId The transaction ID
     * @param feeDTO The updated fee transaction line data
     * @return A Mono containing the updated fee transaction line
     */
    Mono<TransactionLineFeeDTO> updateFeeLine(UUID transactionId, TransactionLineFeeDTO feeDTO);

    /**
     * Delete the fee line record for the specified transaction.
     *
     * @param transactionId The transaction ID
     * @return A Mono that completes when the deletion is done
     */
    Mono<Void> deleteFeeLine(UUID transactionId);
}
