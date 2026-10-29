/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.core.services.funds.ownership;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.core.*;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.*;
import java.util.UUID;
import org.fireflyframework.security.api.domain.SecurityPrincipal;

/** Exact original request and provisional business result, never an audit grant. */
public sealed interface LedgerMutation {
    record Created(LedgerPostingRequestDTO request, LedgerPostingDTO result) implements LedgerMutation {}
    record Confirmed(UUID postingId, LedgerPostingDTO result) implements LedgerMutation {}
    record Released(UUID postingId, LedgerPostingDTO result) implements LedgerMutation {}
    record Reversed(UUID postingId, String key, LedgerPostingDTO result) implements LedgerMutation {}
    record Funded(SecurityPrincipal principal, String tenant, Funding request, FundingReceipt result) implements LedgerMutation {}
    record Reserved(SecurityPrincipal principal, String tenant, Reserve request, Reservation result) implements LedgerMutation {}
    record Acted(SecurityPrincipal principal, String tenant, UUID reservationId, Command request, Reservation result) implements LedgerMutation {}
}
