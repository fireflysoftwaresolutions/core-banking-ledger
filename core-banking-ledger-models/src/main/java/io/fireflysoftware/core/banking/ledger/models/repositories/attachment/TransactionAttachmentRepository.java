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


package io.fireflysoftware.core.banking.ledger.models.repositories.attachment;

import io.fireflysoftware.core.banking.ledger.interfaces.enums.attachment.AttachmentTypeEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.attachment.TransactionAttachment;
import io.fireflysoftware.core.banking.ledger.models.repositories.BaseRepository;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


import java.util.UUID;
/**
 * Repository interface for transaction attachments.
 */
public interface TransactionAttachmentRepository extends BaseRepository<TransactionAttachment, UUID> {
    /**
     * Find all attachments for a specific transaction.
     */
    Flux<TransactionAttachment> findByTransactionId(UUID transactionId, Pageable pageable);

    /**
     * Count all attachments for a specific transaction.
     */
    Mono<Long> countByTransactionId(UUID transactionId);

    /**
     * Find all attachments of a specific type for a transaction.
     */
    Flux<TransactionAttachment> findByTransactionIdAndAttachmentType(UUID transactionId, AttachmentTypeEnum attachmentType, Pageable pageable);

    /**
     * Count all attachments of a specific type for a transaction.
     */
    Mono<Long> countByTransactionIdAndAttachmentType(UUID transactionId, AttachmentTypeEnum attachmentType);
}
