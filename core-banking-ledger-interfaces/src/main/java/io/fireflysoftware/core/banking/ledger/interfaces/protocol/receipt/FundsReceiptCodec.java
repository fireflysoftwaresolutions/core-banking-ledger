package io.fireflysoftware.core.banking.ledger.interfaces.protocol.receipt;

import static io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsReceiptContracts.*;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

public final class FundsReceiptCodec {
    public static final String INTENT_ENCODING = "FND_RESERVE_INTENT_V1";
    public static final String ACCEPTED_ENCODING = "FND_ACCEPTED_RESERVE_V1";
    public static final String RECEIPT_ENCODING = "FND_MONETARY_RECEIPT_V1";
    private static final long LAST_MILLISECOND = 253402300799999L;
    private static final int INTENT_LIMIT = 4096;
    private static final int ACCEPTED_LIMIT = 16384;
    private static final int RECEIPT_LIMIT = 65536;

    private FundsReceiptCodec() {}

    /** Original operation-claim fingerprint; separate from the length-prefixed receipt protocol. */
    public static String reserveOperationFingerprint(FundsContracts.Reserve input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    java.util.stream.Stream.of("RESERVE", input.sourceAccountId(), input.destinationAccountId(),
                            input.amount().stripTrailingZeros().toPlainString(), input.currency(), input.ttlSeconds(),
                            input.notAfterEpochMillis()).map(String::valueOf)
                            .collect(java.util.stream.Collectors.joining("|")).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    public static CanonicalForm canonicalIntent(String tenant, FundsContracts.Reserve intent) {
        tenant(tenant);
        require(intent != null, "Original reserve intent required");
        uuid(intent.operationId());
        uuid(intent.sourceAccountId());
        uuid(intent.destinationAccountId());
        require(
                !intent.sourceAccountId().equals(intent.destinationAccountId()),
                "Distinct accounts required");
        positive(intent.amount());
        currency(intent.currency());
        require(intent.ttlSeconds() >= 1 && intent.ttlSeconds() <= 900, "Invalid original TTL");
        if (intent.notAfterEpochMillis() != null) timestamp(intent.notAfterEpochMillis());
        return new Writer(INTENT_LIMIT)
                .field(INTENT_ENCODING)
                .field(tenant)
                .field(intent.operationId())
                .field(intent.sourceAccountId())
                .field(intent.destinationAccountId())
                .field(decimal(intent.amount()))
                .field(intent.currency())
                .field(intent.ttlSeconds())
                .field(intent.notAfterEpochMillis())
                .finish(INTENT_ENCODING);
    }

    public static CanonicalForm canonicalAccepted(AcceptedReserve value) {
        validateAccepted(value);
        var w =
                new Writer(ACCEPTED_LIMIT)
                        .field(ACCEPTED_ENCODING)
                        .field(canonicalIntent(value.tenant(), value.intent()).canonical())
                        .field(value.reservationId())
                        .field(value.intentRecordedAtEpochMillis())
                        .field(value.expiresAtEpochMillis());
        snapshot(w, value.source());
        snapshot(w, value.destination());
        return w.finish(ACCEPTED_ENCODING);
    }

    public static CanonicalForm canonicalReceipt(MonetaryReceipt value) {
        validateReceipt(value);
        var e = value.effect();
        var w =
                new Writer(RECEIPT_LIMIT)
                        .field(RECEIPT_ENCODING)
                        .field(value.receiptId())
                        .field(value.tenant())
                        .field(value.reservationId())
                        .field(value.operationId())
                        .field(value.effectKind())
                        .field(value.actionId())
                        .field(canonicalAccepted(value.acceptedReserve()).canonical())
                        .field(value.postingId())
                        .field(value.originalCommitReceiptId())
                        .field(value.originalPostingId())
                        .field(value.effectRecordedAtEpochMillis())
                        .field(decimal(e.amount()))
                        .field(e.currency());
        snapshot(w, e.source());
        snapshot(w, e.destination());
        w.field(e.sourceMinorUnits())
                .field(e.destinationMinorUnits())
                .field(e.fromAccountId())
                .field(e.toAccountId());
        balances(w, e.sourceBalances());
        balances(w, e.destinationBalances());
        w.field(value.actor().issuer()).field(value.actor().subject());
        return w.finish(RECEIPT_ENCODING);
    }

    public static AcceptedReserve decodeAccepted(String encoded) {
        var r = new Reader(encoded, ACCEPTED_LIMIT);
        r.encoding(ACCEPTED_ENCODING);
        var original = decodeIntent(r.field(INTENT_LIMIT));
        var value =
                new AcceptedReserve(
                        original.tenant(),
                        r.uuid(),
                        original.intent(),
                        r.number(),
                        r.number(),
                        snapshot(r),
                        snapshot(r));
        r.end();
        require(
                canonicalAccepted(value).canonical().equals(encoded),
                "Noncanonical accepted reserve");
        return value;
    }

    public static MonetaryReceipt decodeReceipt(String encoded) {
        var r = new Reader(encoded, RECEIPT_LIMIT);
        r.encoding(RECEIPT_ENCODING);
        UUID receipt = r.uuid();
        String tenant = r.field();
        UUID reservation = r.uuid();
        UUID operation = r.uuid();
        EffectKind kind = r.enumeration(EffectKind.class);
        UUID action = r.uuid();
        AcceptedReserve accepted = decodeAccepted(r.field(ACCEPTED_LIMIT));
        UUID posting = r.uuid(),
                originalReceipt = r.nullableUuid(),
                originalPosting = r.nullableUuid();
        long recorded = r.number();
        BigDecimal amount = r.decimal();
        String currency = r.field();
        EnrollmentSnapshot source = snapshot(r), destination = snapshot(r);
        String sourceMinor = r.field(), destinationMinor = r.field();
        UUID from = r.uuid(), to = r.uuid();
        var effect =
                new MoneyEffect(
                        amount,
                        currency,
                        source,
                        destination,
                        sourceMinor,
                        destinationMinor,
                        from,
                        to,
                        balances(r),
                        balances(r));
        var value =
                new MonetaryReceipt(
                        receipt,
                        receipt,
                        tenant,
                        reservation,
                        operation,
                        kind,
                        action,
                        accepted,
                        posting,
                        originalReceipt,
                        originalPosting,
                        recorded,
                        effect,
                        new Actor(r.field(), r.field()));
        r.end();
        require(
                canonicalReceipt(value).canonical().equals(encoded),
                "Noncanonical monetary receipt");
        return value;
    }

    public static AcceptedReserveEnvelope envelope(AcceptedReserve value) {
        return new AcceptedReserveEnvelope(value, canonicalAccepted(value));
    }

    public static MonetaryReceiptEnvelope envelope(MonetaryReceipt value) {
        return new MonetaryReceiptEnvelope(value, canonicalReceipt(value));
    }

    public static void verify(AcceptedReserveEnvelope envelope) {
        require(
                envelope != null
                        && canonicalAccepted(envelope.value()).equals(envelope.canonical()),
                "Accepted reserve envelope mismatch");
    }

    public static void verify(MonetaryReceiptEnvelope envelope) {
        require(
                envelope != null && canonicalReceipt(envelope.value()).equals(envelope.canonical()),
                "Monetary receipt envelope mismatch");
    }

    public static void validateReversal(MonetaryReceipt reversal, MonetaryReceipt original) {
        validateReceipt(reversal);
        validateReceipt(original);
        require(
                reversal.effectKind() == EffectKind.REVERSE
                        && original.effectKind() == EffectKind.COMMIT,
                "Actual original COMMIT and REVERSE required");
        require(
                reversal.originalCommitReceiptId().equals(original.receiptId())
                        && reversal.originalPostingId().equals(original.postingId())
                        && !reversal.actionId().equals(original.actionId())
                        && canonicalAccepted(reversal.acceptedReserve())
                                .equals(canonicalAccepted(original.acceptedReserve()))
                        && reversal.effectRecordedAtEpochMillis()
                                >= original.effectRecordedAtEpochMillis(),
                "Reversal does not link its original commit");
    }

    private record Original(String tenant, FundsContracts.Reserve intent) {}

    private static Original decodeIntent(String encoded) {
        var r = new Reader(encoded, INTENT_LIMIT);
        r.encoding(INTENT_ENCODING);
        String tenant = r.field();
        UUID operation = r.uuid(), source = r.uuid(), destination = r.uuid();
        BigDecimal amount = r.decimal();
        String currency = r.field();
        long ttl = r.number();
        require(ttl >= 1 && ttl <= 900, "Invalid original TTL");
        String deadline = r.field();
        var intent =
                new FundsContracts.Reserve(
                        operation,
                        source,
                        destination,
                        amount,
                        currency,
                        (int) ttl,
                        deadline == null ? null : integer(deadline));
        r.end();
        require(
                canonicalIntent(tenant, intent).canonical().equals(encoded),
                "Noncanonical original intent");
        return new Original(tenant, intent);
    }

    private static void validateAccepted(AcceptedReserve value) {
        require(value != null, "Accepted original intent required");
        canonicalIntent(value.tenant(), value.intent());
        uuid(value.reservationId());
        timestamp(value.intentRecordedAtEpochMillis());
        timestamp(value.expiresAtEpochMillis());
        long expected =
                Math.min(
                        value.intentRecordedAtEpochMillis() + value.intent().ttlSeconds() * 1000L,
                        value.intent().notAfterEpochMillis() == null
                                ? LAST_MILLISECOND
                                : value.intent().notAfterEpochMillis());
        require(
                value.expiresAtEpochMillis() > value.intentRecordedAtEpochMillis()
                        && value.expiresAtEpochMillis() == expected,
                "Original actual expiry mismatch");
        validateSnapshot(value.source());
        validateSnapshot(value.destination());
        require(
                value.source().kind() == FundsContracts.Kind.CUSTOMER
                        && value.destination().kind() == FundsContracts.Kind.CUSTOMER,
                "Customer internal transfer required");
        require(
                value.source().accountId().equals(value.intent().sourceAccountId())
                        && value.destination()
                                .accountId()
                                .equals(value.intent().destinationAccountId())
                        && !value.source().enrollmentId().equals(value.destination().enrollmentId())
                        && value.source().currency().equals(value.intent().currency())
                        && value.destination().currency().equals(value.intent().currency()),
                "Original enrollment mismatch");
        minor(value.intent().amount(), value.source().currencyScale());
        minor(value.intent().amount(), value.destination().currencyScale());
    }

    private static void validateReceipt(MonetaryReceipt value) {
        require(value != null, "Monetary receipt required");
        validateAccepted(value.acceptedReserve());
        var original = value.acceptedReserve();
        uuid(value.receiptId());
        uuid(value.eventId());
        uuid(value.actionId());
        uuid(value.postingId());
        require(value.receiptId().equals(value.eventId()), "Receipt and event identity mismatch");
        require(
                Objects.equals(value.tenant(), original.tenant())
                        && Objects.equals(value.reservationId(), original.reservationId())
                        && Objects.equals(value.operationId(), original.intent().operationId()),
                "Receipt original identity mismatch");
        timestamp(value.effectRecordedAtEpochMillis());
        require(
                value.effectRecordedAtEpochMillis() >= original.intentRecordedAtEpochMillis(),
                "Effect predates acceptance");
        require(value.effectKind() != null, "Effect kind required");
        boolean reverse = value.effectKind() == EffectKind.REVERSE;
        if (reverse) {
            uuid(value.originalCommitReceiptId());
            uuid(value.originalPostingId());
            require(
                    !value.receiptId().equals(value.originalCommitReceiptId())
                            && !value.postingId().equals(value.originalPostingId()),
                    "Reversal must link distinct original effect");
        } else {
            require(
                    value.originalCommitReceiptId() == null && value.originalPostingId() == null,
                    "COMMIT must not invent original effect links");
            require(
                    value.effectRecordedAtEpochMillis() < original.expiresAtEpochMillis(),
                    "COMMIT is after expiry");
        }
        var e = value.effect();
        require(e != null, "Actual effect required");
        positive(e.amount());
        currency(e.currency());
        require(
                e.amount().compareTo(original.intent().amount()) == 0
                        && e.currency().equals(original.intent().currency()),
                "Effect changed original money");
        validateSnapshot(e.source());
        validateSnapshot(e.destination());
        require(
                e.source().equals(original.source())
                        && e.destination().equals(original.destination()),
                "Effect enrollment representation changed");
        require(
                minor(e.amount(), e.source().currencyScale()).equals(e.sourceMinorUnits())
                        && minor(e.amount(), e.destination().currencyScale())
                                .equals(e.destinationMinorUnits()),
                "Exact minor unit mismatch");
        require(
                Objects.equals(
                                e.fromAccountId(),
                                reverse ? e.destination().accountId() : e.source().accountId())
                        && Objects.equals(
                                e.toAccountId(),
                                reverse ? e.source().accountId() : e.destination().accountId()),
                "Actual effect flow mismatch");
        validateBalances(e.sourceBalances(), e.source());
        validateBalances(e.destinationBalances(), e.destination());
        var s = e.sourceBalances();
        var d = e.destinationBalances();
        BigDecimal movement = reverse ? e.amount() : e.amount().negate();
        equal(s.bookedAfter(), s.bookedBefore().add(movement), "Source booked effect mismatch");
        equal(
                d.bookedAfter(),
                d.bookedBefore().subtract(movement),
                "Destination booked effect mismatch");
        equal(
                s.heldAfter(),
                reverse ? s.heldBefore() : s.heldBefore().subtract(e.amount()),
                "Source hold effect mismatch");
        equal(d.heldAfter(), d.heldBefore(), "Destination hold changed");
        require(value.actor() != null, "Verified actor required");
        text(value.actor().issuer(), 2048);
        text(value.actor().subject(), 800);
    }

    private static void validateSnapshot(EnrollmentSnapshot value) {
        require(value != null, "Enrollment snapshot required");
        uuid(value.accountId());
        uuid(value.enrollmentId());
        uuid(value.contractId());
        currency(value.currency());
        require(
                value.currencyScale() >= 0 && value.currencyScale() <= 18,
                "Invalid actual currency scale");
        text(value.currencyPolicy(), 400);
        require(
                value.currencyPolicy().length() <= 100 && value.kind() != null,
                "Invalid enrollment policy or kind");
    }

    private static void validateBalances(BalanceVector vector, EnrollmentSnapshot account) {
        require(vector != null, "Actual balance vector required");
        numeric(vector.bookedBefore());
        numeric(vector.bookedAfter());
        numeric(vector.heldBefore());
        numeric(vector.heldAfter());
        require(
                vector.heldBefore().signum() >= 0 && vector.heldAfter().signum() >= 0,
                "Negative held funds");
        if (account.kind() == FundsContracts.Kind.CUSTOMER)
            require(
                    vector.bookedBefore().compareTo(vector.heldBefore()) >= 0
                            && vector.bookedAfter().compareTo(vector.heldAfter()) >= 0,
                    "Customer booked funds below holds");
        for (var amount :
                new BigDecimal[] {
                    vector.bookedBefore(),
                    vector.bookedAfter(),
                    vector.heldBefore(),
                    vector.heldAfter()
                }) minor(amount, account.currencyScale());
    }

    private static void snapshot(Writer w, EnrollmentSnapshot value) {
        w.field(value.accountId())
                .field(value.enrollmentId())
                .field(value.contractId())
                .field(value.currency())
                .field(value.currencyScale())
                .field(value.currencyPolicy())
                .field(value.kind());
    }

    private static EnrollmentSnapshot snapshot(Reader r) {
        UUID account = r.uuid(), enrollment = r.uuid(), contract = r.uuid();
        String currency = r.field();
        long scale = r.number();
        require(scale >= 0 && scale <= 18, "Invalid actual currency scale");
        return new EnrollmentSnapshot(
                account,
                enrollment,
                contract,
                currency,
                (int) scale,
                r.field(),
                r.enumeration(FundsContracts.Kind.class));
    }

    private static void balances(Writer w, BalanceVector value) {
        w.field(decimal(value.bookedBefore()))
                .field(decimal(value.heldBefore()))
                .field(decimal(value.bookedAfter()))
                .field(decimal(value.heldAfter()));
    }

    private static BalanceVector balances(Reader r) {
        return new BalanceVector(r.decimal(), r.decimal(), r.decimal(), r.decimal());
    }

    private static void equal(BigDecimal actual, BigDecimal expected, String message) {
        require(actual.compareTo(expected) == 0, message);
    }

    private static void tenant(String value) {
        require(value != null && value.matches("[A-Za-z0-9_-]{1,64}"), "Invalid tenant");
    }

    private static void currency(String value) {
        require(value != null && value.matches("[A-Z]{3}"), "Invalid actual currency");
    }

    private static void timestamp(long value) {
        require(value > 0 && value <= LAST_MILLISECOND, "Invalid finite timestamp");
    }

    private static void uuid(UUID value) {
        require(value != null, "UUID required");
    }

    private static void positive(BigDecimal value) {
        numeric(value);
        require(value.signum() > 0, "Positive transferred amount required");
    }

    private static void numeric(BigDecimal value) {
        require(value != null, "Exact numeric value required");
        if (value.signum() == 0) return;
        require((long) value.precision() - value.scale() <= 20, "NUMERIC(38,18) range exceeded");
        var n = value.stripTrailingZeros();
        require(
                n.scale() <= 18 && (long) n.precision() - n.scale() <= 20,
                "NUMERIC(38,18) range exceeded");
    }

    private static String decimal(BigDecimal value) {
        numeric(value);
        return value.stripTrailingZeros().toPlainString();
    }

    private static String minor(BigDecimal value, int scale) {
        try {
            return value.stripTrailingZeros().movePointRight(scale).toBigIntegerExact().toString();
        } catch (ArithmeticException e) {
            throw invalid("Actual representation cannot express amount");
        }
    }

    private static long integer(String value) {
        require(
                value != null
                        && value.matches("-?(0|[1-9][0-9]*)")
                        && !value.equals("-0")
                        && value.length() <= 20,
                "Noncanonical integer");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw invalid("Integer overflow");
        }
    }

