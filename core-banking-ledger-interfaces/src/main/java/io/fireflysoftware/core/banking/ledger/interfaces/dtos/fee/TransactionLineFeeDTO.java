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


package io.fireflysoftware.core.banking.ledger.interfaces.dtos.fee;

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
 * DTO representing detailed information about a fee transaction.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class TransactionLineFeeDTO extends BaseDTO {
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID transactionLineFeeId;

    @FilterableId
    @NotNull(message = "Transaction ID is required")
    private UUID transactionId;

    @NotBlank(message = "Fee type is required")
    @Size(max = 50, message = "Fee type cannot exceed 50 characters")
    private String feeType;

    @Size(max = 200, message = "Fee description cannot exceed 200 characters")
    private String feeDescription;

    @Size(max = 100, message = "Fee reference cannot exceed 100 characters")
    private String feeReference;

    @FilterableId
    private UUID feeRelatedTransactionId;

    @Size(max = 100, message = "Fee related service cannot exceed 100 characters")
    private String feeRelatedService;

    @Size(max = 50, message = "Fee calculation method cannot exceed 50 characters")
    private String feeCalculationMethod;

    @ValidAmount
    private BigDecimal feeCalculationBase;

    @DecimalMin(value = "0.0", message = "Fee rate percentage cannot be negative")
    @DecimalMax(value = "100.0", message = "Fee rate percentage cannot exceed 100")
    private BigDecimal feeRatePercentage;

    @ValidAmount
    private BigDecimal feeFixedAmount;

    @ValidCurrencyCode
    private String feeCurrency;
    private Boolean feeWaived;

    @Size(max = 200, message = "Fee waiver reason cannot exceed 200 characters")
    private String feeWaiverReason;

    @Size(max = 100, message = "Fee waiver authorized by cannot exceed 100 characters")
    private String feeWaiverAuthorizedBy;

    @ValidDateTime
    private LocalDateTime feeTimestamp;

    @Size(max = 100, message = "Fee processed by cannot exceed 100 characters")
    private String feeProcessedBy;

    @Size(max = 20, message = "Fee Spanish tax code cannot exceed 20 characters")
    private String feeSpanishTaxCode;
}
