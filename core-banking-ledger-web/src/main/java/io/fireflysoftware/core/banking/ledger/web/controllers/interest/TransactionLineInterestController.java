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


package io.fireflysoftware.core.banking.ledger.web.controllers.interest;

import java.util.UUID;

import io.fireflysoftware.core.banking.ledger.core.services.interest.impl.TransactionLineInterestServiceImpl;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.interest.TransactionLineInterestDTO;
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

@Tag(name = "Transaction Line Interest", description = "APIs for managing interest lines associated with a specific transaction")
@RestController
@RequestMapping("/api/v1/transactions/{transactionId}/line-interest")
public class TransactionLineInterestController {

    @Autowired
    private TransactionLineInterestServiceImpl service;

    @Operation(
            summary = "Get Interest Line",
            description = "Retrieve the interest line record associated with the specified transaction."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved the interest line",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TransactionLineInterestDTO.class))),
            @ApiResponse(responseCode = "404", description = "Interest line not found for this transaction",
                    content = @Content)
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<TransactionLineInterestDTO>> getInterestLine(
            @Parameter(description = "Unique identifier of the transaction", required = true)
            @PathVariable UUID transactionId
    ) {
        return service.getInterestLine(transactionId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Create Interest Line",
            description = "Create a new interest line record associated with the specified transaction."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Interest line record created successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TransactionLineInterestDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid interest line data provided",
                    content = @Content)
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<TransactionLineInterestDTO>> createInterestLine(
            @Parameter(description = "Unique identifier of the transaction", required = true)
            @PathVariable UUID transactionId,

            @Parameter(description = "Data for the new interest line record", required = true,
                    schema = @Schema(implementation = TransactionLineInterestDTO.class))
            @RequestBody TransactionLineInterestDTO interestDTO
    ) {
        return service.createInterestLine(transactionId, interestDTO)
                .map(createdLine -> ResponseEntity.status(201).body(createdLine))
                .defaultIfEmpty(ResponseEntity.badRequest().build());
    }

    @Operation(
            summary = "Update Interest Line",
            description = "Update an existing interest line record for the specified transaction."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Interest line record updated successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TransactionLineInterestDTO.class))),
            @ApiResponse(responseCode = "404", description = "Interest line record not found for this transaction",
                    content = @Content)
    })
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<TransactionLineInterestDTO>> updateInterestLine(
            @Parameter(description = "Unique identifier of the transaction", required = true)
            @PathVariable UUID transactionId,

            @Parameter(description = "Updated data for the interest line record", required = true,
                    schema = @Schema(implementation = TransactionLineInterestDTO.class))
            @RequestBody TransactionLineInterestDTO interestDTO
    ) {
        return service.updateInterestLine(transactionId, interestDTO)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Delete Interest Line",
            description = "Remove an existing interest line record from the specified transaction."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Interest line record deleted successfully",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Interest line record not found for this transaction",
                    content = @Content)
    })
    @DeleteMapping
    public Mono<ResponseEntity<Void>> deleteInterestLine(
            @Parameter(description = "Unique identifier of the transaction", required = true)
            @PathVariable UUID transactionId
    ) {
        return service.deleteInterestLine(transactionId)
                .then(Mono.just(ResponseEntity.noContent().build()));
    }
}
