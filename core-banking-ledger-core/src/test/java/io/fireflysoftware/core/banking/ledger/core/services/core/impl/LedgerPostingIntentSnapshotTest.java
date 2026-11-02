package io.fireflysoftware.core.banking.ledger.core.services.core.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.banking.ledger.core.services.core.LedgerPostingIntent;
import io.fireflysoftware.core.banking.ledger.core.mappers.core.TransactionMapper;
import io.fireflysoftware.core.banking.ledger.core.mappers.leg.TransactionLegMapper;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.core.*;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.core.TransactionTypeEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.core.Transaction;
import io.fireflysoftware.core.banking.ledger.models.entities.leg.TransactionLeg;
import io.fireflysoftware.core.banking.ledger.models.repositories.core.*;
import io.fireflysoftware.core.banking.ledger.models.repositories.funds.*;
import io.fireflysoftware.core.banking.ledger.models.repositories.leg.TransactionLegRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.*;

class LedgerPostingIntentSnapshotTest {
    static final UUID ACCOUNT = new UUID(0, 11), OTHER = new UUID(0, 12), ID = new UUID(0, 13);

    static LedgerPostingRequestDTO request() {
        var header = new LedgerPostingHeaderDTO();
        header.setAccountId(ACCOUNT);
        header.setTransactionType(TransactionTypeEnum.TRANSFER);
        header.setTransactionStatus(LedgerPostingHeaderDTO.InitialStatus.POSTED);
        header.setCurrency("USD");
        header.setTotalAmount(new BigDecimal("10"));
        var debit = new LedgerPostingLegDTO();
        debit.setAccountId(ACCOUNT); debit.setLegType("DEBIT");
        debit.setAmount(new BigDecimal("10")); debit.setCurrency("USD");
        var credit = new LedgerPostingLegDTO();
        credit.setAccountId(OTHER); credit.setLegType("CREDIT");
        credit.setAmount(new BigDecimal("10")); credit.setCurrency("USD");
        var input = new LedgerPostingRequestDTO();
        input.setIdempotencyKey("snapshot-original"); input.setTransaction(header);
        input.setLegs(new ArrayList<>(List.of(debit, credit)));
        return input;
    }

    static final class Fixture {
        final FundsNativeRepository nativeOps = mock(FundsNativeRepository.class);
        final FundsMutationRepository writes = mock(FundsMutationRepository.class);
        final LedgerPostingClaimRepository claims = mock(LedgerPostingClaimRepository.class);
        final TransactionRepository transactions = mock(TransactionRepository.class);
        final TransactionLegRepository legs = mock(TransactionLegRepository.class);
        final TransactionStatusHistoryRepository history = mock(TransactionStatusHistoryRepository.class);
        final List<TransactionLeg> emitted = new ArrayList<>();
        final List<String> completedKeys = new ArrayList<>();
        final List<io.fireflysoftware.core.banking.ledger.models.entities.funds.LedgerPostingClaim> inserted = new ArrayList<>();
        Transaction saved;
        final LedgerPostingServiceImpl service;
        Fixture(Runnable whileGateHeld) {
            var first = new AtomicBoolean(true);
            when(nativeOps.accountGate(anyString())).thenAnswer(i -> Mono.fromRunnable(() -> {
                if (first.compareAndSet(true, false)) whileGateHeld.run();
            }));
            when(nativeOps.postingAccountsAllowed(any(UUID[].class), anyBoolean())).thenReturn(Mono.just(true));
            when(nativeOps.postingGate(anyString())).thenReturn(Mono.empty());
            when(claims.findById(anyString())).thenReturn(Mono.empty());
            when(writes.insertLedgerPostingClaim(any())).thenAnswer(i -> Mono.fromSupplier(() -> {
                var claim = (io.fireflysoftware.core.banking.ledger.models.entities.funds.LedgerPostingClaim)i.getArgument(0);
                inserted.add(claim); return claim;
            }));
            when(transactions.save(any())).thenAnswer(i -> Mono.fromSupplier(() -> {
                saved = i.getArgument(0); saved.setTransactionId(ID); return saved;
            }));
            when(transactions.findById(ID)).thenAnswer(i -> Mono.fromSupplier(() -> saved));
            when(history.save(any())).thenAnswer(i -> Mono.just(i.getArgument(0)));
            when(legs.save(any())).thenAnswer(i -> Mono.fromSupplier(() -> {
                TransactionLeg leg = i.getArgument(0); emitted.add(leg); return leg;
            }));
            when(legs.findAllByTransactionIdOrderByTransactionLegId(ID)).thenAnswer(i -> Flux.fromIterable(emitted));
            when(writes.completePosting(anyString(), eq(ID))).thenAnswer(i -> Mono.fromSupplier(() -> {
                completedKeys.add(i.getArgument(0)); return 1L;
            }));
            var mapper = mock(TransactionMapper.class);
            when(mapper.toDTO(any())).thenReturn(new TransactionDTO());
            var legMapper = mock(TransactionLegMapper.class);
            when(legMapper.toDTO(any())).thenReturn(new io.fireflysoftware.core.banking.ledger.interfaces.dtos.leg.TransactionLegDTO());
            var funds = new FundsRepositories(null,null,null,null,null,null,null,claims,null,null,writes,nativeOps);
            service = new LedgerPostingServiceImpl(funds, transactions, legs, history, mapper, legMapper);
        }
    }

