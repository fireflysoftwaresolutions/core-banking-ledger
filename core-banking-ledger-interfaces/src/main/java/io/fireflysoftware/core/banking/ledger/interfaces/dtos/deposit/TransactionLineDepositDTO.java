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


package io.fireflysoftware.core.banking.ledger.interfaces.dtos.deposit;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.BaseDTO;
import org.fireflyframework.utils.annotations.FilterableId;
import org.fireflyframework.annotations.ValidAmount;
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
 * DTO representing detailed information about a deposit transaction.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class TransactionLineDepositDTO extends BaseDTO {
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID transactionLineDepositId;

    @FilterableId
    @NotNull(message = "Transaction ID is required")
    private UUID transactionId;

    @NotBlank(message = "Deposit method is required")
    @Size(max = 50, message = "Deposit method cannot exceed 50 characters")
    private String depositMethod;

    @Size(max = 100, message = "Deposit reference cannot exceed 100 characters")
    private String depositReference;

    @Size(max = 200, message = "Deposit location cannot exceed 200 characters")
    private String depositLocation;

    @Size(max = 500, message = "Deposit notes cannot exceed 500 characters")
    private String depositNotes;

    @Size(max = 50, message = "Deposit confirmation code cannot exceed 50 characters")
    private String depositConfirmationCode;

    @Size(max = 50, message = "Deposit receipt number cannot exceed 50 characters")
    private String depositReceiptNumber;

    @Size(max = 20, message = "Deposit ATM ID cannot exceed 20 characters")
    private String depositAtmId;

    @Size(max = 20, message = "Deposit branch ID cannot exceed 20 characters")
    private String depositBranchId;

    @ValidAmount
    private BigDecimal depositCashAmount;

    @ValidAmount
    private BigDecimal depositCheckAmount;

    @Size(max = 50, message = "Deposit check number cannot exceed 50 characters")
    private String depositCheckNumber;

    @ValidDate
    private LocalDate depositCheckDate;

    @Size(max = 100, message = "Deposit check bank cannot exceed 100 characters")
    private String depositCheckBank;

    @ValidDateTime
    private LocalDateTime depositTimestamp;

    @Size(max = 100, message = "Deposit processed by cannot exceed 100 characters")
    private String depositProcessedBy;

    @Size(max = 20, message = "Deposit Spanish tax code cannot exceed 20 characters")
    private String depositSpanishTaxCode;
}
