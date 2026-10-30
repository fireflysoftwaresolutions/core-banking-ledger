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


package io.fireflysoftware.core.banking.ledger.models.entities.deposit;

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
/**
 * Entity representing detailed information about a deposit transaction.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("transaction_line_deposit")
public class TransactionLineDeposit extends BaseEntity {
    @Id
    @Column("transaction_line_deposit_id")
    private UUID transactionLineDepositId;

    @Column("transaction_id")
    private UUID transactionId;

    @Column("deposit_method")
    private String depositMethod;

    @Column("deposit_reference")
    private String depositReference;

    @Column("deposit_location")
    private String depositLocation;

    @Column("deposit_notes")
    private String depositNotes;

    @Column("deposit_confirmation_code")
    private String depositConfirmationCode;

    @Column("deposit_receipt_number")
    private String depositReceiptNumber;

    @Column("deposit_atm_id")
    private String depositAtmId;

    @Column("deposit_branch_id")
    private String depositBranchId;

    @Column("deposit_cash_amount")
    private BigDecimal depositCashAmount;

    @Column("deposit_check_amount")
    private BigDecimal depositCheckAmount;

    @Column("deposit_check_number")
    private String depositCheckNumber;

    @Column("deposit_check_date")
    private LocalDate depositCheckDate;

    @Column("deposit_check_bank")
    private String depositCheckBank;

    @Column("deposit_timestamp")
    private LocalDateTime depositTimestamp;

    @Column("deposit_processed_by")
    private String depositProcessedBy;

    @Column("deposit_spanish_tax_code")
    private String depositSpanishTaxCode;
}
