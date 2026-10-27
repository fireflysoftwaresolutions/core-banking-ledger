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


package io.fireflysoftware.core.banking.ledger.models.entities.transfer;

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
 * Entity representing detailed information about an internal transfer transaction.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("transaction_line_transfer")
public class TransactionLineTransfer extends BaseEntity {
    @Id
    @Column("transaction_line_transfer_id")
    private UUID transactionLineTransferId;

    @Column("transaction_id")
    private UUID transactionId;

    @Column("transfer_reference")
    private String transferReference;

    @Column("transfer_source_account_id")
    private UUID transferSourceAccountId;

    @Column("transfer_destination_account_id")
    private UUID transferDestinationAccountId;

    @Column("transfer_source_account_number")
    private String transferSourceAccountNumber;

    @Column("transfer_destination_account_number")
    private String transferDestinationAccountNumber;

    @Column("transfer_source_account_name")
    private String transferSourceAccountName;

    @Column("transfer_destination_account_name")
    private String transferDestinationAccountName;

    @Column("transfer_purpose")
    private String transferPurpose;

    @Column("transfer_notes")
    private String transferNotes;

    @Column("transfer_timestamp")
    private LocalDateTime transferTimestamp;

    @Column("transfer_processed_by")
    private String transferProcessedBy;

    @Column("transfer_fee_amount")
    private BigDecimal transferFeeAmount;

    @Column("transfer_fee_currency")
    private String transferFeeCurrency;

    @Column("transfer_scheduled_date")
    private LocalDate transferScheduledDate;

    @Column("transfer_execution_date")
    private LocalDate transferExecutionDate;

    @Column("transfer_spanish_tax_code")
    private String transferSpanishTaxCode;
}
