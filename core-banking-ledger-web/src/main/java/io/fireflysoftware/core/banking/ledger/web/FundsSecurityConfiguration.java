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

package io.fireflysoftware.core.banking.ledger.web;

import org.fireflyframework.security.rs.AccessTokenValidators;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
public class FundsSecurityConfiguration {
    @Bean
    @Order(-100)
    SecurityWebFilterChain managedFundsSecurity(
            ServerHttpSecurity http,
            @Value("${funds.security.issuer:}") String issuer,
            @Value("${funds.security.audience:}") String audience,
            @Value("${funds.security.jwk-set-uri:}") String jwks) {
        ReactiveJwtDecoder decoder;
        if (issuer.isBlank() || audience.isBlank() || jwks.isBlank()) {
            decoder =
                    token -> Mono.error(new JwtException("Managed funds trust is not configured"));
        } else {
            var configured = NimbusReactiveJwtDecoder.withJwkSetUri(jwks).build();
            configured.setJwtValidator(
                    AccessTokenValidators.forIssuerAndAudience(issuer, audience));
            decoder = configured;
        }
        return http.securityMatcher(
                        ServerWebExchangeMatchers.pathMatchers("/api/v1/tenants/*/funds/**"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(e -> e.anyExchange().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtDecoder(decoder)))
                .build();
    }
}
