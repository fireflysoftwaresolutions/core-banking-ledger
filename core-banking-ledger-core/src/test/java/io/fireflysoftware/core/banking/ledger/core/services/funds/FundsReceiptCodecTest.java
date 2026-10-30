package io.fireflysoftware.core.banking.ledger.core.services.funds;

import io.fireflysoftware.core.banking.ledger.interfaces.protocol.receipt.FundsReceiptCodec;

import static io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsReceiptContracts.*;

import static org.assertj.core.api.Assertions.*;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

class FundsReceiptCodecTest {
    private static final long NOW = 1700000000000L;

    private static UUID id(int value) {
        return new UUID(0, value);
    }

    private static BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    private static EnrollmentSnapshot snapshot(int account, int scale, FundsContracts.Kind kind) {
        return new EnrollmentSnapshot(
                id(account), id(account + 10), id(account + 20), "USD", scale, "policy|é", kind);
    }

    private static FundsContracts.Reserve intent(String amount, Long deadline) {
        return new FundsContracts.Reserve(id(1), id(2), id(3), n(amount), "USD", 300, deadline);
    }

    private static AcceptedReserve accepted() {
        return new AcceptedReserve(
                "owned",
                id(4),
                intent("1.2300", null),
                NOW,
                NOW + 300000,
                snapshot(2, 2, FundsContracts.Kind.CUSTOMER),
                snapshot(3, 2, FundsContracts.Kind.CUSTOMER));
    }

    private static BalanceVector balances(String a, String b, String c, String d) {
        return new BalanceVector(n(a), n(b), n(c), n(d));
    }

    private static MoneyEffect effect() {
        return new MoneyEffect(
                n("1.23"),
                "USD",
                accepted().source(),
                accepted().destination(),
                "123",
                "123",
                id(2),
                id(3),
                balances("10", "1.23", "8.77", "0"),
                balances("0", "0", "1.23", "0"));
    }

    private static MonetaryReceipt commit() {
        return receipt(effect());
    }

    private static MonetaryReceipt receipt(MoneyEffect e) {
        return new MonetaryReceipt(
                id(6),
                id(6),
                "owned",
                id(4),
                id(1),
                EffectKind.COMMIT,
                id(7),
                accepted(),
                id(5),
                null,
                null,
                NOW + 1000,
                e,
                new Actor("https://issuer", "staff|é"));
    }

    private static MonetaryReceipt reverse() {
        var e =
                new MoneyEffect(
                        n("1.23"),
                        "USD",
                        accepted().source(),
                        accepted().destination(),
                        "123",
                        "123",
                        id(3),
                        id(2),
                        balances("8.77", "0", "10", "0"),
                        balances("1.23", "0", "0", "0"));
        return new MonetaryReceipt(
                id(8),
                id(8),
                "owned",
                id(4),
                id(1),
                EffectKind.REVERSE,
                id(9),
                accepted(),
                id(10),
                id(6),
                id(5),
                NOW + 2000,
                e,
                new Actor("https://issuer", "checker"));
    }

    private static MoneyEffect changed(
            String amount, String minor, UUID from, BalanceVector source) {
        var e = effect();
        return new MoneyEffect(
                n(amount),
                e.currency(),
                e.source(),
                e.destination(),
                minor,
                e.destinationMinorUnits(),
                from,
                e.toAccountId(),
                source,
                e.destinationBalances());
    }

    private static String raw(String... fields) {
        int size = 0;
        for (String f : fields)
            size += 4 + (f == null ? 0 : f.getBytes(StandardCharsets.UTF_8).length);
        var b = ByteBuffer.allocate(size);
        for (String f : fields) {
            if (f == null) b.putInt(-1);
            else {
                byte[] v = f.getBytes(StandardCharsets.UTF_8);
                b.putInt(v.length).put(v);
            }
        }
        return Base64.getEncoder().encodeToString(b.array());
    }

    @Test
    void originalIntentHasIndependentGoldenBytesAndDigest() {
        var encoded = FundsReceiptCodec.canonicalIntent("owned", intent("1.2300", null));
        assertThat(encoded.canonical())
                .isEqualTo(
                        raw(
                                "FND_RESERVE_INTENT_V1",
                                "owned",
                                id(1).toString(),
                                id(2).toString(),
                                id(3).toString(),
                                "1.23",
                                "USD",
                                "300",
                                null));
        assertThat(encoded.digest())
                .isEqualTo("f3fc4bd1a623596a79808351aec8014e2ac59380006ec33dcc616bbc51bb0f0e");
    }

