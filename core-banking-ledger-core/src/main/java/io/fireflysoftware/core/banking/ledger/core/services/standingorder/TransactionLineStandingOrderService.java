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


package io.fireflysoftware.core.banking.ledger.core.services.standingorder;

import java.util.UUID;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.standingorder.TransactionLineStandingOrderDTO;
import reactor.core.publisher.Mono;

public interface TransactionLineStandingOrderService {

    /**
     * Retrieve the standing order line for the specified transaction.
     */
    Mono<TransactionLineStandingOrderDTO> getStandingOrderLine(UUID transactionId);

    /**
     * Create a new standing order line record for a specified transaction.
     */
    Mono<TransactionLineStandingOrderDTO> createStandingOrderLine(UUID transactionId, TransactionLineStandingOrderDTO standingOrderDTO);

    /**
     * Update an existing standing order line for the specified transaction.
     */
    Mono<TransactionLineStandingOrderDTO> updateStandingOrderLine(UUID transactionId, TransactionLineStandingOrderDTO standingOrderDTO);

    /**
     * Delete the standing order line record for the specified transaction.
     */
    Mono<Void> deleteStandingOrderLine(UUID transactionId);
}
