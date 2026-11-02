package io.fireflysoftware.core.banking.ledger.web.funds;

import static org.junit.jupiter.api.Assertions.*;
import io.fireflysoftware.core.banking.ledger.core.services.core.LedgerAuditRecordService;
import io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.*;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.List;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import reactor.core.publisher.Mono;

class LedgerFundsReserveCaptureConfigurationTest {
    static final String FLAG="firefly.ledger.first-reserve-capture.enabled";
    static final List<Class<?>> REQUIRED=List.of(LedgerMutationTime.class,LedgerOwningMutationService.class,
            LedgerFundsFirstCaptureProvider.class,SecurityContextPort.class,LedgerMutationAuditAppender.class,
            LedgerAuditRecordService.class,LedgerFundsReserveWitnessService.class);
    static Object port(Class<?> type){return type==LedgerMutationTime.class?new LedgerMutationTime(Clock.systemUTC(),System::nanoTime):
            Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(object,method,args)->{
                if(method.getName().equals("toString"))return "refusing-source-port";
                if(method.getName().equals("hashCode"))return System.identityHashCode(object);
                if(method.getName().equals("equals"))return object==args[0];
                return Mono.error(new IllegalStateException("No live provider authority"));
            });}
    @SuppressWarnings({"unchecked","rawtypes"})
    static AnnotationConfigApplicationContext context(boolean enabled,Class<?> omitted,String disabled) throws Exception {
        var context=new AnnotationConfigApplicationContext();var values=new java.util.HashMap<String,Object>();values.put(FLAG,enabled);
        for(var key:List.of("owning-mutations","audit-capture","reserve-completed-witness"))values.put("firefly.ledger."+key+".enabled",!key.equals(disabled));
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("controlled",values));
        for(var type:REQUIRED)if(enabled&&type!=omitted)context.registerBean((Class)type,()->port(type));
        try{context.register(Class.forName("io.fireflysoftware.core.banking.ledger.web.funds.LedgerFundsReserveCaptureConfiguration"));}
        catch(ClassNotFoundException baseline){}
        return context;
    }
    @Test void disabledFeatureRequiresNoNewCapabilities() throws Exception {
        try(var context=context(false,null,null)){context.refresh();assertTrue(context.getBeansOfType(org.springframework.web.server.WebFilter.class).isEmpty());}
    }
    @Test void enabledFeatureRequiresEveryRealCapabilityAtStartup() throws Exception {
        for(var missing:REQUIRED)try(var context=context(true,missing,null)){assertThrows(org.springframework.beans.BeansException.class,context::refresh,missing.getSimpleName());}
    }
    @Test void enabledFeatureCannotBypassAnyOwningAuditOrWitnessSwitch() throws Exception {
        for(var flag:List.of("owning-mutations","audit-capture","reserve-completed-witness"))try(var context=context(true,null,flag)){assertThrows(org.springframework.beans.BeansException.class,context::refresh,flag);}
    }
    @Test void explicitCompleteGraphRegistersOneHighestPrecedenceFilterWithTheOriginalTime() throws Exception {
        try(var context=context(true,null,null)){var cache=new org.fireflyframework.web.idempotency.config.IdempotencyProperties();cache.setHeaderName("X-Configured-Response-Key");context.registerBean(org.fireflyframework.web.idempotency.config.IdempotencyProperties.class,()->cache);context.refresh();var filters=context.getBeansOfType(org.springframework.web.server.WebFilter.class);assertEquals(1,filters.size());
            var filter=filters.values().iterator().next();assertEquals(org.springframework.core.Ordered.HIGHEST_PRECEDENCE,((org.springframework.core.Ordered)filter).getOrder());
            var field=filter.getClass().getDeclaredField("time");field.setAccessible(true);assertSame(context.getBean(LedgerMutationTime.class),field.get(filter));
            var caches=filter.getClass().getDeclaredField("responseCaches");caches.setAccessible(true);assertEquals(java.util.List.of(cache),caches.get(filter));}
    }
}
