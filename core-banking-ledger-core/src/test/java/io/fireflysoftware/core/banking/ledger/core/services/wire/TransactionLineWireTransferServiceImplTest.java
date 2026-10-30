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


package io.fireflysoftware.core.banking.ledger.core.services.wire;
import io.fireflysoftware.core.banking.ledger.core.services.wire.impl.TransactionLineWireTransferServiceImpl;

import io.fireflysoftware.core.banking.ledger.core.mappers.wire.TransactionLineWireTransferMapper;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.wire.TransactionLineWireTransferDTO;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.wire.WireTransferPriorityEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.wire.TransactionLineWireTransfer;
import io.fireflysoftware.core.banking.ledger.models.repositories.wire.TransactionLineWireRepository;
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
public class TransactionLineWireTransferServiceImplTest {

    @Mock
    private TransactionLineWireRepository repository;

    @Mock
    private TransactionLineWireTransferMapper mapper;

    @InjectMocks
    private TransactionLineWireTransferServiceImpl service;

    private TransactionLineWireTransferDTO wireDTO;
    private TransactionLineWireTransfer wireEntity;
    private final UUID transactionId = UUID.randomUUID();
    private final UUID wireId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Initialize test data
        wireDTO = new TransactionLineWireTransferDTO();
        wireDTO.setTransactionLineWireTransferId(wireId);
        wireDTO.setTransactionId(transactionId);
        wireDTO.setWireTransferReference("WIRE123456");
        wireDTO.setWireOriginSwiftBic("BOFAUS3N");
        wireDTO.setWireDestinationSwiftBic("CHASUS33");
        wireDTO.setWireOriginAccountNumber("123456789");
        wireDTO.setWireDestinationAccountNumber("987654321");
        wireDTO.setWireTransferPurpose("Business payment");
        wireDTO.setWireTransferPriority(WireTransferPriorityEnum.HIGH);
        wireDTO.setWireExchangeRate(new BigDecimal("1.25"));
        wireDTO.setWireFeeAmount(new BigDecimal("15.00"));
        wireDTO.setWireFeeCurrency("USD");
        wireDTO.setWireInstructingParty("ABC Corporation");
        wireDTO.setWireBeneficiaryName("XYZ Inc.");
        wireDTO.setWireBeneficiaryAddress("123 Wall St, New York, NY");
        wireDTO.setWireProcessingDate(LocalDateTime.now());
        wireDTO.setWireTransactionNotes("Urgent payment");
        wireDTO.setWireReceptionStatus("RECEIVED");
        wireDTO.setWireDeclineReason(null);
        wireDTO.setWireCancelledFlag(false);
        wireDTO.setBankOfSpainRegCode("REG123");

