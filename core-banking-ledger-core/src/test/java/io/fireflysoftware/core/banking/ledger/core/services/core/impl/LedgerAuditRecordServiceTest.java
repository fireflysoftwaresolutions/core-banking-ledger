package io.fireflysoftware.core.banking.ledger.core.services.core.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.*;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.core.*;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.*;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsReceiptContracts.*;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.core.*;
import io.fireflysoftware.core.banking.ledger.interfaces.protocol.receipt.FundsReceiptCodec;
import io.fireflysoftware.core.banking.ledger.models.entities.core.*;
import io.fireflysoftware.core.banking.ledger.models.entities.funds.*;
import io.fireflysoftware.core.banking.ledger.models.entities.leg.TransactionLeg;
import io.fireflysoftware.core.banking.ledger.models.repositories.core.*;
import io.fireflysoftware.core.banking.ledger.models.repositories.funds.*;
import io.fireflysoftware.core.banking.ledger.models.repositories.leg.TransactionLegRepository;
import io.r2dbc.spi.*;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.*;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.core.transaction.impl.DefaultBoundedRootTransaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.reactivestreams.Publisher;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import reactor.core.publisher.*;

class LedgerAuditRecordServiceTest {
    static UUID id(int n){return new UUID(0,n);}
    static final long WHEN=1700000000000L;
    static final class Driver implements ConnectionFactory {
        int creates,commits,rollbacks,closes;
        final Connection c=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->switch(m.getName()){
            case "isAutoCommit"->true;case "getTransactionIsolationLevel"->IsolationLevel.READ_COMMITTED;
            case "beginTransaction"->Mono.empty();case "commitTransaction"->Mono.fromRunnable(()->commits++);case "rollbackTransaction"->Mono.fromRunnable(()->rollbacks++);case "close"->Mono.fromRunnable(()->closes++);
            case "hashCode"->System.identityHashCode(p);case "equals"->p==a[0];default->Mono.empty();});
        public Publisher<? extends Connection> create(){return Mono.defer(()->{creates++;return Mono.just(c);});}public ConnectionFactoryMetadata getMetadata(){return ()->"PostgreSQL";}
    }
    static final class Resources {
        final Driver driver=new Driver();final OwnedLeaseConnectionFactory factory=new OwnedLeaseConnectionFactory(driver);final DatabaseClient client=DatabaseClient.create(factory);final R2dbcEntityTemplate template=new R2dbcEntityTemplate(client,PostgresDialect.INSTANCE);final R2dbcTransactionManager manager=new R2dbcTransactionManager(factory);final BoundedR2dbcResources resources=BoundedR2dbcResources.verify(factory,client,template,manager);
        final Clock clock=Clock.systemUTC();final LongSupplier nanos=System::nanoTime;final BoundedRootTransaction root=new DefaultBoundedRootTransaction(resources,new CleanupPolicy(Duration.ofSeconds(2),Duration.ofSeconds(2)));
        <T>T run(Function<RootTransactionScope,Mono<T>> work){var now=clock.instant();var budget=R2dbcOperationBudget.observed(now,now.plusSeconds(5),nanos.getAsLong(),Duration.ofSeconds(5),clock,nanos);return root.execute(budget,new DefaultTransactionDefinition(),work).block().value();}
    }
    static final class Fixture {
        final Resources r=new Resources();final FundsRepositories funds=mock(FundsRepositories.class);final TransactionRepository transactions=mock(TransactionRepository.class);final TransactionLegRepository legs=mock(TransactionLegRepository.class);final TransactionStatusHistoryRepository history=mock(TransactionStatusHistoryRepository.class);
        final LedgerPostingClaimRepository claims=mock(LedgerPostingClaimRepository.class);final FundsOperationRepository operations=mock(FundsOperationRepository.class);final FundsReserveIntentRepository originals=mock(FundsReserveIntentRepository.class);final FundsReservationRepository reservations=mock(FundsReservationRepository.class);final FundsActionRepository actions=mock(FundsActionRepository.class);final FundsMonetaryReceiptRepository receipts=mock(FundsMonetaryReceiptRepository.class);final FundsEventRepository events=mock(FundsEventRepository.class);
        final Transaction transaction=new Transaction();final LedgerPostingRequestDTO request=new LedgerPostingRequestDTO();final LedgerPostingDTO result=new LedgerPostingDTO();final FundsOperation operation=new FundsOperation();final FundsReservation reservation=new FundsReservation();
        final AcceptedReserve accepted;final Reserve reserve;final LedgerAuditRecordServiceImpl service;
        Fixture(String variant){
            when(funds.postings()).thenReturn(claims);when(funds.operations()).thenReturn(operations);when(funds.originals()).thenReturn(originals);when(funds.reservations()).thenReturn(reservations);when(funds.actions()).thenReturn(actions);when(funds.receipts()).thenReturn(receipts);when(funds.events()).thenReturn(events);
            reserve=new Reserve(id(1),id(2),id(3),new BigDecimal("1.23"),"USD",300,null);accepted=new AcceptedReserve("owned",id(4),reserve,WHEN,WHEN+300000,new EnrollmentSnapshot(id(2),id(12),id(22),"USD",2,"unit-policy",Kind.CUSTOMER),new EnrollmentSnapshot(id(3),id(13),id(23),"USD",2,"unit-policy",Kind.CUSTOMER));
            transaction.setTransactionId(id(5));transaction.setManagedPosting(true);transaction.setAccountId(id(2));transaction.setTotalAmount(new BigDecimal("1.23"));transaction.setCurrency("USD");transaction.setTransactionType(TransactionTypeEnum.TRANSFER);transaction.setTransactionStatus(TransactionStatusEnum.PENDING);transaction.setTransactionDate(LocalDateTime.of(2026,10,28,12,0));
            var header=new LedgerPostingHeaderDTO();header.setAccountId(id(2));header.setTotalAmount(new BigDecimal("1.23"));header.setCurrency("USD");header.setTransactionType(TransactionTypeEnum.TRANSFER);header.setTransactionStatus(LedgerPostingHeaderDTO.InitialStatus.PENDING);request.setTransaction(header);request.setIdempotencyKey("unit-create");
            var debit=new LedgerPostingLegDTO();debit.setAccountId(id(2));debit.setAmount(new BigDecimal("1.23"));debit.setCurrency("USD");debit.setLegType("DEBIT");var credit=new LedgerPostingLegDTO();credit.setAccountId(id(3));credit.setAmount(new BigDecimal("1.23"));credit.setCurrency("USD");credit.setLegType("CREDIT");request.setLegs(List.of(debit,credit));
            if(variant.equals("reverse")){request.setIdempotencyKey("unit-reverse");transaction.setRelatedTransactionId(id(10));transaction.setRelationType("REVERSAL");header.setTransactionStatus(LedgerPostingHeaderDTO.InitialStatus.POSTED);}
            if(variant.equals("fund")){request.setIdempotencyKey("unit-fund");header.setTransactionStatus(LedgerPostingHeaderDTO.InitialStatus.POSTED);}
            transaction.setExternalReference(request.getIdempotencyKey());var claim=new LedgerPostingClaim(request.getIdempotencyKey(),LedgerPostingServiceImpl.fingerprint(request,variant.equals("reverse")?id(10):null),id(5));when(claims.findById(request.getIdempotencyKey())).thenReturn(Mono.just(claim));when(transactions.findById(id(5))).thenReturn(Mono.just(transaction));
            var dto=new TransactionDTO();dto.setTransactionId(id(5));result.setTransaction(dto);result.setLegs(List.of());
            when(legs.findByTransactionIdOrderByTransactionLegId(eq(id(5)),any())).thenAnswer(i->{org.springframework.data.domain.Pageable page=i.getArgument(1);assertEquals(1001,page.getPageSize());return Flux.just(leg(31,"DEBIT",2),leg(32,"CREDIT",3));});
            when(history.findByTransactionIdAndStatusCodeOrderByTransactionStatusHistoryId(eq(id(5)),any(),any())).thenAnswer(i->{org.springframework.data.domain.Pageable page=i.getArgument(2);assertEquals(2,page.getPageSize());var h=new TransactionStatusHistory();h.setTransactionStatusHistoryId(id(40+((TransactionStatusEnum)i.getArgument(1)).ordinal()));h.setTransactionId(id(5));h.setStatusCode(i.getArgument(1));h.setStatusStartDatetime(LocalDateTime.of(2026,10,28,12,0));return Flux.just(h);});
            operation.setId(new FundsOperationId("owned",id(1)));operation.setKind(variant.equals("fund")?"FUNDING":"RESERVE");operation.setSourceAccountId(id(2));operation.setDestinationAccountId(id(3));operation.setPostingId(variant.equals("fund")?id(5):null);operation.setReservationId(variant.equals("fund")?null:id(4));operation.setFingerprint(variant.equals("fund")?LedgerAuditRecordServiceImpl.fundingFingerprint(new Funding(id(1),id(2),id(3),new BigDecimal("1.23"),"USD")):"aaeb15d3ca1d0a24fbcf4564083a5b4d4d51187c37faae3fc9062127891d9661");operation.setClosedWithoutEffect(false);when(operations.findById(new FundsOperationId("owned",id(1)))).thenReturn(Mono.just(operation));
            var canonical=FundsReceiptCodec.canonicalAccepted(accepted);when(originals.findByTenantIdAndReservationId("owned",id(4))).thenReturn(Mono.just(new FundsReserveIntent(id(4),"owned",id(1),canonical.canonical(),canonical.digest())));
            reservation.setReservationId(id(4));reservation.setTenantId("owned");reservation.setOperationId(id(1));reservation.setSourceAccountId(id(2));reservation.setDestinationAccountId(id(3));reservation.setAmount(new BigDecimal("1.23"));reservation.setCurrency("USD");reservation.setPostingId(id(5));reservation.setState("HELD");reservation.setVersion(1L);reservation.setExpiresAt(OffsetDateTime.ofInstant(Instant.ofEpochMilli(WHEN+300000),ZoneOffset.UTC));when(reservations.findByTenantIdAndReservationId("owned",id(4))).thenReturn(Mono.just(reservation));
            when(events.findByTenantIdAndResourceIdAndEventTypeOrderByEventId(eq("owned"),eq(id(1)),eq("BANK_CONTROL_FUNDING"),any())).thenReturn(Flux.just(FundsEvent.builder().eventId(id(60)).tenantId("owned").resourceId(id(1)).actor("legacy-subject-only").eventType("BANK_CONTROL_FUNDING").payload(io.r2dbc.postgresql.codec.Json.of("{\"resourceId\":\""+id(1)+"\",\"postingId\":\""+id(5)+"\"}")).build()));
            service=new LedgerAuditRecordServiceImpl(r.resources,funds,transactions,legs,history);
        }
        TransactionLeg leg(int n,String type,int account){var l=new TransactionLeg();l.setTransactionLegId(id(n));l.setTransactionId(id(5));l.setAccountId(id(account));l.setLegType(type);l.setAmount(new BigDecimal("1.2300"));l.setCurrency("USD");return l;}
        Reservation value(){return new Reservation(id(4),id(1),id(2),id(3),new BigDecimal("1.23"),"USD",ReservationState.HELD,Instant.ofEpochMilli(WHEN+300000),id(5),null,1);}
        LedgerMutation mutation(String v){return switch(v){case"create"->new LedgerMutation.Created(request,result);case"confirm"->new LedgerMutation.Confirmed(id(5),result);case"release"->new LedgerMutation.Released(id(5),result);case"reverse"->new LedgerMutation.Reversed(id(10),"unit-reverse",result);case"fund"->new LedgerMutation.Funded(null,"owned",new Funding(id(1),id(2),id(3),new BigDecimal("1.23"),"USD"),new FundingReceipt(id(1),id(5)));case"reserve"->new LedgerMutation.Reserved(null,"owned",reserve,value());default->throw new IllegalArgumentException();};}
    }
    @ParameterizedTest @ValueSource(strings={"create","confirm","release","reverse","fund","reserve"})
    void actualBoundedMappedRecordsProduceStableEvidenceForSixOuterVariants(String variant){
        var f=new Fixture(variant);var evidence=f.r.run(scope->f.service.observe(scope,f.mutation(variant),"owned"));
        assertEquals("owned",evidence.ownerTenant());assertTrue(evidence.digest().matches("[0-9a-f]{64}"));assertNotNull(evidence.resourceId());
        if(!variant.equals("reserve"))assertNull(evidence.retainedRecordTimeMillis());
        assertEquals(1,f.r.driver.commits);assertEquals(0,f.r.driver.rollbacks);
    }
    static MonetaryReceipt receipt(Fixture f,Action action){
        boolean reverse=action==Action.REVERSE;var effect=new MoneyEffect(new BigDecimal("1.23"),"USD",f.accepted.source(),f.accepted.destination(),"123","123",id(reverse?3:2),id(reverse?2:3),reverse?new BalanceVector(new BigDecimal("8.77"),BigDecimal.ZERO,BigDecimal.TEN,BigDecimal.ZERO):new BalanceVector(BigDecimal.TEN,new BigDecimal("1.23"),new BigDecimal("8.77"),BigDecimal.ZERO),reverse?new BalanceVector(new BigDecimal("1.23"),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO):new BalanceVector(BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("1.23"),BigDecimal.ZERO));
        return new MonetaryReceipt(id(reverse?8:6),id(reverse?8:6),"owned",id(4),id(1),reverse?EffectKind.REVERSE:EffectKind.COMMIT,id(reverse?9:7),f.accepted,id(reverse?10:5),reverse?id(6):null,reverse?id(5):null,WHEN+(reverse?2000:1000),effect,new Actor("https://retained-issuer","retained-effect-actor"));
    }
    static FundsMonetaryReceipt row(MonetaryReceipt value){var c=FundsReceiptCodec.canonicalReceipt(value);return new FundsMonetaryReceipt(value.receiptId(),value.tenant(),value.reservationId(),value.effectKind().name(),c.canonical(),c.digest());}
    @ParameterizedTest @ValueSource(strings={"COMMIT","REVERSE","RELEASE","EXPIRE"})
    void exactActionAndOriginalCanonicalReceiptsCoverTheSeventhVariantWithoutStatusNoEffectClaims(String kind){
        var f=new Fixture("reserve");var action=Action.valueOf(kind);
        var mutation=actionMutation(f,action);var evidence=f.r.run(scope->f.service.observe(scope,mutation,"owned"));
        assertEquals("FUNDS_"+kind+"_CAPTURED",evidence.action());assertEquals(id(4),evidence.resourceId());
        if(action==Action.COMMIT||action==Action.REVERSE){assertEquals("https://retained-issuer",evidence.retainedActorIssuer());assertEquals("retained-effect-actor",evidence.retainedActorSubject());}else{assertNull(evidence.retainedActorIssuer());assertNull(evidence.retainedRecordTimeMillis());}
    }
    static LedgerMutation actionMutation(Fixture f,Action action){
        String kind=action.name();UUID actionId=id(action==Action.REVERSE?9:7);
        when(f.actions.findById(new FundsActionId("owned",actionId))).thenReturn(Mono.just(new FundsAction(new FundsActionId("owned",actionId),id(4),kind)));
        if(action==Action.REVERSE){
            var reverse=new Transaction();reverse.setTransactionId(id(10));reverse.setManagedPosting(true);reverse.setAccountId(id(3));reverse.setTotalAmount(new BigDecimal("1.23"));reverse.setCurrency("USD");reverse.setExternalReference("unit-funds-reverse");reverse.setRelatedTransactionId(id(5));reverse.setRelationType("REVERSAL");
            when(f.transactions.findById(id(10))).thenReturn(Mono.just(reverse));when(f.claims.findById("unit-funds-reverse")).thenReturn(Mono.just(new LedgerPostingClaim("unit-funds-reverse","b".repeat(64),id(10))));
            var debit=f.leg(51,"DEBIT",3);debit.setTransactionId(id(10));var credit=f.leg(52,"CREDIT",2);credit.setTransactionId(id(10));when(f.legs.findByTransactionIdOrderByTransactionLegId(eq(id(10)),any())).thenReturn(Flux.just(debit,credit));
            var h=new TransactionStatusHistory();h.setTransactionStatusHistoryId(id(53));h.setTransactionId(id(10));h.setStatusCode(TransactionStatusEnum.POSTED);when(f.history.findByTransactionIdAndStatusCodeOrderByTransactionStatusHistoryId(eq(id(10)),eq(TransactionStatusEnum.POSTED),any())).thenReturn(Flux.just(h));
        }
        when(f.receipts.findTop3ByTenantIdAndReservationIdOrderByEffectKindAsc("owned",id(4))).thenReturn(action==Action.REVERSE?Flux.just(row(receipt(f,Action.COMMIT)),row(receipt(f,Action.REVERSE))):action==Action.COMMIT?Flux.just(row(receipt(f,action))):Flux.empty());
        return new LedgerMutation.Acted(null,"owned",id(4),new Command(actionId,action),f.value());
    }
    @ParameterizedTest @CsvSource({"reserve,accounts","COMMIT,accounts","RELEASE,accounts","EXPIRE,accounts","REVERSE,accounts","reserve,reservation","reserve,posting","reserve,kind","reserve,fingerprint","reserve,missing","COMMIT,missing","RELEASE,missing","EXPIRE,missing","REVERSE,missing"})
    void contradictoryOriginalReserveOperationRefusesForReserveAndEveryAction(String variant,String field){
        var f=new Fixture("reserve");var mutation=variant.equals("reserve")?f.mutation("reserve"):actionMutation(f,Action.valueOf(variant));
        var original=f.r.run(scope->f.service.observe(scope,mutation,"owned"));assertNotNull(original);
        switch(field){
            case "accounts"->{f.operation.setSourceAccountId(id(80));f.operation.setDestinationAccountId(id(81));}
            case "reservation"->f.operation.setReservationId(id(82));
            case "posting"->f.operation.setPostingId(id(5));
            case "kind"->f.operation.setKind("FUNDING");
            case "fingerprint"->f.operation.setFingerprint("0".repeat(64));
            case "missing"->when(f.operations.findById(new FundsOperationId("owned",id(1)))).thenReturn(Mono.empty());
            default->throw new IllegalArgumentException();
        }
        assertThrows(RootTransactionException.class,()->f.r.run(scope->f.service.observe(scope,mutation,"owned")));
        assertEquals(1,f.r.driver.commits);assertEquals(1,f.r.driver.rollbacks);
    }
    @Test void reserveWithoutItsActualPostingRefusesDespiteAcceptedReserve(){var f=new Fixture("reserve");when(f.transactions.findById(id(5))).thenReturn(Mono.empty());assertThrows(RootTransactionException.class,()->f.r.run(scope->f.service.observe(scope,f.mutation("reserve"),"owned")));}
    @Test void fundingWithDifferentDurableAccountsRefuses(){var f=new Fixture("fund");when(f.legs.findByTransactionIdOrderByTransactionLegId(eq(id(5)),any())).thenReturn(Flux.just(f.leg(31,"DEBIT",20),f.leg(32,"CREDIT",3)));assertThrows(RootTransactionException.class,()->f.r.run(scope->f.service.observe(scope,f.mutation("fund"),"owned")));}
    @Test void missingDurableClaimRefusesRatherThanTrustingSuccessfulTransportResult(){var f=new Fixture("create");when(f.claims.findById(any())).thenReturn(Mono.empty());assertThrows(RootTransactionException.class,()->f.r.run(scope->f.service.observe(scope,f.mutation("create"),"owned")));assertEquals(0,f.r.driver.commits);assertEquals(1,f.r.driver.rollbacks);}
    @Test void twoMatchingStatusHistoriesAreAmbiguousAndRefuse(){var f=new Fixture("confirm");var h=new TransactionStatusHistory();when(f.history.findByTransactionIdAndStatusCodeOrderByTransactionStatusHistoryId(any(),any(),any())).thenReturn(Flux.just(h,h));assertThrows(RootTransactionException.class,()->f.r.run(scope->f.service.observe(scope,f.mutation("confirm"),"owned")));}
    @Test void mismatchedAcceptedCanonicalDigestRefuses(){var f=new Fixture("reserve");when(f.originals.findByTenantIdAndReservationId(any(),any())).thenReturn(Mono.just(new FundsReserveIntent(id(4),"owned",id(1),FundsReceiptCodec.canonicalAccepted(f.accepted).canonical(),"0".repeat(64))));assertThrows(RootTransactionException.class,()->f.r.run(scope->f.service.observe(scope,f.mutation("reserve"),"owned")));}
    @Test void moreThanTheBoundedOriginalLegCountRefuses(){var f=new Fixture("create");when(f.legs.findByTransactionIdOrderByTransactionLegId(any(),any())).thenReturn(Flux.range(0,1001).map(n->f.leg(n+1,"DEBIT",2)));assertThrows(RootTransactionException.class,()->f.r.run(scope->f.service.observe(scope,f.mutation("create"),"owned")));}
}
