package io.fireflysoftware.core.banking.ledger.core.services.funds;

import static org.assertj.core.api.Assertions.*;

import io.fireflysoftware.core.banking.ledger.models.entities.funds.FundsAdmission;

import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.UUID;

class FundsReceiptRepositoryTest {
    @Test
    void nativeProjectionPreservesOwnerTimeAndLegacyClassification() {
        var v =
                new FundsAdmission(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1700000000000L,
                        OffsetDateTime.ofInstant(
                                Instant.ofEpochMilli(1700000300000L), ZoneOffset.UTC),
                        (short) 0);
        assertThat(v.contractVersion()).isZero();
        assertThat(v.recordedAt().toEpochMilli()).isEqualTo(1700000000000L);
    }
}
