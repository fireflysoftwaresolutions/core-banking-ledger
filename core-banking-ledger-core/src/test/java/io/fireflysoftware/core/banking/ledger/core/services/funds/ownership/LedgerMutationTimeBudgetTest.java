package io.fireflysoftware.core.banking.ledger.core.services.funds.ownership;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicLong;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import org.junit.jupiter.api.Test;

class LedgerMutationTimeBudgetTest {
    static final class Time extends Clock {
        final AtomicLong nanos=new AtomicLong(10);Instant now=Instant.parse("2026-01-01T00:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return now;}
        void advance(Duration elapsed){now=now.plus(elapsed);nanos.addAndGet(elapsed.toNanos());}
    }
    static R2dbcOperationBudget original(LedgerMutationTime time)throws Exception {
        var method=java.util.Arrays.stream(LedgerMutationTime.class.getMethods()).filter(value->value.getName().equals("originalBudget")&&value.getParameterCount()==0).findFirst();
        assertTrue(method.isPresent(),"Caller-owned LedgerMutationTime must create the original ingress budget");
        return (R2dbcOperationBudget)method.orElseThrow().invoke(time);
    }
    @Test void originalBudgetSharesTheExactTimePairUsedByNativeWitnessAndAuditCaps()throws Exception {
        var clock=new Time();var time=new LedgerMutationTime(clock,clock.nanos::get);var original=original(time);
        var now=clock.instant();var cap=R2dbcOperationBudget.observed(now,now.plusSeconds(2),time.nanos().getAsLong(),Duration.ofSeconds(5),time.clock(),time.nanos());
        assertEquals(Duration.ofSeconds(2),original.intersect(cap).remaining());
    }
    @Test void originalFiveSecondWindowDoesNotRestartAfterAuthenticationOrOwnerWait()throws Exception {
        var clock=new Time();var time=new LedgerMutationTime(clock,clock.nanos::get);var original=original(time);
        clock.advance(Duration.ofSeconds(3));assertEquals(Duration.ofSeconds(2),original.remaining());
        clock.advance(Duration.ofSeconds(2));assertThrows(IllegalStateException.class,original::remaining);
    }
    @Test void wallClockRollbackCannotRenewTheOriginalIngressWindow()throws Exception {
        var clock=new Time();var time=new LedgerMutationTime(clock,clock.nanos::get);var original=original(time);
        clock.now=clock.now.minusSeconds(1);assertThrows(IllegalStateException.class,original::remaining);
    }
}
