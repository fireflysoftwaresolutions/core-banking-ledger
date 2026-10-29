/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.core.services.funds.ownership;

import io.fireflysoftware.core.banking.ledger.core.services.core.LedgerAuditRecordService;
import io.fireflysoftware.core.banking.ledger.core.services.core.impl.LedgerAuditRecordServiceImpl;
import io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.impl.LedgerMutationAuditCaptureImpl;
import io.fireflysoftware.core.banking.ledger.models.config.LedgerOwningMutationResources;
import io.fireflysoftware.core.banking.ledger.models.repositories.core.*;
import io.fireflysoftware.core.banking.ledger.models.repositories.funds.FundsRepositories;
import io.fireflysoftware.core.banking.ledger.models.repositories.leg.TransactionLegRepository;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;

/** Additive opt-in only. Current authority and original native anchors have no default implementation. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="firefly.ledger",name={"owning-mutations.enabled","audit-capture.enabled"},havingValue="true")
public class LedgerAuditCaptureConfiguration {
    @Bean
    LedgerAuditRecordService ledgerAuditRecordService(LedgerOwningMutationResources resources, FundsRepositories funds,
            TransactionRepository transactions, TransactionLegRepository legs, TransactionStatusHistoryRepository history) {
        return new LedgerAuditRecordServiceImpl(resources.bounded(),funds,transactions,legs,history);
    }
    @Bean
    @ConditionalOnMissingBean(LedgerMutationAuditCapture.class)
    LedgerMutationAuditCapture ledgerMutationAuditCapture(LedgerOwningMutationResources resources,
            LedgerAuditRecordService records, FundsRepositories funds, SecurityContextPort principals,
            LedgerAuditAdmission admission, LedgerMutationTime time) {
        return new LedgerMutationAuditCaptureImpl(resources.bounded(),records,funds.nativeOps(),principals,admission,time.clock(),time.nanos());
    }
}