    @Test void gateWindowMutationCannotChangeBalancedMoneyOrClaimIdentity() {
        var input = request();
        String originalFingerprint = LedgerPostingServiceImpl.fingerprint(input, null);
        var f = new Fixture(() -> {
            input.setIdempotencyKey("snapshot-mutated");
            input.getTransaction().setTotalAmount(new BigDecimal("99"));
            input.getTransaction().setAccountId(new UUID(0, 99));
            input.getLegs().getFirst().setAmount(new BigDecimal("99"));
            input.getLegs().getFirst().setAccountId(new UUID(0, 99));
            input.getLegs().getLast().setAmount(new BigDecimal("1"));
            input.getLegs().add(input.getLegs().getFirst());
        });
        assertNotNull(f.service.create(input).block(Duration.ofSeconds(2)));
        assertAll(
            () -> assertEquals(new BigDecimal("10"), f.saved.getTotalAmount()),
            () -> assertEquals(ACCOUNT, f.saved.getAccountId()),
            () -> assertEquals("snapshot-original", f.saved.getExternalReference()),
            () -> assertEquals(2, f.emitted.size()),
            () -> assertTrue(f.emitted.stream().allMatch(leg -> new BigDecimal("10").equals(leg.getAmount()))),
            () -> assertEquals(List.of("snapshot-original"), f.completedKeys),
            () -> assertEquals("snapshot-original", f.inserted.getFirst().getIdempotencyKey()),
            () -> assertEquals(originalFingerprint, f.inserted.getFirst().getInputFingerprint()));
    }

    @Test void eachSubscriptionCapturesItsOwnCurrentInput() {
        var input = request();
        var f = new Fixture(() -> {});
        var operation = f.service.create(input);
        assertNotNull(operation.block(Duration.ofSeconds(2)));
        input.setIdempotencyKey("snapshot-second");
        input.getTransaction().setTotalAmount(new BigDecimal("20"));
        input.getLegs().forEach(leg -> leg.setAmount(new BigDecimal("20")));
        assertNotNull(operation.block(Duration.ofSeconds(2)));
        assertEquals(List.of("snapshot-original", "snapshot-second"), f.completedKeys);
        assertEquals(new BigDecimal("20"), f.saved.getTotalAmount());
        assertNotEquals(f.inserted.getFirst().getInputFingerprint(), f.inserted.getLast().getInputFingerprint());
    }

