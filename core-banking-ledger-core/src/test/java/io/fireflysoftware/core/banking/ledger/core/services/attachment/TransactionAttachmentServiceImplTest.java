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


package io.fireflysoftware.core.banking.ledger.core.services.attachment;
import io.fireflysoftware.core.banking.ledger.core.services.attachment.impl.TransactionAttachmentServiceImpl;

import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import org.fireflyframework.core.queries.PaginationUtils;
import io.fireflysoftware.core.banking.ledger.core.mappers.attachment.TransactionAttachmentMapper;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.attachment.TransactionAttachmentDTO;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.attachment.AttachmentTypeEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.attachment.TransactionAttachment;
import io.fireflysoftware.core.banking.ledger.models.repositories.attachment.TransactionAttachmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransactionAttachmentServiceImplTest {

    @Mock
    private TransactionAttachmentRepository repository;

    @Mock
    private TransactionAttachmentMapper mapper;

    @InjectMocks
    private TransactionAttachmentServiceImpl service;

    private TransactionAttachmentDTO attachmentDTO;
    private TransactionAttachment attachmentEntity;
    private final UUID transactionId = UUID.randomUUID();
    private final UUID attachmentId = UUID.randomUUID();
    private final AttachmentTypeEnum attachmentType = AttachmentTypeEnum.INVOICE;

    @BeforeEach
    void setUp() {
        // Initialize test data
        attachmentDTO = new TransactionAttachmentDTO();
        attachmentDTO.setTransactionAttachmentId(attachmentId);
        attachmentDTO.setTransactionId(transactionId);
        attachmentDTO.setAttachmentType(attachmentType);
        attachmentDTO.setAttachmentName("invoice.pdf");
        attachmentDTO.setAttachmentDescription("Test invoice");
        attachmentDTO.setDocumentId("ECM-DOC-123456");
        attachmentDTO.setContentType("application/pdf");
        attachmentDTO.setSizeBytes(1024L);
        attachmentDTO.setHashSha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        attachmentDTO.setUploadedBy("John Doe");
        attachmentDTO.setUploadDate(LocalDateTime.now());

        attachmentEntity = new TransactionAttachment();
        attachmentEntity.setTransactionAttachmentId(attachmentId);
        attachmentEntity.setTransactionId(transactionId);
        attachmentEntity.setAttachmentType(attachmentType);
        attachmentEntity.setAttachmentName("invoice.pdf");
        attachmentEntity.setAttachmentDescription("Test invoice");
        attachmentEntity.setDocumentId("ECM-DOC-123456");
        attachmentEntity.setContentType("application/pdf");
        attachmentEntity.setSizeBytes(1024L);
        attachmentEntity.setHashSha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        attachmentEntity.setUploadedBy("John Doe");
        attachmentEntity.setUploadDate(LocalDateTime.now());
    }

    @Test
    void createAttachment_Success() {
        // Arrange
        when(mapper.toEntity(any(TransactionAttachmentDTO.class))).thenReturn(attachmentEntity);
        when(repository.save(any(TransactionAttachment.class))).thenReturn(Mono.just(attachmentEntity));
        when(mapper.toDTO(any(TransactionAttachment.class))).thenReturn(attachmentDTO);

        // Act & Assert
        StepVerifier.create(service.createAttachment(transactionId, attachmentDTO))
                .expectNext(attachmentDTO)
                .verifyComplete();

        verify(mapper).toEntity(attachmentDTO);
        verify(repository).save(attachmentEntity);
        verify(mapper).toDTO(attachmentEntity);
    }

    @Test
    void getAttachment_Success() {
        // Arrange
        when(repository.findById(attachmentId)).thenReturn(Mono.just(attachmentEntity));
        when(mapper.toDTO(any(TransactionAttachment.class))).thenReturn(attachmentDTO);

        // Act & Assert
        StepVerifier.create(service.getAttachment(transactionId, attachmentId))
                .expectNext(attachmentDTO)
                .verifyComplete();

        verify(repository).findById(attachmentId);
        verify(mapper).toDTO(attachmentEntity);
    }

    @Test
    void getAttachment_WrongTransactionId_EmptyResult() {
        // Arrange
        attachmentEntity.setTransactionId(UUID.randomUUID()); // Different transaction ID
        when(repository.findById(attachmentId)).thenReturn(Mono.just(attachmentEntity));

        // Act & Assert
        StepVerifier.create(service.getAttachment(transactionId, attachmentId))
                .verifyComplete(); // Empty result

        verify(repository).findById(attachmentId);
        verify(mapper, never()).toDTO(any(TransactionAttachment.class));
    }

    @Test
    void listAttachments_Success() {
        // Arrange
        PaginationRequest paginationRequest = new PaginationRequest(0, 10, null, null);
        PaginationResponse<TransactionAttachmentDTO> expectedResponse = new PaginationResponse<>(
                List.of(attachmentDTO), 0, 10, 1
        );

        try (MockedStatic<PaginationUtils> paginationUtilsMocked = Mockito.mockStatic(PaginationUtils.class)) {
            paginationUtilsMocked.when(() -> PaginationUtils.paginateQuery(
                    eq(paginationRequest),
                    any(),
                    any(),
                    any()
            )).thenReturn(Mono.just(expectedResponse));

            // Act & Assert
            StepVerifier.create(service.listAttachments(transactionId, paginationRequest))
                    .expectNext(expectedResponse)
                    .verifyComplete();
        }
    }

    @Test
    void listAttachmentsByType_Success() {
        // Arrange
        PaginationRequest paginationRequest = new PaginationRequest(0, 10, null, null);
        PaginationResponse<TransactionAttachmentDTO> expectedResponse = new PaginationResponse<>(
                List.of(attachmentDTO), 0, 10, 1
        );

        try (MockedStatic<PaginationUtils> paginationUtilsMocked = Mockito.mockStatic(PaginationUtils.class)) {
            paginationUtilsMocked.when(() -> PaginationUtils.paginateQuery(
                    eq(paginationRequest),
                    any(),
                    any(),
                    any()
            )).thenReturn(Mono.just(expectedResponse));

            // Act & Assert
            StepVerifier.create(service.listAttachmentsByType(transactionId, attachmentType, paginationRequest))
                    .expectNext(expectedResponse)
                    .verifyComplete();
        }
    }
}