    @Test
    void equivalentDecimalSpellingsHaveSameIntent() {
        assertThat(FundsReceiptCodec.canonicalIntent("owned", intent("1.2300", null)))
                .isEqualTo(FundsReceiptCodec.canonicalIntent("owned", intent("1.23", null)));
    }

    @Test
    void explicitDeadlineChangesIntentAndAcceptanceExpiryIsPinned() {
        var a = accepted();
        var pinned =
                new AcceptedReserve(
                        a.tenant(),
                        a.reservationId(),
                        intent("1.23", NOW + 120000),
                        NOW,
                        NOW + 120000,
                        a.source(),
                        a.destination());
        assertThat(FundsReceiptCodec.canonicalAccepted(pinned))
                .isNotEqualTo(FundsReceiptCodec.canonicalAccepted(a));
        assertThat(
                        FundsReceiptCodec.decodeAccepted(
                                FundsReceiptCodec.canonicalAccepted(pinned).canonical()))
                .isEqualTo(pinned);
    }

    @Test
    void acceptedIntentAndUnicodeSnapshotsRoundTrip() {
        var encoded = FundsReceiptCodec.canonicalAccepted(accepted());
        var decoded = FundsReceiptCodec.decodeAccepted(encoded.canonical());
        assertThat(decoded.intent().amount()).isEqualByComparingTo("1.23");
        assertThat(decoded.source().currencyPolicy()).isEqualTo("policy|é");
        assertThat(FundsReceiptCodec.canonicalAccepted(decoded)).isEqualTo(encoded);
    }

    @Test
    void commitRoundTripsToStableCanonicalReceipt() {
        var encoded = FundsReceiptCodec.canonicalReceipt(commit());
        assertThat(
                        FundsReceiptCodec.canonicalReceipt(
                                FundsReceiptCodec.decodeReceipt(encoded.canonical())))
                .isEqualTo(encoded);
        assertThat(encoded.digest())
                .isEqualTo("23233b82c59c9400c8d425050aeb5ca3b0a7511a10e3e5cab275e90b35839bc2");
    }

    @Test
    void reversalLinksExactRetainedOriginal() {
        FundsReceiptCodec.validateReversal(reverse(), commit());
        var encoded = FundsReceiptCodec.canonicalReceipt(reverse());
        assertThat(
                        FundsReceiptCodec.canonicalReceipt(
                                FundsReceiptCodec.decodeReceipt(encoded.canonical())))
                .isEqualTo(encoded);
    }

