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


package io.fireflysoftware.core.banking.ledger.core.services.statement;

import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.statement.StatementMetadataDTO;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

import java.util.UUID;
/**
 * Service interface for managing statement records.
 * Provides basic CRUD operations for statement data persistence.
 */
public interface StatementService {
    /**
     * Get a specific statement by ID.
     *
     * @param statementId The ID of the statement.
     * @return The statement metadata.
     */
    Mono<StatementMetadataDTO> getStatement(UUID statementId);

    /**
     * List statements for an account.
     *
     * @param accountId The ID of the account.
     * @param paginationRequest Pagination parameters.
     * @return A paginated list of statement metadata.
     */
    Mono<PaginationResponse<StatementMetadataDTO>> listAccountStatements(UUID accountId, PaginationRequest paginationRequest);

    /**
     * List statements for an account space.
     *
     * @param accountSpaceId The ID of the account space.
     * @param paginationRequest Pagination parameters.
     * @return A paginated list of statement metadata.
     */
    Mono<PaginationResponse<StatementMetadataDTO>> listAccountSpaceStatements(UUID accountSpaceId, PaginationRequest paginationRequest);

    /**
     * List statements for an account within a date range.
     *
     * @param accountId The ID of the account.
     * @param startDate The start date of the range.
     * @param endDate The end date of the range.
     * @param paginationRequest Pagination parameters.
     * @return A paginated list of statement metadata.
     */
    Mono<PaginationResponse<StatementMetadataDTO>> listAccountStatementsByDateRange(
            UUID accountId, LocalDate startDate, LocalDate endDate, PaginationRequest paginationRequest);

    /**
     * List statements for an account space within a date range.
     *
     * @param accountSpaceId The ID of the account space.
     * @param startDate The start date of the range.
     * @param endDate The end date of the range.
     * @param paginationRequest Pagination parameters.
     * @return A paginated list of statement metadata.
     */
    Mono<PaginationResponse<StatementMetadataDTO>> listAccountSpaceStatementsByDateRange(
            UUID accountSpaceId, LocalDate startDate, LocalDate endDate, PaginationRequest paginationRequest);
}
