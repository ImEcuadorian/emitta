package io.github.imecuadorian.emitta.recovery;
import org.springframework.context.annotation.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.*;
import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.fiscalprocessing.application.port.in.ProcessFiscalDocumentUseCase;
import io.github.imecuadorian.emitta.ride.application.port.in.GenerateAndStoreInvoiceRideUseCase;
import io.micrometer.core.instrument.MeterRegistry;
@Configuration(proxyBeanMethods=false)
@EnableScheduling
@EnableConfigurationProperties(ProcessingPolicy.class)
public class DurableWorkConfiguration {
 @Bean DurableWorkRunner durableWorkRunner(PostgreSqlDurableWorkAdapter jobs,ProcessingPolicy policy,DocumentRepository documents,
      ProcessFiscalDocumentUseCase fiscal,ObjectProvider<GenerateAndStoreInvoiceRideUseCase> ride,MeterRegistry metrics,ObjectProvider<SriTestWorkflow> sri) {
  var runner=new DurableWorkRunner(jobs,policy,documents,fiscal,ride,metrics);runner.setSriWorkflow(sri);return runner;
 }
 @Bean WorkSchedule workSchedule(DurableWorkRunner runner,PostgreSqlDurableWorkAdapter jobs,ObjectProvider<SriTestWorkflow> sri,
   @Value("${emitta.processing.jobs.enabled:false}") boolean enabled,
   @Value("${emitta.fiscal.worker.enabled:false}") boolean fiscal,
   @Value("${emitta.ride.enabled:false}") boolean ride) {return new WorkSchedule(runner,jobs,enabled,fiscal,ride,sri);}
 static final class WorkSchedule {
  private final DurableWorkRunner runner;private final PostgreSqlDurableWorkAdapter jobs;private final boolean enabled,fiscal,ride;
  private final ObjectProvider<SriTestWorkflow> sri;
  WorkSchedule(DurableWorkRunner runner,PostgreSqlDurableWorkAdapter jobs,boolean enabled,boolean fiscal,boolean ride,ObjectProvider<SriTestWorkflow> sri) {
   this.runner=runner;this.jobs=jobs;this.enabled=enabled;this.fiscal=fiscal;this.ride=ride;this.sri=sri;
  }
  @Scheduled(fixedDelayString="${emitta.processing.jobs.poll-ms:2000}") public void poll() {
   if(!enabled)return;
   try {
    if(fiscal)runner.run(DurableWorkPort.Kind.FISCAL);
    if(sri.getIfAvailable()!=null){sri.getObject().discover();runner.run(DurableWorkPort.Kind.SRI_TEST);}
    if(ride){jobs.discoverMissingRides(100);runner.run(DurableWorkPort.Kind.RIDE);}
   } catch(RuntimeException failure) {org.slf4j.LoggerFactory.getLogger(WorkSchedule.class).warn("Durable processing unavailable; failureType={}",failure.getClass().getSimpleName());}
  }
 }
}
