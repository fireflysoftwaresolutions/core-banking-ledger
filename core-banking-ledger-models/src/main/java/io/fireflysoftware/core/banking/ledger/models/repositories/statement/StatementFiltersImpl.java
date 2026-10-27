package io.fireflysoftware.core.banking.ledger.models.repositories.statement;

import io.fireflysoftware.core.banking.ledger.models.entities.statement.Statement;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;

import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.util.UUID;

public class StatementFiltersImpl implements StatementFilters {
    private final R2dbcEntityOperations operations;

    public StatementFiltersImpl(R2dbcEntityOperations operations) {
        this.operations = operations;
    }

    @Override
    public Flux<Statement> findByAccountIdAndDateRange(
            UUID accountId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        if (accountId == null || endDate == null || startDate == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("account_id").is(accountId));
        criteria = criteria.and(Criteria.where("start_date").greaterThanOrEquals(startDate));
        criteria = criteria.and(Criteria.where("end_date").lessThanOrEquals(endDate));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        query = query.sort(Sort.by(Sort.Order.desc("generation_date")));
        return operations.select(query, Statement.class);
    }

    @Override
    public Flux<Statement> findByAccountSpaceIdAndDateRange(
            UUID accountSpaceId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        if (accountSpaceId == null || endDate == null || startDate == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("account_space_id").is(accountSpaceId));
        criteria = criteria.and(Criteria.where("start_date").greaterThanOrEquals(startDate));
        criteria = criteria.and(Criteria.where("end_date").lessThanOrEquals(endDate));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        query = query.sort(Sort.by(Sort.Order.desc("generation_date")));
        return operations.select(query, Statement.class);
    }
}
