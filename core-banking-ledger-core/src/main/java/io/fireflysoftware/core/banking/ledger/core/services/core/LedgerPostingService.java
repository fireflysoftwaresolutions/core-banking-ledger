package io.fireflysoftware.core.banking.ledger.core.services.core;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.core.LedgerPostingDTO;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.core.LedgerPostingRequestDTO;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface LedgerPostingService {
    Mono<LedgerPostingDTO> create(LedgerPostingRequestDTO request);
    Mono<LedgerPostingDTO> confirm(UUID id);
    Mono<LedgerPostingDTO> release(UUID id);
    Mono<LedgerPostingDTO> reverse(UUID originalId, String key);
}
