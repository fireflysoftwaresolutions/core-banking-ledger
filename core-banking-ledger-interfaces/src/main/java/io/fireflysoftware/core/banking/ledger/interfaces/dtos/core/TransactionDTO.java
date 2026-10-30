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


package io.fireflysoftware.core.banking.ledger.interfaces.dtos.core;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.BaseDTO;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.core.TransactionStatusEnum;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.core.TransactionTypeEnum;
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
public class TransactionDTO extends BaseDTO {
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID transactionId;

    @Size(max = 100, message = "External reference cannot exceed 100 characters")
    private String externalReference;

    @NotNull(message = "Transaction date is required")
    @ValidDateTime
    private LocalDateTime transactionDate;

    @NotNull(message = "Value date is required")
    @ValidDateTime
    private LocalDateTime valueDate;

    @NotNull(message = "Transaction type is required")
    private TransactionTypeEnum transactionType;

    @NotNull(message = "Transaction status is required")
    private TransactionStatusEnum transactionStatus;

    @NotNull(message = "Total amount is required")
    @ValidAmount
    private BigDecimal totalAmount;

    @NotBlank(message = "Currency is required")
    @ValidCurrencyCode
    private String currency;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    @Size(max = 100, message = "Initiating party cannot exceed 100 characters")
    private String initiatingParty;

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

    /**
     * Reference to category ID in external master data microservice.
     */
    @FilterableId
    private UUID transactionCategoryId;

    @Size(max = 20, message = "Branch office code cannot exceed 20 characters")
    private String branchOfficeCode;

    @Size(max = 20, message = "Initiating party identifier cannot exceed 20 characters")
    private String initiatingPartyIdentifier;

    // Geotag fields
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private Double longitude;

    @Size(max = 100, message = "Location name cannot exceed 100 characters")
    private String locationName;

    @Size(max = 3, message = "Country code cannot exceed 3 characters")
    private String country;

    @Size(max = 100, message = "City cannot exceed 100 characters")
    private String city;

    @Size(max = 20, message = "Postal code cannot exceed 20 characters")
    private String postalCode;

    // Relation fields
    @FilterableId
    private UUID relatedTransactionId;

    @Pattern(regexp = "REVERSAL|ADJUSTMENT|CHARGEBACK|CORRECTION", message = "Relation type must be one of: REVERSAL, ADJUSTMENT, CHARGEBACK, CORRECTION")
    private String relationType;

    @Size(max = 100, message = "Request ID cannot exceed 100 characters")
    private String requestId;

    @Size(max = 100, message = "Batch ID cannot exceed 100 characters")
    private String batchId;

    @ValidDateTime
    private LocalDateTime bookingDate;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Min(value = 0, message = "Row version cannot be negative")
    private Long rowVersion;

    // Compliance fields
    @Min(value = 0, message = "AML risk score cannot be negative")
    @Max(value = 100, message = "AML risk score cannot exceed 100")
    private Integer amlRiskScore;

    @Size(max = 50, message = "AML screening result cannot exceed 50 characters")
    private String amlScreeningResult;

    private Boolean amlLargeTxnFlag;

    @Size(max = 50, message = "SCA method cannot exceed 50 characters")
    private String scaMethod;

    @Size(max = 50, message = "SCA result cannot exceed 50 characters")
    private String scaResult;

    private Boolean instantFlag;

    @Pattern(regexp = "OK|MISMATCH|UNAVAILABLE", message = "Confirmation of payee result must be one of: OK, MISMATCH, UNAVAILABLE")
    private String confirmationOfPayeeResult;
}
