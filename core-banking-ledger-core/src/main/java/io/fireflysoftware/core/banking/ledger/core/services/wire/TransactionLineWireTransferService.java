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


package io.fireflysoftware.core.banking.ledger.core.services.wire;

import java.util.UUID;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.wire.TransactionLineWireTransferDTO;
import reactor.core.publisher.Mono;

public interface TransactionLineWireTransferService {

    /**
     * Retrieve the wire transfer line for the specified transaction.
     */
    Mono<TransactionLineWireTransferDTO> getWireTransferLine(UUID transactionId);

    /**
     * Create a new wire transfer line record for the specified transaction.
     */
    Mono<TransactionLineWireTransferDTO> createWireTransferLine(UUID transactionId, TransactionLineWireTransferDTO wireDTO);

    /**
     * Update an existing wire transfer line for the specified transaction.
     */
    Mono<TransactionLineWireTransferDTO> updateWireTransferLine(UUID transactionId, TransactionLineWireTransferDTO wireDTO);

    /**
     * Delete the wire transfer line record for the specified transaction.
     */
    Mono<Void> deleteWireTransferLine(UUID transactionId);
}