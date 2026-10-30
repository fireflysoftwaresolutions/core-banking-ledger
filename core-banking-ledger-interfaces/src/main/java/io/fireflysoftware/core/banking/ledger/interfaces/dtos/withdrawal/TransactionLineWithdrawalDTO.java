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


package io.fireflysoftware.core.banking.ledger.interfaces.dtos.withdrawal;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.BaseDTO;
import org.fireflyframework.utils.annotations.FilterableId;
import org.fireflyframework.annotations.ValidAmount;
import org.fireflyframework.annotations.ValidCurrencyCode;
import org.fireflyframework.annotations.ValidDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.util.UUID;
/**
 * DTO representing detailed information about a withdrawal transaction.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class TransactionLineWithdrawalDTO extends BaseDTO {
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID transactionLineWithdrawalId;

    @FilterableId
    @NotNull(message = "Transaction ID is required")
    private UUID transactionId;

    @NotBlank(message = "Withdrawal method is required")
    @Size(max = 50, message = "Withdrawal method cannot exceed 50 characters")
    private String withdrawalMethod;

    @Size(max = 100, message = "Withdrawal reference cannot exceed 100 characters")
    private String withdrawalReference;

    @Size(max = 200, message = "Withdrawal location cannot exceed 200 characters")
    private String withdrawalLocation;

    @Size(max = 500, message = "Withdrawal notes cannot exceed 500 characters")
    private String withdrawalNotes;

    @Size(max = 50, message = "Withdrawal confirmation code cannot exceed 50 characters")
    private String withdrawalConfirmationCode;

    @Size(max = 50, message = "Withdrawal receipt number cannot exceed 50 characters")
    private String withdrawalReceiptNumber;

    @Size(max = 20, message = "Withdrawal ATM ID cannot exceed 20 characters")
    private String withdrawalAtmId;

    @Size(max = 20, message = "Withdrawal branch ID cannot exceed 20 characters")
    private String withdrawalBranchId;

    @ValidDateTime
    private LocalDateTime withdrawalTimestamp;

    @Size(max = 100, message = "Withdrawal processed by cannot exceed 100 characters")
    private String withdrawalProcessedBy;

    @Size(max = 50, message = "Withdrawal authorization code cannot exceed 50 characters")
    private String withdrawalAuthorizationCode;

    private Boolean withdrawalDailyLimitCheck;

    @ValidAmount
    private BigDecimal withdrawalDailyAmountUsed;

    @ValidAmount
    private BigDecimal withdrawalDailyLimit;

    @Size(max = 20, message = "Withdrawal Spanish tax code cannot exceed 20 characters")
    private String withdrawalSpanishTaxCode;
}
