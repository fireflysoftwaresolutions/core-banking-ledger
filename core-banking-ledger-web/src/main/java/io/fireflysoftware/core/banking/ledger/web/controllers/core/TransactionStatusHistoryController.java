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


package io.fireflysoftware.core.banking.ledger.web.controllers.core;

import java.util.UUID;

import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.banking.ledger.core.services.core.impl.TransactionStatusHistoryServiceImpl;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.core.TransactionStatusHistoryDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Tag(name = "Transaction Status History", description = "APIs for managing the status history of a specific transaction in the ledger")
@RestController
@RequestMapping("/api/v1/transactions/{transactionId}/status-history")
public class TransactionStatusHistoryController {

    @Autowired
    private TransactionStatusHistoryServiceImpl service;

    @Operation(
            summary = "List Transaction Status History",
            description = "Retrieve a paginated list of status history records for the specified transaction."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved transaction status history records",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PaginationResponse.class))),
            @ApiResponse(responseCode = "404", description = "No status history records found for the specified transaction",
                    content = @Content)
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<PaginationResponse<TransactionStatusHistoryDTO>>> getAllStatusHistory(
            @Parameter(description = "Unique identifier of the transaction", required = true)
            @PathVariable UUID transactionId,

            @ParameterObject
            @ModelAttribute PaginationRequest paginationRequest
    ) {
        return service.listStatusHistory(transactionId, paginationRequest)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Create Transaction Status History",
            description = "Create a new status history record tied to the specified transaction."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Transaction status history record created successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TransactionStatusHistoryDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid status history data provided",
                    content = @Content)
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<TransactionStatusHistoryDTO>> createStatusHistory(
            @Parameter(description = "Unique identifier of the transaction", required = true)
            @PathVariable UUID transactionId,

            @Parameter(description = "Data for the new transaction status history record", required = true,
                    schema = @Schema(implementation = TransactionStatusHistoryDTO.class))
            @RequestBody TransactionStatusHistoryDTO historyDTO
    ) {
        return service.createStatusHistory(transactionId, historyDTO)
                .map(createdHistory -> ResponseEntity.status(201).body(createdHistory))
                .defaultIfEmpty(ResponseEntity.badRequest().build());
    }

    @Operation(
            summary = "Get Transaction Status History by ID",
            description = "Retrieve a specific status history record by its unique identifier, ensuring it is tied to the specified transaction."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved the transaction status history record",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TransactionStatusHistoryDTO.class))),
            @ApiResponse(responseCode = "404", description = "Transaction status history record not found",
                    content = @Content)
    })
    @GetMapping(value = "/{historyId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<TransactionStatusHistoryDTO>> getStatusHistory(
            @Parameter(description = "Unique identifier of the transaction", required = true)
            @PathVariable UUID transactionId,

            @Parameter(description = "Unique identifier of the status history record", required = true)
            @PathVariable UUID historyId
    ) {
        return service.getStatusHistory(transactionId, historyId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Update Transaction Status History",
            description = "Update an existing status history record associated with the specified transaction."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction status history record updated successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TransactionStatusHistoryDTO.class))),
            @ApiResponse(responseCode = "404", description = "Transaction status history record not found",
                    content = @Content)
    })
    @PutMapping(value = "/{historyId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<TransactionStatusHistoryDTO>> updateStatusHistory(
            @Parameter(description = "Unique identifier of the transaction", required = true)
            @PathVariable UUID transactionId,

            @Parameter(description = "Unique identifier of the status history record to update", required = true)
            @PathVariable UUID historyId,

            @Parameter(description = "Updated status history data", required = true,
                    schema = @Schema(implementation = TransactionStatusHistoryDTO.class))
            @RequestBody TransactionStatusHistoryDTO historyDTO
    ) {
        return service.updateStatusHistory(transactionId, historyId, historyDTO)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Delete Transaction Status History",
            description = "Remove an existing status history record by its unique identifier, for the specified transaction."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Transaction status history record deleted successfully",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Transaction status history record not found",
                    content = @Content)
    })
    @DeleteMapping(value = "/{historyId}")
    public Mono<ResponseEntity<Void>> deleteStatusHistory(
            @Parameter(description = "Unique identifier of the transaction", required = true)
            @PathVariable UUID transactionId,

            @Parameter(description = "Unique identifier of the status history record to delete", required = true)
            @PathVariable UUID historyId
    ) {
        return service.deleteStatusHistory(transactionId, historyId)
                .then(Mono.just(ResponseEntity.noContent().build()));
    }
}