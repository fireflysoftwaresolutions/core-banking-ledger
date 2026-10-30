package io.fireflysoftware.core.banking.ledger.interfaces.dtos.core;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;

@Getter
@Setter
public class LedgerReversalRequestDTO {
    @NotBlank
    private String idempotencyKey;
}
