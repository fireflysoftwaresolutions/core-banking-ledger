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


package io.fireflysoftware.core.banking.ledger.models.repositories.deposit;

import io.fireflysoftware.core.banking.ledger.models.entities.deposit.TransactionLineDeposit;
import io.fireflysoftware.core.banking.ledger.models.repositories.BaseRepository;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Repository interface for TransactionLineDeposit entity.
 */
public interface TransactionLineDepositRepository extends BaseRepository<TransactionLineDeposit, UUID>, TransactionLineDepositFilters {
    /**
     * Find deposit transaction line by transaction ID.
     *
     * @param transactionId The transaction ID
     * @return A Mono containing the deposit transaction line if found
     */
    Mono<TransactionLineDeposit> findByTransactionId(UUID transactionId);

    /**
     * Find deposit transaction lines by deposit method.
     *
     * @param depositMethod The deposit method
     * @param pageable Pagination information
     * @return A Flux of deposit transaction lines
     */
    

    /**
     * Count deposit transaction lines by deposit method.
     *
     * @param depositMethod The deposit method
     * @return A Mono containing the count
     */
    

    /**
     * Find deposit transaction lines by branch ID.
     *
     * @param branchId The branch ID
     * @param pageable Pagination information
     * @return A Flux of deposit transaction lines
     */
    

    /**
     * Count deposit transaction lines by branch ID.
     *
     * @param branchId The branch ID
     * @return A Mono containing the count
     */
    

    /**
     * Find deposit transaction lines by custom criteria.
     *
     * @param startDate The start date for the search
     * @param endDate The end date for the search
     * @param depositMethod The deposit method
     * @param minAmount The minimum deposit amount
     * @param maxAmount The maximum deposit amount
     * @param pageable Pagination information
     * @return A Flux of deposit transaction lines
     */
    

    /**
     * Count deposit transaction lines by custom criteria.
     *
     * @param startDate The start date for the search
     * @param endDate The end date for the search
     * @param depositMethod The deposit method
     * @param minAmount The minimum deposit amount
     * @param maxAmount The maximum deposit amount
     * @return A Mono containing the count
     */
    
}
