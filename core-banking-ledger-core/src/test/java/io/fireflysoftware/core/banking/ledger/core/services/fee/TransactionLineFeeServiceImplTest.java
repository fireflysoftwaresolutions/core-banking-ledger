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


package io.fireflysoftware.core.banking.ledger.core.services.fee;
import io.fireflysoftware.core.banking.ledger.core.services.fee.impl.TransactionLineFeeServiceImpl;

import io.fireflysoftware.core.banking.ledger.core.mappers.fee.TransactionLineFeeMapper;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.fee.TransactionLineFeeDTO;
import io.fireflysoftware.core.banking.ledger.models.entities.fee.TransactionLineFee;
import io.fireflysoftware.core.banking.ledger.models.repositories.fee.TransactionLineFeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransactionLineFeeServiceImplTest {

    @Mock
    private TransactionLineFeeRepository repository;

    @Mock
    private TransactionLineFeeMapper mapper;

    @InjectMocks
    private TransactionLineFeeServiceImpl service;

    private TransactionLineFeeDTO feeDTO;
    private TransactionLineFee feeEntity;
    private final UUID transactionId = UUID.randomUUID();
    private final UUID feeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Initialize test data
        feeDTO = new TransactionLineFeeDTO();
        feeDTO.setTransactionLineFeeId(feeId);
        feeDTO.setTransactionId(transactionId);
        feeDTO.setFeeFixedAmount(BigDecimal.valueOf(10.00));
        feeDTO.setFeeDescription("Test Fee");
        feeDTO.setFeeType("SERVICE_FEE");
        feeDTO.setFeeTimestamp(LocalDateTime.now());

        feeEntity = new TransactionLineFee();
        feeEntity.setTransactionLineFeeId(feeId);
        feeEntity.setTransactionId(transactionId);
        feeEntity.setFeeFixedAmount(BigDecimal.valueOf(10.00));
        feeEntity.setFeeDescription("Test Fee");
        feeEntity.setFeeType("SERVICE_FEE");
        feeEntity.setFeeTimestamp(LocalDateTime.now());
    }

    @Test
    void getFeeLine_Success() {
        // Arrange
        when(repository.findByTransactionId(transactionId)).thenReturn(Mono.just(feeEntity));
        when(mapper.toDTO(any(TransactionLineFee.class))).thenReturn(feeDTO);

        // Act & Assert
        StepVerifier.create(service.getFeeLine(transactionId))
                .expectNext(feeDTO)
                .verifyComplete();

        verify(repository).findByTransactionId(transactionId);
        verify(mapper).toDTO(feeEntity);
    }

    @Test
    void getFeeLine_NotFound() {
        // Arrange
        when(repository.findByTransactionId(transactionId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(service.getFeeLine(transactionId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Transaction Line Fee not found"))
                .verify();

        verify(repository).findByTransactionId(transactionId);
        verify(mapper, never()).toDTO(any(TransactionLineFee.class));
    }

    @Test
    void createFeeLine_Success() {
        // Arrange
        when(mapper.toEntity(any(TransactionLineFeeDTO.class))).thenReturn(feeEntity);
        when(repository.save(any(TransactionLineFee.class))).thenReturn(Mono.just(feeEntity));
        when(mapper.toDTO(any(TransactionLineFee.class))).thenReturn(feeDTO);

        // Act & Assert
        StepVerifier.create(service.createFeeLine(transactionId, feeDTO))
                .expectNext(feeDTO)
                .verifyComplete();

        verify(mapper).toEntity(feeDTO);
        verify(repository).save(feeEntity);
        verify(mapper).toDTO(feeEntity);
    }

    @Test
    void createFeeLine_Error() {
        // Arrange
        when(mapper.toEntity(any(TransactionLineFeeDTO.class))).thenReturn(feeEntity);
        when(repository.save(any(TransactionLineFee.class))).thenReturn(Mono.error(new RuntimeException("Database error")));

        // Act & Assert
        StepVerifier.create(service.createFeeLine(transactionId, feeDTO))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Failed to create Transaction Line Fee"))
                .verify();

        verify(mapper).toEntity(feeDTO);
        verify(repository).save(feeEntity);
        verify(mapper, never()).toDTO(any(TransactionLineFee.class));
    }

    @Test
    void updateFeeLine_Success() {
        // Arrange
        when(repository.findByTransactionId(transactionId)).thenReturn(Mono.just(feeEntity));
        when(mapper.toEntity(any(TransactionLineFeeDTO.class))).thenReturn(feeEntity);
        when(repository.save(any(TransactionLineFee.class))).thenReturn(Mono.just(feeEntity));
        when(mapper.toDTO(any(TransactionLineFee.class))).thenReturn(feeDTO);

        // Act & Assert
        StepVerifier.create(service.updateFeeLine(transactionId, feeDTO))
                .expectNext(feeDTO)
                .verifyComplete();

        verify(repository).findByTransactionId(transactionId);
        verify(mapper).toEntity(feeDTO);
        verify(repository).save(feeEntity);
        verify(mapper).toDTO(feeEntity);
    }

    @Test
    void updateFeeLine_NotFound() {
        // Arrange
        when(repository.findByTransactionId(transactionId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(service.updateFeeLine(transactionId, feeDTO))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Failed to update Transaction Line Fee"))
                .verify();

        verify(repository).findByTransactionId(transactionId);
        verify(mapper, never()).toEntity(any(TransactionLineFeeDTO.class));
        verify(repository, never()).save(any(TransactionLineFee.class));
        verify(mapper, never()).toDTO(any(TransactionLineFee.class));
    }

    @Test
    void deleteFeeLine_Success() {
        // Arrange
        when(repository.findByTransactionId(transactionId)).thenReturn(Mono.just(feeEntity));
        when(repository.delete(feeEntity)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(service.deleteFeeLine(transactionId))
                .verifyComplete();

        verify(repository).findByTransactionId(transactionId);
        verify(repository).delete(feeEntity);
    }

    @Test
    void deleteFeeLine_NotFound() {
        // Arrange
        when(repository.findByTransactionId(transactionId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(service.deleteFeeLine(transactionId))
                .verifyComplete();

        verify(repository).findByTransactionId(transactionId);
        verify(repository, never()).delete(any(TransactionLineFee.class));
    }
}
