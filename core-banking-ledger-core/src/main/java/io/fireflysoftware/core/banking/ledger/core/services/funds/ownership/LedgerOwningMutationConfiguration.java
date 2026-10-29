/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.core.services.funds.ownership;

import io.fireflysoftware.core.banking.ledger.core.services.core.LedgerPostingService;
import io.fireflysoftware.core.banking.ledger.core.services.core.impl.LedgerPostingServiceImpl;
import io.fireflysoftware.core.banking.ledger.core.services.funds.FundsMoneyService;
import io.fireflysoftware.core.banking.ledger.core.services.funds.impl.FundsMoneyServiceImpl;
import io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.impl.LedgerOwningMutationServiceImpl;
import io.fireflysoftware.core.banking.ledger.models.config.LedgerOwningMutationResources;
import io.fireflysoftware.core.banking.ledger.models.config.LedgerOwningMutationResourcesConfiguration;
import java.time.Duration;
import org.fireflyframework.core.audit.AuditAppendAuthority;
import org.fireflyframework.core.transaction.CleanupPolicy;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

/** Explicit default-off bridge; original bean names, repository fragments and REQUIRED advice remain. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="firefly.ledger.owning-mutations.enabled",havingValue="true")
@Import(LedgerOwningMutationResourcesConfiguration.class)
public class LedgerOwningMutationConfiguration {
    @Bean
    LedgerOwningMutationService ledgerOwningMutationService(LedgerOwningMutationResources resources,
            @Qualifier("ledgerPostingService") LedgerPostingService postings,
            @Qualifier("fundsMoneyService") FundsMoneyService money,
            LedgerMutationAuditCapture capture, AuditAppendAuthority authority, LedgerMutationTime time) {
        if(!AopUtils.isAopProxy(postings)||!AopUtils.isAopProxy(money)
                ||AopUtils.getTargetClass(postings)!=LedgerPostingServiceImpl.class
                ||AopUtils.getTargetClass(money)!=FundsMoneyServiceImpl.class)
            throw new IllegalArgumentException("Original advised Ledger services required");
        return new LedgerOwningMutationServiceImpl(resources.bounded(),postings,money,
                resources.audit(authority,time.clock(),time.nanos()),capture,new CleanupPolicy(Duration.ofSeconds(2),Duration.ofSeconds(2)));
    }
}
