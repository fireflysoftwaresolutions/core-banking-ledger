package io.fireflysoftware.core.banking.ledger.core.services.funds;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.core.*;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.*;

import org.fireflyframework.security.api.domain.SecurityPrincipal;

import reactor.core.publisher.Mono;

import java.util.*;

public interface FundsMoneyService {
    Mono<FundingReceipt> fund(SecurityPrincipal p, String tenant, Funding input);

    Mono<Reservation> reserve(SecurityPrincipal p, String tenant, Reserve input);

    Mono<Reservation> reservation(SecurityPrincipal p, String tenant, UUID id);

    Mono<Reservation> reservationByOperation(SecurityPrincipal p, String tenant, UUID operation);

    Mono<Reservation> action(SecurityPrincipal p, String tenant, UUID id, Command input);
}
