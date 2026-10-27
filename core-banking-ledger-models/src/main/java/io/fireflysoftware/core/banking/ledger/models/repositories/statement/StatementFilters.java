package io.fireflysoftware.core.banking.ledger.models.repositories.statement;

import io.fireflysoftware.core.banking.ledger.models.entities.statement.Statement;

import org.springframework.data.domain.Pageable;

import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.util.UUID;

public interface StatementFilters {
    Flux<Statement> findByAccountIdAndDateRange(
            UUID accountId, LocalDate startDate, LocalDate endDate, Pageable pageable);

    Flux<Statement> findByAccountSpaceIdAndDateRange(
            UUID accountSpaceId, LocalDate startDate, LocalDate endDate, Pageable pageable);
}
