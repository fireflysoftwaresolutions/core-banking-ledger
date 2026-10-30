/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.web.funds;

import io.fireflysoftware.core.banking.ledger.core.services.core.LedgerAuditRecordService;
import io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.*;
import io.fireflysoftware.core.banking.ledger.web.funds.impl.LedgerFundsReserveBudgetFilter;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.fireflyframework.web.idempotency.config.IdempotencyProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** FIRST reserve capture has no implicit issuer, audit provider, principal port, or time source. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="firefly.ledger.first-reserve-capture.enabled",havingValue="true")
public class LedgerFundsReserveCaptureConfiguration {
    @Bean
    LedgerFundsReserveBudgetFilter ledgerFundsReserveBudgetFilter(LedgerMutationTime time,
            LedgerOwningMutationService mutations,LedgerFundsFirstCaptureProvider provider,
            SecurityContextPort principals,LedgerMutationAuditAppender audit,LedgerAuditRecordService records,
            LedgerFundsReserveWitnessService witnesses,ObjectProvider<IdempotencyProperties> responseCaches,
            @Value("${firefly.ledger.owning-mutations.enabled:false}") boolean owningEnabled,
            @Value("${firefly.ledger.audit-capture.enabled:false}") boolean auditEnabled,
            @Value("${firefly.ledger.reserve-completed-witness.enabled:false}") boolean witnessEnabled) {
        if(!owningEnabled||!auditEnabled||!witnessEnabled)
            throw new IllegalStateException("FIRST reserve ingress requires owning audit and witness capabilities");
        return new LedgerFundsReserveBudgetFilter(time,responseCaches.orderedStream().toList());
    }
}
