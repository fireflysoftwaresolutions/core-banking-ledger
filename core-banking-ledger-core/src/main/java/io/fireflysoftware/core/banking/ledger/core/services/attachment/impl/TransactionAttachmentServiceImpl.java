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


package io.fireflysoftware.core.banking.ledger.core.services.attachment.impl;

import io.fireflysoftware.core.banking.ledger.core.mappers.attachment.TransactionAttachmentMapper;
import io.fireflysoftware.core.banking.ledger.core.services.attachment.TransactionAttachmentService;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.attachment.TransactionAttachmentDTO;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.attachment.AttachmentTypeEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.attachment.TransactionAttachment;
import io.fireflysoftware.core.banking.ledger.models.repositories.attachment.TransactionAttachmentRepository;
import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import org.fireflyframework.core.queries.PaginationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.util.UUID;



/**
 * Implementation of the TransactionAttachmentService interface.
 */
@Service
@Transactional
public class TransactionAttachmentServiceImpl implements TransactionAttachmentService {

    @Autowired
    private TransactionAttachmentRepository repository;

    @Autowired
    private TransactionAttachmentMapper mapper;

    @Override
    public Mono<TransactionAttachmentDTO> createAttachment(UUID transactionId, TransactionAttachmentDTO attachmentDTO) {
        attachmentDTO.setTransactionId(transactionId);
        TransactionAttachment entity = mapper.toEntity(attachmentDTO);
        return repository.save(entity)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<TransactionAttachmentDTO> getAttachment(UUID transactionId, UUID attachmentId) {
        return repository.findById(attachmentId)
                .filter(entity -> entity.getTransactionId().equals(transactionId))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<PaginationResponse<TransactionAttachmentDTO>> listAttachments(UUID transactionId, PaginationRequest paginationRequest) {
        return PaginationUtils.paginateQuery(
                paginationRequest,
                mapper::toDTO,
                pageable -> repository.findByTransactionId(transactionId, pageable),
                () -> repository.countByTransactionId(transactionId)
        );
    }

    @Override
    public Mono<PaginationResponse<TransactionAttachmentDTO>> listAttachmentsByType(
            UUID transactionId,
            AttachmentTypeEnum attachmentType,
            PaginationRequest paginationRequest
    ) {
        return PaginationUtils.paginateQuery(
                paginationRequest,
                mapper::toDTO,
                pageable -> repository.findByTransactionIdAndAttachmentType(transactionId, attachmentType, pageable),
                () -> repository.countByTransactionIdAndAttachmentType(transactionId, attachmentType)
        );
    }
}
