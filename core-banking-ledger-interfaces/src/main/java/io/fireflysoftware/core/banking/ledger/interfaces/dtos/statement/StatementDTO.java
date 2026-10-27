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


package io.fireflysoftware.core.banking.ledger.interfaces.dtos.statement;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.BaseDTO;
import org.fireflyframework.annotations.ValidAmount;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for a complete account or account space statement.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class StatementDTO extends BaseDTO {
    /**
     * The metadata for the statement.
     */
    @NotNull(message = "Statement metadata is required")
    @Valid
    private StatementMetadataDTO metadata;

    /**
     * The opening balance at the start of the statement period.
     */
    @NotNull(message = "Opening balance is required")
    @ValidAmount
    private BigDecimal openingBalance;

    /**
     * The closing balance at the end of the statement period.
     */
    @NotNull(message = "Closing balance is required")
    @ValidAmount
    private BigDecimal closingBalance;

    /**
     * The total of all credits (incoming funds) during the period.
     */
    @NotNull(message = "Total credits is required")
    @ValidAmount
    private BigDecimal totalCredits;

    /**
     * The total of all debits (outgoing funds) during the period.
     */
    @NotNull(message = "Total debits is required")
    @ValidAmount
    private BigDecimal totalDebits;

    /**
     * The list of transaction entries in the statement.
     */
    @Valid
    private List<StatementEntryDTO> entries;
}
