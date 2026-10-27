package io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.UUID;

public final class FundsReceiptContracts {
    private FundsReceiptContracts() {}

    public enum EffectKind { COMMIT, REVERSE }
    public enum EvidenceStatus { RETAINED, HISTORICAL_UNAVAILABLE }

    public record EnrollmentSnapshot(UUID accountId, UUID enrollmentId, UUID contractId,
            String currency, int currencyScale, String currencyPolicy, FundsContracts.Kind kind) {}

    public record AcceptedReserve(String tenant, UUID reservationId, FundsContracts.Reserve intent,
            long intentRecordedAtEpochMillis, long expiresAtEpochMillis,
            EnrollmentSnapshot source, EnrollmentSnapshot destination) {}

    public record BalanceVector(BigDecimal bookedBefore, BigDecimal heldBefore,
            BigDecimal bookedAfter, BigDecimal heldAfter) {}

    public record MoneyEffect(BigDecimal amount, String currency, EnrollmentSnapshot source,
            EnrollmentSnapshot destination, String sourceMinorUnits, String destinationMinorUnits,
            UUID fromAccountId, UUID toAccountId, BalanceVector sourceBalances,
            BalanceVector destinationBalances) {}

    public record Actor(String issuer, String subject) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MonetaryReceipt(UUID receiptId, UUID eventId, String tenant, UUID reservationId,
            UUID operationId, EffectKind effectKind, UUID actionId, AcceptedReserve acceptedReserve,
            UUID postingId, UUID originalCommitReceiptId, UUID originalPostingId,
            long effectRecordedAtEpochMillis, MoneyEffect effect, Actor actor) {}

    public record CanonicalForm(String encoding, String canonical, String digest) {}
    public record AcceptedReserveEnvelope(AcceptedReserve value, CanonicalForm canonical) {}
    public record MonetaryReceiptEnvelope(MonetaryReceipt value, CanonicalForm canonical) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record EvidenceView(EvidenceStatus evidenceStatus, AcceptedReserveEnvelope acceptedReserve,
            MonetaryReceiptEnvelope commit, MonetaryReceiptEnvelope reversal,
            FundsContracts.Reservation currentReservation) {}
}
