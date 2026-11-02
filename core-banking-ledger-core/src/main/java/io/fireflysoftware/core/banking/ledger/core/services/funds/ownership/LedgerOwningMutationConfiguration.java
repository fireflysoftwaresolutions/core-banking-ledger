/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.core.services.funds.ownership;

import io.fireflysoftware.core.banking.ledger.core.services.core.LedgerPostingService;
import io.fireflysoftware.core.banking.ledger.core.services.core.LedgerAuditRecordService;
import org.fireflyframework.security.spi.SecurityContextPort;
import io.fireflysoftware.core.banking.ledger.core.services.core.impl.LedgerPostingServiceImpl;
import io.fireflysoftware.core.banking.ledger.core.services.funds.FundsMoneyService;
import io.fireflysoftware.core.banking.ledger.core.services.funds.FundsExpiryCandidateService;
import io.fireflysoftware.core.banking.ledger.core.services.funds.impl.FundsMoneyServiceImpl;
import io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.impl.LedgerOwningMutationServiceImpl;
import io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.impl.LedgerOwningExpiryServiceImpl;
import io.fireflysoftware.core.banking.ledger.models.config.LedgerOwningMutationResources;
import io.fireflysoftware.core.banking.ledger.models.config.LedgerOwningMutationResourcesConfiguration;
import java.time.Duration;
import org.fireflyframework.core.audit.AuditAppendAuthority;
import org.fireflyframework.core.transaction.CleanupPolicy;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

/** Explicit default-off bridge; original bean names, repository fragments and REQUIRED advice remain. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="firefly.ledger.owning-mutations.enabled",havingValue="true")
@Import({LedgerOwningMutationResourcesConfiguration.class, LedgerAuditCaptureConfiguration.class})
public class LedgerOwningMutationConfiguration {
    @Bean
    LedgerOwningMutationService ledgerOwningMutationService(LedgerOwningMutationResources resources,
            @Qualifier("ledgerPostingService") LedgerPostingService postings,
            @Qualifier("fundsMoneyService") FundsMoneyService money,
            LedgerMutationAuditCapture capture, ObjectProvider<AuditAppendAuthority> authority,
            ObjectProvider<LedgerMutationAuditAppender> callLocal, LedgerMutationTime time,
            ObjectProvider<LedgerFundsFirstCaptureProvider> firstCapture, ObjectProvider<SecurityContextPort> principals,
            ObjectProvider<LedgerAuditRecordService> records, ObjectProvider<LedgerFundsReserveWitnessService> witnesses,
            @Value("${firefly.ledger.first-reserve-capture.enabled:false}") boolean firstCaptureEnabled,
            @Value("${firefly.ledger.audit-capture.enabled:false}") boolean auditEnabled,
            @Value("${firefly.ledger.reserve-completed-witness.enabled:false}") boolean witnessEnabled) {
        if(!AopUtils.isAopProxy(postings)||!AopUtils.isAopProxy(money)
                ||AopUtils.getTargetClass(postings)!=LedgerPostingServiceImpl.class
                ||AopUtils.getTargetClass(money)!=FundsMoneyServiceImpl.class)
            throw new IllegalArgumentException("Original advised Ledger services required");
        if(firstCaptureEnabled) {
            if(!auditEnabled||!witnessEnabled)throw new IllegalStateException("FIRST reserve capture requires owning audit and witness capabilities");
            witnesses.getObject();
            return new LedgerOwningMutationServiceImpl(resources.bounded(),postings,money,callLocal.getObject(),
                    new CleanupPolicy(Duration.ofSeconds(2),Duration.ofSeconds(2)),firstCapture.getObject(),principals.getObject(),records.getObject(),time);
        }
        var appender=callLocal.getIfAvailable();
        if(appender!=null)return new LedgerOwningMutationServiceImpl(resources.bounded(),postings,money,appender,
                new CleanupPolicy(Duration.ofSeconds(2),Duration.ofSeconds(2)));
        return new LedgerOwningMutationServiceImpl(resources.bounded(),postings,money,
                resources.audit(authority.getObject(),time.clock(),time.nanos()),capture,new CleanupPolicy(Duration.ofSeconds(2),Duration.ofSeconds(2)));
    }
    @Bean
    LedgerOwningExpiryService ledgerOwningExpiryService(LedgerOwningMutationResources resources,
            @Qualifier("fundsExpiryCandidateService") FundsExpiryCandidateService candidates,
            @Qualifier("ledgerOwningMutationService") LedgerOwningMutationService mutations) {
        return new LedgerOwningExpiryServiceImpl(resources.bounded(),candidates,mutations,
                new CleanupPolicy(Duration.ofSeconds(2),Duration.ofSeconds(2)));
    }
}
