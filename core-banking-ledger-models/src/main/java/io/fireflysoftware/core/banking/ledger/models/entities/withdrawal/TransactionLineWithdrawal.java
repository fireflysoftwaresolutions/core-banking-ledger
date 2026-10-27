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


package io.fireflysoftware.core.banking.ledger.models.entities.withdrawal;

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
/**
 * Entity representing detailed information about a withdrawal transaction.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("transaction_line_withdrawal")
public class TransactionLineWithdrawal extends BaseEntity {
    @Id
    @Column("transaction_line_withdrawal_id")
    private UUID transactionLineWithdrawalId;

    @Column("transaction_id")
    private UUID transactionId;

    @Column("withdrawal_method")
    private String withdrawalMethod;

    @Column("withdrawal_reference")
    private String withdrawalReference;

    @Column("withdrawal_location")
    private String withdrawalLocation;

    @Column("withdrawal_notes")
    private String withdrawalNotes;

    @Column("withdrawal_confirmation_code")
    private String withdrawalConfirmationCode;

    @Column("withdrawal_receipt_number")
    private String withdrawalReceiptNumber;

    @Column("withdrawal_atm_id")
    private String withdrawalAtmId;

    @Column("withdrawal_branch_id")
    private String withdrawalBranchId;

    @Column("withdrawal_timestamp")
    private LocalDateTime withdrawalTimestamp;

    @Column("withdrawal_processed_by")
    private String withdrawalProcessedBy;

    @Column("withdrawal_authorization_code")
    private String withdrawalAuthorizationCode;

    @Column("withdrawal_daily_limit_check")
    private Boolean withdrawalDailyLimitCheck;

    @Column("withdrawal_daily_amount_used")
    private BigDecimal withdrawalDailyAmountUsed;

    @Column("withdrawal_daily_limit")
    private BigDecimal withdrawalDailyLimit;

    @Column("withdrawal_spanish_tax_code")
    private String withdrawalSpanishTaxCode;
}
