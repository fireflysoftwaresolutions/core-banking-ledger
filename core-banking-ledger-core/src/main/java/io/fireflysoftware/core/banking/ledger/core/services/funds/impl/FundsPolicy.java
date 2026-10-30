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

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;



public final class FundsPolicy {
    private FundsPolicy() {}

    public static void require(
            SecurityPrincipal principal, String tenant, String scope, UUID... accounts) {
        if (principal == null || principal.subject() == null || principal.subject().isBlank())
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "TRUSTED_FUNDS_CONTEXT_REQUIRED");
        Object expiration = principal.claims().get("exp");
        if (!(expiration instanceof Instant instant) || !instant.isAfter(Instant.now()))
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "FINITE_FUNDS_TOKEN_REQUIRED");
        if (tenant == null
                || !tenant.matches("[A-Za-z0-9_-]{1,64}")
                || !tenant.equals(principal.tenantId())
                || !principal.hasScope(scope))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "FUNDS_POLICY_DENIED");
        Object grants = principal.claims().get("funds_accounts");
        for (UUID account : accounts) {
            if (account == null
                    || !(grants instanceof List<?> list)
                    || !list.contains(account.toString()))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "FUNDS_RESOURCE_DENIED");
        }
    }

    static UUID[] grantedAccounts(SecurityPrincipal p) {
        Object value = p.claims().get("funds_accounts");
        if (!(value instanceof List<?> grants) || grants.isEmpty() || grants.size() > 1000)
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "BOUNDED_FUNDS_RESOURCES_REQUIRED");
        try {
            return grants.stream()
                    .map(Object::toString)
                    .map(UUID::fromString)
                    .distinct()
                    .toArray(UUID[]::new);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "VALID_FUNDS_RESOURCES_REQUIRED");
        }
    }

    static void enrollment(
            SecurityPrincipal p,
            String tenant,
            UUID account,
            UUID contract,
            String currency,
            String kind) {
        require(p, tenant, "funds:tenant-bind", account);
        Object value = p.claims().get("funds_enrollments");
        boolean approved =
                value instanceof List<?> approvals
                        && approvals.stream()
                                .anyMatch(
                                        item ->
                                                item instanceof java.util.Map<?, ?> assertion
                                                        && tenant.equals(assertion.get("tenantId"))
                                                        && account.toString()
                                                                .equals(assertion.get("accountId"))
                                                        && contract.toString()
                                                                .equals(assertion.get("contractId"))
                                                        && currency.equals(
                                                                assertion.get("currency"))
                                                        && kind.equals(assertion.get("kind")));
        if (!approved)
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "ISSUER_APPROVED_TENANT_ENROLLMENT_REQUIRED");
    }
}
