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


package io.fireflysoftware.core.banking.ledger.core.services.core.impl;

import io.fireflysoftware.core.banking.ledger.core.mappers.core.TransactionMapper;
import io.fireflysoftware.core.banking.ledger.core.mappers.core.TransactionStatusHistoryMapper;
import io.fireflysoftware.core.banking.ledger.core.services.core.TransactionService;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.core.TransactionDTO;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.core.TransactionStatusEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.core.Transaction;
import io.fireflysoftware.core.banking.ledger.models.entities.core.TransactionStatusHistory;
import io.fireflysoftware.core.banking.ledger.models.repositories.core.TransactionRepository;
import io.fireflysoftware.core.banking.ledger.models.repositories.core.TransactionStatusHistoryRepository;
import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.util.UUID;



/**
 * Implementation of the TransactionService interface.
 * Provides basic CRUD operations for transaction data persistence
 * and various search and filtering capabilities.
 */
@Service
@Transactional
public class TransactionServiceImpl implements TransactionService {

    @Autowired
    private TransactionRepository repository;

    @Autowired
    private TransactionStatusHistoryRepository statusHistoryRepository;

    @Autowired
    private TransactionMapper mapper;

    @Autowired
    private TransactionStatusHistoryMapper statusHistoryMapper;



    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<TransactionDTO> createTransaction(TransactionDTO transactionDTO) {
        if (transactionDTO.getTransactionId() != null || transactionDTO.getRowVersion() != null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction create requires a server-assigned identifier and version"));
        }
        Transaction transaction = mapper.toEntity(transactionDTO);
        return repository.save(transaction)
                .flatMap(savedTransaction -> {
                    // Create initial status history record
                    TransactionStatusHistory statusHistory = new TransactionStatusHistory();
                    statusHistory.setTransactionId(savedTransaction.getTransactionId());
                    statusHistory.setStatusCode(savedTransaction.getTransactionStatus());
                    statusHistory.setStatusStartDatetime(LocalDateTime.now());
                    statusHistory.setReason("Initial transaction creation");
                    statusHistory.setRegulatedReportingFlag(false);

                    return statusHistoryRepository.save(statusHistory)
                            .then(Mono.just(savedTransaction));
                })

                .map(mapper::toDTO);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<TransactionDTO> getTransaction(UUID transactionId) {
        return repository.findById(transactionId)
                .map(mapper::toDTO);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<TransactionDTO> updateTransaction(UUID transactionId, TransactionDTO transactionDTO) {
        return repository.findById(transactionId)
                .flatMap(existingTransaction -> {
                    ManagedPostingGuard.requireLegacy(existingTransaction);
                    TransactionStatusEnum oldStatus = existingTransaction.getTransactionStatus();
                    Transaction updatedTransaction = mapper.toEntity(transactionDTO);
                    updatedTransaction.setTransactionId(existingTransaction.getTransactionId());

                    return repository.save(updatedTransaction)
                            .flatMap(savedTransaction -> {
                                // If status has changed, create a status history record
                                if (!oldStatus.equals(savedTransaction.getTransactionStatus())) {
                                    TransactionStatusHistory statusHistory = new TransactionStatusHistory();
                                    statusHistory.setTransactionId(savedTransaction.getTransactionId());
                                    statusHistory.setStatusCode(savedTransaction.getTransactionStatus());
                                    statusHistory.setStatusStartDatetime(LocalDateTime.now());
                                    statusHistory.setReason("Status updated via API");
                                    statusHistory.setRegulatedReportingFlag(false);

                                    return statusHistoryRepository.save(statusHistory)
                                            .then(Mono.just(savedTransaction));
                                }
                                return Mono.just(savedTransaction);
                            })
;
                })
                .map(mapper::toDTO);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<Void> deleteTransaction(UUID transactionId) {
        return repository.findById(transactionId)
                .doOnNext(ManagedPostingGuard::requireLegacy)
                .flatMap(repository::delete);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<PaginationResponse<TransactionDTO>> filterTransactions(FilterRequest<TransactionDTO> filterRequest) {
        return FilterUtils
                .createFilter(
                        Transaction.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Flux<TransactionDTO> getTransactionsByAccountId(UUID accountId) {
        return repository.findByAccountId(accountId)
                .map(mapper::toDTO);
    }

    @Override
    public Flux<TransactionDTO> getTransactionsByAccountSpaceId(UUID accountSpaceId) {
        return repository.findByAccountSpaceId(accountSpaceId)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<TransactionDTO> updateTransactionStatus(UUID transactionId, TransactionStatusEnum newStatus, String reason) {
        return repository.findById(transactionId)
                .flatMap(transaction -> {
                    ManagedPostingGuard.requireLegacy(transaction);
                    TransactionStatusEnum oldStatus = transaction.getTransactionStatus();

                    // Only update if status is different
                    if (oldStatus.equals(newStatus)) {
                        return Mono.just(transaction);
                    }

                    // Update transaction status
                    transaction.setTransactionStatus(newStatus);

                    return repository.save(transaction)
                            .flatMap(savedTransaction -> {
                                // Create status history record
                                TransactionStatusHistory statusHistory = new TransactionStatusHistory();
                                statusHistory.setTransactionId(savedTransaction.getTransactionId());
                                statusHistory.setStatusCode(newStatus);
                                statusHistory.setStatusStartDatetime(LocalDateTime.now());
                                statusHistory.setReason(reason);
                                statusHistory.setRegulatedReportingFlag(false);

                                return statusHistoryRepository.save(statusHistory)
                                        .then(Mono.just(savedTransaction));
                            });
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<TransactionDTO> findByExternalReference(String externalReference) {
        return repository.findByExternalReference(externalReference)
                .map(mapper::toDTO);
    }
}
