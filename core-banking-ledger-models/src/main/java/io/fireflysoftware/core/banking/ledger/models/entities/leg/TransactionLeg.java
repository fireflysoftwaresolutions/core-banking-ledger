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


package io.fireflysoftware.core.banking.ledger.models.entities.leg;

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
 * Entity representing a transaction leg in double-entry accounting.
 * Each transaction consists of at least two legs (debit and credit).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("transaction_leg")
public class TransactionLeg extends BaseEntity {
    @Id
    @Column("transaction_leg_id")
    private UUID transactionLegId;

    @Column("transaction_id")
    private UUID transactionId;

    /**
     * Reference to account ID in external account microservice.
     * This is a logical reference to an account managed in a separate microservice.
     */
    @Column("account_id")
    private UUID accountId;

    /**
     * Reference to account space ID in external account microservice.
     * This is a logical reference to an account space managed in a separate microservice.
     */
    @Column("account_space_id")
    private UUID accountSpaceId;

    @Column("leg_type")
    private String legType;  // "DEBIT" or "CREDIT"

    @Column("amount")
    private BigDecimal amount;

    @Column("currency")
    private String currency;

    @Column("description")
    private String description;

    @Column("value_date")
    private LocalDateTime valueDate;

    @Column("booking_date")
    private LocalDateTime bookingDate;
}
