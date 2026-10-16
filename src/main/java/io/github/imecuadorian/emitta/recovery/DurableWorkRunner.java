package io.github.imecuadorian.emitta.recovery;
import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.fiscalprocessing.application.port.in.ProcessFiscalDocumentUseCase;
import io.github.imecuadorian.emitta.fiscalprocessing.application.command.ProcessFiscalDocumentCommand;
import io.github.imecuadorian.emitta.ride.application.port.in.GenerateAndStoreInvoiceRideUseCase;
import org.springframework.beans.factory.ObjectProvider;
import io.micrometer.core.instrument.*;
public final class DurableWorkRunner {
 private final PostgreSqlDurableWorkAdapter jobs;private final ProcessingPolicy policy;private final DocumentRepository documents;
 private final ProcessFiscalDocumentUseCase fiscal;private final ObjectProvider<GenerateAndStoreInvoiceRideUseCase> ride;private final MeterRegistry metrics;
 private ObjectProvider<SriTestWorkflow> sri;
 void setSriWorkflow(ObjectProvider<SriTestWorkflow> sri){this.sri=sri;}
 public DurableWorkRunner(PostgreSqlDurableWorkAdapter jobs,ProcessingPolicy policy,DocumentRepository documents,
       ProcessFiscalDocumentUseCase fiscal,ObjectProvider<GenerateAndStoreInvoiceRideUseCase> ride,MeterRegistry metrics) {
  this.jobs=jobs;this.policy=policy;this.documents=documents;this.fiscal=fiscal;this.ride=ride;this.metrics=metrics;
 }
 public int run(DurableWorkPort.Kind kind) {
  int completed=0;
  // Claim immediately before execution: later jobs must not consume their lease while waiting locally.
  for(int slot=0;slot<policy.batchSize();slot++) {
   var claimed=jobs.claim(kind,policy,1);
   if(claimed.isEmpty())break;
   var job=claimed.getFirst();
   if(job.attempts()>1)metrics.counter("emitta.processing.retries","kind",kind.name()).increment();
   metrics.timer("emitta.processing.queue.wait","kind",kind.name()).record(java.time.Duration.ofMillis(
       Math.max(0,java.time.Duration.between(job.nextAttemptAt(),java.time.Instant.now()).toMillis())));
   var timer=Timer.start(metrics);
   try {
    boolean executed=jobs.withLock(job,()->process(job));
    if(executed)completed++;
    metrics.counter("emitta.processing.attempts","kind",kind.name(),"result",executed?"completed":"stale_claim").increment();
   } catch(RuntimeException failure) {
    var category=WorkFailureClassifier.classify(failure);
    jobs.failed(job,category,policy);
    metrics.counter("emitta.processing.attempts","kind",kind.name(),"result",category.name()).increment();
    if(kind==DurableWorkPort.Kind.RIDE) metrics.counter("emitta.ride.generation","result","failure").increment();
    break; // Back off at the durable boundary rather than reclaiming the failed job in this poll.
   } finally { timer.stop(metrics.timer("emitta.processing.duration","kind",kind.name())); }
  }
  return completed;
 }
 private void process(DurableWorkPort.Job job) {
  if(job.kind()==DurableWorkPort.Kind.SRI_TEST) {sri.getObject().process(job);return;}
  var document=documents.findById(job.documentId()).filter(d->d.getTenantId().equals(job.tenantId()))
    .orElseThrow(()->new IllegalArgumentException("Document unavailable"));
  if(job.kind()==DurableWorkPort.Kind.RIDE) {
   if(document.getStatus()!=DocumentStatus.AUTHORIZED) throw new IllegalArgumentException("Authorization required");
   ride.getObject().generate(job.tenantId(),job.documentId());
   metrics.counter("emitta.ride.generation","result","success").increment();
  } else {
   if(document.getStatus()==DocumentStatus.SUBMITTED || document.getStatus()==DocumentStatus.RETRY_PENDING)
     throw new io.github.imecuadorian.emitta.srireception.application.exception.SriReceptionException("External result requires reconciliation");
   if(document.getStatus()==DocumentStatus.SIGNED || document.getStatus()==DocumentStatus.AUTHORIZED)return;
   if(document.getStatus()!=DocumentStatus.QUEUED && document.getStatus()!=DocumentStatus.GENERATING)
      throw new IllegalArgumentException("Document not eligible for local processing");
   var result=fiscal.process(new ProcessFiscalDocumentCommand(job.documentId()));
   if(result.status()!=DocumentStatus.SIGNED) throw new IllegalStateException("Signing pipeline incomplete");
  }
 }
}
