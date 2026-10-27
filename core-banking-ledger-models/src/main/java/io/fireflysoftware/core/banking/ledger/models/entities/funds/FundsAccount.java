package io.fireflysoftware.core.banking.ledger.models.entities.funds;

import lombok.*;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("ledger_funds_account")
public class FundsAccount {
    @Id private UUID accountId;
    private String tenantId;
    private UUID enrollmentId;
    private UUID contractId;
    private String currency;
    private Integer currencyScale;
    private String currencyPolicy;
    private String kind;
    private String state;
    private BigDecimal booked;
    private BigDecimal held;
    private Long version;
}
