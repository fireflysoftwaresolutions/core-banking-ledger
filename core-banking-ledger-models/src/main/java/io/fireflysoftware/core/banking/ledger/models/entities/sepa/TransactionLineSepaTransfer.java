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


package io.fireflysoftware.core.banking.ledger.models.entities.sepa;

import io.fireflysoftware.core.banking.ledger.interfaces.enums.sepa.SepaSpanishSchemeEnum;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.sepa.SepaTransactionStatusEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.UUID;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("transaction_line_sepa_transfer")
public class TransactionLineSepaTransfer extends BaseEntity {
    @Id
    @Column("transaction_line_sepa_id")
    private UUID transactionLineSepaId;

    @Column("transaction_id")
    private UUID transactionId;

    @Column("sepa_end_to_end_id")
    private String sepaEndToEndId;

    @Column("sepa_remittance_info")
    private String sepaRemittanceInfo;

    @Column("sepa_origin_iban")
    private String sepaOriginIban;

    @Column("sepa_origin_bic")
    private String sepaOriginBic;

    @Column("sepa_destination_iban")
    private String sepaDestinationIban;

    @Column("sepa_destination_bic")
    private String sepaDestinationBic;

    @Column("sepa_transaction_status")
    private SepaTransactionStatusEnum sepaTransactionStatus;

    @Column("sepa_creditor_id")
    private String sepaCreditorId;

    @Column("sepa_debtor_id")
    private String sepaDebtorId;

    @Column("sepa_initiating_agent_bic")
    private String sepaInitiatingAgentBic;

    @Column("sepa_intermediary_bic")
    private String sepaIntermediaryBic;

    @Column("sepa_transaction_purpose")
    private String sepaTransactionPurpose;

    @Column("sepa_requested_execution_date")
    private LocalDate sepaRequestedExecutionDate;

    @Column("sepa_exchange_rate")
    private BigDecimal sepaExchangeRate;

    @Column("sepa_fee_amount")
    private BigDecimal sepaFeeAmount;

    @Column("sepa_fee_currency")
    private String sepaFeeCurrency;

    @Column("sepa_recipient_name")
    private String sepaRecipientName;

    @Column("sepa_recipient_address")
    private String sepaRecipientAddress;

    @Column("sepa_processing_date")
    private LocalDateTime sepaProcessingDate;

    @Column("sepa_notes")
    private String sepaNotes;

    @Column("sepa_payment_scheme")
    private SepaSpanishSchemeEnum sepaPaymentScheme;
}

