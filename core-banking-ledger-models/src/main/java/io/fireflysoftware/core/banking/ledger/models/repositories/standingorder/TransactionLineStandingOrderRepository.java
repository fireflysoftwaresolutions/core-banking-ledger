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


package io.fireflysoftware.core.banking.ledger.models.repositories.standingorder;

import io.fireflysoftware.core.banking.ledger.interfaces.enums.standingorder.StandingOrderFrequencyEnum;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.standingorder.StandingOrderStatusEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.standingorder.TransactionLineStandingOrder;
import io.fireflysoftware.core.banking.ledger.models.repositories.BaseRepository;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDate;

public interface TransactionLineStandingOrderRepository extends BaseRepository<TransactionLineStandingOrder, UUID>, TransactionLineStandingOrderFilters {
    Mono<TransactionLineStandingOrder> findByTransactionId(UUID transactionId);

    Mono<TransactionLineStandingOrder> findByStandingOrderId(String standingOrderId);

    Flux<TransactionLineStandingOrder> findByStandingOrderStatus(StandingOrderStatusEnum status, Pageable pageable);

    

    Flux<TransactionLineStandingOrder> findByStandingOrderFrequency(StandingOrderFrequencyEnum frequency, Pageable pageable);

    

    

    

    

    

    

    

    

    

    

    

    

    
}