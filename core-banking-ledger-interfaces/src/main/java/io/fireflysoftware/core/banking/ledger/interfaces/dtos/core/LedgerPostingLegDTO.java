package io.fireflysoftware.core.banking.ledger.interfaces.dtos.core;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class LedgerPostingLegDTO {
    private UUID accountId;
    private UUID accountSpaceId;
    private String legType;
    private BigDecimal amount;
    private String currency;
    private String description;
}
