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

import java.util.UUID;

import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.attachment.TransactionAttachmentDTO;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.attachment.AttachmentTypeEnum;
import reactor.core.publisher.Mono;

/**
 * Service interface for managing transaction attachments.
 */
public interface TransactionAttachmentService {
    /**
     * Create a new transaction attachment.
     *
     * @param transactionId The ID of the transaction to which the attachment belongs.
     * @param attachmentDTO The attachment data.
     * @return The created attachment.
     */
    Mono<TransactionAttachmentDTO> createAttachment(UUID transactionId, TransactionAttachmentDTO attachmentDTO);

    /**
     * Get a specific transaction attachment by ID.
     *
     * @param transactionId The ID of the transaction to which the attachment belongs.
     * @param attachmentId The ID of the attachment.
     * @return The attachment.
     */
    Mono<TransactionAttachmentDTO> getAttachment(UUID transactionId, UUID attachmentId);

    /**
     * List all attachments for a specific transaction.
     *
     * @param transactionId The ID of the transaction.
     * @param paginationRequest Pagination parameters.
     * @return A paginated list of attachments.
     */
    Mono<PaginationResponse<TransactionAttachmentDTO>> listAttachments(UUID transactionId, PaginationRequest paginationRequest);

    /**
     * List all attachments of a specific type for a transaction.
     *
     * @param transactionId The ID of the transaction.
     * @param attachmentType The type of attachment.
     * @param paginationRequest Pagination parameters.
     * @return A paginated list of attachments.
     */
    Mono<PaginationResponse<TransactionAttachmentDTO>> listAttachmentsByType(
            UUID transactionId,
            AttachmentTypeEnum attachmentType,
            PaginationRequest paginationRequest
    );
}
