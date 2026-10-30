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


package io.fireflysoftware.core.banking.ledger.core.services.sepa;
import io.fireflysoftware.core.banking.ledger.core.services.sepa.impl.TransactionLineSepaTransferServiceImpl;

import io.fireflysoftware.core.banking.ledger.core.mappers.sepa.TransactionLineSepaTransferMapper;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.sepa.TransactionLineSepaTransferDTO;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.sepa.SepaSpanishSchemeEnum;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.sepa.SepaTransactionStatusEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.sepa.TransactionLineSepaTransfer;
import io.fireflysoftware.core.banking.ledger.models.repositories.sepa.TransactionLineSepaRepository;
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
public class TransactionLineSepaTransferServiceImplTest {

    @Mock
    private TransactionLineSepaRepository repository;

    @Mock
    private TransactionLineSepaTransferMapper mapper;

    @InjectMocks
    private TransactionLineSepaTransferServiceImpl service;

    private TransactionLineSepaTransferDTO sepaDTO;
    private TransactionLineSepaTransfer sepaEntity;
    private final UUID transactionId = UUID.randomUUID();
    private final UUID sepaId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Initialize test data
        sepaDTO = new TransactionLineSepaTransferDTO();
        sepaDTO.setTransactionLineSepaId(sepaId);
        sepaDTO.setTransactionId(transactionId);
        sepaDTO.setSepaEndToEndId("END2END123");
        sepaDTO.setSepaRemittanceInfo("Payment for services");
        sepaDTO.setSepaOriginIban("ES9121000418450200051332");
        sepaDTO.setSepaOriginBic("CAIXESBBXXX");
        sepaDTO.setSepaDestinationIban("DE89370400440532013000");
        sepaDTO.setSepaDestinationBic("DEUTDEFFXXX");
        sepaDTO.setSepaTransactionStatus(SepaTransactionStatusEnum.ACCP);
        sepaDTO.setSepaCreditorId("CREDITOR123");
        sepaDTO.setSepaDebtorId("DEBTOR456");
        sepaDTO.setSepaTransactionPurpose("SERVICES");
        sepaDTO.setSepaRequestedExecutionDate(LocalDate.now().plusDays(1));
        sepaDTO.setSepaExchangeRate(new BigDecimal("1.15"));
        sepaDTO.setSepaFeeAmount(new BigDecimal("10.00"));
        sepaDTO.setSepaFeeCurrency("EUR");
        sepaDTO.setSepaRecipientName("John Doe");
        sepaDTO.setSepaRecipientAddress("123 Main St, Berlin, Germany");
        sepaDTO.setSepaProcessingDate(LocalDateTime.now());
        sepaDTO.setSepaNotes("Priority transfer");
        sepaDTO.setSepaPaymentScheme(SepaSpanishSchemeEnum.CORE);

