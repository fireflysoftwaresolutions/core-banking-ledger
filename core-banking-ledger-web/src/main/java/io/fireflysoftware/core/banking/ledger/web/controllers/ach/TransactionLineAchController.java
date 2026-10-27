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


package io.fireflysoftware.core.banking.ledger.web.controllers.ach;

import io.fireflysoftware.core.banking.ledger.core.services.ach.impl.TransactionLineAchServiceImpl;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.ach.TransactionLineAchDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Tag(name = "Transaction Line ACH", description = "APIs for managing ACH lines associated with a specific transaction")
@RestController
@RequestMapping("/api/v1/transactions/{transactionId}/line-ach")
public class TransactionLineAchController {

    @Autowired
    private TransactionLineAchServiceImpl service;

    @Operation(
            summary = "Get ACH Line",
            description = "Retrieve the ACH line record associated with the specified transaction."
    )
    @ApiResponse(
            responseCode = "200",
            description = "ACH line retrieved successfully"
    )
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<TransactionLineAchDTO>> getAchLine(
            @Parameter(description = "Transaction ID", required = true)
            @PathVariable UUID transactionId
    ) {
        return service.getAchLine(transactionId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Create ACH Line",
            description = "Create a new ACH line record for the specified transaction."
    )
    @ApiResponse(
            responseCode = "201",
            description = "ACH line created successfully"
    )
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<TransactionLineAchDTO>> createAchLine(
            @Parameter(description = "Transaction ID", required = true)
            @PathVariable UUID transactionId,
            @Valid @RequestBody TransactionLineAchDTO achDTO
    ) {
        return service.createAchLine(transactionId, achDTO)
                .map(created -> ResponseEntity.status(HttpStatus.CREATED).body(created));
    }

    @Operation(
            summary = "Update ACH Line",
            description = "Update an existing ACH line for the specified transaction."
    )
    @ApiResponse(
            responseCode = "200",
            description = "ACH line updated successfully"
    )
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<TransactionLineAchDTO>> updateAchLine(
            @Parameter(description = "Transaction ID", required = true)
            @PathVariable UUID transactionId,
            @Valid @RequestBody TransactionLineAchDTO achDTO
    ) {
        return service.updateAchLine(transactionId, achDTO)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Delete ACH Line",
            description = "Delete the ACH line record for the specified transaction."
    )
    @ApiResponse(
            responseCode = "204",
            description = "ACH line deleted successfully"
    )
    @DeleteMapping
    public Mono<ResponseEntity<Void>> deleteAchLine(
            @Parameter(description = "Transaction ID", required = true)
            @PathVariable UUID transactionId
    ) {
        return service.deleteAchLine(transactionId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
