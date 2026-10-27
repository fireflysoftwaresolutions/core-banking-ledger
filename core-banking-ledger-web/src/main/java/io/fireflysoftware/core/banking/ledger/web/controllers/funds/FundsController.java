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

package io.fireflysoftware.core.banking.ledger.web.controllers.funds;

import io.fireflysoftware.core.banking.ledger.core.services.funds.FundsAuthority;
import io.fireflysoftware.core.banking.ledger.core.services.funds.impl.FundsExpiryWorker;
import io.fireflysoftware.core.banking.ledger.core.services.funds.FundsMoneyService;
import io.fireflysoftware.core.banking.ledger.core.services.funds.FundsReconciliationService;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.*;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@io.swagger.v3.oas.annotations.tags.Tag(name = "LedgerFunds")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "fundsBearer")
@io.swagger.v3.oas.annotations.security.SecurityScheme(
        name = "fundsBearer",
        type = io.swagger.v3.oas.annotations.enums.SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
@RestController
@RequestMapping("/api/v1/tenants/{tenant}/funds")
public class FundsController {
    private final FundsExpiryWorker expiry;
    private final FundsReconciliationService reconciliation;
    private final FundsAuthority authority;
    private final FundsMoneyService money;

    public FundsController(
            FundsAuthority authority,
            FundsMoneyService money,
            FundsExpiryWorker expiry,
            FundsReconciliationService reconciliation) {
        this.authority = authority;
        this.money = money;
        this.expiry = expiry;
        this.reconciliation = reconciliation;
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "enrollLedgerFunds")
    @PostMapping("/accounts/{accountId}/enrollment")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Account> enroll(
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @AuthenticationPrincipal
                    Jwt jwt,
            @PathVariable String tenant,
            @PathVariable UUID accountId,
            @RequestBody Enrollment input) {
        return authority.enroll(principal(jwt), tenant, accountId, input);
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "controlLedgerFunds")
    @PostMapping("/accounts/{accountId}/controls")
    public Mono<Account> control(
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @AuthenticationPrincipal
                    Jwt jwt,
            @PathVariable String tenant,
            @PathVariable UUID accountId,
            @RequestBody Control input) {
        return authority.control(principal(jwt), tenant, accountId, input);
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "getLedgerFunds")
    @GetMapping("/accounts/{accountId}")
    public Mono<Account> account(
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @AuthenticationPrincipal
                    Jwt jwt,
            @PathVariable String tenant,
            @PathVariable UUID accountId) {
        return authority.account(principal(jwt), tenant, accountId);
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "fundLedgerAccount")
    @PostMapping("/funding")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<FundingReceipt> fund(
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @AuthenticationPrincipal
                    Jwt jwt,
            @PathVariable String tenant,
            @RequestBody Funding input) {
        return money.fund(principal(jwt), tenant, input);
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "reserveFunds")
    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Reservation> reserve(
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @AuthenticationPrincipal
                    Jwt jwt,
            @PathVariable String tenant,
            @RequestBody Reserve input) {
        return money.reserve(principal(jwt), tenant, input);
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "reconcileFundsReservation")
    @PostMapping("/reservations/reconciliation")
    public Mono<Reconciliation> reconcile(
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @AuthenticationPrincipal
                    Jwt jwt,
            @PathVariable String tenant,
            @RequestBody Reserve input) {
        return reconciliation.reconcile(principal(jwt), tenant, input);
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "expireDueFunds")
    @PostMapping("/reservations/expiry")
    public Mono<java.util.List<Reservation>> expire(
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @AuthenticationPrincipal
                    Jwt jwt,
            @PathVariable String tenant,
            @RequestBody ExpiryBatch input) {
        return expiry.expire(principal(jwt), tenant, input);
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "getFundsReservationByOperation")
    @GetMapping("/reservations/by-operation/{operationId}")
    public Mono<Reservation> byOperation(
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @AuthenticationPrincipal
                    Jwt jwt,
            @PathVariable String tenant,
            @PathVariable UUID operationId) {
        return money.reservationByOperation(principal(jwt), tenant, operationId);
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "getFundsReservation")
    @GetMapping("/reservations/{id}")
    public Mono<Reservation> reservation(
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @AuthenticationPrincipal
                    Jwt jwt,
            @PathVariable String tenant,
            @PathVariable UUID id) {
        return money.reservation(principal(jwt), tenant, id);
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "actOnFundsReservation")
    @PostMapping("/reservations/{id}/actions")
    public Mono<Reservation> action(
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @AuthenticationPrincipal
                    Jwt jwt,
            @PathVariable String tenant,
            @PathVariable UUID id,
            @RequestBody Command input) {
        return money.action(principal(jwt), tenant, id, input);
    }

    static SecurityPrincipal principal(Jwt jwt) {
        if (jwt == null) return null;
        String scope = jwt.getClaimAsString("scope");
        Set<String> scopes =
                scope == null
                        ? Set.of()
                        : Arrays.stream(scope.split(" "))
                                .filter(s -> !s.isBlank())
                                .collect(Collectors.toSet());
        return SecurityPrincipal.builder()
                .subject(jwt.getSubject())
                .issuer(jwt.getIssuer().toString())
                .tenantId(jwt.getClaimAsString("tenant_id"))
                .scopes(scopes)
                .claims(jwt.getClaims())
                .build();
    }
}
