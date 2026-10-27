package io.fireflysoftware.core.banking.ledger.core.services.funds.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.banking.ledger.core.services.funds.FundsMoneyService;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.ExpiryBatch;
import io.fireflysoftware.core.banking.ledger.models.repositories.funds.*;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.ReactiveTransaction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.time.*;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

class FundsExpiryWorkerClockTest {
    @ParameterizedTest
    @ValueSource(longs = {0L, 253402300000000L})
    void discoveryUsesDatabaseTimeInsideEstablishedTransactionDespiteApplicationClockSkew(long appMillis) {
        var active = new AtomicBoolean();
        var established = new AtomicBoolean();
        OffsetDateTime databaseTime = OffsetDateTime.parse("2026-10-24T12:00:00Z");
        FundsNativeRepository nativeOps = mock(FundsNativeRepository.class, invocation -> {
            if (invocation.getMethod().getName().equals("databaseNow")) {
                return Mono.defer(() -> {
                    assertThat(active).isTrue();
                    assertThat(established).isTrue();
                    return Mono.just(databaseTime);
                });
            }
            return RETURNS_DEFAULTS.answer(invocation);
        });
        var reservations = mock(FundsReservationRepository.class);
        when(reservations.findByTenantIdAndStateAndExpiresAtLessThanEqualOrderByExpiresAtAscReservationIdAsc(
                eq("owned"), eq("HELD"), any(), any())).thenAnswer(invocation -> {
                    OffsetDateTime queryTime = invocation.getArgument(2);
                    return Flux.defer(() -> {
                        assertThat(active).isTrue();
                        assertThat(established).isTrue();
                        assertThat(queryTime).isEqualTo(databaseTime);
                        return Flux.empty();
                    });
                });
        var repos = new FundsRepositories(null, reservations, null, null, null, null, null,
                null, null, null, null, nativeOps);
        var context = mock(FundsDatabaseContext.class);
        when(context.establish(any(), eq("owned"), eq("EXPIRE"), any(java.util.UUID[].class)))
                .thenReturn(Mono.defer(() -> {
                    assertThat(active).isTrue();
                    established.set(true);
                    return Mono.empty();
                }));
        var manager = mock(ReactiveTransactionManager.class);
        when(manager.getReactiveTransaction(any())).thenReturn(Mono.fromSupplier(() -> {
            active.set(true);
            return mock(ReactiveTransaction.class);
        }));
        when(manager.commit(any())).thenReturn(Mono.fromRunnable(() -> active.set(false)));
        when(manager.rollback(any())).thenReturn(Mono.fromRunnable(() -> active.set(false)));
        var principal = SecurityPrincipal.builder().tenantId("owned").subject("clock-unit")
                .scopes(Set.of("funds:expire"))
                .claims(java.util.Map.of("exp", Instant.now().plusSeconds(120), "funds_accounts",
                        java.util.List.of("00000000-0000-0000-0000-000000000002"))).build();
        try (var beans = new AnnotationConfigApplicationContext()) {
            beans.registerBean(FundsRepositories.class, () -> repos);
            beans.registerBean(FundsDatabaseContext.class, () -> context);
            beans.registerBean(FundsMoneyService.class, () -> mock(FundsMoneyService.class));
            beans.registerBean(ReactiveTransactionManager.class, () -> manager);
            beans.registerBean(Clock.class, () -> Clock.fixed(Instant.ofEpochMilli(appMillis), ZoneOffset.UTC));
            beans.registerBean(FundsExpiryWorker.class);
            beans.refresh();
            StepVerifier.create(beans.getBean(FundsExpiryWorker.class).expire(principal, "owned", new ExpiryBatch(2)))
                    .assertNext(rows -> assertThat(rows).isEmpty()).verifyComplete();
        }
        assertThat(active).isFalse();
        assertThat(mockingDetails(nativeOps).getInvocations())
                .anyMatch(invocation -> invocation.getMethod().getName().equals("databaseNow"));
    }
}
