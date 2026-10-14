package io.github.imecuadorian.emitta.recovery;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
@Component
public final class RabbitQueueMetrics {
 private final RabbitAdmin admin;
 private final AtomicInteger fiscal=new AtomicInteger(-1),dead=new AtomicInteger(-1),consumers=new AtomicInteger(-1);
 private final AtomicInteger available=new AtomicInteger(-1);
 public RabbitQueueMetrics(RabbitTemplate rabbit,MeterRegistry metrics) {
  admin=new RabbitAdmin(rabbit);
  metrics.gauge("emitta.rabbit.ready",java.util.List.of(io.micrometer.core.instrument.Tag.of("queue","fiscal")),fiscal);
  metrics.gauge("emitta.rabbit.ready",java.util.List.of(io.micrometer.core.instrument.Tag.of("queue","dlq")),dead);
  metrics.gauge("emitta.rabbit.consumers",consumers);
  metrics.gauge("emitta.rabbit.available",available);
 }
 @Scheduled(fixedDelayString="${emitta.processing.queue-metrics-ms:30000}",initialDelay=30000)
 public void sample() {
  try {
   var q=admin.getQueueProperties("emitta.fiscal.documents");
   var d=admin.getQueueProperties("emitta.processing.dlq");
   fiscal.set(q==null?-1:((Number)q.get(RabbitAdmin.QUEUE_MESSAGE_COUNT)).intValue());
   consumers.set(q==null?-1:((Number)q.get(RabbitAdmin.QUEUE_CONSUMER_COUNT)).intValue());
   dead.set(d==null?-1:((Number)d.get(RabbitAdmin.QUEUE_MESSAGE_COUNT)).intValue());
   available.set(q!=null && d!=null?1:0);
  } catch(RuntimeException unavailable) {this.available.set(0);fiscal.set(-1);dead.set(-1);consumers.set(-1);}
 }
}
