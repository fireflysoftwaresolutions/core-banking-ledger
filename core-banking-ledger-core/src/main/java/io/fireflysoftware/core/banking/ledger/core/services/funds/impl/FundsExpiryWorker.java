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

package io.fireflysoftware.core.banking.ledger.core.services.funds.impl;

import static io.fireflysoftware.core.banking.ledger.core.services.funds.impl.FundsErrors.fail;

import io.fireflysoftware.core.banking.ledger.core.services.funds.FundsMoneyService;
import io.fireflysoftware.core.banking.ledger.core.services.funds.FundsExpiryCandidateService;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.*;
import io.fireflysoftware.core.banking.ledger.models.repositories.funds.FundsRepositories;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
public final class FundsExpiryWorker {
    private final FundsExpiryCandidateService candidates;
    private final FundsMoneyService money;
    private final TransactionalOperator transaction;

    public FundsExpiryWorker(
            FundsRepositories repos,
            FundsDatabaseContext context,
            FundsMoneyService money,
            ReactiveTransactionManager manager) {
        this.candidates = new FundsExpiryCandidateServiceImpl(repos, context);
        this.money = money;
        this.transaction = TransactionalOperator.create(manager);
    }

    public Mono<List<Reservation>> expire(SecurityPrincipal p, String tenant, ExpiryBatch input) {
        return Mono.defer(
                () -> {
                    FundsPolicy.require(p, tenant, "funds:expire");
                    if (input == null
                            || input.limit() == null
                            || input.limit() < 1
                            || input.limit() > 100)
                        return Mono.error(fail(400, "BOUNDED_EXPIRY_BATCH_REQUIRED"));
                    return candidates.select(p, tenant, input)
                            .as(transaction::transactional)
                            .flatMapMany(
                                    ids ->
                                            Flux.fromIterable(ids)
                                                    .concatMap(id -> expireOne(p, tenant, id)))
                            .collectList();
                });
    }

    private Mono<Reservation> expireOne(SecurityPrincipal p, String tenant, UUID id) {
        UUID action =
                UUID.nameUUIDFromBytes(
                        ("funds-expiry:" + tenant + ":" + id).getBytes(StandardCharsets.UTF_8));
        return money.action(p, tenant, id, new Command(action, Action.EXPIRE))
                .onErrorResume(
                        org.springframework.web.server.ResponseStatusException.class,
                        error ->
                                error.getStatusCode().value() == 409
                                                && "RESERVATION_IS_TERMINAL"
                                                        .equals(error.getReason())
                                        ? Mono.empty()
                                        : Mono.error(error));
    }
}
