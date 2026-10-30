package io.fireflysoftware.core.banking.ledger.core.services.funds;

import static io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsReceiptContracts.*;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts;

/** Compatibility entry point for callers of the former core implementation. */
public final class FundsReceiptCodec {
    public static final String INTENT_ENCODING =
            io.fireflysoftware.core.banking.ledger.interfaces.protocol.receipt.FundsReceiptCodec.INTENT_ENCODING;
    public static final String ACCEPTED_ENCODING =
            io.fireflysoftware.core.banking.ledger.interfaces.protocol.receipt.FundsReceiptCodec.ACCEPTED_ENCODING;
    public static final String RECEIPT_ENCODING =
            io.fireflysoftware.core.banking.ledger.interfaces.protocol.receipt.FundsReceiptCodec.RECEIPT_ENCODING;

    private FundsReceiptCodec() {}

    public static String reserveOperationFingerprint(FundsContracts.Reserve input) {
        return io.fireflysoftware.core.banking.ledger.interfaces.protocol.receipt.FundsReceiptCodec.reserveOperationFingerprint(input);
    }

    public static CanonicalForm canonicalIntent(String tenant, FundsContracts.Reserve intent) {
        return io.fireflysoftware.core.banking.ledger.interfaces.protocol.receipt.FundsReceiptCodec.canonicalIntent(tenant, intent);
    }

    public static CanonicalForm canonicalAccepted(AcceptedReserve value) {
        return io.fireflysoftware.core.banking.ledger.interfaces.protocol.receipt.FundsReceiptCodec.canonicalAccepted(value);
    }

    public static CanonicalForm canonicalReceipt(MonetaryReceipt value) {
        return io.fireflysoftware.core.banking.ledger.interfaces.protocol.receipt.FundsReceiptCodec.canonicalReceipt(value);
    }

    public static AcceptedReserve decodeAccepted(String encoded) {
        return io.fireflysoftware.core.banking.ledger.interfaces.protocol.receipt.FundsReceiptCodec.decodeAccepted(encoded);
    }

    public static MonetaryReceipt decodeReceipt(String encoded) {
        return io.fireflysoftware.core.banking.ledger.interfaces.protocol.receipt.FundsReceiptCodec.decodeReceipt(encoded);
    }

    public static AcceptedReserveEnvelope envelope(AcceptedReserve value) {
        return io.fireflysoftware.core.banking.ledger.interfaces.protocol.receipt.FundsReceiptCodec.envelope(value);
    }

    public static MonetaryReceiptEnvelope envelope(MonetaryReceipt value) {
        return io.fireflysoftware.core.banking.ledger.interfaces.protocol.receipt.FundsReceiptCodec.envelope(value);
    }

    public static void verify(AcceptedReserveEnvelope envelope) {
        io.fireflysoftware.core.banking.ledger.interfaces.protocol.receipt.FundsReceiptCodec.verify(envelope);
    }

    public static void verify(MonetaryReceiptEnvelope envelope) {
        io.fireflysoftware.core.banking.ledger.interfaces.protocol.receipt.FundsReceiptCodec.verify(envelope);
    }

    public static void validateReversal(MonetaryReceipt reversal, MonetaryReceipt original) {
        io.fireflysoftware.core.banking.ledger.interfaces.protocol.receipt.FundsReceiptCodec.validateReversal(reversal, original);
    }
}
