/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.core.services.funds.ownership;

import java.time.Clock;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Same caller-owned time pair used by the original admission budget and shared audit caps. */
public record LedgerMutationTime(Clock clock, LongSupplier nanos) {
    public LedgerMutationTime { Objects.requireNonNull(clock); Objects.requireNonNull(nanos); }
}
