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


package io.fireflysoftware.core.banking.ledger.web.controllers.card;

import java.util.UUID;

import io.fireflysoftware.core.banking.ledger.core.services.card.impl.TransactionLineCardServiceImpl;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.card.TransactionLineCardDTO;
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

@Tag(name = "Transaction Line Card", description = "APIs for managing card line records associated with a specific transaction")
@RestController
@RequestMapping("/api/v1/transactions/{transactionId}/line-card")
public class TransactionLineCardController {

    @Autowired
    private TransactionLineCardServiceImpl service;

    @Operation(
            summary = "Get Card Line",
            description = "Retrieve the card line record associated with the specified transaction."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved the card line",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TransactionLineCardDTO.class))),
            @ApiResponse(responseCode = "404", description = "Card line not found for this transaction",
                    content = @Content)
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<TransactionLineCardDTO>> getCardLine(
            @Parameter(description = "Unique identifier of the transaction", required = true)
            @PathVariable UUID transactionId
    ) {
        return service.getCardLine(transactionId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Create Card Line",
            description = "Create a new card line record associated with the specified transaction."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Card line created successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TransactionLineCardDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid card line data provided",
                    content = @Content)
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<TransactionLineCardDTO>> createCardLine(
            @Parameter(description = "Unique identifier of the transaction", required = true)
            @PathVariable UUID transactionId,

            @Parameter(description = "Data for the new card line record", required = true,
                    schema = @Schema(implementation = TransactionLineCardDTO.class))
            @RequestBody TransactionLineCardDTO cardDTO
    ) {
        return service.createCardLine(transactionId, cardDTO)
                .map(createdCardLine -> ResponseEntity.status(201).body(createdCardLine))
                .defaultIfEmpty(ResponseEntity.badRequest().build());
    }

    @Operation(
            summary = "Update Card Line",
            description = "Update an existing card line record associated with the specified transaction."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Card line updated successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TransactionLineCardDTO.class))),
            @ApiResponse(responseCode = "404", description = "Card line not found for this transaction",
                    content = @Content)
    })
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<TransactionLineCardDTO>> updateCardLine(
            @Parameter(description = "Unique identifier of the transaction", required = true)
            @PathVariable UUID transactionId,

            @Parameter(description = "Updated card line data", required = true,
                    schema = @Schema(implementation = TransactionLineCardDTO.class))
            @RequestBody TransactionLineCardDTO cardDTO
    ) {
        return service.updateCardLine(transactionId, cardDTO)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Delete Card Line",
            description = "Remove an existing card line record from a specific transaction."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Card line record deleted successfully",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Card line record not found for this transaction",
                    content = @Content)
    })
    @DeleteMapping
    public Mono<ResponseEntity<Void>> deleteCardLine(
            @Parameter(description = "Unique identifier of the transaction", required = true)
            @PathVariable("transactionId") UUID transactionId
    ) {
        return service.deleteCardLine(transactionId)
                .then(Mono.just(ResponseEntity.noContent().build()));
    }
}