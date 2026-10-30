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


package io.fireflysoftware.core.banking.ledger.core.services.directdebit;

import java.util.UUID;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.directdebit.TransactionLineDirectDebitDTO;
import reactor.core.publisher.Mono;

public interface TransactionLineDirectDebitService {

    /**
     * Retrieve the transaction line direct debit for the specified transaction.
     */
    Mono<TransactionLineDirectDebitDTO> getDirectDebitLine(UUID transactionId);

    /**
     * Create a new direct debit line record for the specified transaction.
     */
    Mono<TransactionLineDirectDebitDTO> createDirectDebitLine(UUID transactionId, TransactionLineDirectDebitDTO directDebitDTO);

    /**
     * Update an existing direct debit line for the specified transaction.
     */
    Mono<TransactionLineDirectDebitDTO> updateDirectDebitLine(UUID transactionId, TransactionLineDirectDebitDTO directDebitDTO);

    /**
     * Delete the direct debit line record for the specified transaction.
     */
    Mono<Void> deleteDirectDebitLine(UUID transactionId);
}

