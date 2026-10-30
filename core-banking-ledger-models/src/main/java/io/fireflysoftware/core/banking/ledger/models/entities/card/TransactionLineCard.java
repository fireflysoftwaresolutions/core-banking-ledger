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


package io.fireflysoftware.core.banking.ledger.models.entities.card;

import io.fireflysoftware.core.banking.ledger.models.entities.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.util.UUID;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("transaction_line_card")
public class TransactionLineCard extends BaseEntity {
    @Id
    @Column("transaction_line_card_id")
    private UUID transactionLineCardId;

    @Column("transaction_id")
    private UUID transactionId;

    @Column("card_auth_code")
    private String cardAuthCode;

    @Column("card_merchant_category_code")
    private String cardMerchantCategoryCode;

    @Column("card_merchant_name")
    private String cardMerchantName;

    @Column("card_pos_entry_mode")
    private String cardPosEntryMode;

    @Column("card_transaction_reference")
    private String cardTransactionReference;

    @Column("card_terminal_id")
    private String cardTerminalId;

    @Column("card_holder_country")
    private String cardHolderCountry;

    @Column("card_present_flag")
    private Boolean cardPresentFlag;

    @Column("card_transaction_timestamp")
    private LocalDateTime cardTransactionTimestamp;

    @Column("card_fraud_flag")
    private Boolean cardFraudFlag;

    @Column("card_currency_conversion_rate")
    private BigDecimal cardCurrencyConversionRate;

    @Column("card_fee_amount")
    private BigDecimal cardFeeAmount;

    @Column("card_fee_currency")
    private String cardFeeCurrency;

    @Column("card_installment_plan")
    private String cardInstallmentPlan;

    @Column("card_merchant_tax_id")
    private String cardMerchantTaxId;
}
