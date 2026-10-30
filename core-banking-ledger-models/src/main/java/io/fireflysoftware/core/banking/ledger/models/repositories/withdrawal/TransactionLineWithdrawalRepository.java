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


package io.fireflysoftware.core.banking.ledger.models.repositories.withdrawal;

import io.fireflysoftware.core.banking.ledger.models.entities.withdrawal.TransactionLineWithdrawal;
import io.fireflysoftware.core.banking.ledger.models.repositories.BaseRepository;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Repository interface for TransactionLineWithdrawal entity.
 */
public interface TransactionLineWithdrawalRepository extends BaseRepository<TransactionLineWithdrawal, UUID>, TransactionLineWithdrawalFilters {
    /**
     * Find withdrawal transaction line by transaction ID.
     *
     * @param transactionId The transaction ID
     * @return A Mono containing the withdrawal transaction line if found
     */
    Mono<TransactionLineWithdrawal> findByTransactionId(UUID transactionId);

    /**
     * Find withdrawal transaction lines by withdrawal method.
     *
     * @param withdrawalMethod The withdrawal method
     * @param pageable Pagination information
     * @return A Flux of withdrawal transaction lines
     */
    

    /**
     * Count withdrawal transaction lines by withdrawal method.
     *
     * @param withdrawalMethod The withdrawal method
     * @return A Mono containing the count
     */
    

    /**
     * Find withdrawal transaction lines by ATM ID.
     *
     * @param atmId The ATM ID
     * @param pageable Pagination information
     * @return A Flux of withdrawal transaction lines
     */
    

    /**
     * Count withdrawal transaction lines by ATM ID.
     *
     * @param atmId The ATM ID
     * @return A Mono containing the count
     */
    

    /**
     * Find withdrawal transaction lines by custom criteria.
     *
     * @param startDate The start date for the search
     * @param endDate The end date for the search
     * @param withdrawalMethod The withdrawal method
     * @param minAmount The minimum withdrawal amount
     * @param maxAmount The maximum withdrawal amount
     * @param pageable Pagination information
     * @return A Flux of withdrawal transaction lines
     */
    

    /**
     * Count withdrawal transaction lines by custom criteria.
     *
     * @param startDate The start date for the search
     * @param endDate The end date for the search
     * @param withdrawalMethod The withdrawal method
     * @param minAmount The minimum withdrawal amount
     * @param maxAmount The maximum withdrawal amount
     * @return A Mono containing the count
     */
    
}
