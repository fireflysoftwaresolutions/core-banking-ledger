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


package io.fireflysoftware.core.banking.ledger.core.services.withdrawal;

import java.util.UUID;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.withdrawal.TransactionLineWithdrawalDTO;
import reactor.core.publisher.Mono;

/**
 * Service interface for managing withdrawal transaction lines.
 */
public interface TransactionLineWithdrawalService {

    /**
     * Retrieve the withdrawal line for the specified transaction.
     *
     * @param transactionId The transaction ID
     * @return A Mono containing the withdrawal transaction line if found
     */
    Mono<TransactionLineWithdrawalDTO> getWithdrawalLine(UUID transactionId);

    /**
     * Create a new withdrawal line record for the specified transaction.
     *
     * @param transactionId The transaction ID
     * @param withdrawalDTO The withdrawal transaction line data
     * @return A Mono containing the created withdrawal transaction line
     */
    Mono<TransactionLineWithdrawalDTO> createWithdrawalLine(UUID transactionId, TransactionLineWithdrawalDTO withdrawalDTO);

    /**
     * Update an existing withdrawal line for the specified transaction.
     *
     * @param transactionId The transaction ID
     * @param withdrawalDTO The updated withdrawal transaction line data
     * @return A Mono containing the updated withdrawal transaction line
     */
    Mono<TransactionLineWithdrawalDTO> updateWithdrawalLine(UUID transactionId, TransactionLineWithdrawalDTO withdrawalDTO);

    /**
     * Delete the withdrawal line record for the specified transaction.
     *
     * @param transactionId The transaction ID
     * @return A Mono that completes when the deletion is done
     */
    Mono<Void> deleteWithdrawalLine(UUID transactionId);
}
