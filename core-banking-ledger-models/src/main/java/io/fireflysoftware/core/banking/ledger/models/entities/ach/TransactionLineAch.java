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


package io.fireflysoftware.core.banking.ledger.models.entities.ach;

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
 * Entity representing detailed information about an ACH (Automated Clearing House) transaction.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("transaction_line_ach")
public class TransactionLineAch extends BaseEntity {
    @Id
    @Column("transaction_line_ach_id")
    private UUID transactionLineAchId;

    @Column("transaction_id")
    private UUID transactionId;

    @Column("ach_reference")
    private String achReference;

    @Column("ach_source_account_id")
    private UUID achSourceAccountId;

    @Column("ach_destination_account_id")
    private UUID achDestinationAccountId;

    @Column("ach_source_account_number")
    private String achSourceAccountNumber;

    @Column("ach_destination_account_number")
    private String achDestinationAccountNumber;

    @Column("ach_source_account_name")
    private String achSourceAccountName;

    @Column("ach_destination_account_name")
    private String achDestinationAccountName;

    @Column("ach_routing_number")
    private String achRoutingNumber;

    @Column("ach_transaction_code")
    private String achTransactionCode;

    @Column("ach_purpose")
    private String achPurpose;

    @Column("ach_notes")
    private String achNotes;

    @Column("ach_timestamp")
    private LocalDateTime achTimestamp;

    @Column("ach_processed_by")
    private String achProcessedBy;

    @Column("ach_fee_amount")
    private BigDecimal achFeeAmount;

    @Column("ach_fee_currency")
    private String achFeeCurrency;

    @Column("ach_scheduled_date")
    private LocalDate achScheduledDate;

    @Column("ach_execution_date")
    private LocalDate achExecutionDate;

    @Column("ach_batch_number")
    private String achBatchNumber;

    @Column("ach_trace_number")
    private String achTraceNumber;

    @Column("ach_entry_class_code")
    private String achEntryClassCode;

    @Column("ach_settlement_date")
    private LocalDate achSettlementDate;

    @Column("ach_return_code")
    private String achReturnCode;

    @Column("ach_return_reason")
    private String achReturnReason;
}