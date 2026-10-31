package io.fireflysoftware.core.banking.ledger.web.controllers.funds;

import static org.junit.jupiter.api.Assertions.*;
import io.fireflysoftware.core.banking.ledger.core.services.funds.*;
import io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.*;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.*;
import io.fireflysoftware.core.banking.ledger.web.funds.impl.LedgerFundsReserveBudgetFilter;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class FundsFirstReserveDispatchTest {
    @SuppressWarnings("unchecked") static <T>T proxy(Class<T> type,java.lang.reflect.InvocationHandler call){return(T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},call);}
    static FundsController controller(FundsMoneyService money,LedgerOwningMutationService first,boolean enabled)throws Exception {
        try{var factory=new DefaultListableBeanFactory();if(first!=null)factory.registerSingleton("first",first);
            return FundsController.class.getConstructor(FundsAuthority.class,FundsMoneyService.class,
                io.fireflysoftware.core.banking.ledger.core.services.funds.impl.FundsExpiryWorker.class,FundsReconciliationService.class,
                boolean.class,org.springframework.beans.factory.ObjectProvider.class).newInstance(null,money,null,null,enabled,factory.getBeanProvider(LedgerOwningMutationService.class));}
        catch(NoSuchMethodException baseline){return new FundsController(null,money,null,null);}
    }
    @SuppressWarnings("unchecked") static Mono<Reservation> reserve(FundsController controller,Object principal,MockServerWebExchange exchange)throws Exception {
        try{return(Mono<Reservation>)FundsController.class.getMethod("reserve",Object.class,String.class,Reserve.class,org.springframework.web.server.ServerWebExchange.class).invoke(controller,principal,"tenant",null,exchange);}
        catch(NoSuchMethodException baseline){return(Mono<Reservation>)FundsController.class.getMethod("reserve",Object.class,String.class,Reserve.class).invoke(controller,principal,"tenant",null);}
    }
    @Test void enabledDispatchUsesOnlyFirstCaptureWithSameExchangeBudgetAndGenuinePrincipal()throws Exception {
        var legacy=new AtomicInteger();var calls=new AtomicInteger();var captured=new AtomicReference<R2dbcOperationBudget>();
        var principal=SecurityPrincipal.builder().subject("subject").issuer("https://issuer.example").tenantId("tenant").build();
        var money=proxy(FundsMoneyService.class,(o,m,a)->{legacy.incrementAndGet();return Mono.empty();});
        var first=proxy(LedgerOwningMutationService.class,(o,m,a)->{assertEquals("reserveFirstCapture",m.getName());assertSame(principal,a[1]);assertEquals("tenant",a[2]);calls.incrementAndGet();captured.set((R2dbcOperationBudget)a[0]);return Mono.empty();});
        var controller=controller(money,first,true);var exchange=MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/tenants/tenant/funds/reservations"));
        var filter=new LedgerFundsReserveBudgetFilter(new LedgerMutationTime(Clock.fixed(Instant.now(),ZoneOffset.UTC),System::nanoTime));
        filter.filter(exchange,e->{try{var budget=LedgerFundsReserveBudgetFilter.requireBudget(e);return reserve(controller,principal,exchange).doOnSuccess(v->assertSame(budget,captured.get())).then();}catch(Exception error){return Mono.error(error);}}).block();
        assertEquals(1,calls.get());assertEquals(0,legacy.get());
    }
    @Test void disabledDispatchRetainsTheOriginalMoneyPathWithoutNewDependencies()throws Exception {
        var legacy=new AtomicInteger();var money=proxy(FundsMoneyService.class,(o,m,a)->{assertEquals("reserve",m.getName());legacy.incrementAndGet();return Mono.empty();});
        reserve(controller(money,null,false),null,MockServerWebExchange.from(MockServerHttpRequest.post("/unrelated"))).block();assertEquals(1,legacy.get());
    }
    @Test void enabledDispatchDoesNotAcquireAReplacementBudgetWhenIngressIsAbsent()throws Exception {
        var legacy=new AtomicInteger();var calls=new AtomicInteger();var money=proxy(FundsMoneyService.class,(o,m,a)->{legacy.incrementAndGet();return Mono.empty();});
        var first=proxy(LedgerOwningMutationService.class,(o,m,a)->{calls.incrementAndGet();return Mono.empty();});
        assertThrows(RuntimeException.class,()->reserve(controller(money,first,true),null,MockServerWebExchange.from(MockServerHttpRequest.post("/unrelated"))).block());
        assertEquals(0,calls.get());assertEquals(0,legacy.get());
    }
    @Test void enabledControllerCannotStartWithoutTheOwningService() {assertThrows(Exception.class,()->controller(null,null,true));}
}
