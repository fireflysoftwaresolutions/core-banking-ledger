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


package io.fireflysoftware.core.banking.ledger.core.services.core;

import java.util.UUID;

import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.core.TransactionStatusHistoryDTO;
import reactor.core.publisher.Mono;

public interface TransactionStatusHistoryService {

    /**
     * Retrieve a paginated list of transaction status history records for a specific transaction.
     */
    Mono<PaginationResponse<TransactionStatusHistoryDTO>> listStatusHistory(
            UUID transactionId,
            PaginationRequest paginationRequest
    );

    /**
     * Create a new transaction status history record for the specified transaction.
     */
    Mono<TransactionStatusHistoryDTO> createStatusHistory(UUID transactionId, TransactionStatusHistoryDTO historyDTO);

    /**
     * Retrieve a specific transaction status history record by its unique ID.
     */
    Mono<TransactionStatusHistoryDTO> getStatusHistory(UUID transactionId, UUID historyId);

    /**
     * Update an existing transaction status history record.
     */
    Mono<TransactionStatusHistoryDTO> updateStatusHistory(
            UUID transactionId,
            UUID historyId,
            TransactionStatusHistoryDTO historyDTO
    );

    /**
     * Delete a specific transaction status history record by its unique ID.
     */
    Mono<Void> deleteStatusHistory(UUID transactionId, UUID historyId);
}
