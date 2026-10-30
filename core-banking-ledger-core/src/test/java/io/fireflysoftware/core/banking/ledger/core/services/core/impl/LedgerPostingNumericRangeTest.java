package io.fireflysoftware.core.banking.ledger.core.services.core.impl;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.stream.Stream;

class LedgerPostingNumericRangeTest {
    static Stream<BigDecimal> invalidAmounts() {
        return Stream.of(
                new BigDecimal(BigInteger.ONE, Integer.MIN_VALUE),
                new BigDecimal(BigInteger.TEN, Integer.MIN_VALUE),
                new BigDecimal(BigInteger.ONE, Integer.MIN_VALUE + 1),
                new BigDecimal(BigInteger.ONE, Integer.MAX_VALUE),
                new BigDecimal("100000000000000000000"),
                new BigDecimal("0.0000000000000000001"),
                BigDecimal.ZERO,
                BigDecimal.ONE.negate());
    }

    @ParameterizedTest
    @MethodSource("invalidAmounts")
    void actualHeaderAndLegGuardRejectsBeforeAggregateOrJsonWork(BigDecimal amount) {
        assertThatThrownBy(() -> LedgerPostingServiceImpl.checkAmount(amount))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> {
                            assertThat(e.getStatusCode().value()).isEqualTo(400);
                            assertThat(e.getReason())
                                    .isEqualTo(
                                            "Posting amounts must be positive and fit"
                                                + " NUMERIC(38,18) without rounding");
                        });
    }

    @Test
    void exactValidPostingAmountsRemainAccepted() {
        for (String amount :
                new String[] {
                    "1", "1.2300", "0.000000000000000001", "99999999999999999999.123456789012345678"
                })
            assertThatCode(() -> LedgerPostingServiceImpl.checkAmount(new BigDecimal(amount)))
                    .doesNotThrowAnyException();
    }

    @Test
    void actualPostingEntrypointReturns400BeforeJsonFingerprintOrDatabaseWork() {
        var header =
                new io.fireflysoftware.core.banking.ledger.interfaces.dtos.core
                        .LedgerPostingHeaderDTO();
        header.setAccountId(java.util.UUID.randomUUID());
        header.setCurrency("USD");
        header.setTransactionType(
                io.fireflysoftware.core.banking.ledger.interfaces.enums.core.TransactionTypeEnum
                        .TRANSFER);
        header.setTransactionStatus(
                io.fireflysoftware.core.banking.ledger.interfaces.dtos.core.LedgerPostingHeaderDTO
                        .InitialStatus.POSTED);
        header.setTotalAmount(new BigDecimal("100000000000000000000"));
        var debit =
                new io.fireflysoftware.core.banking.ledger.interfaces.dtos.core
                        .LedgerPostingLegDTO();
        debit.setAccountId(header.getAccountId());
        debit.setCurrency("USD");
        debit.setLegType("DEBIT");
        debit.setAmount(BigDecimal.ONE);
        var credit =
                new io.fireflysoftware.core.banking.ledger.interfaces.dtos.core
                        .LedgerPostingLegDTO();
        credit.setAccountId(java.util.UUID.randomUUID());
        credit.setCurrency("USD");
        credit.setLegType("CREDIT");
        credit.setAmount(BigDecimal.ONE);
        var request =
                new io.fireflysoftware.core.banking.ledger.interfaces.dtos.core
                        .LedgerPostingRequestDTO();
        request.setIdempotencyKey("numeric-range-probe");
        request.setTransaction(header);
        request.setLegs(java.util.List.of(debit, credit));
        var service = new LedgerPostingServiceImpl(null, null, null, null, null, null);
        reactor.test.StepVerifier.create(service.create(request))
                .expectErrorSatisfies(
                        error ->
                                assertThat(error)
                                        .isInstanceOfSatisfying(
                                                ResponseStatusException.class,
                                                e ->
                                                        assertThat(e.getStatusCode().value())
                                                                .isEqualTo(400)))
                .verify(java.time.Duration.ofSeconds(1));
    }
}
