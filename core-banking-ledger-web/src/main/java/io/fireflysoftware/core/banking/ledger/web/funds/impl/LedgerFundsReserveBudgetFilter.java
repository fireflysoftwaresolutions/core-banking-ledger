/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.web.funds.impl;

import io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.LedgerMutationTime;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.List;
import org.fireflyframework.web.idempotency.config.IdempotencyProperties;
import java.util.concurrent.TimeoutException;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

/** The original technical window is exchange-owned and begins before authentication or body reads. */
public final class LedgerFundsReserveBudgetFilter implements WebFilter,Ordered {
    private static final String SCOPE=LedgerFundsReserveBudgetFilter.class.getName()+".original";
    private static final PathPattern PATH=PathPatternParser.defaultInstance.parse("/api/v1/tenants/{tenant}/funds/reservations");
    private static final byte[] UNAVAILABLE="{\"code\":\"LEDGER_FIRST_RESERVE_UNAVAILABLE\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CACHE_KEY_REFUSED="{\"code\":\"LEDGER_FIRST_RESERVE_CACHE_KEY_REFUSED\"}".getBytes(StandardCharsets.UTF_8);
    private final LedgerMutationTime time;
    private final List<IdempotencyProperties> responseCaches;
    public LedgerFundsReserveBudgetFilter(LedgerMutationTime time){this(time,List.of());}
    public LedgerFundsReserveBudgetFilter(LedgerMutationTime time,List<IdempotencyProperties> responseCaches){
        this.time=Objects.requireNonNull(time);this.responseCaches=List.copyOf(responseCaches);
    }
    @Override public int getOrder(){return Ordered.HIGHEST_PRECEDENCE;}
    @Override public Mono<Void> filter(ServerWebExchange exchange,WebFilterChain chain){
        if(exchange.getRequest().getMethod()!=HttpMethod.POST||!PATH.matches(exchange.getRequest().getPath().pathWithinApplication()))return chain.filter(exchange);
        final Scope scope;
        try{scope=(Scope)exchange.getAttributes().computeIfAbsent(SCOPE,key->new Scope(time.originalBudget()));}
        catch(RuntimeException error){return unavailable(exchange,null);}
        exchange.getResponse().getHeaders().setCacheControl("no-store");
        for(var cache:responseCaches)if(cache.isEnabled()&&exchange.getRequest().getHeaders().get(cache.getHeaderName())!=null){
            var response=exchange.getResponse();response.setStatusCode(HttpStatus.BAD_REQUEST);response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(CACHE_KEY_REFUSED)));
        }
        exchange.getResponse().beforeCommit(()->Mono.defer(()->{scope.requireCommit();return Mono.empty();}));
        return Mono.defer(()->{
            scope.requireCurrent();
            return Mono.firstWithSignal(Mono.defer(()->chain.filter(exchange)).doOnSuccess(ignored->scope.requireCurrent()),scope.expiration());
        }).onErrorResume(error->error instanceof Expired||error instanceof TimeoutException?unavailable(exchange,scope):Mono.error(error));
    }
    public static R2dbcOperationBudget requireBudget(ServerWebExchange exchange){
        var value=exchange.getAttribute(SCOPE);
        if(!(value instanceof Scope scope))throw new Expired();scope.requireCurrent();return scope.original;
    }
    private static Mono<Void> unavailable(ServerWebExchange exchange,Scope scope){
        var response=exchange.getResponse();
        if(response.isCommitted())return Mono.error(new Expired());
        if(scope!=null)scope.failureResponse=true;
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);response.getHeaders().setCacheControl("no-store");
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);response.getHeaders().setContentLength(UNAVAILABLE.length);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(UNAVAILABLE)));
    }
    private static final class Expired extends IllegalStateException {
        private static final long serialVersionUID=1L;
        private Expired(){super("Original FIRST reserve ingress unavailable");}
    }
    private static final class Scope {
        private final R2dbcOperationBudget original;private volatile boolean failureResponse;
        private Scope(R2dbcOperationBudget original){this.original=original;}
        private void requireCurrent(){
            if(failureResponse)throw new Expired();
            try{original.remaining();}catch(RuntimeException error){throw new Expired();}
        }
        private void requireCommit(){if(!failureResponse)requireCurrent();}
        private Mono<Void> expiration(){return Mono.defer(()->{
            try{return Mono.delay(original.remaining()).then(Mono.error(new Expired()));}
            catch(RuntimeException error){return Mono.error(new Expired());}
        });}
    }
}
