package io.fireflysoftware.core.banking.ledger.models.entities.funds;

import java.util.UUID;

public record FundsControlId(String tenantId, UUID actionId) {}
