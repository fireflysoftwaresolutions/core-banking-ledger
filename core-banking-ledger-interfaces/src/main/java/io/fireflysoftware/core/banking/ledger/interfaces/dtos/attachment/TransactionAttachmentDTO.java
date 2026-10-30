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


package io.fireflysoftware.core.banking.ledger.interfaces.dtos.attachment;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.BaseDTO;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.attachment.AttachmentTypeEnum;
import org.fireflyframework.utils.annotations.FilterableId;
import org.fireflyframework.annotations.ValidDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

import java.util.UUID;
/**
 * DTO representing an attachment related to a transaction.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class TransactionAttachmentDTO extends BaseDTO {
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID transactionAttachmentId;

    @FilterableId
    @NotNull(message = "Transaction ID is required")
    private UUID transactionId;

    @NotNull(message = "Attachment type is required")
    private AttachmentTypeEnum attachmentType;

    @NotBlank(message = "Attachment name is required")
    @Size(max = 255, message = "Attachment name cannot exceed 255 characters")
    private String attachmentName;

    @Size(max = 500, message = "Attachment description cannot exceed 500 characters")
    private String attachmentDescription;

    @NotBlank(message = "Document ID is required")
    @Size(max = 100, message = "Document ID cannot exceed 100 characters")
    private String documentId;

    @NotBlank(message = "Content type is required")
    @Size(max = 100, message = "Content type cannot exceed 100 characters")
    private String contentType;

    @NotNull(message = "Size in bytes is required")
    @Min(value = 0, message = "Size in bytes cannot be negative")
    private Long sizeBytes;

    @Size(max = 64, message = "SHA256 hash cannot exceed 64 characters")
    private String hashSha256;

    @NotBlank(message = "Uploaded by is required")
    @Size(max = 100, message = "Uploaded by cannot exceed 100 characters")
    private String uploadedBy;

    @NotNull(message = "Upload date is required")
    @ValidDateTime
    private LocalDateTime uploadDate;
}