    private static byte[] utf8(String value) {
        try {
            ByteBuffer b =
                    StandardCharsets.UTF_8
                            .newEncoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .encode(java.nio.CharBuffer.wrap(value));
            byte[] bytes = new byte[b.remaining()];
            b.get(bytes);
            return bytes;
        } catch (CharacterCodingException e) {
            throw invalid("Malformed Unicode");
        }
    }

    private static void text(String value, int limit) {
        require(
                value != null
                        && !value.isBlank()
                        && value.length() <= limit
                        && utf8(value).length <= limit,
                "Invalid bounded text");
    }

    private static void require(boolean allowed, String message) {
        if (!allowed) throw invalid(message);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private static final class Writer {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final int limit;

        Writer(int limit) {
            this.limit = limit;
        }

        Writer field(Object value) {
            byte[] data = value == null ? null : utf8(value.toString());
            int length = data == null ? -1 : data.length;
            require(
                    length <= ACCEPTED_LIMIT
                            && bytes.size() + 4L + Math.max(0, length) <= limit * 3L / 4,
                    "Canonical field or message too large");
            bytes.writeBytes(ByteBuffer.allocate(4).putInt(length).array());
            if (data != null) bytes.writeBytes(data);
            return this;
        }

        CanonicalForm finish(String encoding) {
            byte[] content = bytes.toByteArray();
            try {
                return new CanonicalForm(
                        encoding,
                        Base64.getEncoder().encodeToString(content),
                        HexFormat.of()
                                .formatHex(MessageDigest.getInstance("SHA-256").digest(content)));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static final class Reader {
        private final ByteBuffer bytes;

        Reader(String encoded, int limit) {
            require(
                    encoded != null && encoded.length() <= limit && encoded.length() % 4 == 0,
                    "Invalid bounded Base64");
            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(encoded);
            } catch (IllegalArgumentException e) {
                throw invalid("Malformed Base64");
            }
            require(
                    Base64.getEncoder().encodeToString(decoded).equals(encoded),
                    "Noncanonical Base64");
            bytes = ByteBuffer.wrap(decoded);
        }

        String field() {
            return field(2048);
        }

        String field(int limit) {
            require(bytes.remaining() >= 4, "Truncated field length");
            int length = bytes.getInt();
            if (length == -1) return null;
            require(
                    length >= 0 && length <= limit && length <= bytes.remaining(),
                    "Invalid bounded field length");
            ByteBuffer slice = bytes.slice();
            slice.limit(length);
            bytes.position(bytes.position() + length);
            try {
                return StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(slice)
                        .toString();
            } catch (CharacterCodingException e) {
                throw invalid("Malformed UTF-8");
            }
        }

        void encoding(String expected) {
            require(expected.equals(field()), "Unknown encoding");
        }

        UUID nullableUuid() {
            String value = field();
            if (value == null) return null;
            try {
                UUID result = UUID.fromString(value);
                require(result.toString().equals(value), "Noncanonical UUID");
                return result;
            } catch (IllegalArgumentException e) {
                throw invalid("Invalid canonical UUID");
            }
        }

        UUID uuid() {
            UUID result = nullableUuid();
            require(result != null, "UUID required");
            return result;
        }

        long number() {
            return integer(field());
        }

        BigDecimal decimal() {
            String value = field();
            require(
                    value != null
                            && value.length() <= 64
                            && value.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?"),
                    "Invalid exact decimal");
            BigDecimal result = new BigDecimal(value);
            require(FundsReceiptCodec.decimal(result).equals(value), "Noncanonical decimal");
            return result;
        }

        <E extends Enum<E>> E enumeration(Class<E> type) {
            String value = field();
            require(value != null, "Enum required");
            try {
                return Enum.valueOf(type, value);
            } catch (IllegalArgumentException e) {
                throw invalid("Unknown enum");
            }
        }

        void end() {
            require(!bytes.hasRemaining(), "Trailing canonical data");
        }
    }
}
