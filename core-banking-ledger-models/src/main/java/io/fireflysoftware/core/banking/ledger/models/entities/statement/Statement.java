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


package io.fireflysoftware.core.banking.ledger.models.entities.statement;

import io.fireflysoftware.core.banking.ledger.interfaces.enums.statement.StatementPeriodEnum;
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
/**
 * Entity representing a statement record.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("statement")
public class Statement extends BaseEntity {
    @Id
    @Column("statement_id")
    private UUID statementId;

    @Column("account_id")
    private UUID accountId;

    @Column("account_space_id")
    private UUID accountSpaceId;

    @Column("period_type")
    private StatementPeriodEnum periodType;

    @Column("start_date")
    private LocalDate startDate;

    @Column("end_date")
    private LocalDate endDate;

    @Column("generation_date")
    private LocalDateTime generationDate;

    @Column("transaction_count")
    private Integer transactionCount;

    @Column("included_pending")
    private Boolean includedPending;

    @Column("included_details")
    private Boolean includedDetails;
}