    @Test
    void unrelatedOriginalDoesNotValidateReversal() {
        var c = commit();
        var other =
                new MonetaryReceipt(
                        id(99),
                        id(99),
                        c.tenant(),
                        c.reservationId(),
                        c.operationId(),
                        c.effectKind(),
                        c.actionId(),
                        c.acceptedReserve(),
                        c.postingId(),
                        null,
                        null,
                        c.effectRecordedAtEpochMillis(),
                        c.effect(),
                        c.actor());
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FundsReceiptCodec.validateReversal(reverse(), other));
    }

    @Test
    void reversalCannotReuseOriginalActionIdentity() {
        var r = reverse();
        var reused =
                new MonetaryReceipt(
                        r.receiptId(),
                        r.eventId(),
                        r.tenant(),
                        r.reservationId(),
                        r.operationId(),
                        r.effectKind(),
                        commit().actionId(),
                        r.acceptedReserve(),
                        r.postingId(),
                        r.originalCommitReceiptId(),
                        r.originalPostingId(),
                        r.effectRecordedAtEpochMillis(),
                        r.effect(),
                        r.actor());
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FundsReceiptCodec.validateReversal(reused, commit()));
    }

    @Test
    void actualPerSideScalesAndLargeMinorUnitsRemainExact() {
        for (int scale : new int[] {0, 2, 3, 18}) {
            var source = snapshot(2, scale, FundsContracts.Kind.CUSTOMER);
            var destination = snapshot(3, 18, FundsContracts.Kind.CUSTOMER);
            var original =
                    new AcceptedReserve(
                            "owned",
                            id(4),
                            intent("1", null),
                            NOW,
                            NOW + 300000,
                            source,
                            destination);
            var effect =
                    new MoneyEffect(
                            n("1"),
                            "USD",
                            source,
                            destination,
                            n("1").movePointRight(scale).toBigIntegerExact().toString(),
                            "1000000000000000000",
                            id(2),
                            id(3),
                            balances("10", "1", "9", "0"),
                            balances("0", "0", "1", "0"));
            var value =
                    new MonetaryReceipt(
                            id(6),
                            id(6),
                            "owned",
                            id(4),
                            id(1),
                            EffectKind.COMMIT,
                            id(7),
                            original,
                            id(5),
                            null,
                            null,
                            NOW + 1000,
                            effect,
                            commit().actor());
            assertThat(
                            FundsReceiptCodec.decodeReceipt(
                                            FundsReceiptCodec.canonicalReceipt(value).canonical())
                                    .effect()
                                    .destinationMinorUnits())
                    .isEqualTo("1000000000000000000");
        }
        var large = intent("99999999999999999999.123456789012345678", null);
        assertThat(FundsReceiptCodec.canonicalIntent("owned", large).canonical()).isNotBlank();
    }

    @Test
    void fractionalAmountCannotPretendToFitScaleZero() {
        var a = accepted();
        var wrong =
                new AcceptedReserve(
                        a.tenant(),
                        a.reservationId(),
                        a.intent(),
                        NOW,
                        a.expiresAtEpochMillis(),
                        snapshot(2, 0, FundsContracts.Kind.CUSTOMER),
                        a.destination());
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FundsReceiptCodec.canonicalAccepted(wrong));
    }

    @Test
    void fullThirtyEightDigitMinorUnitsRoundTripWithoutRounding() {
        String amount = "99999999999999999999.123456789012345678";
        var source = snapshot(2, 18, FundsContracts.Kind.CUSTOMER);
        var destination = snapshot(3, 18, FundsContracts.Kind.CUSTOMER);
        var original =
                new AcceptedReserve(
                        "owned",
                        id(4),
                        intent(amount, null),
                        NOW,
                        NOW + 300000,
                        source,
                        destination);
        String units = "99999999999999999999123456789012345678";
        var effect =
                new MoneyEffect(
                        n(amount),
                        "USD",
                        source,
                        destination,
                        units,
                        units,
                        id(2),
                        id(3),
                        balances(amount, amount, "0", "0"),
                        balances("0", "0", amount, "0"));
        var value =
                new MonetaryReceipt(
                        id(6),
                        id(6),
                        "owned",
                        id(4),
                        id(1),
                        EffectKind.COMMIT,
                        id(7),
                        original,
                        id(5),
                        null,
                        null,
                        NOW + 1000,
                        effect,
                        commit().actor());
        assertThat(
                        FundsReceiptCodec.decodeReceipt(
                                        FundsReceiptCodec.canonicalReceipt(value).canonical())
                                .effect()
                                .destinationMinorUnits())
                .isEqualTo(units);
    }

    @Test
    void distinctEnrolledAccountsCannotReuseSameEnrollmentIdentity() {
        var a = accepted();
        var destination =
                new EnrollmentSnapshot(
                        a.destination().accountId(),
                        a.source().enrollmentId(),
                        a.destination().contractId(),
                        "USD",
                        2,
                        "policy|é",
                        FundsContracts.Kind.CUSTOMER);
        var wrong =
                new AcceptedReserve(
                        a.tenant(),
                        a.reservationId(),
                        a.intent(),
                        NOW,
                        a.expiresAtEpochMillis(),
                        a.source(),
                        destination);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FundsReceiptCodec.canonicalAccepted(wrong));
    }

    @Test
    void customerBalancesCannotFallBelowExistingHolds() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                FundsReceiptCodec.canonicalReceipt(
                                        receipt(
                                                changed(
                                                        "1.23",
                                                        "123",
                                                        id(2),
                                                        balances("1", "1.23", "-0.23", "0")))));
    }

    @Test
    void canonicalEnvelopeCannotLieAboutDigest() {
        var c = FundsReceiptCodec.canonicalReceipt(commit());
        var wrong =
                new MonetaryReceiptEnvelope(
                        commit(), new CanonicalForm(c.encoding(), c.canonical(), "0".repeat(64)));
        assertThatIllegalArgumentException().isThrownBy(() -> FundsReceiptCodec.verify(wrong));
        FundsReceiptCodec.verify(FundsReceiptCodec.envelope(commit()));
        FundsReceiptCodec.verify(FundsReceiptCodec.envelope(accepted()));
    }

    @Test
    void bankControlReserveCannotManufactureUnsupportedTransferEvidence() {
        var a = accepted();
        var source = snapshot(2, 2, FundsContracts.Kind.BANK_CONTROL);
        var acceptance =
                new AcceptedReserve(
                        a.tenant(),
                        a.reservationId(),
                        a.intent(),
                        a.intentRecordedAtEpochMillis(),
                        a.expiresAtEpochMillis(),
                        source,
                        a.destination());
        var e = effect();
        var negative =
                new MoneyEffect(
                        e.amount(),
                        e.currency(),
                        source,
                        e.destination(),
                        "123",
                        "123",
                        e.fromAccountId(),
                        e.toAccountId(),
                        balances("-2", "1.23", "-3.23", "0"),
                        e.destinationBalances());
        var c = commit();
        var value =
                new MonetaryReceipt(
                        c.receiptId(),
                        c.eventId(),
                        c.tenant(),
                        c.reservationId(),
                        c.operationId(),
                        c.effectKind(),
                        c.actionId(),
                        acceptance,
                        c.postingId(),
                        null,
                        null,
                        c.effectRecordedAtEpochMillis(),
                        negative,
                        c.actor());
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FundsReceiptCodec.canonicalReceipt(value));
    }

    @Test
    void genericBalanceVectorPreservesSignedAndZeroValuesWithoutAuthorizingReceipt() {
        assertThat(balances("-2", "0", "-3.23", "0").bookedAfter()).isEqualByComparingTo("-3.23");
    }

    @Test
    void extremeNegativeScaleIsRejectedBeforeAnyDecimalFormatting() throws Exception {
        // Isolate the numeric guard so a pre-fix failure cannot allocate a huge decimal string.
        var guard = FundsReceiptCodec.class.getDeclaredMethod("numeric", BigDecimal.class);
        guard.setAccessible(true);
        var extreme = new BigDecimal(java.math.BigInteger.ONE, Integer.MIN_VALUE + 1);
        assertThatThrownBy(() -> guard.invoke(null, extreme))
                .isInstanceOf(java.lang.reflect.InvocationTargetException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void extremeScaleValuesRejectSafelyAtPublicBoundary() {
        for (var value :
                new BigDecimal[] {
                    new BigDecimal(java.math.BigInteger.ONE, Integer.MIN_VALUE),
                    new BigDecimal(java.math.BigInteger.TEN, Integer.MIN_VALUE),
                    new BigDecimal(java.math.BigInteger.ONE, Integer.MIN_VALUE + 1),
                    new BigDecimal(java.math.BigInteger.ONE, Integer.MAX_VALUE)
                }) {
            var extreme = new FundsContracts.Reserve(id(1), id(2), id(3), value, "USD", 300, null);
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> FundsReceiptCodec.canonicalIntent("owned", extreme));
        }
    }

    @Test
    void signedBalanceDoesNotPermitNegativeHeldFunds() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                FundsReceiptCodec.canonicalReceipt(
                                        receipt(
                                                changed(
                                                        "1.23",
                                                        "123",
                                                        id(2),
                                                        balances("10", "1.23", "8.77", "-1")))));
    }

    @Test
    void wrongMoneyDeltaIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                FundsReceiptCodec.canonicalReceipt(
                                        receipt(
                                                changed(
                                                        "1.23",
                                                        "123",
                                                        id(2),
                                                        balances("10", "1.23", "8.78", "0")))));
    }

    @Test
    void wrongMinorUnitsAreRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                FundsReceiptCodec.canonicalReceipt(
                                        receipt(
                                                changed(
                                                        "1.23",
                                                        "124",
                                                        id(2),
                                                        effect().sourceBalances()))));
    }

    @Test
    void noncanonicalMinorUnitsAreRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                FundsReceiptCodec.canonicalReceipt(
                                        receipt(
                                                changed(
                                                        "1.23",
                                                        "0123",
                                                        id(2),
                                                        effect().sourceBalances()))));
    }

    @Test
    void wrongFlowIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                FundsReceiptCodec.canonicalReceipt(
                                        receipt(
                                                changed(
                                                        "1.23",
                                                        "123",
                                                        id(3),
                                                        effect().sourceBalances()))));
    }

    @Test
    void effectCannotChangeOriginalAmount() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                FundsReceiptCodec.canonicalReceipt(
                                        receipt(
                                                changed(
                                                        "1.24",
                                                        "124",
                                                        id(2),
                                                        effect().sourceBalances()))));
    }

    @Test
    void duplicateEventIdentityMustMatchReceipt() {
        var c = commit();
        var wrong =
                new MonetaryReceipt(
                        c.receiptId(),
                        id(44),
                        c.tenant(),
                        c.reservationId(),
                        c.operationId(),
                        c.effectKind(),
                        c.actionId(),
                        c.acceptedReserve(),
                        c.postingId(),
                        null,
                        null,
                        c.effectRecordedAtEpochMillis(),
                        c.effect(),
                        c.actor());
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FundsReceiptCodec.canonicalReceipt(wrong));
    }

    @Test
    void effectSnapshotIdentityMustMatchOriginalAccountEnrollment() {
        var e = effect();
        var wrong =
                new MoneyEffect(
                        e.amount(),
                        e.currency(),
                        snapshot(20, 2, FundsContracts.Kind.CUSTOMER),
                        e.destination(),
                        "123",
                        "123",
                        e.fromAccountId(),
                        e.toAccountId(),
                        e.sourceBalances(),
                        e.destinationBalances());
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FundsReceiptCodec.canonicalReceipt(receipt(wrong)));
    }

    @Test
    void acceptanceCannotMisstateActualExpiry() {
        var a = accepted();
        var wrong =
                new AcceptedReserve(
                        a.tenant(),
                        a.reservationId(),
                        a.intent(),
                        NOW,
                        NOW + 1,
                        a.source(),
                        a.destination());
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FundsReceiptCodec.canonicalAccepted(wrong));
    }

    @Test
    void trailingBytesAreRejected() {
        byte[] bytes =
                Base64.getDecoder()
                        .decode(FundsReceiptCodec.canonicalReceipt(commit()).canonical());
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                FundsReceiptCodec.decodeReceipt(
                                        Base64.getEncoder()
                                                .encodeToString(
                                                        java.util.Arrays.copyOf(
                                                                bytes, bytes.length + 1))));
    }

    @Test
    void truncatedFieldsAreRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                FundsReceiptCodec.decodeReceipt(
                                        Base64.getEncoder()
                                                .encodeToString(new byte[] {0, 0, 0, 9, 65})));
    }

    @Test
    void hugeDeclaredFieldCannotAllocateMemory() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FundsReceiptCodec.decodeReceipt("f////w=="));
    }

    @Test
    void unknownEncodingIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FundsReceiptCodec.decodeReceipt(raw("FND_OTHER_V1")));
    }

    @Test
    void malformedUtf8IsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                FundsReceiptCodec.decodeAccepted(
                                        Base64.getEncoder()
                                                .encodeToString(
                                                        new byte[] {0, 0, 0, 1, (byte) 0xff})));
    }

    @Test
    void noncanonicalBase64IsRejected() {
        String encoded = FundsReceiptCodec.canonicalAccepted(accepted()).canonical();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FundsReceiptCodec.decodeAccepted(encoded + "\n"));
    }

    @Test
    void totalEncodedSizeIsBoundedBeforeDecode() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FundsReceiptCodec.decodeReceipt("A".repeat(200000)));
    }

    @Test
    void nestedBase64AndCanonicalPrimitivesAreStrict() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                FundsReceiptCodec.decodeAccepted(
                                        raw("FND_ACCEPTED_RESERVE_V1", "A".repeat(70000))));
        String wrongIntent =
                raw(
                        "FND_RESERVE_INTENT_V1",
                        "owned",
                        id(1).toString(),
                        id(2).toString(),
                        id(3).toString(),
                        "1.2300",
                        "USD",
                        "0300",
                        null);
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                FundsReceiptCodec.decodeAccepted(
                                        raw("FND_ACCEPTED_RESERVE_V1", wrongIntent)));
    }
}
