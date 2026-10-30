package io.fireflysoftware.core.banking.ledger.models.entities.funds;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FundsAdmission(
        UUID commandId,
        UUID reservationId,
        long recordedAtEpochMillis,
        OffsetDateTime expiresAt,
        short contractVersion) {
    public Instant recordedAt() {
        return Instant.ofEpochMilli(recordedAtEpochMillis);
    }
}
