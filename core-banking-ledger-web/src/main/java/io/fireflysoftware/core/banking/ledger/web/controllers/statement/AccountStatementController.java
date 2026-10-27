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


package io.fireflysoftware.core.banking.ledger.web.controllers.statement;

import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.banking.ledger.core.services.statement.StatementService;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.statement.StatementMetadataDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

import java.util.UUID;
/**
 * REST controller for managing account statement records.
 * Provides basic CRUD operations for statement data persistence.
 */
@RestController
@RequestMapping("/api/v1/accounts/{accountId}/statements")
@Tag(name = "Account Statements", description = "API endpoints for managing account statement records")
public class AccountStatementController {

    @Autowired
    private StatementService service;



    @Operation(
            summary = "List Account Statements",
            description = "Retrieve a paginated list of statements for a specific account."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Account statements retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaginationResponse.class))
    )
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<PaginationResponse<StatementMetadataDTO>>> listStatements(
            @Parameter(description = "Account ID", required = true)
            @PathVariable UUID accountId,
            @ParameterObject
            @ModelAttribute PaginationRequest paginationRequest
    ) {
        return service.listAccountStatements(accountId, paginationRequest)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "List Account Statements by Date Range",
            description = "Retrieve a paginated list of statements for a specific account within a date range."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Account statements retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaginationResponse.class))
    )
    @GetMapping(value = "/date-range", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<PaginationResponse<StatementMetadataDTO>>> listStatementsByDateRange(
            @Parameter(description = "Account ID", required = true)
            @PathVariable UUID accountId,

            @Parameter(description = "Start date (ISO format)", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @Parameter(description = "End date (ISO format)", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,

            @ParameterObject
            @ModelAttribute PaginationRequest paginationRequest
    ) {
        return service.listAccountStatementsByDateRange(accountId, startDate, endDate, paginationRequest)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Get Account Statement",
            description = "Retrieve a specific statement by ID."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Statement retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = StatementMetadataDTO.class))
    )
    @GetMapping(value = "/{statementId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<StatementMetadataDTO>> getStatement(
            @Parameter(description = "Account ID", required = true)
            @PathVariable UUID accountId,

            @Parameter(description = "Statement ID", required = true)
            @PathVariable UUID statementId
    ) {
        return service.getStatement(statementId)
                .filter(statement -> statement.getAccountId() != null && statement.getAccountId().equals(accountId))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
