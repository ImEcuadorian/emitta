package io.github.imecuadorian.emitta.recovery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.stereotype.Repository;
import io.github.imecuadorian.emitta.outbox.application.port.out.OutboxEventPort;
import io.github.imecuadorian.emitta.outbox.application.model.OutboxEvent;
import java.util.*;
import java.time.*;
@Repository
public class PostgreSqlDurableWorkAdapter implements DurableWorkPort {
 private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final OutboxEventPort outbox;
 private final io.micrometer.core.instrument.MeterRegistry metrics;
 private final RowMapper<Job> mapper=(rs,n)->new Job(rs.getObject("id",UUID.class),rs.getObject("tenant_id",UUID.class),
    rs.getObject("document_id",UUID.class),Kind.valueOf(rs.getString("kind")),rs.getString("state"),rs.getInt("attempts"),
    rs.getObject("lease_token",UUID.class),rs.getObject("next_attempt_at",OffsetDateTime.class).toInstant(),rs.getString("failure_category"));
 public PostgreSqlDurableWorkAdapter(JdbcTemplate jdbc,PlatformTransactionManager tm,OutboxEventPort outbox) {
  this(jdbc,tm,outbox,new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
 }
 @org.springframework.beans.factory.annotation.Autowired
 public PostgreSqlDurableWorkAdapter(JdbcTemplate jdbc,PlatformTransactionManager tm,OutboxEventPort outbox,io.micrometer.core.instrument.MeterRegistry metrics) {
  this.jdbc=jdbc;this.tx=new TransactionTemplate(tm);this.outbox=outbox;this.metrics=metrics;
 }
 @Override public void enqueue(Kind kind,UUID tenantId,UUID documentId) {
  int inserted=jdbc.update("""
   INSERT INTO emitta.processing_jobs(tenant_id,document_id,kind)
   SELECT tenant_id,id,? FROM emitta.documents WHERE id=? AND tenant_id=?
    AND ((?='FISCAL' AND status IN ('QUEUED','GENERATING')) OR (?='RIDE' AND status='AUTHORIZED' AND authorized_at IS NOT NULL
      AND EXISTS(SELECT 1 FROM emitta.sri_authorizations s JOIN emitta.document_artifacts a ON a.document_id=s.document_id
        AND a.artifact_type='AUTHORIZED_XML' AND a.sha256=s.authorized_xml_sha256
        WHERE s.document_id=documents.id AND s.authorization_number=documents.access_key AND s.environment=documents.environment AND s.authorized_at=documents.authorized_at)))
   ON CONFLICT(document_id,kind) DO NOTHING
   """,kind.name(),documentId,tenantId,kind.name(),kind.name());
  if(inserted==0 && find(tenantId,documentId,kind).isEmpty()) throw new IllegalArgumentException("Document ineligible for durable work");
  if(inserted>0) {
   Runnable committed=()->metrics.counter("emitta.processing.admitted","kind",kind.name()).increment();
   if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
    org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){@Override public void afterCommit(){committed.run();}});
   else committed.run();
  }
 }
 @Override public Optional<Job> find(UUID tenantId,UUID documentId,Kind kind) {
  return jdbc.query("SELECT * FROM emitta.processing_jobs WHERE tenant_id=? AND document_id=? AND kind=?",mapper,tenantId,documentId,kind.name()).stream().findFirst();
 }
 @Override public void enqueueFiscalBatch(List<AdmissionTarget> targets) {
  if(targets.size()>50)throw new IllegalArgumentException("Admission batch exceeds 50");
  if(targets.isEmpty())return;
  // Stable order avoids opposite unique-key acquisition order across concurrent consumers.
  var distinct=targets.stream().distinct().sorted(Comparator.comparing(AdmissionTarget::documentId).thenComparing(AdmissionTarget::tenantId)).toList();
  tx.executeWithoutResult(status->{
   var inserted=jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Integer>) connection->{
    var tenants=connection.createArrayOf("uuid",distinct.stream().map(AdmissionTarget::tenantId).toArray(UUID[]::new));
    var documents=connection.createArrayOf("uuid",distinct.stream().map(AdmissionTarget::documentId).toArray(UUID[]::new));
    var sample=io.micrometer.core.instrument.Timer.start(metrics);
    try {
     int count;
     try(var statement=connection.prepareStatement("""
      INSERT INTO emitta.processing_jobs(tenant_id,document_id,kind)
      SELECT d.tenant_id,d.id,'FISCAL' FROM unnest(?::uuid[],?::uuid[]) AS r(tenant_id,document_id)
      JOIN emitta.documents d ON d.id=r.document_id AND d.tenant_id=r.tenant_id
      WHERE d.status IN ('QUEUED','GENERATING') ORDER BY d.id
      ON CONFLICT(document_id,kind) DO NOTHING
      """)) {
      org.springframework.jdbc.datasource.DataSourceUtils.applyTimeout(statement,jdbc.getDataSource(),5);
      statement.setArray(1,tenants);statement.setArray(2,documents);count=statement.executeUpdate();
     }
     if(count!=distinct.size()) {
      try(var statement=connection.prepareStatement("""
       SELECT count(*) FROM unnest(?::uuid[],?::uuid[]) AS r(tenant_id,document_id)
       JOIN emitta.processing_jobs j ON j.document_id=r.document_id AND j.tenant_id=r.tenant_id AND j.kind='FISCAL'
       """)) {
       org.springframework.jdbc.datasource.DataSourceUtils.applyTimeout(statement,jdbc.getDataSource(),5);
       statement.setArray(1,tenants);statement.setArray(2,documents);
       try(var rows=statement.executeQuery()) {rows.next();if(rows.getInt(1)!=distinct.size())throw new IllegalArgumentException("Document ineligible for durable work");}
      }
     }
     return count;
    }finally {sample.stop(metrics.timer("emitta.rabbit.admission.statement.duration"));tenants.free();documents.free();}
   });
   org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){
    private long commitStarted;
    @Override public void beforeCommit(boolean readOnly){commitStarted=System.nanoTime();}
    @Override public void afterCommit(){
     metrics.counter("emitta.processing.admitted","kind","FISCAL").increment(inserted);
     if(commitStarted!=0)metrics.timer("emitta.rabbit.admission.commit.duration").record(System.nanoTime()-commitStarted,java.util.concurrent.TimeUnit.NANOSECONDS);
    }
   });
  });
 }
 public int discoverMissingRides(int limit) {
  // Evidence hash must agree with the persisted authorized artifact. No SRI call or state change.
  return jdbc.update("""
   INSERT INTO emitta.processing_jobs(tenant_id,document_id,kind)
   SELECT d.tenant_id,d.id,'RIDE' FROM emitta.documents d
    JOIN emitta.sri_authorizations s ON s.document_id=d.id AND s.authorization_number=d.access_key AND s.environment=d.environment AND s.authorized_at=d.authorized_at
    JOIN emitta.document_artifacts a ON a.document_id=d.id AND a.artifact_type='AUTHORIZED_XML' AND a.sha256=s.authorized_xml_sha256
    WHERE d.status='AUTHORIZED' AND d.authorized_at IS NOT NULL
    AND NOT EXISTS(SELECT 1 FROM emitta.document_artifacts p WHERE p.document_id=d.id AND p.artifact_type='RIDE_PDF')
    AND NOT EXISTS(SELECT 1 FROM emitta.processing_jobs j WHERE j.document_id=d.id AND j.kind='RIDE')
    ORDER BY d.authorized_at LIMIT ? ON CONFLICT(document_id,kind) DO NOTHING
   """,limit);
 }
 public List<Job> claim(Kind kind,ProcessingPolicy policy) {
  return claim(kind,policy,policy.batchSize());
 }
 public List<Job> claim(Kind kind,ProcessingPolicy policy,int limit) {
  if(limit<1 || limit>policy.batchSize())throw new IllegalArgumentException("Invalid claim limit");
  return tx.execute(status->{
   var expired=jdbc.query("""
    WITH expired AS (SELECT id FROM emitta.processing_jobs WHERE kind=? AND state='RUNNING' AND lease_until<CURRENT_TIMESTAMP AND attempts-retry_budget_from>=? FOR UPDATE SKIP LOCKED)
    UPDATE emitta.processing_jobs j SET state='DEAD',failure_category='TRANSIENT',lease_token=NULL,lease_until=NULL,updated_at=CURRENT_TIMESTAMP
    FROM expired e WHERE j.id=e.id RETURNING j.*
    """,mapper,kind.name(),policy.maxAttempts());
   for(var job:expired) { finishJournal(job,"LEASE_EXPIRED");notice(job,"TRANSIENT"); }
   UUID token=UUID.randomUUID();
   var jobs=jdbc.query("""
    WITH candidate AS (SELECT id FROM emitta.processing_jobs
      WHERE kind=? AND attempts-retry_budget_from<? AND ((state='PENDING' AND next_attempt_at<=CURRENT_TIMESTAMP)
        OR (state='RUNNING' AND lease_until<CURRENT_TIMESTAMP))
      ORDER BY next_attempt_at FOR UPDATE SKIP LOCKED LIMIT ?)
    UPDATE emitta.processing_jobs j SET state='RUNNING',attempts=attempts+1,lease_token=?,
      lease_until=CURRENT_TIMESTAMP+(? * INTERVAL '1 millisecond'),updated_at=CURRENT_TIMESTAMP
      FROM candidate c WHERE j.id=c.id RETURNING j.*
    """,mapper,kind.name(),policy.maxAttempts(),limit,token,policy.lease().toMillis());
   for(var job:jobs) {
    jdbc.update("UPDATE emitta.processing_job_attempts SET finished_at=CURRENT_TIMESTAMP,result='LEASE_EXPIRED' WHERE job_id=? AND finished_at IS NULL",job.id());
    jdbc.update("INSERT INTO emitta.processing_job_attempts(job_id,attempt_number,lease_token) VALUES (?,?,?)",job.id(),job.attempts(),job.leaseToken());
   }
   return jobs;
  });
 }
 public boolean withLock(Job job,Runnable work) {
  // Dedicated row lock; fiscal services retain their existing independent transaction boundaries.
  // Active work cannot be reclaimed even if its lease expires: claim uses SKIP LOCKED.
  try(var connection=java.util.Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
   connection.setAutoCommit(false);
   try {
    try(var select=connection.prepareStatement("SELECT id FROM emitta.processing_jobs WHERE id=? AND state='RUNNING' AND lease_token=? FOR UPDATE")) {
     select.setObject(1,job.id());select.setObject(2,job.leaseToken());
     try(var rows=select.executeQuery()) {if(!rows.next()){connection.rollback();return false;}}
    }
    work.run();
    try(var update=connection.prepareStatement("UPDATE emitta.processing_jobs SET state='COMPLETED',failure_category=NULL,lease_token=NULL,lease_until=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=? AND lease_token=?")) {
     update.setObject(1,job.id());update.setObject(2,job.leaseToken());update.executeUpdate();
    }
    try(var journal=connection.prepareStatement("UPDATE emitta.processing_job_attempts SET finished_at=CURRENT_TIMESTAMP,result='COMPLETED' WHERE job_id=? AND attempt_number=? AND finished_at IS NULL")) {
     journal.setObject(1,job.id());journal.setInt(2,job.attempts());journal.executeUpdate();
    }
    connection.commit();return true;
   } catch(RuntimeException | java.sql.SQLException failure) {connection.rollback();throw failure;}
  } catch(java.sql.SQLException failure) {throw new org.springframework.dao.DataAccessResourceFailureException("Durable work lock unavailable",failure);}
 }
 public void failed(Job job,WorkFailureClassifier.Category category,ProcessingPolicy policy) {
  tx.executeWithoutResult(status->{
   var budget=jdbc.queryForObject("SELECT attempts-retry_budget_from FROM emitta.processing_jobs WHERE id=?",Integer.class,job.id());
   String state=category==WorkFailureClassifier.Category.EXTERNAL_UNKNOWN?"BLOCKED":category==WorkFailureClassifier.Category.TRANSIENT && budget<policy.maxAttempts()?"PENDING":"DEAD";
   int changed=jdbc.update("""
    UPDATE emitta.processing_jobs SET state=?,failure_category=?,lease_token=NULL,lease_until=NULL,
      next_attempt_at=CURRENT_TIMESTAMP+(? * INTERVAL '1 millisecond'),updated_at=CURRENT_TIMESTAMP
    WHERE id=? AND state='RUNNING' AND lease_token=?
    """,state,category.name(),policy.delay(job.attempts()).toMillis(),job.id(),job.leaseToken());
   if(changed==1) { finishJournal(job,category.name());if(state.equals("DEAD") || state.equals("BLOCKED"))notice(job,category.name()); }
  });
 }
 private void finishJournal(Job job,String result) { jdbc.update("UPDATE emitta.processing_job_attempts SET finished_at=CURRENT_TIMESTAMP,result=? WHERE job_id=? AND attempt_number=? AND finished_at IS NULL",result,job.id(),job.attempts()); }
 private void notice(Job job,String category) {outbox.append(new OutboxEvent(UUID.randomUUID(),job.tenantId(),"PROCESSING_JOB",job.id(),"processing.failed.v1",Map.of("jobId",job.id().toString(),"documentId",job.documentId().toString(),"kind",job.kind().name(),"category",category)));}
 @Override public boolean recover(UUID tenantId,UUID jobId) {
  // Only infrastructure failures. No arbitrary document-state edits and no external-result replay.
  return jdbc.update("""
   UPDATE emitta.processing_jobs j SET state='PENDING',retry_budget_from=attempts,recoveries=recoveries+1,
     next_attempt_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP
   WHERE j.id=? AND j.tenant_id=? AND j.state='DEAD' AND j.failure_category='TRANSIENT'
     AND EXISTS(SELECT 1 FROM emitta.documents d WHERE d.id=j.document_id AND d.tenant_id=j.tenant_id
       AND ((j.kind='RIDE' AND d.status='AUTHORIZED') OR (j.kind='FISCAL' AND d.status IN ('QUEUED','GENERATING'))))
   """,jobId,tenantId)==1;
 }
 @Override public boolean recoverAdmission(UUID tenantId,UUID documentId,UUID eventId) {
  return Boolean.TRUE.equals(tx.execute(status->{
   // Reconstruct only from the original durable event, never from an untrusted DLQ payload.
   boolean eligible=Boolean.TRUE.equals(jdbc.queryForObject("""
    SELECT EXISTS(SELECT 1 FROM emitta.outbox_events e JOIN emitta.documents d ON d.id=e.aggregate_id
     WHERE e.id=? AND e.tenant_id=? AND d.tenant_id=? AND d.id=? AND e.aggregate_type='DOCUMENT'
       AND e.event_type='document.received.v1' AND e.published_at IS NOT NULL
       AND e.payload->>'documentId'=? AND d.status IN ('QUEUED','GENERATING')
       AND NOT EXISTS(SELECT 1 FROM emitta.processing_jobs j WHERE j.document_id=d.id AND j.kind='FISCAL'))
    """,Boolean.class,eventId,tenantId,tenantId,documentId,documentId.toString()));
   if(!eligible)return false;
   enqueueFiscalBatch(List.of(new AdmissionTarget(tenantId,documentId)));
   jdbc.update("UPDATE emitta.processing_jobs SET recoveries=recoveries+1,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND document_id=? AND kind='FISCAL'",tenantId,documentId);
   return true;
  }));
 }
 public double pendingOutbox() {return jdbc.queryForObject("SELECT count(*) FROM emitta.outbox_events WHERE published_at IS NULL",Long.class).doubleValue();}
 public double oldestPendingAge() {return jdbc.queryForObject("SELECT COALESCE(EXTRACT(EPOCH FROM CURRENT_TIMESTAMP-min(created_at)),0) FROM emitta.processing_jobs WHERE state='PENDING'",Double.class);}
 public double count(String state) {return jdbc.queryForObject("SELECT count(*) FROM emitta.processing_jobs WHERE state=?",Long.class,state).doubleValue();}
 public double pendingRides() {return jdbc.queryForObject("""
  SELECT count(*) FROM emitta.documents d
  JOIN emitta.sri_authorizations s ON s.document_id=d.id AND s.authorization_number=d.access_key
    AND s.environment=d.environment AND s.authorized_at=d.authorized_at
  JOIN emitta.document_artifacts x ON x.document_id=d.id AND x.artifact_type='AUTHORIZED_XML' AND x.sha256=s.authorized_xml_sha256
  WHERE d.status='AUTHORIZED' AND NOT EXISTS(SELECT 1 FROM emitta.document_artifacts a WHERE a.document_id=d.id AND a.artifact_type='RIDE_PDF')
  """,Long.class).doubleValue();}
}
