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

import io.fireflysoftware.core.banking.ledger.models.repositories.funds.FundsNativeRepository;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public final class FundsDatabaseContext {
    private final FundsNativeRepository repository;

    public FundsDatabaseContext(FundsNativeRepository repository) {
        this.repository = repository;
    }

    Mono<Void> establish(SecurityPrincipal p, String tenant, String purpose, UUID... accounts) {
        String scope =
                switch (purpose) {
                    case "ENROLL" -> "funds:enroll";
                    case "READ" -> "funds:read";
                    case "CONTROL" -> "funds:control";
                    case "FUNDING" -> "funds:fund";
                    case "RESERVE" -> "funds:reserve";
                    case "COMMIT" -> "funds:commit";
                    case "RELEASE" -> "funds:release";
                    case "EXPIRE" -> "funds:expire";
                    case "REVERSE" -> "funds:reverse";
                    case "RECONCILE" -> "funds:reconcile";
                    default -> throw fail(403, "SERVER_FUNDS_PURPOSE_REQUIRED");
                };
        FundsPolicy.require(p, tenant, scope, accounts);
        Object expiration = p.claims().get("exp");
        if (!(expiration instanceof java.time.Instant expires)
                || expires.isAfter(java.time.Instant.ofEpochMilli(253402300799999L)))
            return Mono.error(fail(401, "FINITE_FUNDS_TOKEN_REQUIRED"));
        if (!boundedText(p.issuer(), 2048)
                || !boundedText(p.subject(), 800)
                || p.subject().codePointCount(0, p.subject().length()) > 200)
            return Mono.error(fail(401, "TRUSTED_FUNDS_CONTEXT_REQUIRED"));
        if (accounts.length < 1 || accounts.length > 1000)
            return Mono.error(fail(403, "BOUNDED_FUNDS_RESOURCES_REQUIRED"));
        String resourceJson =
                Arrays.stream(accounts)
                        .distinct()
                        .map(a -> "\"" + a + "\"")
                        .collect(Collectors.joining(",", "[", "]"));
        return repository
                .runtimeAllowed()
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(fail(503, "NONPRIVILEGED_FUNDS_RUNTIME_REQUIRED")))
                .then(
                        repository.establish(
                                tenant,
                                resourceJson,
                                purpose,
                                p.issuer(),
                                p.subject(),
                                Long.toString(expires.toEpochMilli())));
    }

    private static boolean boundedText(String value, int maximumBytes) {
        return value != null
                && !value.isBlank()
                && value.length() <= maximumBytes
                && java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value)
                && value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= maximumBytes;
    }
}
