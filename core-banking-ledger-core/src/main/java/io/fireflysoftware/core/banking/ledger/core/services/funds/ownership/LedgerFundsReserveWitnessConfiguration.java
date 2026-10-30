/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.core.services.funds.ownership;

import io.fireflysoftware.core.banking.ledger.core.services.core.LedgerAuditRecordService;
import io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.impl.LedgerFundsReserveWitnessServiceImpl;
import io.fireflysoftware.core.banking.ledger.models.config.LedgerOwningMutationResources;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

/** Optional reader only; no first-capture provider, installer or HTTP path is activated. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="firefly.ledger",name={"owning-mutations.enabled","audit-capture.enabled","reserve-completed-witness.enabled"},havingValue="true")
public class LedgerFundsReserveWitnessConfiguration {
    @Bean
    LedgerFundsReserveWitnessService ledgerFundsReserveWitnessService(LedgerOwningMutationResources resources,
            LedgerAuditRecordService records,LedgerMutationTime time) {
        return new LedgerFundsReserveWitnessServiceImpl(resources,records,time);
    }
}
