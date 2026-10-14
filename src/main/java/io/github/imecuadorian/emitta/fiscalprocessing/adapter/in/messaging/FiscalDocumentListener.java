package io.github.imecuadorian.emitta.fiscalprocessing.adapter.in.messaging;
import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.recovery.DurableWorkPort;
import io.github.imecuadorian.emitta.outbox.adapter.config.RabbitOutboxConfiguration;
import io.github.imecuadorian.emitta.outbox.adapter.out.messaging.RabbitOutboxMessagePublisher;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import java.util.UUID;
@Component
@ConditionalOnProperty(name="emitta.fiscal.worker.enabled",havingValue="true")
public class FiscalDocumentListener {
 private final DurableWorkPort jobs;private final DocumentRepository documents;
 private final RabbitOutboxMessagePublisher publisher;private final JsonMapper json;
 private final org.springframework.transaction.support.TransactionTemplate tx;
 private final io.micrometer.core.instrument.MeterRegistry metrics;
 public FiscalDocumentListener(DurableWorkPort jobs,DocumentRepository documents,RabbitOutboxMessagePublisher publisher,JsonMapper json) {
  this(jobs,documents,publisher,json,null,new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
 }
 @org.springframework.beans.factory.annotation.Autowired
 public FiscalDocumentListener(DurableWorkPort jobs,DocumentRepository documents,RabbitOutboxMessagePublisher publisher,JsonMapper json,
     org.springframework.transaction.PlatformTransactionManager manager,io.micrometer.core.instrument.MeterRegistry metrics) {
  this.jobs=jobs;this.documents=documents;this.publisher=publisher;this.json=json;this.metrics=metrics;
  this.tx=manager==null?null:new org.springframework.transaction.support.TransactionTemplate(manager);
  if(tx!=null)tx.setTimeout(5);
 }
 @RabbitListener(queues=RabbitOutboxConfiguration.FISCAL_DOCUMENT_QUEUE,containerFactory="fiscalDurableListenerFactory")
 public void onMessages(java.util.List<Message> messages) {
  if(messages.size()>50){for(var message:messages)deadLetter(message,"VALIDATION");return;}
  // Parse before acquiring a DB connection. Untrusted messages still have bounded size/type validation.
  var ids=new java.util.ArrayList<UUID>();
  for(var message:messages)ids.add(parseId(message));
  var categories=metrics.timer("emitta.rabbit.admission.transaction.duration").record(()-> {
   java.util.function.Supplier<java.util.List<String>> work=()->{
    var snapshots=documents.findForAdmission(ids.stream().filter(java.util.Objects::nonNull).distinct().toList());
    var outcomes=new java.util.ArrayList<String>();
    var targets=new java.util.ArrayList<DurableWorkPort.AdmissionTarget>();
    for(int i=0;i<messages.size();i++) {
     var id=ids.get(i);var document=id==null?null:snapshots.get(id);
     var category=classify(messages.get(i),document);outcomes.add(category);
     if(category==null && document!=null && (document.status()==DocumentStatus.QUEUED || document.status()==DocumentStatus.GENERATING))
      targets.add(new DurableWorkPort.AdmissionTarget(document.tenantId(),id));
    }
    jobs.enqueueFiscalBatch(targets);return outcomes;
   };
   return tx==null?work.get():tx.execute(status->work.get());
  });
  // Commit PostgreSQL before waiting for DLQ confirms. If DLQ fails, redelivery is idempotent.
  for(int i=0;i<messages.size();i++)if(categories.get(i)!=null)deadLetter(messages.get(i),categories.get(i));
  metrics.counter("emitta.rabbit.admission.completed").increment(messages.size());
 }
 public void onMessage(Message message) {
  var category=admit(message);if(category!=null)deadLetter(message,category);
 }
 private String admit(Message message) {
  UUID id=parseId(message);
  if(id==null)return "VALIDATION";
  var document=documents.findById(id); // Infrastructure failures remain unacked/requeued, never discarded.
  var snapshot=document.map(d->new DocumentRepository.AdmissionDocument(id,d.getTenantId(),d.getStatus())).orElse(null);
  var category=classify(message,snapshot);
  if(category==null && snapshot!=null && (snapshot.status()==DocumentStatus.QUEUED || snapshot.status()==DocumentStatus.GENERATING))
   jobs.enqueue(DurableWorkPort.Kind.FISCAL,snapshot.tenantId(),id);
  return category;
 }
 private UUID parseId(Message message) {
  try {
   if(message.getBody().length>65536)throw new IllegalArgumentException("Oversized event");
   String type=message.getMessageProperties().getType();
   if(type!=null && !type.equals("document.received.v1"))throw new IllegalArgumentException("Unexpected event type");
   return UUID.fromString(json.readValue(message.getBody(),DocumentReceivedMessage.class).documentId());
  } catch(RuntimeException invalid) {return null;}
 }
 private String classify(Message message,DocumentRepository.AdmissionDocument document) {
  if(document==null)return "VALIDATION";
  Object header=message.getMessageProperties().getHeader("x-emitta-tenant-id");
  if(header!=null && !header.toString().equals(document.tenantId().toString())) {return "VALIDATION";}
  var state=document.status();
  if(state==DocumentStatus.SIGNED || state==DocumentStatus.AUTHORIZED)return null; // harmless late duplicate
  if(state==DocumentStatus.SUBMITTED || state==DocumentStatus.RETRY_PENDING) {return "EXTERNAL_UNKNOWN";}
  if(state!=DocumentStatus.QUEUED && state!=DocumentStatus.GENERATING) {return "PERMANENT";}
  return null;
 }
 private void deadLetter(Message original,String category) {
  var message=new Message(original.getBody(),org.springframework.amqp.core.MessagePropertiesBuilder
      .fromClonedProperties(original.getMessageProperties()).setHeader("x-emitta-failure-category",category).build());
  String id=original.getMessageProperties().getMessageId();
  publisher.publishMessage(RabbitOutboxConfiguration.DEAD_LETTER_EXCHANGE,"fiscal.invalid",message,
      id==null?UUID.randomUUID().toString():id);
  // If confirms fail, throw and retain the original broker delivery. No silent discard.
 }
}
