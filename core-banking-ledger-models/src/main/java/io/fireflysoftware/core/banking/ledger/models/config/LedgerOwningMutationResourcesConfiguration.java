/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.models.config;

import java.util.Map;
import org.fireflyframework.core.transaction.OwnedLeaseConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.ReactiveTransactionManager;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="firefly.ledger.owning-mutations.enabled",havingValue="true")
public class LedgerOwningMutationResourcesConfiguration {
    @Bean
    LedgerOwningMutationResources ledgerOwningMutationResources(
            DatabaseClient client,
            @Qualifier("r2dbcEntityTemplate") R2dbcEntityTemplate operations,
            R2dbcTransactionManager manager,
            Map<String,ReactiveTransactionManager> managers) {
        if(!(client.getConnectionFactory() instanceof OwnedLeaseConnectionFactory factory)
                ||managers.size()!=1||managers.values().iterator().next()!=manager)
            throw new IllegalArgumentException("One original Ledger owning resource graph required");
        return new LedgerOwningMutationResources(factory,client,operations,manager);
    }
}