        sepaEntity = new TransactionLineSepaTransfer();
        sepaEntity.setTransactionLineSepaId(sepaId);
        sepaEntity.setTransactionId(transactionId);
        sepaEntity.setSepaEndToEndId("END2END123");
        sepaEntity.setSepaRemittanceInfo("Payment for services");
        sepaEntity.setSepaOriginIban("ES9121000418450200051332");
        sepaEntity.setSepaOriginBic("CAIXESBBXXX");
        sepaEntity.setSepaDestinationIban("DE89370400440532013000");
        sepaEntity.setSepaDestinationBic("DEUTDEFFXXX");
        sepaEntity.setSepaTransactionStatus(SepaTransactionStatusEnum.ACCP);
        sepaEntity.setSepaCreditorId("CREDITOR123");
        sepaEntity.setSepaDebtorId("DEBTOR456");
        sepaEntity.setSepaTransactionPurpose("SERVICES");
        sepaEntity.setSepaRequestedExecutionDate(LocalDate.now().plusDays(1));
        sepaEntity.setSepaExchangeRate(new BigDecimal("1.15"));
        sepaEntity.setSepaFeeAmount(new BigDecimal("10.00"));
        sepaEntity.setSepaFeeCurrency("EUR");
        sepaEntity.setSepaRecipientName("John Doe");
        sepaEntity.setSepaRecipientAddress("123 Main St, Berlin, Germany");
        sepaEntity.setSepaProcessingDate(LocalDateTime.now());
        sepaEntity.setSepaNotes("Priority transfer");
        sepaEntity.setSepaPaymentScheme(SepaSpanishSchemeEnum.CORE);
    }

    @Test
    void getSepaTransferLine_Success() {
        // Arrange
        when(repository.findByTransactionId(transactionId)).thenReturn(Mono.just(sepaEntity));
        when(mapper.toDTO(any(TransactionLineSepaTransfer.class))).thenReturn(sepaDTO);

        // Act & Assert
        StepVerifier.create(service.getSepaTransferLine(transactionId))
                .expectNext(sepaDTO)
                .verifyComplete();

        verify(repository).findByTransactionId(transactionId);
        verify(mapper).toDTO(sepaEntity);
    }

    @Test
    void getSepaTransferLine_NotFound() {
        // Arrange
        when(repository.findByTransactionId(transactionId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(service.getSepaTransferLine(transactionId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("SEPA transfer line not found for transactionId: " + transactionId))
                .verify();

        verify(repository).findByTransactionId(transactionId);
        verify(mapper, never()).toDTO(any(TransactionLineSepaTransfer.class));
    }

    @Test
    void createSepaTransferLine_Success() {
        // Arrange
        when(mapper.toEntity(any(TransactionLineSepaTransferDTO.class))).thenReturn(sepaEntity);
        when(repository.save(any(TransactionLineSepaTransfer.class))).thenReturn(Mono.just(sepaEntity));
        when(mapper.toDTO(any(TransactionLineSepaTransfer.class))).thenReturn(sepaDTO);

        // Act & Assert
        StepVerifier.create(service.createSepaTransferLine(transactionId, sepaDTO))
                .expectNext(sepaDTO)
                .verifyComplete();

        verify(mapper).toEntity(sepaDTO);
        verify(repository).save(sepaEntity);
        verify(mapper).toDTO(sepaEntity);
    }

    @Test
    void updateSepaTransferLine_Success() {
        // Arrange
        when(repository.findByTransactionId(transactionId)).thenReturn(Mono.just(sepaEntity));
        when(mapper.toEntity(any(TransactionLineSepaTransferDTO.class))).thenReturn(sepaEntity);
        when(repository.save(any(TransactionLineSepaTransfer.class))).thenReturn(Mono.just(sepaEntity));
        when(mapper.toDTO(any(TransactionLineSepaTransfer.class))).thenReturn(sepaDTO);

        // Act & Assert
        StepVerifier.create(service.updateSepaTransferLine(transactionId, sepaDTO))
                .expectNext(sepaDTO)
                .verifyComplete();

        verify(repository).findByTransactionId(transactionId);
        verify(mapper).toEntity(sepaDTO);
        verify(repository).save(sepaEntity);
        verify(mapper).toDTO(sepaEntity);
    }

    @Test
    void updateSepaTransferLine_NotFound() {
        // Arrange
        when(repository.findByTransactionId(transactionId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(service.updateSepaTransferLine(transactionId, sepaDTO))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("SEPA transfer line not found for transactionId: " + transactionId))
                .verify();

        verify(repository).findByTransactionId(transactionId);
        verify(mapper, never()).toEntity(any(TransactionLineSepaTransferDTO.class));
        verify(repository, never()).save(any(TransactionLineSepaTransfer.class));
        verify(mapper, never()).toDTO(any(TransactionLineSepaTransfer.class));
    }

    @Test
    void deleteSepaTransferLine_Success() {
        // Arrange
        when(repository.findByTransactionId(transactionId)).thenReturn(Mono.just(sepaEntity));
        when(repository.delete(sepaEntity)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(service.deleteSepaTransferLine(transactionId))
                .verifyComplete();

        verify(repository).findByTransactionId(transactionId);
        verify(repository).delete(sepaEntity);
    }

    @Test
    void deleteSepaTransferLine_NotFound() {
        // Arrange
        when(repository.findByTransactionId(transactionId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(service.deleteSepaTransferLine(transactionId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("SEPA transfer line not found for transactionId: " + transactionId))
                .verify();

        verify(repository).findByTransactionId(transactionId);
        verify(repository, never()).delete(any(TransactionLineSepaTransfer.class));
    }
}