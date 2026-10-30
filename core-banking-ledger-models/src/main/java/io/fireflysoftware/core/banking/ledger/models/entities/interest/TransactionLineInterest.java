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


package io.fireflysoftware.core.banking.ledger.models.entities.interest;

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
 * Entity representing detailed information about an interest transaction.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("transaction_line_interest")
public class TransactionLineInterest extends BaseEntity {
    @Id
    @Column("transaction_line_interest_id")
    private UUID transactionLineInterestId;

    @Column("transaction_id")
    private UUID transactionId;

    @Column("interest_type")
    private String interestType;

    @Column("interest_description")
    private String interestDescription;

    @Column("interest_reference")
    private String interestReference;

    @Column("interest_related_account_id")
    private UUID interestRelatedAccountId;

    @Column("interest_calculation_method")
    private String interestCalculationMethod;

    @Column("interest_calculation_base")
    private BigDecimal interestCalculationBase;

    @Column("interest_rate_percentage")
    private BigDecimal interestRatePercentage;

    @Column("interest_accrual_start_date")
    private LocalDate interestAccrualStartDate;

    @Column("interest_accrual_end_date")
    private LocalDate interestAccrualEndDate;

    @Column("interest_days_calculated")
    private Integer interestDaysCalculated;

    @Column("interest_currency")
    private String interestCurrency;

    @Column("interest_tax_withheld_amount")
    private BigDecimal interestTaxWithheldAmount;

    @Column("interest_tax_withheld_rate")
    private BigDecimal interestTaxWithheldRate;

    @Column("interest_gross_amount")
    private BigDecimal interestGrossAmount;

    @Column("interest_net_amount")
    private BigDecimal interestNetAmount;

    @Column("interest_timestamp")
    private LocalDateTime interestTimestamp;

    @Column("interest_processed_by")
    private String interestProcessedBy;

    @Column("interest_spanish_tax_code")
    private String interestSpanishTaxCode;
}
