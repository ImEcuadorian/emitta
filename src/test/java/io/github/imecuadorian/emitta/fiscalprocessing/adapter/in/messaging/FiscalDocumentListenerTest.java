package io.github.imecuadorian.emitta.fiscalprocessing.adapter.in.messaging;
import org.junit.jupiter.api.Test;
import io.github.imecuadorian.emitta.recovery.DurableWorkPort;
import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.*;
import io.github.imecuadorian.emitta.outbox.adapter.out.messaging.RabbitOutboxMessagePublisher;
import org.springframework.amqp.core.*;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.mockito.Mockito.*;
class FiscalDocumentListenerTest {
 @Test void admitsDuplicateMessagesToTheSameDurableDocumentJob() {
  var jobs=mock(DurableWorkPort.class);var docs=mock(DocumentRepository.class);var publisher=mock(RabbitOutboxMessagePublisher.class);
  UUID id=UUID.randomUUID(),tenant=UUID.randomUUID();var document=mock(Document.class);
  when(document.getTenantId()).thenReturn(tenant);when(document.getStatus()).thenReturn(DocumentStatus.QUEUED);
  when(docs.findById(id)).thenReturn(Optional.of(document));
  var listener=new FiscalDocumentListener(jobs,docs,publisher,JsonMapper.builder().build());
  var message=new Message(("{\"documentId\":\""+id+"\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8),new MessageProperties());
  listener.onMessage(message);listener.onMessage(message);
  verify(jobs,times(2)).enqueue(DurableWorkPort.Kind.FISCAL,tenant,id);verifyNoInteractions(publisher);
 }
 @Test void invalidMessageIsConfirmedToDlqInsteadOfCirculating() {
  var jobs=mock(DurableWorkPort.class);var docs=mock(DocumentRepository.class);var publisher=mock(RabbitOutboxMessagePublisher.class);
  new FiscalDocumentListener(jobs,docs,publisher,JsonMapper.builder().build()).onMessage(new Message(new byte[]{0},new MessageProperties()));
  verify(publisher).publishMessage(eq("emitta.deadletters"),eq("fiscal.invalid"),any(),any());verifyNoInteractions(jobs,docs);
 }
}
