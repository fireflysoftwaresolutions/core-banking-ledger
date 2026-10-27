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


package io.fireflysoftware.core.banking.ledger.models.repositories.ach;

import io.fireflysoftware.core.banking.ledger.models.entities.ach.TransactionLineAch;
import io.fireflysoftware.core.banking.ledger.models.repositories.BaseRepository;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Repository interface for TransactionLineAch entity.
 */
public interface TransactionLineAchRepository extends BaseRepository<TransactionLineAch, UUID>, TransactionLineAchFilters {
    /**
     * Find ACH transaction line by transaction ID.
     *
     * @param transactionId The transaction ID
     * @return A Mono containing the ACH transaction line if found
     */
    Mono<TransactionLineAch> findByTransactionId(UUID transactionId);

    /**
     * Find ACH transaction lines by source account ID.
     *
     * @param sourceAccountId The source account ID
     * @param pageable Pagination information
     * @return A Flux of ACH transaction lines
     */
    

    /**
     * Count ACH transaction lines by source account ID.
     *
     * @param sourceAccountId The source account ID
     * @return A Mono containing the count
     */
    

    /**
     * Find ACH transaction lines by destination account ID.
     *
     * @param destinationAccountId The destination account ID
     * @param pageable Pagination information
     * @return A Flux of ACH transaction lines
     */
    

    /**
     * Count ACH transaction lines by destination account ID.
     *
     * @param destinationAccountId The destination account ID
     * @return A Mono containing the count
     */
    

    /**
     * Find ACH transaction lines by custom criteria.
     *
     * @param startDate The start date for the search
     * @param endDate The end date for the search
     * @param sourceAccountId The source account ID
     * @param destinationAccountId The destination account ID
     * @param minAmount The minimum transfer amount
     * @param maxAmount The maximum transfer amount
     * @param pageable Pagination information
     * @return A Flux of ACH transaction lines
     */
    

    /**
     * Count ACH transaction lines by custom criteria.
     *
     * @param startDate The start date for the search
     * @param endDate The end date for the search
     * @param sourceAccountId The source account ID
     * @param destinationAccountId The destination account ID
     * @param minAmount The minimum transfer amount
     * @param maxAmount The maximum transfer amount
     * @return A Mono containing the count
     */
    

    /**
     * Find ACH transaction lines by routing number.
     *
     * @param routingNumber The ACH routing number
     * @param pageable Pagination information
     * @return A Flux of ACH transaction lines
     */
    

    /**
     * Find ACH transaction lines by batch number.
     *
     * @param batchNumber The ACH batch number
     * @param pageable Pagination information
     * @return A Flux of ACH transaction lines
     */
    
}