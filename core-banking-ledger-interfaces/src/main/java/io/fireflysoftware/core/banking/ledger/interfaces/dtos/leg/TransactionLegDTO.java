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


package io.fireflysoftware.core.banking.ledger.interfaces.dtos.leg;

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
 * DTO representing a transaction leg in double-entry accounting.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class TransactionLegDTO extends BaseDTO {
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID transactionLegId;

    @FilterableId
    @NotNull(message = "Transaction ID is required")
    private UUID transactionId;

    /**
     * Reference to account ID in external account microservice.
     */
    @FilterableId
    @NotNull(message = "Account ID is required")
    private UUID accountId;

    /**
     * Reference to account space ID in external account microservice.
     */
    @FilterableId
    private UUID accountSpaceId;

    @NotBlank(message = "Leg type is required")
    @Pattern(regexp = "DEBIT|CREDIT", message = "Leg type must be either DEBIT or CREDIT")
    private String legType;  // "DEBIT" or "CREDIT"

    @NotNull(message = "Amount is required")
    @ValidAmount
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @ValidCurrencyCode
    private String currency;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    @NotNull(message = "Value date is required")
    @ValidDateTime
    private LocalDateTime valueDate;

    @ValidDateTime
    private LocalDateTime bookingDate;
}
