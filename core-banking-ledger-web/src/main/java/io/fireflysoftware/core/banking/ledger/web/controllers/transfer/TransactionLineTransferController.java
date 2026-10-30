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


package io.fireflysoftware.core.banking.ledger.web.controllers.transfer;

import java.util.UUID;

import io.fireflysoftware.core.banking.ledger.core.services.transfer.impl.TransactionLineTransferServiceImpl;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.transfer.TransactionLineTransferDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Tag(name = "Transaction Line Transfer", description = "APIs for managing transfer lines associated with a specific transaction")
@RestController
@RequestMapping("/api/v1/transactions/{transactionId}/line-transfer")
public class TransactionLineTransferController {

    @Autowired
    private TransactionLineTransferServiceImpl service;

    @Operation(
            summary = "Get Transfer Line",
            description = "Retrieve the transfer line record associated with the specified transaction."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved the transfer line",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TransactionLineTransferDTO.class))),
            @ApiResponse(responseCode = "404", description = "Transfer line not found for this transaction",
                    content = @Content)
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<TransactionLineTransferDTO>> getTransferLine(
            @Parameter(description = "Unique identifier of the transaction", required = true)
            @PathVariable UUID transactionId
    ) {
        return service.getTransferLine(transactionId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Create Transfer Line",
            description = "Create a new transfer line record associated with the specified transaction."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Transfer line record created successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TransactionLineTransferDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid transfer line data provided",
                    content = @Content)
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<TransactionLineTransferDTO>> createTransferLine(
            @Parameter(description = "Unique identifier of the transaction", required = true)
            @PathVariable UUID transactionId,

            @Parameter(description = "Data for the new transfer line record", required = true,
                    schema = @Schema(implementation = TransactionLineTransferDTO.class))
            @RequestBody TransactionLineTransferDTO transferDTO
    ) {
        return service.createTransferLine(transactionId, transferDTO)
                .map(createdLine -> ResponseEntity.status(201).body(createdLine))
                .defaultIfEmpty(ResponseEntity.badRequest().build());
    }

    @Operation(
            summary = "Update Transfer Line",
            description = "Update an existing transfer line record for the specified transaction."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transfer line record updated successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TransactionLineTransferDTO.class))),
            @ApiResponse(responseCode = "404", description = "Transfer line record not found for this transaction",
                    content = @Content)
    })
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<TransactionLineTransferDTO>> updateTransferLine(
            @Parameter(description = "Unique identifier of the transaction", required = true)
            @PathVariable UUID transactionId,

            @Parameter(description = "Updated data for the transfer line record", required = true,
                    schema = @Schema(implementation = TransactionLineTransferDTO.class))
            @RequestBody TransactionLineTransferDTO transferDTO
    ) {
        return service.updateTransferLine(transactionId, transferDTO)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Delete Transfer Line",
            description = "Remove an existing transfer line record from the specified transaction."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Transfer line record deleted successfully",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Transfer line record not found for this transaction",
                    content = @Content)
    })
    @DeleteMapping
    public Mono<ResponseEntity<Void>> deleteTransferLine(
            @Parameter(description = "Unique identifier of the transaction", required = true)
            @PathVariable UUID transactionId
    ) {
        return service.deleteTransferLine(transactionId)
                .then(Mono.just(ResponseEntity.noContent().build()));
    }
}
