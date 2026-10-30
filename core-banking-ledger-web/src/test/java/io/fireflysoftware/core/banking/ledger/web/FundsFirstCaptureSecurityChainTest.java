package io.fireflysoftware.core.banking.ledger.web;

import static org.junit.jupiter.api.Assertions.*;
import io.fireflysoftware.core.banking.ledger.core.services.funds.*;
import io.fireflysoftware.core.banking.ledger.core.services.funds.impl.FundsExpiryWorker;
import io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.*;
import io.fireflysoftware.core.banking.ledger.web.controllers.funds.*;
import io.fireflysoftware.core.banking.ledger.web.funds.impl.LedgerFundsReserveBudgetFilter;
import java.lang.reflect.Proxy;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.webflux.context.ReactorSecurityContextAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.ReactiveAuthenticationManagerResolver;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.reactive.result.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.security.web.server.*;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.*;
import org.springframework.web.server.handler.DefaultWebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** In-memory produced-chain proof. Only its credential decoder is controlled; no JWT/JWKS wire claim. */
class FundsFirstCaptureSecurityChainTest {
    static final String BASE="/api/v1/tenants/tenant/funds";
    static Jwt jwt(Object tenant){var builder=Jwt.withTokenValue("source-token").header("alg","RS256").issuer("https://issuer.example").subject("subject")
        .issuedAt(Instant.now().minusSeconds(1)).expiresAt(Instant.now().plusSeconds(60))
        .claim("scope","funds:read funds:reserve funds:expire funds:fund funds:control funds:confirm funds:release funds:reverse");
        if(tenant!=null)builder.claim("tenant_id",tenant);return builder.build();}
    @SuppressWarnings("unchecked") static SecurityWebFilterChain chain(boolean enabled,Function<String,Mono<Jwt>> decode)throws Exception {
        var chain=new FundsSecurityConfiguration().managedFundsSecurity(ServerHttpSecurity.http(),"https://issuer.example","funds","https://issuer.example/jwks",enabled);
        var authentication=chain.getWebFilters().ofType(AuthenticationWebFilter.class).single().block();assertNotNull(authentication);
        var field=AuthenticationWebFilter.class.getDeclaredField("authenticationManagerResolver");field.setAccessible(true);
        var resolver=(ReactiveAuthenticationManagerResolver<ServerWebExchange>)field.get(authentication);
        var manager=resolver.resolve(MockServerWebExchange.from(MockServerHttpRequest.get(BASE))).block();assertNotNull(manager);
        var decoder=manager.getClass().getDeclaredField("jwtDecoder");decoder.setAccessible(true);decoder.set(manager,(ReactiveJwtDecoder)decode::apply);
        return chain;
    }
    @SuppressWarnings("unchecked") static <T>T port(Class<T> type,AtomicInteger calls){return(T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(object,method,args)->{
        var expected=new ReactorSecurityContextAdapter().currentPrincipal();
        return expected.flatMap(principal->{assertSame(principal,args[0]);assertEquals("tenant",principal.tenantId());assertEquals("https://issuer.example",principal.issuer());assertNotNull(principal.claim("exp",Instant.class));calls.incrementAndGet();return Mono.empty();});
    });}
    static WebTestClient client(SecurityWebFilterChain chain,LedgerMutationTime time,FundsController controller,FundsReceiptController receipts){return WebTestClient.bindToController(controller,receipts)
        .argumentResolvers(configurer->configurer.addCustomResolver(new AuthenticationPrincipalArgumentResolver(org.springframework.core.ReactiveAdapterRegistry.getSharedInstance())))
        .webFilter(new LedgerFundsReserveBudgetFilter(time),new WebFilterChainProxy(chain)).build();}
    @Test void producedChainPreservesCompletePrincipalAcrossExistingReadFundingControlAndReceiptMethods()throws Exception {
        var calls=new AtomicInteger();var money=port(FundsMoneyService.class,calls);var authority=port(FundsAuthority.class,calls);var reconciliation=port(FundsReconciliationService.class,calls);
        var manager=(org.springframework.transaction.ReactiveTransactionManager)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{org.springframework.transaction.ReactiveTransactionManager.class},(o,m,a)->{throw new AssertionError("No SQL or transaction in Web source test");});
        var controller=new FundsController(authority,money,new FundsExpiryWorker(null,null,money,manager),reconciliation);
        var client=client(chain(true,token->Mono.just(jwt("tenant"))),new LedgerMutationTime(Clock.systemUTC(),System::nanoTime),controller,new FundsReceiptController(port(FundsReceiptService.class,calls)));
        var id=UUID.randomUUID().toString();
        for(var path:List.of("/accounts/"+id,"/reservations/"+id,"/reservations/by-operation/"+id,"/reservations/"+id+"/monetary-receipts"))client.get().uri(BASE+path).headers(h->h.setBearerAuth("source-token")).exchange().expectStatus().isOk();
        for(var path:List.of("/accounts/"+id+"/enrollment","/accounts/"+id+"/controls","/funding","/reservations/"+id+"/actions"))client.post().uri(BASE+path).headers(h->h.setBearerAuth("source-token")).contentType(org.springframework.http.MediaType.APPLICATION_JSON).bodyValue("{}").exchange().expectStatus().is2xxSuccessful();
        client.post().uri(BASE+"/reservations/reconciliation").headers(h->h.setBearerAuth("source-token")).contentType(org.springframework.http.MediaType.APPLICATION_JSON).bodyValue("{\"ttlSeconds\":1,\"notAfterEpochMillis\":2000000000000}").exchange().expectStatus().isOk();
        client.post().uri(BASE+"/reservations/expiry").headers(h->h.setBearerAuth("source-token")).contentType(org.springframework.http.MediaType.APPLICATION_JSON).bodyValue("{\"limit\":0}").exchange().expectStatus().isBadRequest();
        assertEquals(9,calls.get());
    }
    @Test void missingOrAmbiguousClaimCannotBeSuppliedByTenantHeaders()throws Exception {
        for(var tenant:Arrays.asList(null,List.of("tenant","other"),12)){
            var chain=chain(true,token->Mono.just(jwt(tenant)));var calls=new AtomicInteger();
            var client=client(chain,new LedgerMutationTime(Clock.systemUTC(),System::nanoTime),new FundsController(null,port(FundsMoneyService.class,calls),null,null),new FundsReceiptController(port(FundsReceiptService.class,calls)));
            client.get().uri(BASE+"/reservations/"+UUID.randomUUID()).headers(h->{h.setBearerAuth("source-token");h.set("X-Tenant-Id","tenant");}).exchange().expectStatus().isUnauthorized();assertEquals(0,calls.get());
        }
    }
    @Test void absentOrRejectedCredentialsDoNotReachAnyController()throws Exception {
        var calls=new AtomicInteger();var client=client(chain(true,token->Mono.error(new BadJwtException("Controlled rejected credential"))),new LedgerMutationTime(Clock.systemUTC(),System::nanoTime),new FundsController(null,port(FundsMoneyService.class,calls),null,null),new FundsReceiptController(port(FundsReceiptService.class,calls)));
        client.get().uri(BASE+"/reservations/"+UUID.randomUUID()).exchange().expectStatus().isUnauthorized();
        client.get().uri(BASE+"/reservations/"+UUID.randomUUID()).headers(h->h.setBearerAuth("rejected")).exchange().expectStatus().isUnauthorized();assertEquals(0,calls.get());
    }
    static void cacheRequests(boolean firstEnabled,String configuredHeader,String actualHeader,boolean rejected,int expectedCalls)throws Exception {
        var cached=new java.util.concurrent.ConcurrentHashMap<String,org.fireflyframework.web.idempotency.model.CachedResponse>();var gets=new AtomicInteger();var puts=new AtomicInteger();
        var cache=new org.fireflyframework.web.idempotency.cache.IdempotencyCache(){
            public Mono<org.fireflyframework.web.idempotency.model.CachedResponse> get(String key){gets.incrementAndGet();return Mono.justOrEmpty(cached.get(key));}
            public Mono<Void> put(String key,org.fireflyframework.web.idempotency.model.CachedResponse response){puts.incrementAndGet();return Mono.fromRunnable(()->cached.put(key,response));}
        };
        var properties=new org.fireflyframework.web.idempotency.config.IdempotencyProperties();properties.setNamespace("ledger-source-proof");properties.setHeaderName(configuredHeader);
        var idempotency=new org.fireflyframework.web.idempotency.filter.IdempotencyWebFilter(properties,cache);
        var chain=chain(firstEnabled,token->Mono.just(jwt("tenant")));var calls=new AtomicInteger();var time=new LedgerMutationTime(Clock.systemUTC(),System::nanoTime);
        for(int request=0;request<2;request++){
            var builder=MockServerHttpRequest.post(BASE+"/reservations").header("Authorization","Bearer source-token");
            if(actualHeader!=null)builder.header(actualHeader,"same-key");
            var exchange=MockServerWebExchange.from(builder.body("{\"operationId\":\"same-durable-operation\"}"));
            var filters=new ArrayList<WebFilter>();if(firstEnabled)filters.add(new LedgerFundsReserveBudgetFilter(time,List.of(properties)));filters.add(new WebFilterChainProxy(chain));filters.add(idempotency);
            new DefaultWebFilterChain(e->{calls.incrementAndGet();e.getResponse().setStatusCode(HttpStatus.CREATED);return e.getResponse().writeWith(Mono.just(e.getResponse().bufferFactory().wrap("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8))));},filters).filter(exchange).block();
            if(rejected){assertEquals(HttpStatus.BAD_REQUEST,exchange.getResponse().getStatusCode());assertEquals("no-store",exchange.getResponse().getHeaders().getCacheControl());assertEquals("{\"code\":\"LEDGER_FIRST_RESERVE_CACHE_KEY_REFUSED\"}",exchange.getResponse().getBodyAsString().block());}
        }
        assertEquals(expectedCalls,calls.get());if(rejected){assertEquals(0,gets.get());assertEquals(0,puts.get());}
    }
    @Test void configuredCacheKeyAndCaseVariantsAreRejectedBeforeAnyHandlerOrCacheEffect()throws Exception {
        cacheRequests(true,"X-Ledger-Response-Key","X-Ledger-Response-Key",true,0);
        cacheRequests(true,"X-Ledger-Response-Key","x-lEdGeR-rEsPoNsE-kEy",true,0);
    }
    @Test void noHeaderRepeatDurableOperationReachesCurrentPermissionTwice()throws Exception {cacheRequests(true,"X-Ledger-Response-Key",null,false,2);}
    @Test void disabledFirstCapturePreservesTheExistingLegacyJwtCacheRefusal()throws Exception {cacheRequests(false,"X-Ledger-Response-Key","X-Ledger-Response-Key",false,2);}
    @Test void actualProducedChainAndReserveControllerPassTheOriginalTimeAndPrincipalToFirstCapture()throws Exception {
        var calls=new AtomicInteger();var legacyCalls=new AtomicInteger();var time=new LedgerMutationTime(Clock.systemUTC(),System::nanoTime);
        var first=(LedgerOwningMutationService)Proxy.newProxyInstance(FundsFirstCaptureSecurityChainTest.class.getClassLoader(),new Class<?>[]{LedgerOwningMutationService.class},(object,method,args)->{
            assertEquals("reserveFirstCapture",method.getName());var original=(org.fireflyframework.core.transaction.R2dbcOperationBudget)args[0];original.intersect(time.originalBudget()).remaining();
            return new ReactorSecurityContextAdapter().currentPrincipal().flatMap(principal->{assertSame(principal,args[1]);assertEquals("tenant",args[2]);assertNotNull(args[3]);calls.incrementAndGet();return Mono.empty();});
        });
        var factory=new DefaultListableBeanFactory();factory.registerSingleton("source-first",first);
        var controller=new FundsController(null,port(FundsMoneyService.class,legacyCalls),null,null,true,factory.getBeanProvider(LedgerOwningMutationService.class));
        var client=client(chain(true,token->Mono.just(jwt("tenant"))),time,controller,new FundsReceiptController(port(FundsReceiptService.class,legacyCalls)));
        client.post().uri(BASE+"/reservations").headers(h->h.setBearerAuth("source-token")).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .bodyValue("{\"operationId\":\"11111111-1111-4111-8111-111111111111\",\"ttlSeconds\":1,\"notAfterEpochMillis\":2000000000000}").exchange().expectStatus().isCreated();
        assertEquals(1,calls.get());assertEquals(0,legacyCalls.get());
    }
    static final class Time extends Clock {Instant now=Instant.now();final AtomicLong nanos=new AtomicLong(10);public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}void advance(){now=now.plusSeconds(6);nanos.addAndGet(Duration.ofSeconds(6).toNanos());}}
    @Test void realAuthenticationDecoderWaitIsInsideTheOriginalIngressWindow()throws Exception {
        var time=new Time();var token=jwt("tenant");var chain=chain(true,value->Mono.fromSupplier(()->{time.advance();return token;}));var exchange=MockServerWebExchange.from(MockServerHttpRequest.post(BASE+"/reservations").header("Authorization","Bearer source-token"));var called=new AtomicInteger();
        new DefaultWebFilterChain(e->{called.incrementAndGet();e.getResponse().setStatusCode(HttpStatus.CREATED);return e.getResponse().writeWith(Mono.fromSupplier(()->e.getResponse().bufferFactory().wrap("must-not-leak".getBytes(java.nio.charset.StandardCharsets.UTF_8))));},List.of(new LedgerFundsReserveBudgetFilter(new LedgerMutationTime(time,time.nanos::get)),new WebFilterChainProxy(chain))).filter(exchange).block();
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,exchange.getResponse().getStatusCode());assertFalse(exchange.getResponse().getBodyAsString().block().contains("must-not-leak"));
    }
    @Test void stalledAuthenticationIsCancelledAtTheOriginalFiveSecondCap()throws Exception {
        var cancelled=new AtomicBoolean();var chain=chain(true,token->Mono.<Jwt>never().doOnCancel(()->cancelled.set(true)));var time=new Time();var exchange=MockServerWebExchange.from(MockServerHttpRequest.post(BASE+"/reservations").header("Authorization","Bearer source-token"));var reached=new AtomicBoolean();
        StepVerifier.withVirtualTime(()->new DefaultWebFilterChain(e->{reached.set(true);return Mono.empty();},List.of(new LedgerFundsReserveBudgetFilter(new LedgerMutationTime(time,time.nanos::get)),new WebFilterChainProxy(chain))).filter(exchange)).thenAwait(Duration.ofSeconds(5)).verifyComplete();
        assertTrue(cancelled.get());assertFalse(reached.get());assertEquals(HttpStatus.SERVICE_UNAVAILABLE,exchange.getResponse().getStatusCode());assertFalse(exchange.getResponse().getBodyAsString().block().contains("reservationId"));
    }
}
