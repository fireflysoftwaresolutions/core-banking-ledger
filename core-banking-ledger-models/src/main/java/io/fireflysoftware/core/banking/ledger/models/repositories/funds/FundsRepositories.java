package io.fireflysoftware.core.banking.ledger.models.repositories.funds;

import org.springframework.stereotype.Component;

@Component
public record FundsRepositories(
        FundsAccountRepository accounts,
        FundsReservationRepository reservations,
        FundsOperationRepository operations,
        FundsActionRepository actions,
        FundsControlRepository controls,
        FundsEventRepository events,
        FundsOutboxRepository outbox,
        LedgerPostingClaimRepository postings,
        FundsReserveIntentRepository originals,
        FundsMonetaryReceiptRepository receipts,
        FundsMutationRepository writes,
        FundsNativeRepository nativeOps) {}