    @Test void malformedOrUnbalancedInputRefusesBeforeAnyGateOrWrite() {
        var f = new Fixture(() -> fail("No gate before valid captured intent"));
        var bad = request(); bad.getLegs().getLast().setAmount(BigDecimal.ONE);
        for (var input : Arrays.asList(null, bad)) {
            var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> f.service.create(input).block(Duration.ofSeconds(2)));
            assertEquals(400, error.getStatusCode().value());
        }
        verifyNoInteractions(f.nativeOps, f.writes, f.transactions, f.legs, f.claims);
    }

    @Test void accountRefusalLeavesAllPostingEffectsAbsent() {
        var f = new Fixture(() -> {});
        when(f.nativeOps.postingAccountsAllowed(any(UUID[].class), anyBoolean())).thenReturn(Mono.just(false));
        var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> f.service.create(request()).block(Duration.ofSeconds(2)));
        assertEquals(403, error.getStatusCode().value());
        assertNull(f.saved); assertTrue(f.inserted.isEmpty()); assertTrue(f.emitted.isEmpty());
    }

    @Test void matchingIdempotencyClaimReplaysWithoutNewEffects() {
        var input = request(); var f = new Fixture(() -> {});
        f.saved = new Transaction(); f.saved.setTransactionId(ID);
        var claim = io.fireflysoftware.core.banking.ledger.models.entities.funds.LedgerPostingClaim.builder()
                .idempotencyKey("snapshot-original").inputFingerprint(LedgerPostingServiceImpl.fingerprint(input, null))
                .transactionId(ID).build();
        when(f.claims.findById("snapshot-original")).thenReturn(Mono.just(claim));
        assertTrue(f.service.create(input).block(Duration.ofSeconds(2)).isReplayed());
        assertTrue(f.inserted.isEmpty()); assertTrue(f.emitted.isEmpty()); assertTrue(f.completedKeys.isEmpty());
        verify(f.transactions, never()).save(any());
    }

    @Test void differentCapturedIntentCannotReuseAnIdempotencyClaim() {
        var input = request(); var f = new Fixture(() -> {});
        var claim = io.fireflysoftware.core.banking.ledger.models.entities.funds.LedgerPostingClaim.builder()
                .idempotencyKey("snapshot-original").inputFingerprint("0".repeat(64)).transactionId(ID).build();
        when(f.claims.findById("snapshot-original")).thenReturn(Mono.just(claim));
        var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> f.service.create(input).block(Duration.ofSeconds(2)));
        assertEquals(409, error.getStatusCode().value());
        assertNull(f.saved); assertTrue(f.inserted.isEmpty()); assertTrue(f.emitted.isEmpty());
    }

    @Test void everyHeaderAndLegFieldSurvivesWithoutExposingTheRetainedSnapshot() {
        var input = request(); var header = input.getTransaction();
        header.setAccountSpaceId(new UUID(0, 21)); header.setDescription("header");
        header.setTransactionDate(java.time.LocalDateTime.of(2026, 10, 1, 1, 2));
        header.setValueDate(java.time.LocalDateTime.of(2026, 10, 2, 1, 2));
        header.setBookingDate(java.time.LocalDateTime.of(2026, 10, 3, 1, 2));
        header.setExternalReference("snapshot-original"); header.setRequestId("request");
        input.getLegs().getFirst().setAccountSpaceId(new UUID(0, 22));
        input.getLegs().getFirst().setDescription("leg");
        var intent = LedgerPostingIntent.capture(input);
        var first = intent.request();
        String expected = LedgerPostingServiceImpl.fingerprint(input, null);
        first.getTransaction().setDescription("changed");
        first.getLegs().getFirst().setDescription("changed"); first.getLegs().clear();
        input.getTransaction().setCurrency("EUR"); input.getLegs().getFirst().setAccountSpaceId(null);
        input.getLegs().clear(); input.setIdempotencyKey("changed");
        var second = intent.request();
        assertEquals(expected, LedgerPostingServiceImpl.fingerprint(second, null));
        assertEquals("header", second.getTransaction().getDescription());
        assertEquals("leg", second.getLegs().getFirst().getDescription());
        assertNotSame(first, second); assertNotSame(first.getTransaction(), second.getTransaction());
    }

    @Test void nullableInvalidShapesRetainTyped400WithoutDatabaseAdmission() {
        var f = new Fixture(() -> fail("Invalid input must not enter account gate"));
        var missingHeader = request(); missingHeader.setTransaction(null);
        var missingLegs = request(); missingLegs.setLegs(null);
        var nullLeg = request(); nullLeg.getLegs().set(0, null);
        var missingAmount = request(); missingAmount.getLegs().getFirst().setAmount(null);
        var missingCurrency = request(); missingCurrency.getTransaction().setCurrency(null);
        var missingKey = request(); missingKey.setIdempotencyKey(null);
        var oversized = request(); oversized.setLegs(Collections.nCopies(1001, oversized.getLegs().getFirst()));
        for (var input : List.of(missingHeader, missingLegs, nullLeg, missingAmount, missingCurrency, missingKey, oversized)) {
            var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> f.service.create(input).block(Duration.ofSeconds(2)));
            assertEquals(400, error.getStatusCode().value());
        }
        verifyNoInteractions(f.nativeOps, f.writes, f.transactions, f.legs, f.claims);
    }
}
