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


package io.fireflysoftware.core.banking.ledger.models.repositories.core;

import io.fireflysoftware.core.banking.ledger.interfaces.enums.core.TransactionStatusEnum;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.core.TransactionTypeEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.core.Transaction;
import io.fireflysoftware.core.banking.ledger.models.repositories.BaseRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends BaseRepository<Transaction, UUID>, TransactionFilters {
    @org.springframework.data.relational.repository.Lock(org.springframework.data.relational.core.sql.LockMode.PESSIMISTIC_WRITE)
    Mono<Transaction> findLockedByTransactionId(UUID transactionId);

    default Mono<Transaction> findByIdForUpdate(UUID transactionId) { return findLockedByTransactionId(transactionId); }

    Mono<Transaction> findByRelatedTransactionIdAndRelationTypeAndManagedPostingTrueAndTransactionStatus(
            UUID originalId, String relationType, TransactionStatusEnum status);

    default Mono<Transaction> findManagedPostedReversal(UUID originalId) {
        return findByRelatedTransactionIdAndRelationTypeAndManagedPostingTrueAndTransactionStatus(originalId, "REVERSAL", TransactionStatusEnum.POSTED);
    }

    Flux<Transaction> findByAccountId(UUID accountId, Pageable pageable);
    Mono<Long> countByAccountId(UUID accountId);

    Flux<Transaction> findByAccountSpaceId(UUID accountSpaceId, Pageable pageable);
    Mono<Long> countByAccountSpaceId(UUID accountSpaceId);

    Flux<Transaction> findByTransactionCategoryId(UUID categoryId, Pageable pageable);
    Mono<Long> countByTransactionCategoryId(UUID categoryId);

    // Geotag query methods
    @Query("SELECT t.* FROM transaction t " +
           "WHERE (6371 * acos(cos(radians(:latitude)) * cos(radians(t.latitude)) * " +
           "cos(radians(t.longitude) - radians(:longitude)) + sin(radians(:latitude)) * " +
           "sin(radians(t.latitude)))) <= :radiusInKm " +
           "ORDER BY (6371 * acos(cos(radians(:latitude)) * cos(radians(t.latitude)) * " +
           "cos(radians(t.longitude) - radians(:longitude)) + sin(radians(:latitude)) * " +
           "sin(radians(t.latitude)))) ASC")
    Flux<Transaction> findTransactionsWithinRadius(Double latitude, Double longitude, Double radiusInKm, Pageable pageable);

    @Query("SELECT COUNT(*) FROM transaction t " +
           "WHERE (6371 * acos(cos(radians(:latitude)) * cos(radians(t.latitude)) * " +
           "cos(radians(t.longitude) - radians(:longitude)) + sin(radians(:latitude)) * " +
           "sin(radians(t.latitude)))) <= :radiusInKm")
    Mono<Long> countTransactionsWithinRadius(Double latitude, Double longitude, Double radiusInKm);
    Flux<Transaction> findByCountry(String country, Pageable pageable);
    Mono<Long> countByCountry(String country);

    Flux<Transaction> findByCity(String city, Pageable pageable);
    Mono<Long> countByCity(String city);

    Flux<Transaction> findByPostalCode(String postalCode, Pageable pageable);
    Mono<Long> countByPostalCode(String postalCode);

    Mono<Transaction> findByExternalReference(String externalReference);

    /**
     * Find a transaction by blockchain transaction hash.
     *
     * @param blockchainTransactionHash The blockchain transaction hash
     * @return A Mono emitting the transaction if found, or an empty Mono if not found
     */
    Mono<Transaction> findByBlockchainTransactionHash(String blockchainTransactionHash);

    /**
     * Find all transactions for an account.
     *
     * @param accountId The account ID
     * @return A Flux emitting all transactions for the account
     */
    Flux<Transaction> findByAccountId(UUID accountId);

    /**
     * Find all transactions for an account space.
     *
     * @param accountSpaceId The account space ID
     * @return A Flux emitting all transactions for the account space
     */
    Flux<Transaction> findByAccountSpaceId(UUID accountSpaceId);

    Flux<Transaction> findByLocationNameContainingIgnoreCase(String locationName, Pageable pageable);
    Mono<Long> countByLocationNameContainingIgnoreCase(String locationName);

    

    

    

    

}
