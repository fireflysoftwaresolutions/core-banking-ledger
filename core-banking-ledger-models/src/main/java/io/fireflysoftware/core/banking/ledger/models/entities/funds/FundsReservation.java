package io.fireflysoftware.core.banking.ledger.models.entities.funds;

import lombok.*;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("ledger_funds_reservation")
public class FundsReservation {
    @Id private UUID reservationId;
    private String tenantId;
    private UUID operationId;
    private UUID sourceAccountId;
    private UUID destinationAccountId;
    private BigDecimal amount;
    private String currency;
    private String state;
    private OffsetDateTime expiresAt;
    private UUID postingId;
    private UUID reversalId;
    private Long version;
    @org.springframework.data.annotation.ReadOnlyProperty private Short receiptContractVersion;
}
