package io.github.imecuadorian.emitta.outbox.adapter.config;
import org.junit.jupiter.api.Test;
import org.aopalliance.intercept.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RabbitAdmissionRetryTest {
 @Test void persistentOutageIsMeteredAndCooledDownWithoutAcknowledging() throws Throwable {
  var meters=new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
  var publisher=mock(io.github.imecuadorian.emitta.outbox.adapter.out.messaging.RabbitOutboxMessagePublisher.class);
  doThrow(new IllegalStateException("Synthetic missing route")).when(publisher).publishMessage(anyString(),anyString(),any(),anyString());
  var factory=new RabbitOutboxConfiguration().fiscalDurableListenerFactory(mock(ConnectionFactory.class),0,
      Duration.ofMillis(1),Duration.ofMillis(1),Duration.ofMillis(30),meters,publisher);
  var endpoint=new SimpleRabbitListenerEndpoint();endpoint.setId("synthetic-cooldown");endpoint.setQueueNames("synthetic");endpoint.setMessageListener(message->{});
  var container=factory.createListenerContainer(endpoint);
  var interceptor=(MethodInterceptor)((org.aopalliance.aop.Advice[])ReflectionTestUtils.getField(container,"adviceChain"))[0];
  var invocation=mock(MethodInvocation.class);when(invocation.getArguments()).thenReturn(new Object[]{null,java.util.List.of(new Message(new byte[0],new MessageProperties()))});
  when(invocation.proceed()).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic"));
  long started=System.nanoTime();
  assertThrows(org.springframework.amqp.ImmediateRequeueAmqpException.class,()->interceptor.invoke(invocation));
  assertTrue(System.nanoTime()-started>=Duration.ofMillis(25).toNanos());
  assertEquals(1,meters.get("emitta.rabbit.admission.exhausted").counter().count());
  assertEquals(1,meters.get("emitta.rabbit.admission.quarantine.failed").counter().count());
  verify(invocation,times(1)).proceed();
 }
 private MethodInterceptor advice() {
  var factory=new RabbitOutboxConfiguration().fiscalDurableListenerFactory(mock(ConnectionFactory.class),2,Duration.ofMillis(1),Duration.ofMillis(4));
  var endpoint=new SimpleRabbitListenerEndpoint();endpoint.setId("synthetic-admission");endpoint.setQueueNames("synthetic");endpoint.setMessageListener(message->{});
  var container=factory.createListenerContainer(endpoint);
  return (MethodInterceptor)((org.aopalliance.aop.Advice[])ReflectionTestUtils.getField(container,"adviceChain"))[0];
 }
 @Test void unavailableDatabaseGetsBoundedRetriesThenConfirmedDurableQuarantine() throws Throwable {
  var publisher=mock(io.github.imecuadorian.emitta.outbox.adapter.out.messaging.RabbitOutboxMessagePublisher.class);
  var factory=new RabbitOutboxConfiguration().fiscalDurableListenerFactory(mock(ConnectionFactory.class),2,
    Duration.ofMillis(1),Duration.ofMillis(4),Duration.ZERO,new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),publisher);
  var endpoint=new SimpleRabbitListenerEndpoint();endpoint.setId("synthetic-quarantine");endpoint.setQueueNames("synthetic");endpoint.setMessageListener(message->{});
  var container=factory.createListenerContainer(endpoint);
  var interceptor=(MethodInterceptor)((org.aopalliance.aop.Advice[])ReflectionTestUtils.getField(container,"adviceChain"))[0];
  var invocation=mock(MethodInvocation.class);when(invocation.getArguments()).thenReturn(new Object[]{null,java.util.List.of(new Message(new byte[0],new MessageProperties()))});
  when(invocation.proceed()).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic failure"));
  assertNull(interceptor.invoke(invocation));verify(invocation,times(3)).proceed();
  var message=org.mockito.ArgumentCaptor.forClass(Message.class);
  verify(publisher).publishMessage(eq(RabbitOutboxConfiguration.DEAD_LETTER_EXCHANGE),eq("fiscal.admission.exhausted"),message.capture(),anyString());
  assertEquals(3L,((Number)message.getValue().getMessageProperties().getHeader("x-emitta-admission-attempts")).longValue());
  assertEquals("TRANSIENT",message.getValue().getMessageProperties().getHeader("x-emitta-failure-category"));
  assertEquals(MessageDeliveryMode.PERSISTENT,message.getValue().getMessageProperties().getDeliveryMode());
 }
 @Test void partialQuarantineConfirmationCannotAcknowledgeTheOriginalBatch() throws Throwable {
  var publisher=mock(io.github.imecuadorian.emitta.outbox.adapter.out.messaging.RabbitOutboxMessagePublisher.class);
  doNothing().doThrow(new IllegalStateException("Synthetic unknown confirm")).when(publisher).publishMessage(anyString(),anyString(),any(),anyString());
  var meters=new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
  var factory=new RabbitOutboxConfiguration().fiscalDurableListenerFactory(mock(ConnectionFactory.class),0,
    Duration.ofMillis(1),Duration.ofMillis(1),Duration.ZERO,meters,publisher);
  var endpoint=new SimpleRabbitListenerEndpoint();endpoint.setId("synthetic-partial");endpoint.setQueueNames("synthetic");endpoint.setMessageListener(message->{});
  var container=factory.createListenerContainer(endpoint);
  var interceptor=(MethodInterceptor)((org.aopalliance.aop.Advice[])ReflectionTestUtils.getField(container,"adviceChain"))[0];
  var invocation=mock(MethodInvocation.class);when(invocation.getArguments()).thenReturn(new Object[]{null,java.util.List.of(new Message(new byte[0],new MessageProperties()),new Message(new byte[0],new MessageProperties()))});
  when(invocation.proceed()).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic"));
  assertThrows(org.springframework.amqp.ImmediateRequeueAmqpException.class,()->interceptor.invoke(invocation));
  verify(publisher,times(2)).publishMessage(anyString(),anyString(),any(),anyString());
  assertNull(meters.find("emitta.rabbit.admission.quarantined").counter());
 }
 @Test void admissionCanRecoverWithinTheBoundedRetryWithoutDiscarding() throws Throwable {
  var invocation=mock(MethodInvocation.class);when(invocation.proceed()).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic failure")).thenReturn(null);
  assertNull(advice().invoke(invocation));verify(invocation,times(2)).proceed();
 }
}
