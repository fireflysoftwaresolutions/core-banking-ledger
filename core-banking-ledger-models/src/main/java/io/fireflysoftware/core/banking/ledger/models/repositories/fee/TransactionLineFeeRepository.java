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


package io.fireflysoftware.core.banking.ledger.models.repositories.fee;

import io.fireflysoftware.core.banking.ledger.models.entities.fee.TransactionLineFee;
import io.fireflysoftware.core.banking.ledger.models.repositories.BaseRepository;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Repository interface for TransactionLineFee entity.
 */
public interface TransactionLineFeeRepository extends BaseRepository<TransactionLineFee, UUID>, TransactionLineFeeFilters {
    /**
     * Find fee transaction line by transaction ID.
     *
     * @param transactionId The transaction ID
     * @return A Mono containing the fee transaction line if found
     */
    Mono<TransactionLineFee> findByTransactionId(UUID transactionId);

    /**
     * Find fee transaction lines by fee type.
     *
     * @param feeType The fee type
     * @param pageable Pagination information
     * @return A Flux of fee transaction lines
     */
    

    /**
     * Count fee transaction lines by fee type.
     *
     * @param feeType The fee type
     * @return A Mono containing the count
     */
    

    /**
     * Find fee transaction lines by related transaction ID.
     *
     * @param relatedTransactionId The related transaction ID
     * @param pageable Pagination information
     * @return A Flux of fee transaction lines
     */
    

    /**
     * Count fee transaction lines by related transaction ID.
     *
     * @param relatedTransactionId The related transaction ID
     * @return A Mono containing the count
     */
    

    /**
     * Find fee transaction lines by waived status.
     *
     * @param waived The waived status
     * @param pageable Pagination information
     * @return A Flux of fee transaction lines
     */
    

    /**
     * Count fee transaction lines by waived status.
     *
     * @param waived The waived status
     * @return A Mono containing the count
     */
    

    /**
     * Find fee transaction lines by custom criteria.
     *
     * @param startDate The start date for the search
     * @param endDate The end date for the search
     * @param feeType The fee type
     * @param waived The waived status
     * @param minAmount The minimum fee amount
     * @param maxAmount The maximum fee amount
     * @param pageable Pagination information
     * @return A Flux of fee transaction lines
     */
    

    /**
     * Count fee transaction lines by custom criteria.
     *
     * @param startDate The start date for the search
     * @param endDate The end date for the search
     * @param feeType The fee type
     * @param waived The waived status
     * @param minAmount The minimum fee amount
     * @param maxAmount The maximum fee amount
     * @return A Mono containing the count
     */
    
}
