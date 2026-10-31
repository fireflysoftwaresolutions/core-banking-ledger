/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.core.services.funds.ownership;

import java.time.Clock;
import java.time.Duration;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Same caller-owned time pair used by the original admission budget and shared audit caps. */
public record LedgerMutationTime(Clock clock, LongSupplier nanos) {
    public LedgerMutationTime { Objects.requireNonNull(clock); Objects.requireNonNull(nanos); }
    public R2dbcOperationBudget originalBudget() {
        long before=nanos.getAsLong();var observed=clock.instant();var maximum=Duration.ofSeconds(5);
        return R2dbcOperationBudget.observed(observed,observed.plus(maximum),before,maximum,clock,nanos);
    }
}
