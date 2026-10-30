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


package io.fireflysoftware.core.banking.ledger.core.services.standingorder;
import io.fireflysoftware.core.banking.ledger.core.services.standingorder.impl.TransactionLineStandingOrderServiceImpl;

import io.fireflysoftware.core.banking.ledger.core.mappers.standingorder.TransactionLineStandingOrderMapper;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.standingorder.TransactionLineStandingOrderDTO;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.standingorder.StandingOrderFrequencyEnum;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.standingorder.StandingOrderStatusEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.standingorder.TransactionLineStandingOrder;
import io.fireflysoftware.core.banking.ledger.models.repositories.standingorder.TransactionLineStandingOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransactionLineStandingOrderServiceImplTest {

    @Mock
    private TransactionLineStandingOrderRepository repository;

    @Mock
    private TransactionLineStandingOrderMapper mapper;

    @InjectMocks
    private TransactionLineStandingOrderServiceImpl service;

    private TransactionLineStandingOrderDTO standingOrderDTO;
    private TransactionLineStandingOrder standingOrderEntity;
    private final UUID transactionId = UUID.randomUUID();
    private final UUID standingOrderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Initialize test data
        standingOrderDTO = new TransactionLineStandingOrderDTO();
        standingOrderDTO.setTransactionLineStandingOrderId(standingOrderId);
        standingOrderDTO.setTransactionId(transactionId);
        standingOrderDTO.setStandingOrderId("SO123456");
        standingOrderDTO.setStandingOrderFrequency(StandingOrderFrequencyEnum.MONTHLY);
        standingOrderDTO.setStandingOrderStartDate(LocalDate.now());
        standingOrderDTO.setStandingOrderEndDate(LocalDate.now().plusYears(1));
        standingOrderDTO.setStandingOrderNextExecutionDate(LocalDate.now().plusMonths(1));
        standingOrderDTO.setStandingOrderReference("Monthly Rent");
        standingOrderDTO.setStandingOrderRecipientName("Landlord Inc.");
        standingOrderDTO.setStandingOrderRecipientIban("ES9121000418450200051332");
        standingOrderDTO.setStandingOrderRecipientBic("CAIXESBBXXX");
        standingOrderDTO.setStandingOrderPurpose("Rent payment");
        standingOrderDTO.setStandingOrderStatus(StandingOrderStatusEnum.ACTIVE);
        standingOrderDTO.setStandingOrderNotes("Apartment 3B");
        standingOrderDTO.setStandingOrderLastExecutionDate(LocalDate.now().minusMonths(1));
        standingOrderDTO.setStandingOrderTotalExecutions(12);
        standingOrderDTO.setStandingOrderCreatedBy("user123");
        standingOrderDTO.setStandingOrderUpdatedBy("user123");
        standingOrderDTO.setStandingOrderCreationTimestamp(LocalDateTime.now().minusMonths(12));
        standingOrderDTO.setStandingOrderUpdateTimestamp(LocalDateTime.now());
        standingOrderDTO.setStandingOrderSpanishTaxFlag(false);

        standingOrderEntity = new TransactionLineStandingOrder();
        standingOrderEntity.setTransactionLineStandingOrderId(standingOrderId);
        standingOrderEntity.setTransactionId(transactionId);
        standingOrderEntity.setStandingOrderId("SO123456");
        standingOrderEntity.setStandingOrderFrequency(StandingOrderFrequencyEnum.MONTHLY);
        standingOrderEntity.setStandingOrderStartDate(LocalDate.now());
        standingOrderEntity.setStandingOrderEndDate(LocalDate.now().plusYears(1));
        standingOrderEntity.setStandingOrderNextExecutionDate(LocalDate.now().plusMonths(1));
        standingOrderEntity.setStandingOrderReference("Monthly Rent");
        standingOrderEntity.setStandingOrderRecipientName("Landlord Inc.");
        standingOrderEntity.setStandingOrderRecipientIban("ES9121000418450200051332");
        standingOrderEntity.setStandingOrderRecipientBic("CAIXESBBXXX");
        standingOrderEntity.setStandingOrderPurpose("Rent payment");
        standingOrderEntity.setStandingOrderStatus(StandingOrderStatusEnum.ACTIVE);
        standingOrderEntity.setStandingOrderNotes("Apartment 3B");
        standingOrderEntity.setStandingOrderLastExecutionDate(LocalDate.now().minusMonths(1));
        standingOrderEntity.setStandingOrderTotalExecutions(12);
        standingOrderEntity.setStandingOrderCreatedBy("user123");
        standingOrderEntity.setStandingOrderUpdatedBy("user123");
        standingOrderEntity.setStandingOrderCreationTimestamp(LocalDateTime.now().minusMonths(12));
        standingOrderEntity.setStandingOrderUpdateTimestamp(LocalDateTime.now());
        standingOrderEntity.setStandingOrderSpanishTaxFlag(false);
    }

    @Test
    void getStandingOrderLine_Success() {
        // Arrange
        when(repository.findByTransactionId(transactionId)).thenReturn(Mono.just(standingOrderEntity));
        when(mapper.toDTO(any(TransactionLineStandingOrder.class))).thenReturn(standingOrderDTO);

        // Act & Assert
        StepVerifier.create(service.getStandingOrderLine(transactionId))
                .expectNext(standingOrderDTO)
                .verifyComplete();

        verify(repository).findByTransactionId(transactionId);
        verify(mapper).toDTO(standingOrderEntity);
    }

    @Test
    void getStandingOrderLine_NotFound() {
        // Arrange
        when(repository.findByTransactionId(transactionId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(service.getStandingOrderLine(transactionId))
                .verifyComplete();

        verify(repository).findByTransactionId(transactionId);
        verify(mapper, never()).toDTO(any(TransactionLineStandingOrder.class));
    }

    @Test
    void createStandingOrderLine_Success() {
        // Arrange
        when(mapper.toEntity(any(TransactionLineStandingOrderDTO.class))).thenReturn(standingOrderEntity);
        when(repository.save(any(TransactionLineStandingOrder.class))).thenReturn(Mono.just(standingOrderEntity));
        when(mapper.toDTO(any(TransactionLineStandingOrder.class))).thenReturn(standingOrderDTO);

        // Act & Assert
        StepVerifier.create(service.createStandingOrderLine(transactionId, standingOrderDTO))
                .expectNext(standingOrderDTO)
                .verifyComplete();

        verify(mapper).toEntity(standingOrderDTO);
        verify(repository).save(standingOrderEntity);
        verify(mapper).toDTO(standingOrderEntity);
    }

    @Test
    void updateStandingOrderLine_Success() {
        // Arrange
        when(repository.findByTransactionId(transactionId)).thenReturn(Mono.just(standingOrderEntity));
        when(mapper.toEntity(any(TransactionLineStandingOrderDTO.class))).thenReturn(standingOrderEntity);
        when(repository.save(any(TransactionLineStandingOrder.class))).thenReturn(Mono.just(standingOrderEntity));
        when(mapper.toDTO(any(TransactionLineStandingOrder.class))).thenReturn(standingOrderDTO);

        // Act & Assert
        StepVerifier.create(service.updateStandingOrderLine(transactionId, standingOrderDTO))
                .expectNext(standingOrderDTO)
                .verifyComplete();

        verify(repository).findByTransactionId(transactionId);
        verify(mapper).toEntity(standingOrderDTO);
        verify(repository).save(standingOrderEntity);
        verify(mapper).toDTO(standingOrderEntity);
    }

    @Test
    void updateStandingOrderLine_NotFound() {
        // Arrange
        when(repository.findByTransactionId(transactionId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(service.updateStandingOrderLine(transactionId, standingOrderDTO))
                .verifyComplete();

        verify(repository).findByTransactionId(transactionId);
        verify(mapper, never()).toEntity(any(TransactionLineStandingOrderDTO.class));
        verify(repository, never()).save(any(TransactionLineStandingOrder.class));
        verify(mapper, never()).toDTO(any(TransactionLineStandingOrder.class));
    }

    @Test
    void deleteStandingOrderLine_Success() {
        // Arrange
        when(repository.findByTransactionId(transactionId)).thenReturn(Mono.just(standingOrderEntity));
        when(repository.delete(standingOrderEntity)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(service.deleteStandingOrderLine(transactionId))
                .verifyComplete();

        verify(repository).findByTransactionId(transactionId);
        verify(repository).delete(standingOrderEntity);
    }

    @Test
    void deleteStandingOrderLine_NotFound() {
        // Arrange
        when(repository.findByTransactionId(transactionId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(service.deleteStandingOrderLine(transactionId))
                .verifyComplete();

        verify(repository).findByTransactionId(transactionId);
        verify(repository, never()).delete(any(TransactionLineStandingOrder.class));
    }
}