        wireEntity = new TransactionLineWireTransfer();
        wireEntity.setTransactionLineWireTransferId(wireId);
        wireEntity.setTransactionId(transactionId);
        wireEntity.setWireTransferReference("WIRE123456");
        wireEntity.setWireOriginSwiftBic("BOFAUS3N");
        wireEntity.setWireDestinationSwiftBic("CHASUS33");
        wireEntity.setWireOriginAccountNumber("123456789");
        wireEntity.setWireDestinationAccountNumber("987654321");
        wireEntity.setWireTransferPurpose("Business payment");
        wireEntity.setWireTransferPriority(WireTransferPriorityEnum.HIGH);
        wireEntity.setWireExchangeRate(new BigDecimal("1.25"));
        wireEntity.setWireFeeAmount(new BigDecimal("15.00"));
        wireEntity.setWireFeeCurrency("USD");
        wireEntity.setWireInstructingParty("ABC Corporation");
        wireEntity.setWireBeneficiaryName("XYZ Inc.");
        wireEntity.setWireBeneficiaryAddress("123 Wall St, New York, NY");
        wireEntity.setWireProcessingDate(LocalDateTime.now());
        wireEntity.setWireTransactionNotes("Urgent payment");
        wireEntity.setWireReceptionStatus("RECEIVED");
        wireEntity.setWireDeclineReason(null);
        wireEntity.setWireCancelledFlag(false);
        wireEntity.setBankOfSpainRegCode("REG123");
    }

    @Test
    void getWireTransferLine_Success() {
        // Arrange
        when(repository.findByTransactionId(transactionId)).thenReturn(Mono.just(wireEntity));
        when(mapper.toDTO(any(TransactionLineWireTransfer.class))).thenReturn(wireDTO);

        // Act & Assert
        StepVerifier.create(service.getWireTransferLine(transactionId))
                .expectNext(wireDTO)
                .verifyComplete();

        verify(repository).findByTransactionId(transactionId);
        verify(mapper).toDTO(wireEntity);
    }

    @Test
    void getWireTransferLine_NotFound() {
        // Arrange
        when(repository.findByTransactionId(transactionId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(service.getWireTransferLine(transactionId))
                .verifyComplete();

        verify(repository).findByTransactionId(transactionId);
        verify(mapper, never()).toDTO(any(TransactionLineWireTransfer.class));
    }

    @Test
    void createWireTransferLine_Success() {
        // Arrange
        when(mapper.toEntity(any(TransactionLineWireTransferDTO.class))).thenReturn(wireEntity);
        when(repository.save(any(TransactionLineWireTransfer.class))).thenReturn(Mono.just(wireEntity));
        when(mapper.toDTO(any(TransactionLineWireTransfer.class))).thenReturn(wireDTO);

        // Act & Assert
        StepVerifier.create(service.createWireTransferLine(transactionId, wireDTO))
                .expectNext(wireDTO)
                .verifyComplete();

        verify(mapper).toEntity(wireDTO);
        verify(repository).save(wireEntity);
        verify(mapper).toDTO(wireEntity);
    }

    @Test
    void updateWireTransferLine_Success() {
        // Arrange
        when(repository.findByTransactionId(transactionId)).thenReturn(Mono.just(wireEntity));
        when(mapper.toEntity(any(TransactionLineWireTransferDTO.class))).thenReturn(wireEntity);
        when(repository.save(any(TransactionLineWireTransfer.class))).thenReturn(Mono.just(wireEntity));
        when(mapper.toDTO(any(TransactionLineWireTransfer.class))).thenReturn(wireDTO);

        // Act & Assert
        StepVerifier.create(service.updateWireTransferLine(transactionId, wireDTO))
                .expectNext(wireDTO)
                .verifyComplete();

        verify(repository).findByTransactionId(transactionId);
        verify(mapper).toEntity(wireDTO);
        verify(repository).save(wireEntity);
        verify(mapper).toDTO(wireEntity);
    }

    @Test
    void updateWireTransferLine_NotFound() {
        // Arrange
        when(repository.findByTransactionId(transactionId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(service.updateWireTransferLine(transactionId, wireDTO))
                .verifyComplete();

        verify(repository).findByTransactionId(transactionId);
        verify(mapper, never()).toEntity(any(TransactionLineWireTransferDTO.class));
        verify(repository, never()).save(any(TransactionLineWireTransfer.class));
        verify(mapper, never()).toDTO(any(TransactionLineWireTransfer.class));
    }

    @Test
    void deleteWireTransferLine_Success() {
        // Arrange
        when(repository.findByTransactionId(transactionId)).thenReturn(Mono.just(wireEntity));
        when(repository.delete(wireEntity)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(service.deleteWireTransferLine(transactionId))
                .verifyComplete();

        verify(repository).findByTransactionId(transactionId);
        verify(repository).delete(wireEntity);
    }

    @Test
    void deleteWireTransferLine_NotFound() {
        // Arrange
        when(repository.findByTransactionId(transactionId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(service.deleteWireTransferLine(transactionId))
                .verifyComplete();

        verify(repository).findByTransactionId(transactionId);
        verify(repository, never()).delete(any(TransactionLineWireTransfer.class));
    }
}