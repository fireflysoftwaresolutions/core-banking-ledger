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


package io.fireflysoftware.core.banking.ledger.web.controllers.directdebit;

import java.util.UUID;

import io.fireflysoftware.core.banking.ledger.core.services.directdebit.impl.TransactionLineDirectDebitServiceImpl;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.directdebit.TransactionLineDirectDebitDTO;
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

@Tag(name = "Transaction Line Direct Debit", description = "APIs for managing direct debit lines associated with a specific transaction")
@RestController
@RequestMapping("/api/v1/transactions/{transactionId}/line-direct-debit")
public class TransactionLineDirectDebitController {

    @Autowired
    private TransactionLineDirectDebitServiceImpl service;

    @Operation(
            summary = "Get Direct Debit Line",
            description = "Retrieve the direct debit line record associated with the specified transaction."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved the direct debit line",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TransactionLineDirectDebitDTO.class))),
            @ApiResponse(responseCode = "404", description = "Direct debit line not found for this transaction",
                    content = @Content)
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<TransactionLineDirectDebitDTO>> getDirectDebitLine(
            @Parameter(description = "Unique identifier of the transaction", required = true)
            @PathVariable UUID transactionId
    ) {
        return service.getDirectDebitLine(transactionId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Create Direct Debit Line",
            description = "Create a new direct debit line record associated with the specified transaction."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Direct debit line created successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TransactionLineDirectDebitDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid direct debit line data provided",
                    content = @Content)
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<TransactionLineDirectDebitDTO>> createDirectDebitLine(
            @Parameter(description = "Unique identifier of the transaction", required = true)
            @PathVariable UUID transactionId,

            @Parameter(description = "Data for the new direct debit line record", required = true,
                    schema = @Schema(implementation = TransactionLineDirectDebitDTO.class))
            @RequestBody TransactionLineDirectDebitDTO directDebitDTO
    ) {
        return service.createDirectDebitLine(transactionId, directDebitDTO)
                .map(createdDebitLine -> ResponseEntity.status(201).body(createdDebitLine))
                .defaultIfEmpty(ResponseEntity.badRequest().build());
    }

    @Operation(
            summary = "Update Direct Debit Line",
            description = "Update an existing direct debit line record associated with the specified transaction."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Direct debit line updated successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TransactionLineDirectDebitDTO.class))),
            @ApiResponse(responseCode = "404", description = "Direct debit line not found for this transaction",
                    content = @Content)
    })
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<TransactionLineDirectDebitDTO>> updateDirectDebitLine(
            @Parameter(description = "Unique identifier of the transaction", required = true)
            @PathVariable UUID transactionId,

            @Parameter(description = "Updated data for the direct debit line record", required = true,
                    schema = @Schema(implementation = TransactionLineDirectDebitDTO.class))
            @RequestBody TransactionLineDirectDebitDTO directDebitDTO
    ) {
        return service.updateDirectDebitLine(transactionId, directDebitDTO)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Delete Direct Debit Line",
            description = "Remove an existing direct debit line record from the specified transaction."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Direct debit line record deleted successfully",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Direct debit line record not found for this transaction",
                    content = @Content)
    })
    @DeleteMapping
    public Mono<ResponseEntity<Void>> deleteDirectDebitLine(
            @Parameter(description = "Unique identifier of the transaction", required = true)
            @PathVariable("transactionId") UUID transactionId
    ) {
        return service.deleteDirectDebitLine(transactionId)
                .then(Mono.just(ResponseEntity.noContent().build()));
    }
}