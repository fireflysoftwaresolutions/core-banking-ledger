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


package io.fireflysoftware.core.banking.ledger.models.entities.core;

import io.fireflysoftware.core.banking.ledger.interfaces.enums.core.TransactionStatusEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;

import java.util.UUID;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("transaction_status_history")
public class TransactionStatusHistory extends BaseEntity {
    @Id
    @Column("transaction_status_history_id")
    private UUID transactionStatusHistoryId;

    @Column("transaction_id")
    private UUID transactionId;

    @Column("status_code")
    private TransactionStatusEnum statusCode;

    @Column("status_start_datetime")
    private LocalDateTime statusStartDatetime;

    @Column("status_end_datetime")
    private LocalDateTime statusEndDatetime;

    @Column("reason")
    private String reason;

    @Column("regulated_reporting_flag")
    private Boolean regulatedReportingFlag;
}
