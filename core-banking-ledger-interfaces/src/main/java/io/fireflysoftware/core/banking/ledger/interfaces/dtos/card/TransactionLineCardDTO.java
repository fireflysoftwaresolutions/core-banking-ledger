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


package io.fireflysoftware.core.banking.ledger.interfaces.dtos.card;

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
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class TransactionLineCardDTO extends BaseDTO {
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID transactionLineCardId;

    @FilterableId
    @NotNull(message = "Transaction ID is required")
    private UUID transactionId;

    @Size(max = 20, message = "Card auth code cannot exceed 20 characters")
    private String cardAuthCode;

    @Size(max = 10, message = "Card merchant category code cannot exceed 10 characters")
    private String cardMerchantCategoryCode;

    @Size(max = 200, message = "Card merchant name cannot exceed 200 characters")
    private String cardMerchantName;

    @Size(max = 10, message = "Card POS entry mode cannot exceed 10 characters")
    private String cardPosEntryMode;

    @Size(max = 100, message = "Card transaction reference cannot exceed 100 characters")
    private String cardTransactionReference;

    @Size(max = 20, message = "Card terminal ID cannot exceed 20 characters")
    private String cardTerminalId;

    @Size(max = 3, message = "Card holder country cannot exceed 3 characters")
    private String cardHolderCountry;

    private Boolean cardPresentFlag;

    @ValidDateTime
    private LocalDateTime cardTransactionTimestamp;

    private Boolean cardFraudFlag;

    @DecimalMin(value = "0.0", message = "Card currency conversion rate cannot be negative")
    private BigDecimal cardCurrencyConversionRate;

    @ValidAmount
    private BigDecimal cardFeeAmount;

    @ValidCurrencyCode
    private String cardFeeCurrency;

    @Size(max = 50, message = "Card installment plan cannot exceed 50 characters")
    private String cardInstallmentPlan;

    @Size(max = 20, message = "Card merchant tax identifier cannot exceed 20 characters")
    private String cardMerchantTaxId;
}
