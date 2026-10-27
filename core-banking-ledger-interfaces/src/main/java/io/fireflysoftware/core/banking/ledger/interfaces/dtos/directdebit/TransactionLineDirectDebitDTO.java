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


package io.fireflysoftware.core.banking.ledger.interfaces.dtos.directdebit;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.BaseDTO;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.directdebit.DirectDebitProcessingStatusEnum;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.directdebit.DirectDebitSequenceTypeEnum;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.directdebit.DirectDebitSpanishSchemeEnum;
import org.fireflyframework.utils.annotations.FilterableId;
import org.fireflyframework.annotations.ValidDate;
import org.fireflyframework.annotations.ValidDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.UUID;
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class TransactionLineDirectDebitDTO extends BaseDTO {
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID transactionLineDirectDebitId;

    @FilterableId
    @NotNull(message = "Transaction ID is required")
    private UUID transactionId;

    @FilterableId
    @NotBlank(message = "Direct debit mandate ID is required")
    @Size(max = 50, message = "Direct debit mandate ID cannot exceed 50 characters")
    private String directDebitMandateId;

    @FilterableId
    @NotBlank(message = "Direct debit creditor ID is required")
    @Size(max = 50, message = "Direct debit creditor ID cannot exceed 50 characters")
    private String directDebitCreditorId;

    @Size(max = 100, message = "Direct debit reference cannot exceed 100 characters")
    private String directDebitReference;

    @NotNull(message = "Direct debit sequence type is required")
    private DirectDebitSequenceTypeEnum directDebitSequenceType;

    @NotNull(message = "Direct debit due date is required")
    @ValidDate
    private LocalDate directDebitDueDate;

    @Size(max = 50, message = "Direct debit payment method cannot exceed 50 characters")
    private String directDebitPaymentMethod;

    @Size(max = 200, message = "Direct debit debtor name cannot exceed 200 characters")
    private String directDebitDebtorName;

    @Size(max = 500, message = "Direct debit debtor address cannot exceed 500 characters")
    private String directDebitDebtorAddress;

    @Size(max = 100, message = "Direct debit debtor contact cannot exceed 100 characters")
    private String directDebitDebtorContact;

    @NotNull(message = "Direct debit processing status is required")
    private DirectDebitProcessingStatusEnum directDebitProcessingStatus;

    @ValidDateTime
    private LocalDateTime directDebitAuthorizationDate;

    @ValidDateTime
    private LocalDateTime directDebitRevocationDate;

    @NotNull(message = "Direct debit Spanish scheme is required")
    private DirectDebitSpanishSchemeEnum directDebitSpanishScheme;

}