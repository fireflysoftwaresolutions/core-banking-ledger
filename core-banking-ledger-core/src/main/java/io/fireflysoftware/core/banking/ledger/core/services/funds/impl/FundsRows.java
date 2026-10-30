package io.fireflysoftware.core.banking.ledger.core.services.funds.impl;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.*;
import io.fireflysoftware.core.banking.ledger.models.entities.funds.*;

final class FundsRows {
    private FundsRows() {}

    static Account account(FundsAccount a) {
        return new Account(
                a.getAccountId(),
                a.getEnrollmentId(),
                a.getContractId(),
                a.getCurrency(),
                a.getCurrencyScale(),
                a.getCurrencyPolicy(),
                Kind.valueOf(a.getKind()),
                AccountState.valueOf(a.getState()),
                a.getBooked(),
                a.getHeld(),
                a.getBooked().subtract(a.getHeld()),
                a.getVersion());
    }

    static Reservation reservation(FundsReservation r) {
        return new Reservation(
                r.getReservationId(),
                r.getOperationId(),
                r.getSourceAccountId(),
                r.getDestinationAccountId(),
                r.getAmount(),
                r.getCurrency(),
                ReservationState.valueOf(r.getState()),
                r.getExpiresAt().toInstant(),
                r.getPostingId(),
                r.getReversalId(),
                r.getVersion());
    }
}
