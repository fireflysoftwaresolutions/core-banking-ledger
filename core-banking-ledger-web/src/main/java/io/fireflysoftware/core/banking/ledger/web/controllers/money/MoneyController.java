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


package io.fireflysoftware.core.banking.ledger.web.controllers.money;

import java.util.UUID;

import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.banking.ledger.core.services.money.MoneyService;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.money.MoneyDTO;
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

/**
 * REST controller for managing money values.
 * Provides endpoints for creating, retrieving, and listing money values.
 */
@RestController
@RequestMapping("/api/v1/money")
@Tag(name = "Money", description = "Money management API")
public class MoneyController {

    private final MoneyService moneyService;

    @Autowired
    public MoneyController(MoneyService moneyService) {
        this.moneyService = moneyService;
    }

    /**
     * Creates a new money value.
     *
     * @param moneyDTO The money data
     * @return The created money value
     */
    @PostMapping
    @Operation(summary = "Create money", description = "Creates a new money value")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Money created successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = MoneyDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Mono<ResponseEntity<MoneyDTO>> createMoney(
            @Parameter(description = "Money data", required = true) @RequestBody MoneyDTO moneyDTO) {
        
        return moneyService.createMoney(moneyDTO)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Gets a specific money value by ID.
     *
     * @param moneyId The ID of the money value
     * @return The money value
     */
    @GetMapping("/{moneyId}")
    @Operation(summary = "Get money", description = "Gets a specific money value by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Money retrieved successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = MoneyDTO.class))),
            @ApiResponse(responseCode = "404", description = "Money not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Mono<ResponseEntity<MoneyDTO>> getMoney(
            @Parameter(description = "ID of the money value", required = true) @PathVariable UUID moneyId) {
        
        return moneyService.getMoney(moneyId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Lists all money values.
     *
     * @param page Page number (0-based)
     * @param size Page size
     * @return A paginated list of money values
     */
    @GetMapping
    @Operation(summary = "List money", description = "Lists all money values")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Money list retrieved successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PaginationResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Mono<ResponseEntity<PaginationResponse<MoneyDTO>>> listMoney(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(required = false) String sort,
            @Parameter(description = "Sort direction") @RequestParam(required = false) String direction) {
        
        PaginationRequest paginationRequest = new PaginationRequest(page, size, sort, direction);
        return moneyService.listMoney(paginationRequest)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Lists all money values with a specific currency.
     *
     * @param currency The currency code
     * @param page Page number (0-based)
     * @param size Page size
     * @return A paginated list of money values
     */
    @GetMapping("/currency/{currency}")
    @Operation(summary = "List money by currency", description = "Lists all money values with a specific currency")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Money list retrieved successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PaginationResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Mono<ResponseEntity<PaginationResponse<MoneyDTO>>> listMoneyByCurrency(
            @Parameter(description = "Currency code", required = true) @PathVariable String currency,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(required = false) String sort,
            @Parameter(description = "Sort direction") @RequestParam(required = false) String direction) {
        
        PaginationRequest paginationRequest = new PaginationRequest(page, size, sort, direction);
        return moneyService.listMoneyByCurrency(currency, paginationRequest)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}