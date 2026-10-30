package io.fireflysoftware.core.banking.ledger.web.funds;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.LedgerMutationTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.*;
import org.springframework.web.server.handler.DefaultWebFilterChain;
import reactor.core.publisher.Mono;

class LedgerFundsReserveBudgetFilterTest {
    static final String PATH="/api/v1/tenants/opaque/funds/reservations";
    static final class Time extends Clock {
        final AtomicLong nanos=new AtomicLong(10);Instant now=Instant.parse("2026-01-01T00:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}
        void advance(long seconds){now=now.plusSeconds(seconds);nanos.addAndGet(Duration.ofSeconds(seconds).toNanos());}
    }
    static WebFilter filter(LedgerMutationTime time)throws Exception {
        try{return (WebFilter)Class.forName("io.fireflysoftware.core.banking.ledger.web.funds.impl.LedgerFundsReserveBudgetFilter").getConstructor(LedgerMutationTime.class).newInstance(time);}
        catch(ClassNotFoundException legacy){return (exchange,chain)->chain.filter(exchange);}
    }
    static R2dbcOperationBudget budget(ServerWebExchange exchange)throws Exception {
        try{return (R2dbcOperationBudget)Class.forName("io.fireflysoftware.core.banking.ledger.web.funds.impl.LedgerFundsReserveBudgetFilter").getMethod("requireBudget",ServerWebExchange.class).invoke(null,exchange);}
        catch(ClassNotFoundException legacy){return null;}
    }
    static Mono<Void> success(ServerWebExchange exchange){exchange.getResponse().setStatusCode(HttpStatus.CREATED);return exchange.getResponse().writeWith(Mono.fromSupplier(()->exchange.getResponse().bufferFactory().wrap("{\"reservationId\":\"must-not-leak\"}".getBytes(StandardCharsets.UTF_8))));}
    static void unavailable(MockServerWebExchange exchange){assertEquals(HttpStatus.SERVICE_UNAVAILABLE,exchange.getResponse().getStatusCode());var raw=exchange.getResponse().getBodyAsString().block();assertNotNull(raw);assertTrue(raw.contains("LEDGER_FIRST_RESERVE_UNAVAILABLE"));assertFalse(raw.contains("reservationId"));assertFalse(raw.contains("must-not-leak"));}
    @Test void budgetExistsBeforeTheActualAuthenticationFilterAndUsesTheRequiredTimePair()throws Exception {
        var clock=new Time();var time=new LedgerMutationTime(clock,clock.nanos::get);var exchange=MockServerWebExchange.from(MockServerHttpRequest.post(PATH));var observed=new AtomicReference<R2dbcOperationBudget>();
        WebFilter authentication=(e,chain)->{try{observed.set(budget(e));assertNotNull(observed.get());assertEquals(Duration.ofSeconds(5),observed.get().intersect(time.originalBudget()).remaining());}catch(Exception error){return Mono.error(error);}return chain.filter(e);};
        new DefaultWebFilterChain(e->Mono.empty(),List.of(filter(time),authentication)).filter(exchange).block();assertNotNull(observed.get());
    }
    @Test void actualChainResubscriptionRetainsTheSameOriginalExchangeBudget()throws Exception {
        var clock=new Time();var time=new LedgerMutationTime(clock,clock.nanos::get);var exchange=MockServerWebExchange.from(MockServerHttpRequest.post(PATH));var observed=new ArrayList<R2dbcOperationBudget>();
        var chain=new DefaultWebFilterChain(e->{try{observed.add(budget(e));return Mono.empty();}catch(Exception error){return Mono.error(error);}},List.of(filter(time)));
        var assembled=chain.filter(exchange);assembled.block();clock.advance(4);assembled.block();assertNotNull(observed.get(0));assertSame(observed.get(0),observed.get(1));assertEquals(Duration.ofSeconds(1),observed.get(1).remaining());
        clock.advance(1);assembled.block();assertEquals(2,observed.size());unavailable(exchange);
    }
    @Test void delayedSubscriptionCannotCreateANewWindowAfterTheFilterHasObservedIngress()throws Exception {
        var clock=new Time();var time=new LedgerMutationTime(clock,clock.nanos::get);var exchange=MockServerWebExchange.from(MockServerHttpRequest.post(PATH));var calls=new AtomicInteger();
        var assembled=filter(time).filter(exchange,e->{calls.incrementAndGet();return success(e);});clock.advance(6);assembled.block();assertEquals(0,calls.get());unavailable(exchange);
    }
    @Test void delayedAuthenticationCannotEmitAQueuedSuccessBodyAfterOriginalExpiry()throws Exception {
        var clock=new Time();var time=new LedgerMutationTime(clock,clock.nanos::get);var exchange=MockServerWebExchange.from(MockServerHttpRequest.post(PATH));
        WebFilter authentication=(e,chain)->Mono.defer(()->{clock.advance(6);return chain.filter(e);});
        new DefaultWebFilterChain(LedgerFundsReserveBudgetFilterTest::success,List.of(filter(time),authentication)).filter(exchange).block();unavailable(exchange);
    }
    @Test void bodyAndSerializationWaitAreRecheckedAtTheRealResponseCommitBoundary()throws Exception {
        var clock=new Time();var time=new LedgerMutationTime(clock,clock.nanos::get);var exchange=MockServerWebExchange.from(MockServerHttpRequest.post(PATH));
        WebHandler delayed=e->{e.getResponse().setStatusCode(HttpStatus.CREATED);return e.getResponse().writeWith(Mono.fromSupplier(()->{clock.advance(6);return e.getResponse().bufferFactory().wrap("{\"reservationId\":\"must-not-leak\"}".getBytes(StandardCharsets.UTF_8));}));};
        new DefaultWebFilterChain(delayed,List.of(filter(time))).filter(exchange).block();unavailable(exchange);
    }
}
