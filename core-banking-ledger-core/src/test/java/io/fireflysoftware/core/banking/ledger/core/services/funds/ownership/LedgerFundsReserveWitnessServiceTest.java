package io.fireflysoftware.core.banking.ledger.core.services.funds.ownership;

import static org.junit.jupiter.api.Assertions.*;
import io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.impl.LedgerFundsReserveWitnessServiceImpl;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.*;
import io.fireflysoftware.core.banking.ledger.models.config.LedgerOwningMutationResources;
import io.fireflysoftware.core.banking.ledger.models.repositories.funds.FundsReserveCompletedWitnessRepository;
import io.r2dbc.spi.*;
import java.lang.reflect.Proxy;
import java.time.*;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.LongSupplier;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.core.transaction.impl.DefaultBoundedRootTransaction;
import org.fireflyframework.kernel.audit.AuditActor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Publisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import reactor.core.publisher.*;

class LedgerFundsReserveWitnessServiceTest {
    static final UUID COMMAND=new UUID(0,501),ID=LedgerFundsFirstCaptureTest.ID;
    static final String TENANT=LedgerFundsFirstCaptureTest.TENANT;
    static final Reserve INPUT=LedgerFundsFirstCaptureTest.INPUT;
    // Controlled transport only: real Spring Data mapping and owning resource composition, not PostgreSQL proof.
    static final class Driver implements ConnectionFactory {
        int creates,begins,commits,rollbacks,closes,queries;boolean empty,error;
        String sql;final Map<Integer,Object> bindings=new TreeMap<>();final Map<String,Object> overrides=new HashMap<>();
        final Connection connection=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->switch(m.getName()){
            case "createStatement" -> statement((String)a[0]);case "isAutoCommit" -> true;case "getTransactionIsolationLevel" -> IsolationLevel.READ_COMMITTED;
            case "beginTransaction" -> Mono.fromRunnable(()->begins++);case "commitTransaction" -> Mono.fromRunnable(()->commits++);case "rollbackTransaction" -> Mono.fromRunnable(()->rollbacks++);case "close" -> Mono.fromRunnable(()->closes++);
            case "getMetadata" -> (ConnectionMetadata)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ConnectionMetadata.class},(q,n,b)->n.getName().equals("getDatabaseProductName")?"PostgreSQL":"17");
            case "hashCode" -> System.identityHashCode(p);case "equals" -> p==a[0];case "toString" -> "ControlledBoundWitnessConnection";default -> Mono.empty();});
        public ConnectionFactoryMetadata getMetadata(){return()->"PostgreSQL";}
        public Publisher<? extends Connection> create(){return Mono.defer(()->{creates++;return Mono.just(connection);});}
        Object value(Object input){return input instanceof io.r2dbc.spi.Parameter parameter?parameter.getValue():input;}
        Statement statement(String query){sql=query;bindings.clear();final Statement[] ref=new Statement[1];ref[0]=(Statement)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Statement.class},(p,m,a)->switch(m.getName()){
            case "bind" -> {bindings.put((Integer)a[0],value(a[1]));yield ref[0];}case "bindNull" -> {bindings.put((Integer)a[0],null);yield ref[0];}
            case "execute" -> {queries++;yield error?Flux.error(new IllegalStateException("CONTROLLED_NATIVE_READER_UNAVAILABLE")):Flux.just(result());}
            case "returnGeneratedValues","fetchSize","add" -> ref[0];default -> throw new AssertionError(m.getName());});return ref[0];}
        Map<String,Object> data(){var command=LedgerFundsFirstCaptureProvider.Command.reserve(LedgerFundsFirstCaptureTest.PRINCIPAL,TENANT,INPUT);long now=Instant.now().toEpochMilli();long until=((OffsetDateTime)bindings.get(10)).toInstant().toEpochMilli();var row=new LinkedHashMap<String,Object>();
            row.put("command_id",COMMAND);row.put("reservation_id",ID);row.put("operation_id",INPUT.operationId());row.put("owner_tenant",TENANT);row.put("source_account_id",INPUT.sourceAccountId());row.put("destination_account_id",INPUT.destinationAccountId());row.put("original_intent_canonical",command.canonicalIntent());row.put("original_intent_digest",command.intentDigest());row.put("operation_fingerprint",command.operationFingerprint());row.put("actor_issuer",LedgerFundsFirstCaptureTest.PRINCIPAL.issuer());row.put("actor_subject",LedgerFundsFirstCaptureTest.PRINCIPAL.subject());row.put("created_xid","4294967297");row.put("current_xid","4294967297");row.put("executor_role","controlled_runtime");row.put("session_role","controlled_runtime");row.put("function_role","firefly_funds_receipt_guard");row.put("backend_id",101);row.put("observed_at_epoch_millis",now);row.put("token_expires_epoch_millis",LedgerFundsFirstCaptureTest.PRINCIPAL.claim("exp",Instant.class).toEpochMilli());row.put("read_until_epoch_millis",Math.min(until,now+2000));row.putAll(overrides);return row;
        }
        Result result(){var data=data();var names=new ArrayList<>(data.keySet());var columns=new ArrayList<ColumnMetadata>();for(String name:names){var column=(ColumnMetadata)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ColumnMetadata.class},(p,m,a)->switch(m.getName()){case "getName"->name;case "getJavaType"->data.get(name)==null?Object.class:data.get(name).getClass();case "getType"->R2dbcType.VARCHAR;case "getNullability"->Nullability.NULLABLE;default->null;});columns.add(column);}
            var metadata=(RowMetadata)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{RowMetadata.class},(p,m,a)->switch(m.getName()){case "getColumnMetadatas"->columns;case "getColumnNames"->new LinkedHashSet<>(names);case "getColumnMetadata"->columns.get(a[0]instanceof Integer n?n:names.indexOf(a[0]));default->throw new AssertionError(m.getName());});
            var row=(Row)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Row.class},(p,m,a)->switch(m.getName()){case "get"->data.get(a[0]instanceof Integer n?names.get(n):a[0]);case "getMetadata"->metadata;default->throw new AssertionError(m.getName());});
            final Result[] self=new Result[1];self[0]=(Result)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Result.class},(p,m,a)->switch(m.getName()){
                case "map" -> {if(empty)yield Flux.empty();Object mapped=a[0]instanceof BiFunction<?,?,?>?((BiFunction<Row,RowMetadata,Object>)a[0]).apply(row,metadata):((Function<io.r2dbc.spi.Readable,Object>)a[0]).apply(row);yield Flux.just(mapped);}
                case "getRowsUpdated"->Mono.just(0L);case "filter"->self[0];default->throw new AssertionError(m.getName());});return self[0];}
    }
    static final class Fixture {
        final Driver driver=new Driver();final OwnedLeaseConnectionFactory factory=new OwnedLeaseConnectionFactory(driver);final DatabaseClient client=DatabaseClient.create(factory);final R2dbcEntityTemplate template=new R2dbcEntityTemplate(client,PostgresDialect.INSTANCE);final R2dbcTransactionManager manager=new R2dbcTransactionManager(factory);
        final LedgerOwningMutationResources resources=new LedgerOwningMutationResources(factory,client,template,manager);final Clock clock=Clock.systemUTC();final LongSupplier nanos=System::nanoTime;int records;boolean wrongEvidence;
        final LedgerFundsReserveWitnessService service=new LedgerFundsReserveWitnessServiceImpl(resources,(scope,mutation,tenant)->factory.requireBoundScope(resources.bounded(),scope).then(Mono.defer(()->{records++;var e=LedgerFundsFirstCaptureTest.evidence();return Mono.just(wrongEvidence?new LedgerAuditEvidence(e.action(),e.ownerTenant(),e.resourceType(),e.resourceId(),e.processId(),e.commandKey(),"b".repeat(64),null,null,null):e);})),new LedgerMutationTime(clock,nanos));
        LedgerFundsFirstCaptureProvider.Authorization authorization(LedgerFundsFirstCaptureProvider.Mode mode){return new LedgerFundsFirstCaptureProvider.Authorization(LedgerFundsFirstCaptureProvider.Command.reserve(LedgerFundsFirstCaptureTest.PRINCIPAL,TENANT,INPUT),new UUID(0,91),mode,new AuditActor("unit-issuer","unit-subject","unit-client",TENANT,null,null),"controlled-policy","controlled-policy","controlled-decision",clock.instant().plusSeconds(10));}
        LedgerMutation.Reserved mutation(){return new LedgerMutation.Reserved(LedgerFundsFirstCaptureTest.PRINCIPAL,TENANT,INPUT,new Reservation(ID,INPUT.operationId(),INPUT.sourceAccountId(),INPUT.destinationAccountId(),INPUT.amount(),INPUT.currency(),ReservationState.HELD,clock.instant().plusSeconds(30),ID,null,1));}
        R2dbcOperationBudget budget(){var now=clock.instant();return R2dbcOperationBudget.observed(now,now.plusSeconds(5),nanos.getAsLong(),Duration.ofSeconds(5),clock,nanos);}
        RootCompletion<LedgerFundsFirstCaptureProvider.CompletedCommand> run(){return new DefaultBoundedRootTransaction(resources.bounded(),new CleanupPolicy(Duration.ofSeconds(2),Duration.ofSeconds(2))).execute(budget(),new DefaultTransactionDefinition(),scope->service.requireFirst(scope,authorization(LedgerFundsFirstCaptureProvider.Mode.FIRST),mutation(),LedgerFundsFirstCaptureTest.evidence()).doOnNext(value->{assertSame(scope,value.scope());assertTrue(scope.remaining().compareTo(Duration.ofSeconds(2))<=0);})).block();}
    }
    @Test void actualSpringDataMapsProtectedCommandOnTheOriginalSingleTransaction(){var f=new Fixture();var result=f.run();assertEquals(COMMAND,result.value().commandId());assertNotEquals(INPUT.operationId(),result.value().commandId());assertTrue(result.transactionFacts().commitAcknowledged());assertEquals(1,f.driver.creates);assertEquals(1,f.driver.begins);assertEquals(1,f.driver.commits);assertEquals(1,f.driver.closes);assertEquals(1,f.records);assertEquals(1,f.driver.queries);assertTrue(f.driver.sql.startsWith("SELECT * FROM public.fnd_require_completed_reserve("));assertEquals(TENANT,f.driver.bindings.get(0));assertEquals(INPUT.operationId(),f.driver.bindings.get(1));assertEquals(ID,f.driver.bindings.get(2));assertEquals(INPUT.sourceAccountId(),f.driver.bindings.get(3));assertEquals(INPUT.destinationAccountId(),f.driver.bindings.get(4));assertEquals(LedgerFundsFirstCaptureProvider.Command.reserve(LedgerFundsFirstCaptureTest.PRINCIPAL,TENANT,INPUT).canonicalIntent(),f.driver.bindings.get(5));}
    static void rollback(Fixture f){assertEquals(0,f.driver.commits);assertEquals(1,f.driver.rollbacks);assertEquals(1,f.driver.closes);}
    @Test void missingNativeWitnessRefusesAndRollsBack(){var f=new Fixture();f.driver.empty=true;assertThrows(RootTransactionException.class,f::run);rollback(f);}
    @Test void recordDigestCannotBeReplacedBeforeNativeRead(){var f=new Fixture();f.wrongEvidence=true;assertThrows(RootTransactionException.class,f::run);assertEquals(0,f.driver.queries);rollback(f);}
    @ParameterizedTest @ValueSource(strings={"current_xid","executor_role","function_role","actor_subject","source_account_id","original_intent_canonical","original_intent_digest","operation_fingerprint","command_id","backend_id","read_until_epoch_millis","token_expires_epoch_millis"})
    void contradictoryNativeMetadataCannotBecomeACompletedWitness(String field){var f=new Fixture();Object bad=switch(field){case "source_account_id","command_id"->new UUID(0,0);case "backend_id"->0;case "read_until_epoch_millis","token_expires_epoch_millis"->Long.MIN_VALUE;case "current_xid"->"4294967298";default->"contradictory";};f.driver.overrides.put(field,bad);assertThrows(RootTransactionException.class,f::run);rollback(f);}
    @Test void fullUnsignedXidIsNotTruncatedTo32OrSigned64Bits(){var f=new Fixture();f.driver.overrides.put("created_xid","18446744073709551615");f.driver.overrides.put("current_xid","18446744073709551615");assertEquals(COMMAND,f.run().value().commandId());}
    @Test void xidBeyondUnsigned64Refuses(){var f=new Fixture();f.driver.overrides.put("created_xid","18446744073709551616");f.driver.overrides.put("current_xid","18446744073709551616");assertThrows(RootTransactionException.class,f::run);rollback(f);}
    @Test void replayDoesNotPretendToHaveTheCurrentFirstXid(){var f=new Fixture();assertThrows(RootTransactionException.class,()->new DefaultBoundedRootTransaction(f.resources.bounded(),new CleanupPolicy(Duration.ofSeconds(2),Duration.ofSeconds(2))).execute(f.budget(),new DefaultTransactionDefinition(),scope->f.service.requireFirst(scope,f.authorization(LedgerFundsFirstCaptureProvider.Mode.REPLAY),f.mutation(),LedgerFundsFirstCaptureTest.evidence())).block());assertEquals(0,f.records);assertEquals(0,f.driver.queries);rollback(f);}
    @Test void aForeignOrMissingScopeCannotAcquireAConnection(){var f=new Fixture();assertThrows(IllegalStateException.class,()->f.service.requireFirst(null,f.authorization(LedgerFundsFirstCaptureProvider.Mode.FIRST),f.mutation(),LedgerFundsFirstCaptureTest.evidence()).block());assertEquals(0,f.driver.creates);assertEquals(0,f.driver.queries);}
    @Test void recoveredReaderFailureCannotCommitTheOriginalRoot(){var f=new Fixture();f.driver.error=true;try{var completion=new DefaultBoundedRootTransaction(f.resources.bounded(),new CleanupPolicy(Duration.ofSeconds(2),Duration.ofSeconds(2))).execute(f.budget(),new DefaultTransactionDefinition(),scope->f.service.requireFirst(scope,f.authorization(LedgerFundsFirstCaptureProvider.Mode.FIRST),f.mutation(),LedgerFundsFirstCaptureTest.evidence()).onErrorResume(error->Mono.empty()).thenReturn("observed")).block();assertFalse(completion.transactionFacts().commitAcknowledged());}catch(RootTransactionException refused){assertTrue(refused.transactionFacts().rollbackAcknowledged());}rollback(f);}
    @Test void defaultConfigurationDoesNotActivateReaderOrProvider(){try(var context=new AnnotationConfigApplicationContext()){context.register(LedgerFundsReserveWitnessConfiguration.class);context.refresh();assertTrue(context.getBeansOfType(LedgerFundsReserveWitnessService.class).isEmpty());assertTrue(context.getBeansOfType(LedgerFundsFirstCaptureProvider.class).isEmpty());}assertTrue(FundsReserveCompletedWitnessRepository.class.isAnnotationPresent(NoRepositoryBean.class));}
    static void enable(AnnotationConfigApplicationContext context,Map<String,Object> flags){context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("controlled-witness-flags",flags));}
    @ParameterizedTest @ValueSource(strings={"owning-mutations.enabled","audit-capture.enabled","reserve-completed-witness.enabled"})
    void partialOptInCannotActivateTheReader(String property){try(var context=new AnnotationConfigApplicationContext()){enable(context,Map.of("firefly.ledger."+property,"true"));context.register(LedgerFundsReserveWitnessConfiguration.class);context.refresh();assertTrue(context.getBeansOfType(LedgerFundsReserveWitnessService.class).isEmpty());}}
    @Test void fullOptInRequiresTheActualResourceAndRecordProviders(){try(var context=new AnnotationConfigApplicationContext()){enable(context,Map.of("firefly.ledger.owning-mutations.enabled","true","firefly.ledger.audit-capture.enabled","true","firefly.ledger.reserve-completed-witness.enabled","true"));context.register(LedgerFundsReserveWitnessConfiguration.class);assertThrows(org.springframework.beans.BeansException.class,context::refresh);}}
    @Test void optInUsesTheOriginalTemplateWithoutCreatingAFirstCaptureProvider(){var f=new Fixture();try(var context=new AnnotationConfigApplicationContext()){
        enable(context,Map.of("firefly.ledger.owning-mutations.enabled","true","firefly.ledger.audit-capture.enabled","true","firefly.ledger.reserve-completed-witness.enabled","true"));context.registerBean(LedgerOwningMutationResources.class,()->f.resources);context.registerBean(LedgerMutationTime.class,()->new LedgerMutationTime(f.clock,f.nanos));context.registerBean(io.fireflysoftware.core.banking.ledger.core.services.core.LedgerAuditRecordService.class,()->(scope,mutation,tenant)->f.factory.requireBoundScope(f.resources.bounded(),scope).thenReturn(LedgerFundsFirstCaptureTest.evidence()));context.register(LedgerFundsReserveWitnessConfiguration.class);context.refresh();assertEquals(0,f.driver.creates);assertTrue(context.getBeansOfType(LedgerFundsFirstCaptureProvider.class).isEmpty());var reader=context.getBean(LedgerFundsReserveWitnessService.class);
        var completion=new DefaultBoundedRootTransaction(f.resources.bounded(),new CleanupPolicy(Duration.ofSeconds(2),Duration.ofSeconds(2))).execute(f.budget(),new DefaultTransactionDefinition(),scope->reader.requireFirst(scope,f.authorization(LedgerFundsFirstCaptureProvider.Mode.FIRST),f.mutation(),LedgerFundsFirstCaptureTest.evidence())).block();assertEquals(COMMAND,completion.value().commandId());assertEquals(1,f.driver.creates);assertEquals(1,f.driver.commits);
    }}

}
