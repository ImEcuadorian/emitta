package io.github.imecuadorian.emitta.recovery;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import io.github.imecuadorian.emitta.support.JwtTestProperties;
import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifact;
import io.github.imecuadorian.emitta.ride.application.port.in.GenerateAndStoreInvoiceRideUseCase;
import io.github.imecuadorian.emitta.fiscalprocessing.application.port.in.ProcessFiscalDocumentUseCase;
import io.github.imecuadorian.emitta.fiscalprocessing.application.model.ProcessFiscalDocumentResult;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.fiscalprocessing.adapter.in.messaging.FiscalDocumentListener;
import io.github.imecuadorian.emitta.outbox.adapter.out.messaging.RabbitOutboxMessagePublisher;
import io.github.imecuadorian.emitta.outbox.application.port.in.PublishPendingOutboxUseCase;
import io.github.imecuadorian.emitta.outbox.application.port.out.OutboxEventPort;
import io.github.imecuadorian.emitta.outbox.application.model.OutboxEvent;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.*;
import java.time.Duration;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringBootTest(properties={"emitta.fiscal.worker.enabled=false","emitta.processing.jobs.enabled=false","emitta.outbox.publisher.enabled=false", "emitta.sri.reception.enabled=false","emitta.sri.authorization.enabled=false"})
@Testcontainers
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DurableRecoveryIntegrationTest {
 @Container @ServiceConnection static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:18");
 @Container @ServiceConnection static final RabbitMQContainer RABBIT=new RabbitMQContainer("rabbitmq:4-management-alpine");
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){JwtTestProperties.register(r);}
 @Autowired org.springframework.test.web.servlet.MockMvc mvc;
 @Autowired JdbcTemplate jdbc;
 @Autowired PostgreSqlDurableWorkAdapter jobs;
 @Autowired DocumentRepository documents;
 @Autowired org.springframework.context.ApplicationContext context;
 @Autowired MeterRegistry metrics;
 @Autowired RabbitTemplate rabbit;
 @Autowired RabbitOutboxMessagePublisher publisher;
 @Autowired PublishPendingOutboxUseCase publishOutbox;
 @Autowired OutboxEventPort outbox;
 @Autowired io.github.imecuadorian.emitta.outbox.application.port.out.OutboxClaimPort claims;
 @MockitoBean GenerateAndStoreInvoiceRideUseCase ride;
 @MockitoBean ProcessFiscalDocumentUseCase fiscal;
 private static UUID restartDocument,restartTenant;
 @Test @Order(20) void exhaustedAdmissionIsDurablyQuarantinedAndOnlyOriginalEligibleTenantEventCanRecover() {
  var queued=fixture("QUEUED");var uncertain=fixture("SUBMITTED");
  var queuedEvent=UUID.randomUUID();var uncertainEvent=UUID.randomUUID();
  outbox.append(new OutboxEvent(queuedEvent,queued.tenant,"DOCUMENT",queued.document,"document.received.v1",Map.of("documentId",queued.document.toString())));
  outbox.append(new OutboxEvent(uncertainEvent,uncertain.tenant,"DOCUMENT",uncertain.document,"document.received.v1",Map.of("documentId",uncertain.document.toString())));
  assertEquals(2,publishOutbox.publishBatch());
  String queue="synthetic.admission."+UUID.randomUUID();
  var admin=new RabbitAdmin(rabbit);admin.declareQueue(new org.springframework.amqp.core.Queue(queue,true));
  var factory=new io.github.imecuadorian.emitta.outbox.adapter.config.RabbitOutboxConfiguration()
      .fiscalDurableListenerFactory(rabbit.getConnectionFactory(),2,Duration.ofMillis(1),Duration.ofMillis(4),Duration.ZERO,metrics,publisher);
  var endpoint=new org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint();
  endpoint.setId(queue);endpoint.setQueueNames(queue);
  endpoint.setMessageListener((org.springframework.amqp.core.BatchMessageListener) messages->{
   throw new org.springframework.dao.DataAccessResourceFailureException("Synthetic admission outage");
  });
  var container=factory.createListenerContainer(endpoint);
  try {
   for(int i=0;i<2;i++){var original=rabbit.receive("emitta.fiscal.documents",2000);assertNotNull(original);rabbit.send(queue,original);}
   container.start();
   var quarantined=new HashSet<String>();
   for(int i=0;i<2;i++) {
    var message=rabbit.receive("emitta.processing.dlq",10000);assertNotNull(message);
    quarantined.add(message.getMessageProperties().getMessageId());
    assertEquals(3L,((Number)message.getMessageProperties().getHeader("x-emitta-admission-attempts")).longValue());
    assertEquals(Boolean.TRUE,message.getMessageProperties().getHeader("x-emitta-admission-exhausted"));
    assertEquals(MessageDeliveryMode.PERSISTENT,message.getMessageProperties().getReceivedDeliveryMode());
   }
   assertEquals(Set.of(queuedEvent.toString(),uncertainEvent.toString()),quarantined);
   org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(()->
      assertEquals(0,((Number)admin.getQueueProperties(queue).get(RabbitAdmin.QUEUE_MESSAGE_COUNT)).intValue()));
  } finally {container.stop();admin.deleteQueue(queue);}
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM emitta.processing_jobs",Integer.class));
  assertFalse(jobs.recoverAdmission(uncertain.tenant,queued.document,queuedEvent));
  assertFalse(jobs.recoverAdmission(queued.tenant,queued.document,UUID.randomUUID()));
  assertFalse(jobs.recoverAdmission(uncertain.tenant,uncertain.document,uncertainEvent));
  assertTrue(jobs.recoverAdmission(queued.tenant,queued.document,queuedEvent));
  assertFalse(jobs.recoverAdmission(queued.tenant,queued.document,queuedEvent));
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM emitta.processing_jobs",Integer.class));
  assertEquals(0,jobs.find(queued.tenant,queued.document,DurableWorkPort.Kind.FISCAL).orElseThrow().attempts());
  assertEquals(1,jdbc.queryForObject("SELECT recoveries FROM emitta.processing_jobs WHERE document_id=?",Integer.class,queued.document).intValue());
  assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM emitta.outbox_events",Integer.class));
  assertEquals(DocumentStatus.QUEUED,documents.findById(queued.document).orElseThrow().getStatus());
  assertEquals(DocumentStatus.SUBMITTED,documents.findById(uncertain.document).orElseThrow().getStatus());
  verifyNoInteractions(fiscal,ride);
 }
 @Test @Order(17) void mixedRoutedAndReturnedBatchMarksOnlyTheConfirmedRoutedEvent() {
  var f=fixture("QUEUED");var valid=UUID.randomUUID();var invalid=UUID.randomUUID();
  outbox.append(new OutboxEvent(valid,f.tenant,"DOCUMENT",f.document,"document.received.v1",Map.of("documentId",f.document.toString())));
  outbox.append(new OutboxEvent(invalid,null,"DOCUMENT",UUID.randomUUID(),"unbound.synthetic.v1",Map.of("test",true)));
  assertEquals(1,publishOutbox.publishBatch());
  assertNotNull(jdbc.queryForObject("SELECT published_at FROM emitta.outbox_events WHERE id=?",Object.class,valid));
  assertNull(jdbc.queryForObject("SELECT published_at FROM emitta.outbox_events WHERE id=?",Object.class,invalid));
  assertEquals(1,jdbc.queryForObject("SELECT attempts FROM emitta.outbox_events WHERE id=?",Integer.class,invalid));
 }
 @Test @Order(16) void admissionBatchRollsBackBeforeAckAndThenRecoversWithInvalidMessageInDlq() {
  var first=fixture("QUEUED");var second=fixture("QUEUED");
  var spy=spy(new PostgreSqlDurableWorkAdapter(jdbc,context.getBean(org.springframework.transaction.PlatformTransactionManager.class),outbox,metrics));
  doAnswer(invocation->{spy.enqueue(DurableWorkPort.Kind.FISCAL,first.tenant,first.document);throw new IllegalStateException("synthetic DB admission failure");})
      .when(spy).enqueueFiscalBatch(anyList());
  var listener=new FiscalDocumentListener(spy,documents,publisher,tools.jackson.databind.json.JsonMapper.builder().build(),
      context.getBean(org.springframework.transaction.PlatformTransactionManager.class),metrics);
  var messages=new java.util.ArrayList<Message>();
  for(var f:List.of(first,second))messages.add(new Message(("{\"documentId\":\""+f.document+"\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8),new MessageProperties()));
  messages.add(new Message("invalid".getBytes(),new MessageProperties()));
  assertThrows(IllegalStateException.class,()->listener.onMessages(messages));
  for(var f:List.of(first,second))assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM emitta.processing_jobs WHERE document_id=?",Integer.class,f.document));
  doCallRealMethod().when(spy).enqueueFiscalBatch(anyList());
  listener.onMessages(messages);listener.onMessages(messages.subList(0,2));
  for(var f:List.of(first,second))assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM emitta.processing_jobs WHERE document_id=?",Integer.class,f.document));
  var invalid=rabbit.receive("emitta.processing.dlq",2000);assertNotNull(invalid);assertEquals("VALIDATION",invalid.getMessageProperties().getHeader("x-emitta-failure-category"));
 }
 @Test @Order(18) void bulkAdmissionRejectsCrossTenantAndIneligibleTargetsAtomicallyAndDeduplicates() {
  var first=fixture("QUEUED");var second=fixture("QUEUED");var invalid=fixture("SIGNED");
  var validTarget=new DurableWorkPort.AdmissionTarget(first.tenant,first.document);
  var crossTenant=assertThrows(RuntimeException.class,()->jobs.enqueueFiscalBatch(List.of(validTarget,new DurableWorkPort.AdmissionTarget(first.tenant,second.document))));
  var ineligible=assertThrows(RuntimeException.class,()->jobs.enqueueFiscalBatch(List.of(validTarget,new DurableWorkPort.AdmissionTarget(invalid.tenant,invalid.document))));
  assertEquals(WorkFailureClassifier.Category.VALIDATION,WorkFailureClassifier.classify(crossTenant));
  assertEquals(WorkFailureClassifier.Category.VALIDATION,WorkFailureClassifier.classify(ineligible));
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM emitta.processing_jobs WHERE document_id=?",Integer.class,first.document));
  double before=metrics.counter("emitta.processing.admitted","kind","FISCAL").count();
  var secondTarget=new DurableWorkPort.AdmissionTarget(second.tenant,second.document);
  jobs.enqueueFiscalBatch(List.of(validTarget,secondTarget,validTarget));jobs.enqueueFiscalBatch(List.of(secondTarget,validTarget));
  for(var f:List.of(first,second))assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM emitta.processing_jobs WHERE document_id=? AND tenant_id=?",Integer.class,f.document,f.tenant));
  assertEquals(before+2,metrics.find("emitta.processing.admitted").tag("kind","FISCAL").counter().count());
  var candidate=jdbc.queryForMap(io.github.imecuadorian.emitta.outbox.adapter.config.OutboxAdmissionGuard.BACKLOG_SQL);
  var original=jdbc.queryForMap("""
   SELECT count(*) AS pending FROM emitta.outbox_events e WHERE e.published_at IS NULL OR
   (e.event_type='document.received.v1' AND EXISTS(SELECT 1 FROM emitta.documents d WHERE d.id=e.aggregate_id AND d.status IN ('QUEUED','GENERATING'))
    AND NOT EXISTS(SELECT 1 FROM emitta.processing_jobs j WHERE j.document_id=e.aggregate_id AND j.kind='FISCAL'))
   """);
  assertEquals(original.get("pending"),candidate.get("pending"));
 }
 @Test @Order(19) void bulkAdmissionTimesOutOnRealRowLockAndRecoversWithoutPartialJob() throws Exception {
  var f=fixture("QUEUED");var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
  try(var executor=Executors.newSingleThreadExecutor()) {
   var blocker=executor.submit(()->jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection->{
    connection.setAutoCommit(false);
    try(var statement=connection.prepareStatement("SELECT id FROM emitta.documents WHERE id=? FOR UPDATE")) {
     statement.setObject(1,f.document);try(var rows=statement.executeQuery()){assertTrue(rows.next());}
     locked.countDown();try{release.await(15,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
    }finally{connection.rollback();connection.setAutoCommit(true);}return null;
   }));
   try {
    assertTrue(locked.await(5,TimeUnit.SECONDS));
    var failure=assertThrows(RuntimeException.class,()->jobs.enqueueFiscalBatch(List.of(new DurableWorkPort.AdmissionTarget(f.tenant,f.document))));
    assertEquals(WorkFailureClassifier.Category.TRANSIENT,WorkFailureClassifier.classify(failure));
    assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM emitta.processing_jobs WHERE document_id=?",Integer.class,f.document));
   }finally{release.countDown();}
   blocker.get(5,TimeUnit.SECONDS);
  }
  jobs.enqueueFiscalBatch(List.of(new DurableWorkPort.AdmissionTarget(f.tenant,f.document)));
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM emitta.processing_jobs WHERE document_id=?",Integer.class,f.document));
 }
 @Test @Order(15) void killedPublisherJvmRecoversConfirmedBatchWithoutDuplicateJobs() throws Exception {
  // Clean only synthetic invalid leftovers in this disposable database/queue.
  jdbc.update("DELETE FROM emitta.outbox_events WHERE event_type='unbound.synthetic.v1'");
  while(rabbit.receive("emitta.fiscal.documents",100)!=null){}
  var first=fixture("QUEUED");var second=fixture("QUEUED");
  var ids=new java.util.ArrayList<UUID>();
  for(var f:List.of(first,second)){var id=UUID.randomUUID();ids.add(id);outbox.append(new OutboxEvent(id,f.tenant,"DOCUMENT",f.document,"document.received.v1",Map.of("documentId",f.document.toString())));}
  var executable=java.nio.file.Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
  var command=new ProcessBuilder(executable,"-cp",System.getProperty("emitta.test.runtime-launcher"),InterruptedOutboxProcess.class.getName()).redirectErrorStream(true);
  var env=command.environment();env.put("SYNTHETIC_DB_URL",POSTGRES.getJdbcUrl());env.put("SYNTHETIC_DB_USER",POSTGRES.getUsername());env.put("SYNTHETIC_DB_PASSWORD",POSTGRES.getPassword());
  int livePort=Integer.parseInt(RABBIT.getDockerClient().inspectContainerCmd(RABBIT.getContainerId()).exec().getNetworkSettings().getPorts().getBindings().get(new com.github.dockerjava.api.model.ExposedPort(5672))[0].getHostPortSpec());
  env.put("SYNTHETIC_RABBIT_HOST",RABBIT.getHost());env.put("SYNTHETIC_RABBIT_PORT",Integer.toString(livePort));env.put("SYNTHETIC_RABBIT_USER",RABBIT.getAdminUsername());env.put("SYNTHETIC_RABBIT_PASSWORD",RABBIT.getAdminPassword());
  var process=command.start();
  try(var reader=Executors.newSingleThreadExecutor()) {
   var ready=reader.submit(()->{try(var input=process.inputReader()){String line;while((line=input.readLine())!=null)if(line.equals("CONFIRMED_UNMARKED"))return true;return false;}});
   try {
    assertTrue(ready.get(20,TimeUnit.SECONDS));
    for(var id:ids)assertNull(jdbc.queryForObject("SELECT published_at FROM emitta.outbox_events WHERE id=?",Object.class,id));
    process.destroyForcibly();assertTrue(process.waitFor(10,TimeUnit.SECONDS));
    var resumed=new io.github.imecuadorian.emitta.outbox.application.service.PublishPendingOutboxService(claims,publisher,java.time.Clock.systemUTC(),"synthetic-restarted",50,Duration.ofMinutes(2));
    org.awaitility.Awaitility.await().pollInterval(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(150)).until(()->resumed.publishBatch()==2);
    var listener=new FiscalDocumentListener(jobs,documents,publisher,tools.jackson.databind.json.JsonMapper.builder().build());
    for(int i=0;i<4;i++){var delivery=rabbit.receive("emitta.fiscal.documents",3000);assertNotNull(delivery);listener.onMessage(delivery);}
    for(var f:List.of(first,second))assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM emitta.processing_jobs WHERE document_id=? AND kind='FISCAL'",Integer.class,f.document));
    for(var id:ids){assertNotNull(jdbc.queryForObject("SELECT published_at FROM emitta.outbox_events WHERE id=?",Object.class,id));assertEquals(2,jdbc.queryForObject("SELECT attempts FROM emitta.outbox_events WHERE id=?",Integer.class,id));}
    verifyNoInteractions(fiscal);
   } finally {if(process.isAlive()){process.destroyForcibly();process.waitFor(10,TimeUnit.SECONDS);}reader.shutdownNow();}
  }
 }
 @Test @Order(14) void abruptlyKilledWorkerJvmReleasesLockAndDurableLeaseRecoversWithoutFiscalEffects() throws Exception {
  var f=fixture("AUTHORIZED");jobs.enqueue(DurableWorkPort.Kind.RIDE,f.tenant,f.document);
  assertEquals(1,jobs.claim(DurableWorkPort.Kind.RIDE,policy(3)).size());
  String executable=java.nio.file.Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
  var command=new ProcessBuilder(executable,"-cp",System.getProperty("emitta.test.runtime-launcher"),
      InterruptedWorkProcess.class.getName(),f.tenant.toString(),f.document.toString()).redirectErrorStream(true);
  command.environment().put("SYNTHETIC_DB_URL",POSTGRES.getJdbcUrl());
  command.environment().put("SYNTHETIC_DB_USER",POSTGRES.getUsername());
  command.environment().put("SYNTHETIC_DB_PASSWORD",POSTGRES.getPassword());
  var process=command.start();
  var reader=Executors.newSingleThreadExecutor();var diagnostics=new java.util.ArrayList<String>();
  try {
   var ready=reader.submit(()->{try(var input=process.inputReader()){String line;while((line=input.readLine())!=null){diagnostics.add(line);if(line.equals("LOCKED"))return true;}return false;}});
   assertTrue(ready.get(15,TimeUnit.SECONDS));
   assertTrue(jobs.claim(DurableWorkPort.Kind.RIDE,policy(3)).isEmpty());
  } finally {process.destroyForcibly();assertTrue(process.waitFor(15,TimeUnit.SECONDS));reader.shutdownNow();java.nio.file.Files.write(java.nio.file.Path.of("build/operational/interrupted-worker.log"),diagnostics);}
  when(ride.generate(f.tenant,f.document)).thenReturn(mock(DocumentArtifact.class));
  org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(()->assertEquals(1,runner(3).run(DurableWorkPort.Kind.RIDE)));
  var recovered=jobs.find(f.tenant,f.document,DurableWorkPort.Kind.RIDE).orElseThrow();
  assertEquals("COMPLETED",recovered.state());assertEquals(2,recovered.attempts());
  assertEquals(DocumentStatus.AUTHORIZED,documents.findById(f.document).orElseThrow().getStatus());
  verifyNoInteractions(fiscal);
 }
 @Test @Order(13) void measureLockAndPoolContentionAtOneTwoAndFourWorkers() throws Exception {
  var pool=context.getBean(javax.sql.DataSource.class).unwrap(com.zaxxer.hikari.HikariDataSource.class);
  var rows=new java.util.ArrayList<String>();
  rows.add("workers,pool_max,jobs,elapsed_ms,jobs_per_second,peak_active,peak_pending,mean_lock_ms");
  var one=new ProcessingPolicy(3,Duration.ofSeconds(5),Duration.ofMillis(10),Duration.ofSeconds(1),1);
  for(int concurrency:new int[]{1,2,4}) {
   jdbc.update("DELETE FROM emitta.processing_job_attempts");jdbc.update("DELETE FROM emitta.processing_jobs");
   for(int i=0;i<16;i++){var f=fixture("AUTHORIZED");jobs.enqueue(DurableWorkPort.Kind.RIDE,f.tenant,f.document);}
   var peakActive=new java.util.concurrent.atomic.AtomicInteger();var peakPending=new java.util.concurrent.atomic.AtomicInteger();
   var completed=new java.util.concurrent.atomic.AtomicInteger();var lockNanos=new java.util.concurrent.atomic.AtomicLong();
   long started=System.nanoTime();
   try(var sampler=Executors.newSingleThreadScheduledExecutor();var workers=Executors.newFixedThreadPool(concurrency)) {
    sampler.scheduleAtFixedRate(()->{var m=pool.getHikariPoolMXBean();peakActive.accumulateAndGet(m.getActiveConnections(),Math::max);peakPending.accumulateAndGet(m.getThreadsAwaitingConnection(),Math::max);},0,1,TimeUnit.MILLISECONDS);
    var tasks=new java.util.ArrayList<Future<?>>();
    for(int i=0;i<concurrency;i++)tasks.add(workers.submit(()->{
     while(true){var claim=jobs.claim(DurableWorkPort.Kind.RIDE,one);if(claim.isEmpty())return;
      long lockStarted=System.nanoTime();
      if(jobs.withLock(claim.getFirst(),()->jdbc.execute("SELECT pg_sleep(0.02)")))completed.incrementAndGet();
      lockNanos.addAndGet(System.nanoTime()-lockStarted);
     }
    }));
    for(var task:tasks)task.get(30,TimeUnit.SECONDS);
    sampler.shutdownNow();
   }
   double elapsed=(System.nanoTime()-started)/1_000_000d;
   assertEquals(16,completed.get());assertEquals(16,jobs.count("COMPLETED"));
   assertEquals(16,jdbc.queryForObject("SELECT count(*) FROM emitta.processing_job_attempts",Integer.class));
   assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM emitta.processing_jobs j JOIN emitta.documents d ON d.id=j.document_id WHERE d.status<>'AUTHORIZED'",Integer.class));
   rows.add(String.format(java.util.Locale.ROOT,"%d,%d,16,%.2f,%.2f,%d,%d,%.2f",concurrency,pool.getMaximumPoolSize(),elapsed,16000/elapsed,peakActive.get(),peakPending.get(),lockNanos.get()/16_000_000d));
  }
  var output=java.nio.file.Path.of("build/operational/worker-capacity.csv");java.nio.file.Files.createDirectories(output.getParent());
  java.nio.file.Files.write(output,rows);
 }
 @BeforeEach void isolateJobs(TestInfo test) {
  if(test.getTestMethod().orElseThrow().getName().equals("restartedBackendRecoversExpiredClaimAndRetainsAuthorization"))return;
  // Only the disposable Testcontainers database and queues; preserve the restart fixture between the first two methods.
  jdbc.update("DELETE FROM emitta.processing_job_attempts");jdbc.update("DELETE FROM emitta.processing_jobs");jdbc.update("DELETE FROM emitta.outbox_events");
  var admin=new RabbitAdmin(rabbit);admin.initialize();admin.purgeQueue("emitta.processing.dlq",false);admin.purgeQueue("emitta.fiscal.documents",false);
 }
 private record Fixture(UUID tenant,UUID document) {}
 private ProcessingPolicy policy(int max) {return new ProcessingPolicy(max,Duration.ofMillis(100),Duration.ofMillis(1),Duration.ofMillis(2),5);}
 private DurableWorkRunner runner(int max) {return new DurableWorkRunner(jobs,policy(max),documents,fiscal,context.getBeanProvider(GenerateAndStoreInvoiceRideUseCase.class),metrics);}
 private Fixture fixture(String state) {
  UUID tenant=UUID.randomUUID(),taxpayer=UUID.randomUUID(),est=UUID.randomUUID(),point=UUID.randomUUID(),document=UUID.randomUUID();
  String key=String.format("%049d",new java.math.BigInteger(UUID.randomUUID().toString().replace("-",""),16));
  jdbc.update("INSERT INTO emitta.tenants(id,name,status) VALUES (?,'Synthetic recovery tenant','ACTIVE')",tenant);
  jdbc.update("INSERT INTO emitta.taxpayers(id,tenant_id,ruc,legal_name,main_address,status,test_enabled,production_enabled) VALUES (?,?,'1790012345001','SYNTHETIC RECOVERY','Synthetic address','ACTIVE',true,false)",taxpayer,tenant);
  jdbc.update("INSERT INTO emitta.establishments(id,taxpayer_id,code,status) VALUES (?,?,'001','ACTIVE')",est,taxpayer);
  jdbc.update("INSERT INTO emitta.points_of_issue(id,establishment_id,code,status) VALUES (?,?,'001','ACTIVE')",point,est);
  jdbc.update("INSERT INTO emitta.documents(id,tenant_id,taxpayer_id,point_of_issue_id,document_type,environment,status,idempotency_key,issued_at,access_key,sequential,authorized_at) VALUES (?,?,?,?,'INVOICE','TEST',?,?,CURRENT_TIMESTAMP,?,1,CASE WHEN ?='AUTHORIZED' THEN CURRENT_TIMESTAMP ELSE NULL END)",document,tenant,taxpayer,point,state,UUID.randomUUID().toString(),key,state);
  if(state.equals("AUTHORIZED")) {
   jdbc.update("INSERT INTO emitta.document_artifacts(document_id,artifact_type,content_type,storage_key,sha256,size_bytes) VALUES (?,'AUTHORIZED_XML','application/xml','synthetic-no-storage-object',?,1)",document,"b".repeat(64));
   jdbc.update("INSERT INTO emitta.sri_authorizations(document_id,authorization_number,authorized_at,environment,authorized_xml_sha256) SELECT id,?,authorized_at,'TEST',? FROM emitta.documents WHERE id=?",key,"b".repeat(64),document);
  }
  return new Fixture(tenant,document);
 }
 private void makeDue(UUID id) {jdbc.update("UPDATE emitta.processing_jobs SET next_attempt_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE id=?",id);}
 @Test @Order(1) @DirtiesContext(methodMode=DirtiesContext.MethodMode.AFTER_METHOD)
 void durableClaimSurvivesFullSpringContextShutdown() {
  var f=fixture("AUTHORIZED");restartDocument=f.document;restartTenant=f.tenant;
  jobs.enqueue(DurableWorkPort.Kind.RIDE,f.tenant,f.document);
  var claimed=jobs.claim(DurableWorkPort.Kind.RIDE,policy(3));assertEquals(1,claimed.size());
  jdbc.update("UPDATE emitta.processing_jobs SET lease_until=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE id=?",claimed.getFirst().id());
 }
 @Test @Order(2) void restartedBackendRecoversExpiredClaimAndRetainsAuthorization() {
  assertNotNull(restartDocument);when(ride.generate(restartTenant,restartDocument)).thenReturn(mock(DocumentArtifact.class));
  assertEquals(1,runner(3).run(DurableWorkPort.Kind.RIDE));
  var job=jobs.find(restartTenant,restartDocument,DurableWorkPort.Kind.RIDE).orElseThrow();assertEquals("COMPLETED",job.state());assertEquals(2,job.attempts());
  assertEquals("LEASE_EXPIRED",jdbc.queryForObject("SELECT result FROM emitta.processing_job_attempts WHERE job_id=? AND attempt_number=1",String.class,job.id()));
  assertEquals(DocumentStatus.AUTHORIZED,documents.findById(restartDocument).orElseThrow().getStatus());
 }
 @Test @Order(3) void retriesAreLimitedJournaledAndRecoverableOnlyForSameTenant() {
  var f=fixture("AUTHORIZED");jobs.enqueue(DurableWorkPort.Kind.RIDE,f.tenant,f.document);
  when(ride.generate(f.tenant,f.document)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic outage"));
  runner(2).run(DurableWorkPort.Kind.RIDE);var job=jobs.find(f.tenant,f.document,DurableWorkPort.Kind.RIDE).orElseThrow();assertEquals("PENDING",job.state());makeDue(job.id());
  runner(2).run(DurableWorkPort.Kind.RIDE);job=jobs.find(f.tenant,f.document,DurableWorkPort.Kind.RIDE).orElseThrow();assertEquals("DEAD",job.state());assertEquals(2,job.attempts());
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM emitta.outbox_events WHERE aggregate_id=? AND event_type='processing.failed.v1'",Integer.class,job.id()));
  assertFalse(jobs.recover(UUID.randomUUID(),job.id()));assertTrue(jobs.find(UUID.randomUUID(),f.document,DurableWorkPort.Kind.RIDE).isEmpty());
  assertTrue(jobs.recover(f.tenant,job.id()));doReturn(mock(DocumentArtifact.class)).when(ride).generate(f.tenant,f.document);
  assertEquals(1,runner(2).run(DurableWorkPort.Kind.RIDE));assertEquals(3,jobs.find(f.tenant,f.document,DurableWorkPort.Kind.RIDE).orElseThrow().attempts());
  assertEquals(DocumentStatus.AUTHORIZED,documents.findById(f.document).orElseThrow().getStatus());
 }
 @Test @Order(4) void concurrentClaimsAndDuplicateEventsProduceOneWorkExecution() throws Exception {
  var f=fixture("AUTHORIZED");jobs.enqueue(DurableWorkPort.Kind.RIDE,f.tenant,f.document);jobs.enqueue(DurableWorkPort.Kind.RIDE,f.tenant,f.document);
  doReturn(mock(DocumentArtifact.class)).when(ride).generate(f.tenant,f.document);
  try(var pool=Executors.newFixedThreadPool(2)) {
   var gate=new CountDownLatch(1);
   var a=pool.submit(()->{gate.await();return runner(3).run(DurableWorkPort.Kind.RIDE);});var b=pool.submit(()->{gate.await();return runner(3).run(DurableWorkPort.Kind.RIDE);});gate.countDown();
   assertEquals(1,a.get(10,TimeUnit.SECONDS)+b.get(10,TimeUnit.SECONDS));
  }
  verify(ride,times(1)).generate(f.tenant,f.document);
 }
 @Test @Order(5) void permanentAndUncertainResultsCannotBeAutomaticallyRecovered() {
  var invalid=fixture("AUTHORIZED");jobs.enqueue(DurableWorkPort.Kind.RIDE,invalid.tenant,invalid.document);
  when(ride.generate(invalid.tenant,invalid.document)).thenThrow(new IllegalArgumentException("synthetic invalid artifact"));runner(3).run(DurableWorkPort.Kind.RIDE);
  var bad=jobs.find(invalid.tenant,invalid.document,DurableWorkPort.Kind.RIDE).orElseThrow();assertEquals("DEAD",bad.state());assertFalse(jobs.recover(invalid.tenant,bad.id()));
  var f=fixture("QUEUED");jobs.enqueue(DurableWorkPort.Kind.FISCAL,f.tenant,f.document);
  jdbc.update("UPDATE emitta.documents SET status='SUBMITTED' WHERE id=?",f.document);runner(3).run(DurableWorkPort.Kind.FISCAL);
  var blocked=jobs.find(f.tenant,f.document,DurableWorkPort.Kind.FISCAL).orElseThrow();assertEquals("BLOCKED",blocked.state());assertFalse(jobs.recover(f.tenant,blocked.id()));verifyNoInteractions(fiscal);
 }
 @Test @Order(6) void malformedMessagesReachRealDlqAndConsumerRedeliveryIsIdempotent() throws Exception {
  new RabbitAdmin(rabbit).initialize();var listener=new FiscalDocumentListener(jobs,documents,publisher,tools.jackson.databind.json.JsonMapper.builder().build());
  listener.onMessage(new Message("not-json".getBytes(),new MessageProperties()));
  var dlq=rabbit.receive("emitta.processing.dlq",2000);assertNotNull(dlq);assertEquals("VALIDATION",dlq.getMessageProperties().getHeader("x-emitta-failure-category"));
  var f=fixture("QUEUED");var event=new Message(("{\"documentId\":\""+f.document+"\"}").getBytes(),new MessageProperties());
  publisher.publishMessage("emitta.events","document.received.v1",event,UUID.randomUUID().toString());
  var cf=new com.rabbitmq.client.ConnectionFactory();cf.setHost(RABBIT.getHost());cf.setPort(RABBIT.getAmqpPort());cf.setUsername(RABBIT.getAdminUsername());cf.setPassword(RABBIT.getAdminPassword());
  try(var connection=cf.newConnection();var channel=connection.createChannel()) {
   var delivery=channel.basicGet("emitta.fiscal.documents",false);assertNotNull(delivery);
   listener.onMessage(new Message(delivery.getBody(),new MessageProperties()));
   // Crash/close without ACK: RabbitMQ redelivers after durable admission has already committed.
  }
  var delivery=rabbit.receive("emitta.fiscal.documents",3000);assertNotNull(delivery);listener.onMessage(delivery);
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM emitta.processing_jobs WHERE document_id=? AND kind='FISCAL'",Integer.class,f.document));
 }
 @Test @Order(7) void crashAfterPublicationBeforeDatabaseMarkProducesHarmlessRedelivery() {
  var f=fixture("QUEUED");UUID id=UUID.randomUUID();
  outbox.append(new OutboxEvent(id,f.tenant,"DOCUMENT",f.document,"document.received.v1",Map.of("documentId",f.document.toString())));
  var batch=claims.claimBatch("synthetic-crashed-publisher",1,java.time.Instant.now().minusSeconds(60));assertEquals(1,batch.size());
  publisher.publish(batch.getFirst()); // broker confirms; simulate crash before markPublished
  jdbc.update("UPDATE emitta.outbox_events SET claimed_at=CURRENT_TIMESTAMP-INTERVAL '10 minutes' WHERE id=?",id);
  assertEquals(1,publishOutbox.publishBatch());
  var listener=new FiscalDocumentListener(jobs,documents,publisher,tools.jackson.databind.json.JsonMapper.builder().build());
  for(int i=0;i<2;i++){var delivery=rabbit.receive("emitta.fiscal.documents",2000);assertNotNull(delivery);listener.onMessage(delivery);}
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM emitta.processing_jobs WHERE document_id=? AND kind='FISCAL'",Integer.class,f.document));
  assertEquals(2,jdbc.queryForObject("SELECT attempts FROM emitta.outbox_events WHERE id=?",Integer.class,id));
 }
 @Test @Order(8) void publisherConfirmWithoutRouteDoesNotMarkOutboxPublished() {
  new RabbitAdmin(rabbit).initialize();UUID id=UUID.randomUUID();
  outbox.append(new OutboxEvent(id,null,"DOCUMENT",UUID.randomUUID(),"unbound.synthetic.v1",Map.of("test",true)));
  publishOutbox.publishBatch();assertNull(jdbc.queryForObject("SELECT published_at FROM emitta.outbox_events WHERE id=?",Object.class,id));
  assertEquals(1,jdbc.queryForObject("SELECT attempts FROM emitta.outbox_events WHERE id=?",Integer.class,id));
 }
 @Test @Order(12) void rabbitDisconnectRetainsOutboxAndRecoversAfterBrokerRestart() throws Exception {
  new RabbitAdmin(rabbit).initialize();UUID id=UUID.randomUUID();var f=fixture("QUEUED");
  outbox.append(new OutboxEvent(id,f.tenant,"DOCUMENT",f.document,"document.received.v1",Map.of("documentId",f.document.toString())));
  var docker=RABBIT.getDockerClient();docker.stopContainerCmd(RABBIT.getContainerId()).withTimeout(1).exec();
  try {publishOutbox.publishBatch();assertNull(jdbc.queryForObject("SELECT published_at FROM emitta.outbox_events WHERE id=?",Object.class,id));}
  finally {docker.startContainerCmd(RABBIT.getContainerId()).exec();}
  var cf=(CachingConnectionFactory)rabbit.getConnectionFactory();cf.resetConnection();
  long deadline=System.nanoTime()+Duration.ofSeconds(40).toNanos();boolean ready=false;
  while(System.nanoTime()<deadline) {try{if(RABBIT.execInContainer("rabbitmq-diagnostics","-q","ping").getExitCode()==0){ready=true;break;}}catch(Exception unavailable){}Thread.sleep(500);}
  assertTrue(ready,"Rabbit broker did not recover");
  int port=Integer.parseInt(docker.inspectContainerCmd(RABBIT.getContainerId()).exec().getNetworkSettings().getPorts().getBindings().get(new com.github.dockerjava.api.model.ExposedPort(5672))[0].getHostPortSpec());cf.setAddresses(RABBIT.getHost()+":"+port);cf.resetConnection();
  if(cf.getPublisherConnectionFactory() instanceof CachingConnectionFactory pcf){pcf.setAddresses(RABBIT.getHost()+":"+port);pcf.resetConnection();}
  org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).ignoreExceptions().until(()->rabbit.execute(channel->channel.isOpen()));
  new RabbitAdmin(rabbit).initialize();jdbc.update("UPDATE emitta.outbox_events SET next_attempt_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE id=?",id);
  publishOutbox.publishBatch();assertNotNull(jdbc.queryForObject("SELECT published_at FROM emitta.outbox_events WHERE id=?",Object.class,id));
 }
 @Test @Order(10) void expiredLeaseCannotReclaimAnActivelyLockedJob() throws Exception {
  var f=fixture("AUTHORIZED");jobs.enqueue(DurableWorkPort.Kind.RIDE,f.tenant,f.document);
  var job=jobs.claim(DurableWorkPort.Kind.RIDE,policy(3)).getFirst();
  jdbc.update("UPDATE emitta.processing_jobs SET lease_until=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE id=?",job.id());
  var started=new CountDownLatch(1);var finish=new CountDownLatch(1);
  try(var pool=Executors.newSingleThreadExecutor()) {
   var active=pool.submit(()->jobs.withLock(job,()->{started.countDown();try{finish.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}}));
   try{assertTrue(started.await(5,TimeUnit.SECONDS));assertTrue(jobs.claim(DurableWorkPort.Kind.RIDE,policy(3)).isEmpty());}
   finally{finish.countDown();}
   assertTrue(active.get(5,TimeUnit.SECONDS));
  }
  assertEquals(1,jobs.find(f.tenant,f.document,DurableWorkPort.Kind.RIDE).orElseThrow().attempts());
 }
 @Test @Order(11) void rideJobQueryEnforcesScopeAndTenantBeforeReturningMetadata() throws Exception {
  var f=fixture("AUTHORIZED");var other=fixture("AUTHORIZED");jobs.enqueue(DurableWorkPort.Kind.RIDE,f.tenant,f.document);
  var request=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/documents/"+f.document+"/ride-job");
  mvc.perform(request.with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
    .jwt(j->j.claim("tenant_id",f.tenant.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_artifacts:read"))))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.state").value("PENDING"))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control","no-store"));
  mvc.perform(request.with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
    .jwt(j->j.claim("tenant_id",other.tenant.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_artifacts:read"))))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
  mvc.perform(request.with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
    .jwt(j->j.claim("tenant_id",f.tenant.toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_documents:read"))))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
 }

 @Test @Order(9) void mutableBrokerPolicyRoutesRejectedDeliveryToDlq() throws Exception {
  if(System.getProperty("os.name").startsWith("Windows")) {
   var command=new ProcessBuilder("powershell","-NoProfile","-File","scripts/Configure-RabbitDlqPolicy.ps1","-Container",RABBIT.getContainerId()).redirectErrorStream(true).start();
   try{assertTrue(command.waitFor(30,TimeUnit.SECONDS));assertEquals(0,command.exitValue(),new String(command.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
   finally{command.destroyForcibly();}
  } else {
   var result=RABBIT.execInContainer("rabbitmqctl","set_policy","--apply-to","queues","emitta-fiscal-dlx","^emitta[.]fiscal[.]documents$","{\"dead-letter-exchange\":\"emitta.deadletters\"}");
   assertEquals(0,result.getExitCode());
  }
  publisher.publishMessage("emitta.events","document.received.v1",new Message("synthetic-policy".getBytes(),new MessageProperties()),UUID.randomUUID().toString());
  var cf=new com.rabbitmq.client.ConnectionFactory();cf.setHost(RABBIT.getHost());cf.setPort(RABBIT.getAmqpPort());cf.setUsername(RABBIT.getAdminUsername());cf.setPassword(RABBIT.getAdminPassword());
  try(var connection=cf.newConnection();var channel=connection.createChannel()) {
   var delivery=channel.basicGet("emitta.fiscal.documents",false);assertNotNull(delivery);channel.basicReject(delivery.getEnvelope().getDeliveryTag(),false);
  }
  var rejected=rabbit.receive("emitta.processing.dlq",3000);assertNotNull(rejected);assertNotNull(rejected.getMessageProperties().getHeader("x-death"));
 }

}
