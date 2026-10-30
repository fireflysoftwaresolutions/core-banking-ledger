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


package io.fireflysoftware.core.banking.ledger.interfaces.dtos.wire;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.BaseDTO;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.wire.WireTransferPriorityEnum;
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
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class TransactionLineWireTransferDTO extends BaseDTO {
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID transactionLineWireTransferId;

    @FilterableId
    @NotNull(message = "Transaction ID is required")
    private UUID transactionId;

    @Size(max = 100, message = "Wire transfer reference cannot exceed 100 characters")
    private String wireTransferReference;

    @NotBlank(message = "Wire origin SWIFT BIC is required")
    @Size(max = 11, message = "Wire origin SWIFT BIC cannot exceed 11 characters")
    private String wireOriginSwiftBic;

    @NotBlank(message = "Wire destination SWIFT BIC is required")
    @Size(max = 11, message = "Wire destination SWIFT BIC cannot exceed 11 characters")
    private String wireDestinationSwiftBic;

    @NotBlank(message = "Wire origin account number is required")
    @Size(max = 50, message = "Wire origin account number cannot exceed 50 characters")
    private String wireOriginAccountNumber;

    @NotBlank(message = "Wire destination account number is required")
    @Size(max = 50, message = "Wire destination account number cannot exceed 50 characters")
    private String wireDestinationAccountNumber;

    @Size(max = 200, message = "Wire transfer purpose cannot exceed 200 characters")
    private String wireTransferPurpose;

    @NotNull(message = "Wire transfer priority is required")
    private WireTransferPriorityEnum wireTransferPriority;

    @DecimalMin(value = "0.0", message = "Wire exchange rate cannot be negative")
    private BigDecimal wireExchangeRate;

    @ValidAmount
    private BigDecimal wireFeeAmount;

    @ValidCurrencyCode
    private String wireFeeCurrency;
    @Size(max = 200, message = "Wire instructing party cannot exceed 200 characters")
    private String wireInstructingParty;

    @Size(max = 200, message = "Wire beneficiary name cannot exceed 200 characters")
    private String wireBeneficiaryName;

    @Size(max = 500, message = "Wire beneficiary address cannot exceed 500 characters")
    private String wireBeneficiaryAddress;

    @ValidDateTime
    private LocalDateTime wireProcessingDate;

    @Size(max = 500, message = "Wire transaction notes cannot exceed 500 characters")
    private String wireTransactionNotes;

    @Size(max = 50, message = "Wire reception status cannot exceed 50 characters")
    private String wireReceptionStatus;

    @Size(max = 200, message = "Wire decline reason cannot exceed 200 characters")
    private String wireDeclineReason;

    private Boolean wireCancelledFlag;

    @Size(max = 20, message = "Bank of Spain reg code cannot exceed 20 characters")
    private String bankOfSpainRegCode;
}
