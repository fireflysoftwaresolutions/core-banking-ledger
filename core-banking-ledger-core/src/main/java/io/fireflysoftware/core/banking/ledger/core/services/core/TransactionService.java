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

import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.core.TransactionDTO;

import io.fireflysoftware.core.banking.ledger.interfaces.enums.core.TransactionStatusEnum;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service interface for managing transaction records.
 * Provides basic CRUD operations for transaction data persistence,
 * as well as various search and filtering capabilities.
 */
public interface TransactionService {

    /**
     * Creates a new transaction record.
     *
     * @param transactionDTO The transaction data to create
     * @return A Mono emitting the created transaction with its generated ID
     */
    Mono<TransactionDTO> createTransaction(TransactionDTO transactionDTO);

    /**
     * Retrieves a specific transaction by its unique ID.
     *
     * @param transactionId The unique identifier of the transaction
     * @return A Mono emitting the transaction if found, or an empty Mono if not found
     */
    Mono<TransactionDTO> getTransaction(UUID transactionId);

    /**
     * Updates an existing transaction by its unique ID.
     *
     * @param transactionId The unique identifier of the transaction to update
     * @param transactionDTO The updated transaction data
     * @return A Mono emitting the updated transaction, or an empty Mono if not found
     */
    Mono<TransactionDTO> updateTransaction(UUID transactionId, TransactionDTO transactionDTO);

    /**
     * Deletes a transaction by its unique ID.
     *
     * @param transactionId The unique identifier of the transaction to delete
     * @return A Mono completing when the deletion is done, or completing empty if not found
     */
    Mono<Void> deleteTransaction(UUID transactionId);

    /**
     * Filters transactions based on various criteria specified in the filter request.
     *
     * @param filterRequest The filter criteria and pagination parameters
     * @return A Mono emitting a paginated response of transactions matching the filter criteria
     */
    Mono<PaginationResponse<TransactionDTO>> filterTransactions(FilterRequest<TransactionDTO> filterRequest);

    /**
     * Gets all transactions for an account.
     *
     * @param accountId The account ID
     * @return A Flux emitting all transactions for the account
     */
    Flux<TransactionDTO> getTransactionsByAccountId(UUID accountId);

    /**
     * Gets all transactions for an account space.
     *
     * @param accountSpaceId The account space ID
     * @return A Flux emitting all transactions for the account space
     */
    Flux<TransactionDTO> getTransactionsByAccountSpaceId(UUID accountSpaceId);

    /**
     * Updates the status of a transaction and records the status change in the history.
     *
     * @param transactionId The unique identifier of the transaction
     * @param newStatus The new status to set
     * @param reason The reason for the status change
     * @return A Mono emitting the updated transaction, or an empty Mono if not found
     */
    Mono<TransactionDTO> updateTransactionStatus(UUID transactionId, TransactionStatusEnum newStatus, String reason);

    /**
     * Finds a transaction by its external reference.
     *
     * @param externalReference The external reference of the transaction
     * @return A Mono emitting the transaction if found, or an empty Mono if not found
     */
    Mono<TransactionDTO> findByExternalReference(String externalReference);
}