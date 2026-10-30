package io.fireflysoftware.core.banking.ledger.models.entities.funds;

import java.util.UUID;

public record FundsOperationId(String tenantId, UUID operationId) {}
