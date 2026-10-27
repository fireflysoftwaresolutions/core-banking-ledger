package io.fireflysoftware.core.banking.ledger.interfaces.dtos.core;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.leg.TransactionLegDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LedgerPostingDTO {
    private TransactionDTO transaction;
    private List<TransactionLegDTO> legs;
    private boolean replayed;
}
