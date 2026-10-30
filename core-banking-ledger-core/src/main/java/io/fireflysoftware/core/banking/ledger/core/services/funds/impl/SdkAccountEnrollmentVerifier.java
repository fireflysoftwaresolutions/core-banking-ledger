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
import io.fireflysoftware.core.banking.accounts.sdk.api.AccountFundsApi;
import io.fireflysoftware.core.banking.accounts.sdk.invoker.ApiClient;
import io.fireflysoftware.core.banking.ledger.core.services.funds.AccountEnrollmentVerifier;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.Account;
import org.fireflyframework.client.oauth2.OAuth2ClientHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.net.URI;
import java.time.Duration;
import java.util.Set;






@Component
public class SdkAccountEnrollmentVerifier implements AccountEnrollmentVerifier {
    private final OAuth2ClientHelper oauth;
    private final String baseUrl, scopes;

    public SdkAccountEnrollmentVerifier(
            @Value("${funds.accounts.base-url:}") String baseUrl,
            @Value("${funds.accounts.token-uri:}") String tokenUri,
            @Value("${funds.accounts.client-id:}") String clientId,
            @Value("${funds.accounts.client-secret:}") String clientSecret,
            @Value("${funds.accounts.scopes:}") String scopes,
            @Value("${funds.accounts.allow-loopback-http:false}") boolean allowLoopback) {
        this.baseUrl = baseUrl;
        this.scopes = scopes;
        oauth =
                valid(baseUrl, allowLoopback)
                                && valid(tokenUri, allowLoopback)
                                && !clientId.isBlank()
                                && !clientSecret.isBlank()
                                && !scopes.isBlank()
                        ? new OAuth2ClientHelper(
                                tokenUri,
                                clientId,
                                clientSecret,
                                OAuth2ClientHelper.OAuth2Config.builder()
                                        .timeout(Duration.ofSeconds(4))
                                        .enableRetry(false)
                                        .build())
                        : null;
    }

    public Mono<Void> verify(String tenant, Account account) {
        return Mono.defer(
                        () -> {
                            if (oauth == null)
                                return Mono.error(fail(503, "COMMITTED_ACCOUNTS_PEER_REQUIRED"));
                            return oauth.getClientCredentialsToken(scopes)
                                    .filter(token -> !token.isBlank())
                                    .switchIfEmpty(
                                            Mono.error(
                                                    fail(
                                                            503,
                                                            "ACCOUNTS_SERVICE_TOKEN_UNAVAILABLE")))
                                    .flatMap(
                                            token -> {
                                                var web =
                                                        ApiClient.buildWebClientBuilder()
                                                                .codecs(
                                                                        c ->
                                                                                c.defaultCodecs()
                                                                                        .maxInMemorySize(
                                                                                                1024
                                                                                                        * 1024))
                                                                .build();
                                                var client =
                                                        new ApiClient(web).setBasePath(baseUrl);
                                                client.addDefaultHeader(
                                                        "Authorization", "Bearer " + token);
                                                return new AccountFundsApi(client)
                                                        .verifyFundsEnrollment(
                                                                tenant, account.accountId(), null);
                                            })
                                    .switchIfEmpty(
                                            Mono.error(fail(502, "INCOMPLETE_ACCOUNTS_ENROLLMENT")))
                                    .flatMap(
                                            found -> {
                                                if (!account.accountId()
                                                                .equals(found.getAccountId())
                                                        || !account.enrollmentId()
                                                                .equals(found.getEnrollmentId())
                                                        || !account.contractId()
                                                                .equals(found.getContractId())
                                                        || !account.currency()
                                                                .equals(found.getCurrency())
                                                        || found.getKind() == null
                                                        || !account.kind()
                                                                .name()
                                                                .equals(found.getKind().getValue())
                                                        || found.getMode() == null
                                                        || !"MANAGED"
                                                                .equals(found.getMode().getValue())
                                                        || found.getControl() == null
                                                        || !"ACTIVE"
                                                                .equals(
                                                                        found.getControl()
                                                                                .getValue()))
                                                    return Mono.error(
                                                            fail(
                                                                    409,
                                                                    "COMMITTED_ACCOUNTS_LINK_MISMATCH_OR_BLOCKED"));
                                                return Mono.<Void>empty();
                                            })
                                    .timeout(Duration.ofSeconds(10));
                        })
                .onErrorMap(
                        error ->
                                error
                                                instanceof
                                                org.springframework.web.server
                                                        .ResponseStatusException
                                        ? error
                                        : fail(
                                                503,
                                                "ACCOUNTS_ENROLLMENT_VERIFICATION_UNAVAILABLE"));
    }

    private static boolean valid(String value, boolean loopback) {
        try {
            var uri = URI.create(value);
            return uri.getHost() != null
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null
                    && ("https".equals(uri.getScheme())
                            || (loopback
                                    && "http".equals(uri.getScheme())
                                    && Set.of("localhost", "127.0.0.1", "::1", "[::1]")
                                            .contains(uri.getHost())));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
