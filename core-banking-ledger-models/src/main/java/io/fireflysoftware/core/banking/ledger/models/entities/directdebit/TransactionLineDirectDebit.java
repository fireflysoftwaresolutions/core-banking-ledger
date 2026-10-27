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


package io.fireflysoftware.core.banking.ledger.models.entities.directdebit;

import io.fireflysoftware.core.banking.ledger.interfaces.enums.directdebit.DirectDebitProcessingStatusEnum;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.directdebit.DirectDebitSequenceTypeEnum;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.directdebit.DirectDebitSpanishSchemeEnum;
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
@Table("transaction_line_direct_debit")
public class TransactionLineDirectDebit extends BaseEntity {
    @Id
    @Column("transaction_line_direct_debit_id")
    private UUID transactionLineDirectDebitId;

    @Column("transaction_id")
    private UUID transactionId;

    @Column("direct_debit_mandate_id")
    private String directDebitMandateId;

    @Column("direct_debit_creditor_id")
    private String directDebitCreditorId;

    @Column("direct_debit_reference")
    private String directDebitReference;

    @Column("direct_debit_sequence_type")
    private DirectDebitSequenceTypeEnum directDebitSequenceType;

    @Column("direct_debit_due_date")
    private LocalDate directDebitDueDate;

    @Column("direct_debit_payment_method")
    private String directDebitPaymentMethod;

    @Column("direct_debit_debtor_name")
    private String directDebitDebtorName;

    @Column("direct_debit_debtor_address")
    private String directDebitDebtorAddress;

    @Column("direct_debit_debtor_contact")
    private String directDebitDebtorContact;

    @Column("direct_debit_processing_status")
    private DirectDebitProcessingStatusEnum directDebitProcessingStatus;

    @Column("direct_debit_authorization_date")
    private LocalDateTime directDebitAuthorizationDate;

    @Column("direct_debit_revocation_date")
    private LocalDateTime directDebitRevocationDate;

    @Column("direct_debit_spanish_scheme")
    private DirectDebitSpanishSchemeEnum directDebitSpanishScheme;
}

