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


package io.fireflysoftware.core.banking.ledger.models.entities.attachment;

import io.fireflysoftware.core.banking.ledger.interfaces.enums.attachment.AttachmentTypeEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing an attachment related to a transaction.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("transaction_attachment")
public class TransactionAttachment extends BaseEntity {
    @Id
    @Column("transaction_attachment_id")
    private UUID transactionAttachmentId;

    @Column("transaction_id")
    private UUID transactionId;

    /**
     * Type of attachment (INVOICE, RECEIPT, CONTRACT, etc.)
     */
    @Column("attachment_type")
    private AttachmentTypeEnum attachmentType;

    @Column("attachment_name")
    private String attachmentName;

    @Column("attachment_description")
    private String attachmentDescription;

    /**
     * Reference to document ID in external ECM (Enterprise Content Management) system
     */
    @Column("document_id")
    private String documentId;

    @Column("content_type")
    private String contentType;

    @Column("size_bytes")
    private Long sizeBytes;

    @Column("hash_sha256")
    private String hashSha256;

    @Column("uploaded_by")
    private String uploadedBy;

    @Column("upload_date")
    private LocalDateTime uploadDate;
}
