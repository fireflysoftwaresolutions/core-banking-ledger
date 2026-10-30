/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.core.services.funds.ownership;

import java.util.UUID;
import org.fireflyframework.core.audit.TransactionalAuditAppender.ProvisionalAppend;
import org.fireflyframework.core.transaction.RootTransactionScope;
import org.fireflyframework.kernel.audit.AuditEvent;
import reactor.core.publisher.Mono;

/** Required owner authority and native business/audit coupling; there is no default provider. */
public interface LedgerMutationAuditCapture {
    Mono<AuditEvent> capture(RootTransactionScope scope, LedgerMutation mutation);
    Mono<UUID> pair(RootTransactionScope scope, LedgerMutation mutation, ProvisionalAppend append);
}
