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


package io.fireflysoftware.core.banking.ledger.core.services.deposit;

import java.util.UUID;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.deposit.TransactionLineDepositDTO;
import reactor.core.publisher.Mono;

/**
 * Service interface for managing deposit transaction lines.
 */
public interface TransactionLineDepositService {

    /**
     * Retrieve the deposit line for the specified transaction.
     *
     * @param transactionId The transaction ID
     * @return A Mono containing the deposit transaction line if found
     */
    Mono<TransactionLineDepositDTO> getDepositLine(UUID transactionId);

    /**
     * Create a new deposit line record for the specified transaction.
     *
     * @param transactionId The transaction ID
     * @param depositDTO The deposit transaction line data
     * @return A Mono containing the created deposit transaction line
     */
    Mono<TransactionLineDepositDTO> createDepositLine(UUID transactionId, TransactionLineDepositDTO depositDTO);

    /**
     * Update an existing deposit line for the specified transaction.
     *
     * @param transactionId The transaction ID
     * @param depositDTO The updated deposit transaction line data
     * @return A Mono containing the updated deposit transaction line
     */
    Mono<TransactionLineDepositDTO> updateDepositLine(UUID transactionId, TransactionLineDepositDTO depositDTO);

    /**
     * Delete the deposit line record for the specified transaction.
     *
     * @param transactionId The transaction ID
     * @return A Mono that completes when the deletion is done
     */
    Mono<Void> deleteDepositLine(UUID transactionId);
}
