package io.fireflysoftware.core.banking.ledger.core.services.core.impl;

import io.fireflysoftware.core.banking.ledger.models.entities.core.Transaction;
import io.fireflysoftware.core.banking.ledger.models.repositories.core.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.util.UUID;



@Component
@RequiredArgsConstructor
public class ManagedPostingGuard {
    private final TransactionRepository transactions;

    public Mono<Void> requireLegacy(UUID id) {
        return transactions.findById(id).doOnNext(ManagedPostingGuard::requireLegacy).then();
    }

    static void requireLegacy(Transaction transaction) {
        if (transaction.isManagedPosting()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Managed postings can only change through ledger posting lifecycle commands");
        }
    }
}
