package io.fireflysoftware.core.banking.ledger.models.entities.funds;

import io.r2dbc.postgresql.codec.Json;

import lombok.*;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("ledger_funds_operation")
public class FundsOperation {
    @Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_EMPTY)
    private FundsOperationId id;

    private String kind;
    private String fingerprint;
    private UUID reservationId;
    private UUID postingId;
    private UUID sourceAccountId;
    private UUID destinationAccountId;
    private Boolean closedWithoutEffect;
    private UUID closureReceiptId;
    private OffsetDateTime closureObservedAt;
    private Long notAfterEpochMillis;
    private Json canonicalIntent;
    private String closedPostingKey;
}
