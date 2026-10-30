package io.fireflysoftware.core.banking.ledger.models.entities.funds;

import lombok.*;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("ledger_funds_monetary_receipt")
public class FundsMonetaryReceipt {
    @Id private UUID receiptId;
    private String tenantId;
    private UUID reservationId;
    private String effectKind;
    private String receiptCanonical;
    private String receiptDigest;
}
