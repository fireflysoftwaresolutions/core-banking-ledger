package io.fireflysoftware.core.banking.ledger.models.entities.funds;

import lombok.*;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("ledger_funds_reserve_intent")
public class FundsReserveIntent {
    @Id private UUID reservationId;
    private String tenantId;
    private UUID operationId;
    private String acceptedCanonical;
    private String acceptedDigest;
}
