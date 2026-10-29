package io.fireflysoftware.core.banking.ledger.core.services.funds.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.banking.ledger.core.services.funds.FundsMoneyService;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.*;
import io.fireflysoftware.core.banking.ledger.models.entities.funds.FundsReservation;
import io.fireflysoftware.core.banking.ledger.models.repositories.funds.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.*;

class FundsExpiryWorkerBehaviorTest {
    static final UUID FIRST=new UUID(0,61),SECOND=new UUID(0,62);
    static final class Fixture {
        final AtomicBoolean active=new AtomicBoolean();
        final FundsNativeRepository nativeOps=mock(FundsNativeRepository.class);
        final FundsReservationRepository reservations=mock(FundsReservationRepository.class);
        final FundsDatabaseContext context=mock(FundsDatabaseContext.class);
        final FundsMoneyService money=mock(FundsMoneyService.class);
        final ReactiveTransactionManager manager=mock(ReactiveTransactionManager.class);
        final SecurityPrincipal principal=SecurityPrincipal.builder().subject("legacy-expiry-unit").tenantId("owned").scopes(Set.of("funds:expire")).claims(Map.of("exp",Instant.now().plusSeconds(120),"funds_accounts",List.of(FIRST.toString(),SECOND.toString()))).build();
        final FundsExpiryWorker worker;
        Fixture(){
            var repos=new FundsRepositories(null,reservations,null,null,null,null,null,null,null,null,null,nativeOps);
            worker=new FundsExpiryWorker(repos,context,money,manager);
            when(manager.getReactiveTransaction(any())).thenReturn(Mono.fromSupplier(()->{active.set(true);return mock(ReactiveTransaction.class);}));
            when(manager.commit(any())).thenReturn(Mono.fromRunnable(()->active.set(false)));
            when(manager.rollback(any())).thenReturn(Mono.fromRunnable(()->active.set(false)));
            when(context.establish(eq(principal),eq("owned"),eq("EXPIRE"),any(UUID[].class))).thenReturn(Mono.empty());
            when(nativeOps.databaseNow()).thenReturn(Mono.defer(()->{assertTrue(active.get());return Mono.just(OffsetDateTime.parse("2026-10-28T12:00:00Z"));}));
            when(reservations.findByTenantIdAndStateAndExpiresAtLessThanEqualOrderByExpiresAtAscReservationIdAsc(eq("owned"),eq("HELD"),any(),any())).thenReturn(Flux.just(FundsReservation.builder().reservationId(FIRST).build(),FundsReservation.builder().reservationId(SECOND).build()));
        }
        Command command(UUID id){return new Command(UUID.nameUUIDFromBytes(("funds-expiry:owned:"+id).getBytes(StandardCharsets.UTF_8)),Action.EXPIRE);}
    }
    @Test void legacyConstructorStillCompletesSelectionBeforeIndependentMoneyCallsAndSkipsOnlyTerminal(){
        var f=new Fixture();
        var result=new Reservation(SECOND,SECOND,FIRST,SECOND,BigDecimal.ONE,"USD",ReservationState.EXPIRED,Instant.now(),SECOND,null,1);
        when(f.money.action(eq(f.principal),eq("owned"),eq(FIRST),eq(f.command(FIRST)))).thenReturn(Mono.defer(()->{assertFalse(f.active.get());return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,"RESERVATION_IS_TERMINAL"));}));
        when(f.money.action(eq(f.principal),eq("owned"),eq(SECOND),eq(f.command(SECOND)))).thenReturn(Mono.defer(()->{assertFalse(f.active.get());return Mono.just(result);}));
        assertEquals(List.of(result),f.worker.expire(f.principal,"owned",new ExpiryBatch(2)).block());
        verify(f.manager,times(1)).getReactiveTransaction(any());verify(f.manager,times(1)).commit(any());verify(f.manager,never()).rollback(any());
        var order=inOrder(f.money);order.verify(f.money).action(f.principal,"owned",FIRST,f.command(FIRST));order.verify(f.money).action(f.principal,"owned",SECOND,f.command(SECOND));
    }
    @Test void legacyGeneric409StopsTheBatchWithoutCallingAnotherItem(){
        var f=new Fixture();
        when(f.money.action(any(),any(),eq(FIRST),any())).thenReturn(Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,"RESERVATION_NOT_DUE")));
        var failure=assertThrows(ResponseStatusException.class,()->f.worker.expire(f.principal,"owned",new ExpiryBatch(2)).block());
        assertEquals("RESERVATION_NOT_DUE",failure.getReason());verify(f.money,never()).action(any(),any(),eq(SECOND),any());
    }
    @ParameterizedTest @ValueSource(ints={0,101})
    void legacyInvalidBatchStillRefusesBeforeOpeningTheSelectionTransaction(int limit){
        var f=new Fixture();
        assertThrows(ResponseStatusException.class,()->f.worker.expire(f.principal,"owned",new ExpiryBatch(limit)).block());
        verifyNoInteractions(f.manager,f.money,f.reservations);verify(f.nativeOps,never()).databaseNow();
    }
}
