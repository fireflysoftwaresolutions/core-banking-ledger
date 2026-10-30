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


package io.fireflysoftware.core.banking.ledger.interfaces.dtos.transfer;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.BaseDTO;
import org.fireflyframework.utils.annotations.FilterableId;
import org.fireflyframework.annotations.ValidAmount;
import org.fireflyframework.annotations.ValidCurrencyCode;
import org.fireflyframework.annotations.ValidDate;
import org.fireflyframework.annotations.ValidDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.UUID;
/**
 * DTO representing detailed information about an internal transfer transaction.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class TransactionLineTransferDTO extends BaseDTO {
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID transactionLineTransferId;

    @FilterableId
    @NotNull(message = "Transaction ID is required")
    private UUID transactionId;

    @Size(max = 100, message = "Transfer reference cannot exceed 100 characters")
    private String transferReference;

    @FilterableId
    @NotNull(message = "Transfer source account ID is required")
    private UUID transferSourceAccountId;

    @FilterableId
    @NotNull(message = "Transfer destination account ID is required")
    private UUID transferDestinationAccountId;

    @Size(max = 50, message = "Transfer source account number cannot exceed 50 characters")
    private String transferSourceAccountNumber;

    @Size(max = 50, message = "Transfer destination account number cannot exceed 50 characters")
    private String transferDestinationAccountNumber;

    @Size(max = 200, message = "Transfer source account name cannot exceed 200 characters")
    private String transferSourceAccountName;

    @Size(max = 200, message = "Transfer destination account name cannot exceed 200 characters")
    private String transferDestinationAccountName;
    @Size(max = 200, message = "Transfer purpose cannot exceed 200 characters")
    private String transferPurpose;

    @Size(max = 500, message = "Transfer notes cannot exceed 500 characters")
    private String transferNotes;

    @ValidDateTime
    private LocalDateTime transferTimestamp;

    @Size(max = 100, message = "Transfer processed by cannot exceed 100 characters")
    private String transferProcessedBy;

    @ValidAmount
    private BigDecimal transferFeeAmount;

    @ValidCurrencyCode
    private String transferFeeCurrency;

    @ValidDate
    private LocalDate transferScheduledDate;

    @ValidDate
    private LocalDate transferExecutionDate;

    @Size(max = 20, message = "Transfer Spanish tax code cannot exceed 20 characters")
    private String transferSpanishTaxCode;
}
