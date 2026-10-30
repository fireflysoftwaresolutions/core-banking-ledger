/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.models.config;

import java.time.Clock;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.fireflyframework.core.audit.*;
import org.fireflyframework.core.audit.impl.SpringDataTransactionalAuditAppender;
import org.fireflyframework.core.transaction.*;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import io.fireflysoftware.core.banking.ledger.models.repositories.funds.FundsReserveCompletedWitnessRepository;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;

/** Retains the caller's complete graph and converter/client behavior without replacing any instance. */
public final class LedgerOwningMutationResources {
    private final BoundedR2dbcResources resources;
    private final DatabaseClient client;
    private final R2dbcEntityTemplate operations;
    private final R2dbcTransactionManager manager;
    public LedgerOwningMutationResources(OwnedLeaseConnectionFactory factory, DatabaseClient client,
            R2dbcEntityTemplate operations, R2dbcTransactionManager manager) {
        resources=BoundedR2dbcResources.verify(factory,client,operations,manager);
        this.client=client; this.operations=operations; this.manager=manager;
    }
    public BoundedR2dbcResources bounded() { return resources; }
    public FundsReserveCompletedWitnessRepository completedReserveWitnesses() {
        return new R2dbcRepositoryFactory(operations).getRepository(FundsReserveCompletedWitnessRepository.class);
    }
    public TransactionalAuditAppender audit(AuditAppendAuthority authority, Clock clock, LongSupplier nanos) {
        return new SpringDataTransactionalAuditAppender(resources,client,operations,manager,authority,clock,nanos,UUID::randomUUID);
    }
}
