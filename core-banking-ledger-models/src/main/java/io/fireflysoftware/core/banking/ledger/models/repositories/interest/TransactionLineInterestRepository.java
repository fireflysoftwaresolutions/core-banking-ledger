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


package io.fireflysoftware.core.banking.ledger.models.repositories.interest;

import io.fireflysoftware.core.banking.ledger.models.entities.interest.TransactionLineInterest;
import io.fireflysoftware.core.banking.ledger.models.repositories.BaseRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Repository interface for TransactionLineInterest entity.
 */
public interface TransactionLineInterestRepository extends BaseRepository<TransactionLineInterest, UUID>, TransactionLineInterestFilters {
    /**
     * Find interest transaction line by transaction ID.
     *
     * @param transactionId The transaction ID
     * @return A Mono containing the interest transaction line if found
     */
    Mono<TransactionLineInterest> findByTransactionId(UUID transactionId);

    /**
     * Find interest transaction lines by interest type.
     *
     * @param interestType The interest type
     * @param pageable Pagination information
     * @return A Flux of interest transaction lines
     */
    

    /**
     * Count interest transaction lines by interest type.
     *
     * @param interestType The interest type
     * @return A Mono containing the count
     */
    

    /**
     * Find interest transaction lines by related account ID.
     *
     * @param relatedAccountId The related account ID
     * @param pageable Pagination information
     * @return A Flux of interest transaction lines
     */
    

    /**
     * Count interest transaction lines by related account ID.
     *
     * @param relatedAccountId The related account ID
     * @return A Mono containing the count
     */
    

    /**
     * Find interest transaction lines by accrual period.
     *
     * @param startDate The accrual start date
     * @param endDate The accrual end date
     * @param pageable Pagination information
     * @return A Flux of interest transaction lines
     */
    

    /**
     * Count interest transaction lines by accrual period.
     *
     * @param startDate The accrual start date
     * @param endDate The accrual end date
     * @return A Mono containing the count
     */
    

    /**
     * Find interest transaction lines by custom criteria.
     *
     * @param startDate The start date for the search
     * @param endDate The end date for the search
     * @param interestType The interest type
     * @param relatedAccountId The related account ID
     * @param minRate The minimum interest rate
     * @param maxRate The maximum interest rate
     * @param pageable Pagination information
     * @return A Flux of interest transaction lines
     */
    

    /**
     * Count interest transaction lines by custom criteria.
     *
     * @param startDate The start date for the search
     * @param endDate The end date for the search
     * @param interestType The interest type
     * @param relatedAccountId The related account ID
     * @param minRate The minimum interest rate
     * @param maxRate The maximum interest rate
     * @return A Mono containing the count
     */
    
}
