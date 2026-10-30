package io.fireflysoftware.core.banking.ledger.core.services.funds.impl;

import static org.assertj.core.api.Assertions.*;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.Reserve;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.UUID;
import java.util.stream.Stream;

class FundsNumericRangeTest {
    static Stream<BigDecimal> invalidAmounts() {
        return Stream.of(
                new BigDecimal(BigInteger.ONE, Integer.MIN_VALUE),
                new BigDecimal(BigInteger.TEN, Integer.MIN_VALUE),
                new BigDecimal(BigInteger.ONE, Integer.MIN_VALUE + 1),
                new BigDecimal(BigInteger.ONE, Integer.MAX_VALUE),
                new BigDecimal("100000000000000000000"),
                new BigDecimal("0.0000000000000000001"));
    }

    @ParameterizedTest
    @MethodSource("invalidAmounts")
    void actualReserveValidationRejectsBeforeFingerprintFormatting(BigDecimal amount) {
        var request =
                new Reserve(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        amount,
                        "USD",
                        300);
        assertThatThrownBy(() -> FundsMoneyServiceImpl.validateReservation(request))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> {
                            assertThat(e.getStatusCode().value()).isEqualTo(400);
                            assertThat(e.getReason()).isEqualTo("FUNDS_NUMERIC_RANGE_EXCEEDED");
                        });
    }

    @Test
    void exactValidAmountsRemainAccepted() {
        for (String amount :
                new String[] {
                    "1", "1.2300", "0.000000000000000001", "99999999999999999999.123456789012345678"
                })
            assertThatCode(
                            () ->
                                    FundsMoneyServiceImpl.validateReservation(
                                            new Reserve(
                                                    UUID.randomUUID(),
                                                    UUID.randomUUID(),
                                                    UUID.randomUUID(),
                                                    new BigDecimal(amount),
                                                    "USD",
                                                    300)))
                    .doesNotThrowAnyException();
    }

    @Test
    void zeroAndSignedBalanceBoundsRemainValid() {
        for (var amount :
                new BigDecimal[] {
                    BigDecimal.ZERO,
                    new BigDecimal(BigInteger.ZERO, Integer.MIN_VALUE),
                    new BigDecimal(BigInteger.ZERO, Integer.MAX_VALUE),
                    new BigDecimal("-99999999999999999999.123456789012345678")
                })
            assertThatCode(() -> FundsMoneyServiceImpl.numeric(amount)).doesNotThrowAnyException();
    }

    @Test
    void transferredZeroRemainsAnInvalidIntent() {
        assertThatThrownBy(
                        () ->
                                FundsMoneyServiceImpl.validateReservation(
                                        new Reserve(
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                BigDecimal.ZERO,
                                                "USD",
                                                300)))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getReason()).isEqualTo("INVALID_FUNDS_INTENT"));
    }

    @Test
    void actualReserveEntrypointReturns400BeforeDatabaseOrFingerprintWork() {
        UUID source = UUID.randomUUID(), destination = UUID.randomUUID();
        var principal =
                org.fireflyframework.security.api.domain.SecurityPrincipal.builder()
                        .subject("numeric-probe")
                        .issuer("https://issuer")
                        .tenantId("owned")
                        .scopes(java.util.Set.of("funds:reserve"))
                        .claims(
                                java.util.Map.of(
                                        "exp",
                                        java.time.Instant.now().plusSeconds(60),
                                        "funds_accounts",
                                        java.util.List.of(
                                                source.toString(), destination.toString())))
                        .build();
        var clock =
                new org.springframework.beans.factory.support.DefaultListableBeanFactory()
                        .getBeanProvider(java.time.Clock.class);
        var service = new FundsMoneyServiceImpl(null, null, null);
        var request =
                new Reserve(
                        UUID.randomUUID(),
                        source,
                        destination,
                        new BigDecimal("100000000000000000000"),
                        "USD",
                        300);
        reactor.test.StepVerifier.create(service.reserve(principal, "owned", request))
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
