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


package io.fireflysoftware.core.banking.ledger.models.entities.standingorder;

import io.fireflysoftware.core.banking.ledger.interfaces.enums.standingorder.StandingOrderFrequencyEnum;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.standingorder.StandingOrderStatusEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.UUID;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("transaction_line_standing_order")
public class TransactionLineStandingOrder extends BaseEntity {
    @Id
    @Column("transaction_line_standing_order_id")
    private UUID transactionLineStandingOrderId;

    @Column("transaction_id")
    private UUID transactionId;

    @Column("standing_order_id")
    private String standingOrderId;

    @Column("standing_order_frequency")
    private StandingOrderFrequencyEnum standingOrderFrequency;

    @Column("standing_order_start_date")
    private LocalDate standingOrderStartDate;

    @Column("standing_order_end_date")
    private LocalDate standingOrderEndDate;

    @Column("standing_order_next_execution_date")
    private LocalDate standingOrderNextExecutionDate;

    @Column("standing_order_reference")
    private String standingOrderReference;

    @Column("standing_order_recipient_name")
    private String standingOrderRecipientName;

    @Column("standing_order_recipient_iban")
    private String standingOrderRecipientIban;

    @Column("standing_order_recipient_bic")
    private String standingOrderRecipientBic;

    @Column("standing_order_purpose")
    private String standingOrderPurpose;

    @Column("standing_order_status")
    private StandingOrderStatusEnum standingOrderStatus;

    @Column("standing_order_notes")
    private String standingOrderNotes;

    @Column("standing_order_last_execution_date")
    private LocalDate standingOrderLastExecutionDate;

    @Column("standing_order_total_executions")
    private Integer standingOrderTotalExecutions;

    @Column("standing_order_cancelled_date")
    private LocalDateTime standingOrderCancelledDate;

    @Column("standing_order_suspended_until_date")
    private LocalDate standingOrderSuspendedUntilDate;

    @Column("standing_order_created_by")
    private String standingOrderCreatedBy;

    @Column("standing_order_updated_by")
    private String standingOrderUpdatedBy;

    @Column("standing_order_creation_timestamp")
    private LocalDateTime standingOrderCreationTimestamp;

    @Column("standing_order_update_timestamp")
    private LocalDateTime standingOrderUpdateTimestamp;

    @Column("standing_order_spanish_tax_flag")
    private Boolean standingOrderSpanishTaxFlag;
}