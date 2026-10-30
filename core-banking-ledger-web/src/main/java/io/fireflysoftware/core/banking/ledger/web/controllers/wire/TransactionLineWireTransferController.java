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


package io.fireflysoftware.core.banking.ledger.web.controllers.wire;

import java.util.UUID;

import io.fireflysoftware.core.banking.ledger.core.services.wire.impl.TransactionLineWireTransferServiceImpl;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.wire.TransactionLineWireTransferDTO;
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

@Tag(name = "Transaction Line Wire Transfer", description = "APIs for managing wire transfer lines associated with a specific transaction")
@RestController
@RequestMapping("/api/v1/transactions/{transactionId}/line-wire-transfer")
public class TransactionLineWireTransferController {

    @Autowired
    private TransactionLineWireTransferServiceImpl service;

    @Operation(
            summary = "Get Wire Transfer Line",
            description = "Retrieve the wire transfer line record associated with the specified transaction."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved the wire transfer line record",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TransactionLineWireTransferDTO.class))),
            @ApiResponse(responseCode = "404", description = "Wire transfer line not found for this transaction",
                    content = @Content)
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<TransactionLineWireTransferDTO>> getWireTransferLine(
            @Parameter(description = "Unique identifier of the transaction", required = true)
            @PathVariable UUID transactionId
    ) {
        return service.getWireTransferLine(transactionId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Create Wire Transfer Line",
            description = "Create a new wire transfer line record associated with the specified transaction."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Wire transfer line record created successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TransactionLineWireTransferDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid wire transfer line data provided",
                    content = @Content)
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<TransactionLineWireTransferDTO>> createWireTransferLine(
            @Parameter(description = "Unique identifier of the transaction", required = true)
            @PathVariable UUID transactionId,

            @Parameter(description = "Data for the new wire transfer line record", required = true,
                    schema = @Schema(implementation = TransactionLineWireTransferDTO.class))
            @RequestBody TransactionLineWireTransferDTO wireDTO
    ) {
        return service.createWireTransferLine(transactionId, wireDTO)
                .map(createdLine -> ResponseEntity.status(201).body(createdLine))
                .defaultIfEmpty(ResponseEntity.badRequest().build());
    }

    @Operation(
            summary = "Update Wire Transfer Line",
            description = "Update an existing wire transfer line record associated with the specified transaction."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Wire transfer line updated successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TransactionLineWireTransferDTO.class))),
            @ApiResponse(responseCode = "404", description = "Wire transfer line record not found for this transaction",
                    content = @Content)
    })
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<TransactionLineWireTransferDTO>> updateWireTransferLine(
            @Parameter(description = "Unique identifier of the transaction", required = true)
            @PathVariable UUID transactionId,

            @Parameter(description = "Updated data for the wire transfer line record", required = true,
                    schema = @Schema(implementation = TransactionLineWireTransferDTO.class))
            @RequestBody TransactionLineWireTransferDTO wireDTO
    ) {
        return service.updateWireTransferLine(transactionId, wireDTO)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Delete Wire Transfer Line",
            description = "Remove an existing wire transfer line record from the specified transaction."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Wire transfer line record deleted successfully",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Wire transfer line record not found for this transaction",
                    content = @Content)
    })
    @DeleteMapping
    public Mono<ResponseEntity<Void>> deleteWireTransferLine(
            @Parameter(description = "Unique identifier of the transaction", required = true)
            @PathVariable UUID transactionId
    ) {
        return service.deleteWireTransferLine(transactionId)
                .then(Mono.just(ResponseEntity.noContent().build()));
    }
}