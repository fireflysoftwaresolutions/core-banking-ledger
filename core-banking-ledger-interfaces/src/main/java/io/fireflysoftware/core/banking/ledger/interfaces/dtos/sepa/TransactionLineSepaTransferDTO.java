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


package io.fireflysoftware.core.banking.ledger.interfaces.dtos.sepa;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.BaseDTO;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.sepa.SepaSpanishSchemeEnum;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.sepa.SepaTransactionStatusEnum;
import org.fireflyframework.utils.annotations.FilterableId;
import org.fireflyframework.annotations.ValidAmount;
import org.fireflyframework.annotations.ValidBic;
import org.fireflyframework.annotations.ValidCurrencyCode;
import org.fireflyframework.annotations.ValidDate;
import org.fireflyframework.annotations.ValidDateTime;
import org.fireflyframework.annotations.ValidIban;
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
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class TransactionLineSepaTransferDTO extends BaseDTO {
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID transactionLineSepaId;

    @FilterableId
    @NotNull(message = "Transaction ID is required")
    private UUID transactionId;

    @FilterableId
    @NotBlank(message = "SEPA end-to-end ID is required")
    @Size(max = 35, message = "SEPA end-to-end ID cannot exceed 35 characters")
    private String sepaEndToEndId;

    @Size(max = 140, message = "SEPA remittance info cannot exceed 140 characters")
    private String sepaRemittanceInfo;

    @NotBlank(message = "SEPA origin IBAN is required")
    @ValidIban
    private String sepaOriginIban;

    @ValidBic
    private String sepaOriginBic;

    @NotBlank(message = "SEPA destination IBAN is required")
    @ValidIban
    private String sepaDestinationIban;

    @ValidBic
    private String sepaDestinationBic;

    @NotNull(message = "SEPA transaction status is required")
    private SepaTransactionStatusEnum sepaTransactionStatus;

    @FilterableId
    @Size(max = 35, message = "SEPA creditor ID cannot exceed 35 characters")
    private String sepaCreditorId;

    @FilterableId
    @Size(max = 35, message = "SEPA debtor ID cannot exceed 35 characters")
    private String sepaDebtorId;

    @ValidBic
    private String sepaInitiatingAgentBic;

    @ValidBic
    private String sepaIntermediaryBic;

    @Size(max = 4, message = "SEPA transaction purpose cannot exceed 4 characters")
    private String sepaTransactionPurpose;

    @ValidDate
    private LocalDate sepaRequestedExecutionDate;

    @DecimalMin(value = "0.0", message = "SEPA exchange rate cannot be negative")
    private BigDecimal sepaExchangeRate;

    @ValidAmount
    private BigDecimal sepaFeeAmount;

    @ValidCurrencyCode
    private String sepaFeeCurrency;

    @Size(max = 70, message = "SEPA recipient name cannot exceed 70 characters")
    private String sepaRecipientName;

    @Size(max = 140, message = "SEPA recipient address cannot exceed 140 characters")
    private String sepaRecipientAddress;

    @ValidDateTime
    private LocalDateTime sepaProcessingDate;

    @Size(max = 500, message = "SEPA notes cannot exceed 500 characters")
    private String sepaNotes;

    @NotNull(message = "SEPA payment scheme is required")
    private SepaSpanishSchemeEnum sepaPaymentScheme;
}