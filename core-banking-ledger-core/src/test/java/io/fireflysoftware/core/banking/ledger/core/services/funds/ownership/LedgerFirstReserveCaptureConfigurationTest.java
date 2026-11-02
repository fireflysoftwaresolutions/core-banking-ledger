package io.fireflysoftware.core.banking.ledger.core.services.funds.ownership;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.core.env.MapPropertySource;
import reactor.core.publisher.Mono;

class LedgerFirstReserveCaptureConfigurationTest {
    static LedgerOwningMutationConfigurationTest.Graph enabled(boolean provider,boolean principals,boolean appender) {
        var g=new LedgerOwningMutationConfigurationTest.Graph(true,true,true);
        g.context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("first",Map.of(
                "firefly.ledger.first-reserve-capture.enabled","true","firefly.ledger.audit-capture.enabled","true",
                "firefly.ledger.reserve-completed-witness.enabled","true")));
        g.context.register(LedgerFundsReserveWitnessConfiguration.class);
        if(principals)g.context.registerBean(SecurityContextPort.class,()->()->Mono.just(LedgerFundsFirstCaptureTest.PRINCIPAL));
        if(appender)g.context.registerBean(LedgerMutationAuditAppender.class,()->(scope,mutation)->Mono.error(new IllegalStateException("No source-test audit authority")));
        if(provider)g.context.registerBean(LedgerFundsFirstCaptureProvider.class,()->new RefusingProvider());
        return g;
    }
    static final class RefusingProvider implements LedgerFundsFirstCaptureProvider {
        final AtomicInteger authorizations=new AtomicInteger();R2dbcOperationBudget received;
        public Mono<Authorization> authorize(R2dbcOperationBudget original,Command command){received=original;authorizations.incrementAndGet();return Mono.error(new IllegalStateException("No qualified FIRST authority"));}
        public Mono<CompletedCommand> requireCompleted(org.fireflyframework.core.transaction.RootTransactionScope scope,Authorization authorization,LedgerMutation.Reserved mutation,LedgerAuditEvidence evidence){return Mono.error(new AssertionError("No SQL permitted in refused admission"));}
        public Mono<io.fireflysoftware.core.banking.ledger.models.entities.funds.FundsAuditAnchor> requireOriginalAnchor(org.fireflyframework.core.transaction.RootTransactionScope scope,Authorization authorization,CompletedCommand completed,LedgerAuditEvidence evidence){return Mono.error(new AssertionError("No anchor permitted in refused admission"));}
    }
    @Test void enabledFirstCaptureRequiresARealProviderInsteadOfTheLegacyReserveConstructor(){try(var g=enabled(false,true,true)){assertThrows(BeansException.class,g.context::refresh);assertEquals(0,g.driver.creates);}}
    @Test void enabledFirstCaptureRequiresTheTrustedPrincipalPort(){try(var g=enabled(true,false,true)){assertThrows(BeansException.class,g.context::refresh);assertEquals(0,g.driver.creates);}}
    @Test void enabledFirstCaptureCannotFallBackToTheSharedLegacyAuditAuthority(){try(var g=enabled(true,true,false)){assertThrows(BeansException.class,g.context::refresh);assertEquals(0,g.driver.creates);}}
    @Test void explicitConstructorUsesTheSameOriginalBudgetBeforeAnyConnectionAcquisition(){
        try(var g=enabled(true,true,true)){
            g.context.refresh();var provider=(RefusingProvider)g.context.getBean(LedgerFundsFirstCaptureProvider.class);var time=g.context.getBean(LedgerMutationTime.class);var original=time.originalBudget();
            var error=assertThrows(IllegalStateException.class,()->g.context.getBean(LedgerOwningMutationService.class).reserveFirstCapture(original,LedgerFundsFirstCaptureTest.PRINCIPAL,LedgerFundsFirstCaptureTest.TENANT,LedgerFundsFirstCaptureTest.INPUT).block());
            assertEquals("No qualified FIRST authority",error.getMessage());assertSame(original,provider.received);assertEquals(1,provider.authorizations.get());assertEquals(0,g.driver.creates);
        }
    }
    @Test void incompatibleCallerTimeCannotBeMaskedByANewBudgetOrAuthorityFallback(){
        try(var g=enabled(true,true,true)){
            g.context.refresh();var provider=(RefusingProvider)g.context.getBean(LedgerFundsFirstCaptureProvider.class);var time=g.context.getBean(LedgerMutationTime.class);var alien=new LedgerMutationTime(time.clock(),System::nanoTime).originalBudget();
            assertThrows(IllegalArgumentException.class,()->g.context.getBean(LedgerOwningMutationService.class).reserveFirstCapture(alien,LedgerFundsFirstCaptureTest.PRINCIPAL,LedgerFundsFirstCaptureTest.TENANT,LedgerFundsFirstCaptureTest.INPUT).block());
            assertEquals(0,provider.authorizations.get());assertEquals(0,g.driver.creates);
        }
    }
}
