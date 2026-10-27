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


package io.fireflysoftware.core.banking.ledger.models.repositories.sepa;

import io.fireflysoftware.core.banking.ledger.interfaces.enums.sepa.SepaTransactionStatusEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.sepa.TransactionLineSepaTransfer;
import io.fireflysoftware.core.banking.ledger.models.repositories.BaseRepository;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface TransactionLineSepaRepository extends BaseRepository<TransactionLineSepaTransfer, UUID>, TransactionLineSepaFilters {
    Mono<TransactionLineSepaTransfer> findByTransactionId(UUID transactionId);

    Mono<TransactionLineSepaTransfer> findBySepaEndToEndId(String endToEndId);

    Flux<TransactionLineSepaTransfer> findBySepaCreditorId(String creditorId, Pageable pageable);

    Mono<Long> countBySepaCreditorId(String creditorId);

    Flux<TransactionLineSepaTransfer> findBySepaDebtorId(String debtorId, Pageable pageable);

    Mono<Long> countBySepaDebtorId(String debtorId);

    Flux<TransactionLineSepaTransfer> findBySepaTransactionStatus(SepaTransactionStatusEnum status, Pageable pageable);

    Mono<Long> countBySepaTransactionStatus(SepaTransactionStatusEnum status);

    

    

    

    

    

    


    

    
}