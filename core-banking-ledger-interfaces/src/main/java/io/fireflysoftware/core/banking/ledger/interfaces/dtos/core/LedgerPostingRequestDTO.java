package io.fireflysoftware.core.banking.ledger.interfaces.dtos.core;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Getter
@Setter
public class LedgerPostingRequestDTO {
    @NotBlank
    private String idempotencyKey;
    @NotNull
    private LedgerPostingHeaderDTO transaction;
    @NotNull
    private List<LedgerPostingLegDTO> legs;
}
