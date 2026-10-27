package io.fireflysoftware.core.banking.ledger.models.entities.funds;

import lombok.*;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("ledger_posting_request")
public class LedgerPostingClaim {
    @Id private String idempotencyKey;
    private String inputFingerprint;
    private UUID transactionId;
}
