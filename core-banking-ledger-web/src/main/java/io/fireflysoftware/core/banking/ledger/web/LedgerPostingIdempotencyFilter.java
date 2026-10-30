package io.fireflysoftware.core.banking.ledger.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LedgerPostingIdempotencyFilter implements WebFilter {
    private final String cacheHeader;

    public LedgerPostingIdempotencyFilter(@Value("${firefly.web.idempotency.header-name:X-Idempotency-Key}") String cacheHeader) {
        this.cacheHeader = cacheHeader;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.equals("/api/v1/ledger-postings") || path.startsWith("/api/v1/ledger-postings/")) {
            // A key-only HTTP response cache must never bypass the transactional input claim.
            return chain.filter(exchange.mutate().request(exchange.getRequest().mutate()
                    .headers(headers -> headers.remove(cacheHeader)).build()).build());
        }
        return chain.filter(exchange);
    }
}
