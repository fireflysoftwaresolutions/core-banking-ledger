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


package io.fireflysoftware.core.banking.ledger.core.services.money;

import java.util.UUID;

import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.money.MoneyDTO;
import reactor.core.publisher.Mono;

/**
 * Service interface for managing money values.
 */
public interface MoneyService {
    /**
     * Create a new money value.
     *
     * @param moneyDTO The money data.
     * @return The created money value.
     */
    Mono<MoneyDTO> createMoney(MoneyDTO moneyDTO);

    /**
     * Get a specific money value by ID.
     *
     * @param moneyId The ID of the money value.
     * @return The money value.
     */
    Mono<MoneyDTO> getMoney(UUID moneyId);

    /**
     * List all money values.
     *
     * @param paginationRequest Pagination parameters.
     * @return A paginated list of money values.
     */
    Mono<PaginationResponse<MoneyDTO>> listMoney(PaginationRequest paginationRequest);

    /**
     * List all money values with a specific currency.
     *
     * @param currency The currency code.
     * @param paginationRequest Pagination parameters.
     * @return A paginated list of money values.
     */
    Mono<PaginationResponse<MoneyDTO>> listMoneyByCurrency(String currency, PaginationRequest paginationRequest);
}
