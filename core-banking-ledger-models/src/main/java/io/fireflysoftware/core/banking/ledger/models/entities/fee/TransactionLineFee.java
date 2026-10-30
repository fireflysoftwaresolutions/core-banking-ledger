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


package io.fireflysoftware.core.banking.ledger.models.entities.fee;

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
 * Entity representing detailed information about a fee transaction.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("transaction_line_fee")
public class TransactionLineFee extends BaseEntity {
    @Id
    @Column("transaction_line_fee_id")
    private UUID transactionLineFeeId;

    @Column("transaction_id")
    private UUID transactionId;

    @Column("fee_type")
    private String feeType;

    @Column("fee_description")
    private String feeDescription;

    @Column("fee_reference")
    private String feeReference;

    @Column("fee_related_transaction_id")
    private UUID feeRelatedTransactionId;

    @Column("fee_related_service")
    private String feeRelatedService;

    @Column("fee_calculation_method")
    private String feeCalculationMethod;

    @Column("fee_calculation_base")
    private BigDecimal feeCalculationBase;

    @Column("fee_rate_percentage")
    private BigDecimal feeRatePercentage;

    @Column("fee_fixed_amount")
    private BigDecimal feeFixedAmount;

    @Column("fee_currency")
    private String feeCurrency;

    @Column("fee_waived")
    private Boolean feeWaived;

    @Column("fee_waiver_reason")
    private String feeWaiverReason;

    @Column("fee_waiver_authorized_by")
    private String feeWaiverAuthorizedBy;

    @Column("fee_timestamp")
    private LocalDateTime feeTimestamp;

    @Column("fee_processed_by")
    private String feeProcessedBy;

    @Column("fee_spanish_tax_code")
    private String feeSpanishTaxCode;
}
