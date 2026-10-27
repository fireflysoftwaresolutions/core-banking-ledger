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
import io.fireflysoftware.core.banking.ledger.interfaces.enums.statement.StatementPeriodEnum;
import org.fireflyframework.utils.annotations.FilterableId;
import org.fireflyframework.annotations.ValidDate;
import org.fireflyframework.annotations.ValidDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.UUID;
/**
 * DTO for statement metadata.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class StatementMetadataDTO extends BaseDTO {
    /**
     * The unique identifier for the statement.
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID statementId;

    /**
     * The account ID associated with this statement (null if for account space).
     */
    @FilterableId
    private UUID accountId;

    /**
     * The account space ID associated with this statement (null if for account).
     */
    @FilterableId
    private UUID accountSpaceId;

    /**
     * The period type for the statement.
     */
    @NotNull(message = "Period type is required")
    private StatementPeriodEnum periodType;

    /**
     * The start date of the statement period.
     */
    @NotNull(message = "Start date is required")
    @ValidDate
    private LocalDate startDate;

    /**
     * The end date of the statement period.
     */
    @NotNull(message = "End date is required")
    @ValidDate
    private LocalDate endDate;

    /**
     * The date when the statement was generated.
     */
    @NotNull(message = "Generation date is required")
    @ValidDateTime
    private LocalDateTime generationDate;

    /**
     * The number of transactions included in the statement.
     */
    @NotNull(message = "Transaction count is required")
    @Min(value = 0, message = "Transaction count cannot be negative")
    private Integer transactionCount;

    /**
     * Whether pending transactions were included.
     */
    @NotNull(message = "Included pending flag is required")
    private Boolean includedPending;

    /**
     * Whether transaction details were included.
     */
    @NotNull(message = "Included details flag is required")
    private Boolean includedDetails;
}
