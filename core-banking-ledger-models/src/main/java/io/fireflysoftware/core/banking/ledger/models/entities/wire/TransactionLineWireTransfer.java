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


package io.fireflysoftware.core.banking.ledger.models.entities.wire;

import io.fireflysoftware.core.banking.ledger.interfaces.enums.wire.WireTransferPriorityEnum;
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
@Table("transaction_line_wire_transfer")
public class TransactionLineWireTransfer extends BaseEntity {
    @Id
    @Column("transaction_line_wire_transfer_id")
    private UUID transactionLineWireTransferId;

    @Column("transaction_id")
    private UUID transactionId;

    @Column("wire_transfer_reference")
    private String wireTransferReference;

    @Column("wire_origin_swift_bic")
    private String wireOriginSwiftBic;

    @Column("wire_destination_swift_bic")
    private String wireDestinationSwiftBic;

    @Column("wire_origin_account_number")
    private String wireOriginAccountNumber;

    @Column("wire_destination_account_number")
    private String wireDestinationAccountNumber;

    @Column("wire_transfer_purpose")
    private String wireTransferPurpose;

    @Column("wire_transfer_priority")
    private WireTransferPriorityEnum wireTransferPriority;

    @Column("wire_exchange_rate")
    private BigDecimal wireExchangeRate;

    @Column("wire_fee_amount")
    private BigDecimal wireFeeAmount;

    @Column("wire_fee_currency")
    private String wireFeeCurrency;

    @Column("wire_instructing_party")
    private String wireInstructingParty;

    @Column("wire_beneficiary_name")
    private String wireBeneficiaryName;

    @Column("wire_beneficiary_address")
    private String wireBeneficiaryAddress;

    @Column("wire_processing_date")
    private LocalDateTime wireProcessingDate;

    @Column("wire_transaction_notes")
    private String wireTransactionNotes;

    @Column("wire_reception_status")
    private String wireReceptionStatus;

    @Column("wire_decline_reason")
    private String wireDeclineReason;

    @Column("wire_cancelled_flag")
    private Boolean wireCancelledFlag;

    @Column("bank_of_spain_reg_code")
    private String bankOfSpainRegCode;
}
