package io.fireflysoftware.core.banking.ledger.core.services.funds;

import static io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsReceiptContracts.*;

import org.fireflyframework.security.api.domain.SecurityPrincipal;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FundsReceiptService {
    Mono<EvidenceView> read(SecurityPrincipal principal, String tenant, UUID id);
}
