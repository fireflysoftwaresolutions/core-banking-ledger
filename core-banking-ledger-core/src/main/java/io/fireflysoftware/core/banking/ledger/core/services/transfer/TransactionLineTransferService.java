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


package io.fireflysoftware.core.banking.ledger.core.services.transfer;

import java.util.UUID;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.transfer.TransactionLineTransferDTO;
import reactor.core.publisher.Mono;

/**
 * Service interface for managing transfer transaction lines.
 */
public interface TransactionLineTransferService {

    /**
     * Retrieve the transfer line for the specified transaction.
     *
     * @param transactionId The transaction ID
     * @return A Mono containing the transfer transaction line if found
     */
    Mono<TransactionLineTransferDTO> getTransferLine(UUID transactionId);

    /**
     * Create a new transfer line record for the specified transaction.
     *
     * @param transactionId The transaction ID
     * @param transferDTO The transfer transaction line data
     * @return A Mono containing the created transfer transaction line
     */
    Mono<TransactionLineTransferDTO> createTransferLine(UUID transactionId, TransactionLineTransferDTO transferDTO);

    /**
     * Update an existing transfer line for the specified transaction.
     *
     * @param transactionId The transaction ID
     * @param transferDTO The updated transfer transaction line data
     * @return A Mono containing the updated transfer transaction line
     */
    Mono<TransactionLineTransferDTO> updateTransferLine(UUID transactionId, TransactionLineTransferDTO transferDTO);

    /**
     * Delete the transfer line record for the specified transaction.
     *
     * @param transactionId The transaction ID
     * @return A Mono that completes when the deletion is done
     */
    Mono<Void> deleteTransferLine(UUID transactionId);
}
