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


package io.fireflysoftware.core.banking.ledger.core.services.deposit;
import io.fireflysoftware.core.banking.ledger.core.services.deposit.impl.TransactionLineDepositServiceImpl;

import io.fireflysoftware.core.banking.ledger.core.mappers.deposit.TransactionLineDepositMapper;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.deposit.TransactionLineDepositDTO;
import io.fireflysoftware.core.banking.ledger.models.entities.deposit.TransactionLineDeposit;
import io.fireflysoftware.core.banking.ledger.models.repositories.deposit.TransactionLineDepositRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionLineDepositServiceImplTest {

    @Mock
    private TransactionLineDepositRepository repository;

    @Mock
    private TransactionLineDepositMapper mapper;

    @InjectMocks
    private TransactionLineDepositServiceImpl service;

    private UUID transactionId;
    private TransactionLineDeposit depositEntity;
    private TransactionLineDepositDTO depositDTO;

    @BeforeEach
    void setUp() {
        transactionId = UUID.randomUUID();
        
        depositEntity = new TransactionLineDeposit();
        depositEntity.setTransactionLineDepositId(UUID.randomUUID());
        depositEntity.setTransactionId(transactionId);
        depositEntity.setDepositMethod("ATM");
        depositEntity.setDepositReference("DEP123456");
        depositEntity.setDepositLocation("Main Branch");
        depositEntity.setDepositNotes("Cash deposit");
        depositEntity.setDepositConfirmationCode("CONF123");
        depositEntity.setDepositReceiptNumber("REC456");
        depositEntity.setDepositAtmId("ATM001");
        depositEntity.setDepositBranchId("BR001");
        depositEntity.setDepositCashAmount(new BigDecimal("1000.00"));
        depositEntity.setDepositCheckAmount(BigDecimal.ZERO);
        depositEntity.setDepositTimestamp(LocalDateTime.now());
        depositEntity.setDepositProcessedBy("John Doe");
        
        depositDTO = TransactionLineDepositDTO.builder()
                .transactionLineDepositId(UUID.randomUUID())
                .transactionId(transactionId)
                .depositMethod("ATM")
                .depositReference("DEP123456")
                .depositLocation("Main Branch")
                .depositNotes("Cash deposit")
                .depositConfirmationCode("CONF123")
                .depositReceiptNumber("REC456")
                .depositAtmId("ATM001")
                .depositBranchId("BR001")
                .depositCashAmount(new BigDecimal("1000.00"))
                .depositCheckAmount(BigDecimal.ZERO)
                .depositTimestamp(LocalDateTime.now())
                .depositProcessedBy("John Doe")
                .build();
    }

    @Test
    void getDepositLine_Success() {
        // Arrange
        when(repository.findByTransactionId(transactionId)).thenReturn(Mono.just(depositEntity));
        when(mapper.toDTO(any(TransactionLineDeposit.class))).thenReturn(depositDTO);

        // Act & Assert
        StepVerifier.create(service.getDepositLine(transactionId))
                .expectNext(depositDTO)
                .verifyComplete();

        verify(repository).findByTransactionId(transactionId);
        verify(mapper).toDTO(depositEntity);
    }

    @Test
    void getDepositLine_NotFound() {
        // Arrange
        when(repository.findByTransactionId(transactionId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(service.getDepositLine(transactionId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Transaction Line Deposit not found"))
                .verify();

        verify(repository).findByTransactionId(transactionId);
        verify(mapper, never()).toDTO(any(TransactionLineDeposit.class));
    }

    @Test
    void createDepositLine_Success() {
        // Arrange
        when(mapper.toEntity(any(TransactionLineDepositDTO.class))).thenReturn(depositEntity);
        when(repository.save(any(TransactionLineDeposit.class))).thenReturn(Mono.just(depositEntity));
        when(mapper.toDTO(any(TransactionLineDeposit.class))).thenReturn(depositDTO);

        // Act & Assert
        StepVerifier.create(service.createDepositLine(transactionId, depositDTO))
                .expectNext(depositDTO)
                .verifyComplete();

        verify(mapper).toEntity(depositDTO);
        verify(repository).save(depositEntity);
        verify(mapper).toDTO(depositEntity);
    }

    @Test
    void createDepositLine_Error() {
        // Arrange
        when(mapper.toEntity(any(TransactionLineDepositDTO.class))).thenReturn(depositEntity);
        when(repository.save(any(TransactionLineDeposit.class))).thenReturn(Mono.error(new RuntimeException("Database error")));

        // Act & Assert
        StepVerifier.create(service.createDepositLine(transactionId, depositDTO))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Failed to create Transaction Line Deposit"))
                .verify();

        verify(mapper).toEntity(depositDTO);
        verify(repository).save(depositEntity);
        verify(mapper, never()).toDTO(any(TransactionLineDeposit.class));
    }

    @Test
    void updateDepositLine_Success() {
        // Arrange
        when(repository.findByTransactionId(transactionId)).thenReturn(Mono.just(depositEntity));
        when(mapper.toEntity(any(TransactionLineDepositDTO.class))).thenReturn(depositEntity);
        when(repository.save(any(TransactionLineDeposit.class))).thenReturn(Mono.just(depositEntity));
        when(mapper.toDTO(any(TransactionLineDeposit.class))).thenReturn(depositDTO);

        // Act & Assert
        StepVerifier.create(service.updateDepositLine(transactionId, depositDTO))
                .expectNext(depositDTO)
                .verifyComplete();

        verify(repository).findByTransactionId(transactionId);
        verify(mapper).toEntity(depositDTO);
        verify(repository).save(depositEntity);
        verify(mapper).toDTO(depositEntity);
    }

    @Test
    void updateDepositLine_NotFound() {
        // Arrange
        when(repository.findByTransactionId(transactionId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(service.updateDepositLine(transactionId, depositDTO))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().contains("Failed to update Transaction Line Deposit"))
                .verify();

        verify(repository).findByTransactionId(transactionId);
        verify(mapper, never()).toEntity(any(TransactionLineDepositDTO.class));
        verify(repository, never()).save(any(TransactionLineDeposit.class));
        verify(mapper, never()).toDTO(any(TransactionLineDeposit.class));
    }

    @Test
    void deleteDepositLine_Success() {
        // Arrange
        when(repository.findByTransactionId(transactionId)).thenReturn(Mono.just(depositEntity));
        when(repository.delete(depositEntity)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(service.deleteDepositLine(transactionId))
                .verifyComplete();

        verify(repository).findByTransactionId(transactionId);
        verify(repository).delete(depositEntity);
    }

    @Test
    void deleteDepositLine_NotFound() {
        // Arrange
        when(repository.findByTransactionId(transactionId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(service.deleteDepositLine(transactionId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Transaction Line Deposit not found"))
                .verify();

        verify(repository).findByTransactionId(transactionId);
        verify(repository, never()).delete(any(TransactionLineDeposit.class));
    }
}
