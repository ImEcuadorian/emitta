package io.github.imecuadorian.emitta.recovery;
import org.springframework.context.annotation.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
@Configuration(proxyBeanMethods=false)
@Profile("processing-recovery")
public class RecoveryCommandConfiguration {
 @Bean ApplicationRunner recoverProcessingJob(DurableWorkPort work,Environment env,org.springframework.context.ConfigurableApplicationContext context) {
  return args->{
   if(env.getProperty("emitta.fiscal.worker.enabled",Boolean.class,false) || env.getProperty("emitta.outbox.publisher.enabled",Boolean.class,false)
      || env.getProperty("emitta.sri.reception.enabled",Boolean.class,false) || env.getProperty("emitta.sri.authorization.enabled",Boolean.class,false)
      || env.getProperty("emitta.processing.jobs.enabled",Boolean.class,false)) throw new IllegalStateException("Recovery CLI must run with workers and SRI disabled");
   boolean admission=env.containsProperty("emitta.recovery.event-id") || env.containsProperty("emitta.recovery.document-id");
   if(admission && (env.containsProperty("emitta.recovery.job-id")
       || !env.containsProperty("emitta.recovery.event-id") || !env.containsProperty("emitta.recovery.document-id")))
    throw new IllegalArgumentException("Choose a job or a complete document/event admission target");
   var tenant=java.util.UUID.fromString(env.getRequiredProperty("emitta.recovery.tenant-id"));
   if(env.containsProperty("emitta.recovery.event-id")) {
    var event=java.util.UUID.fromString(env.getRequiredProperty("emitta.recovery.event-id"));
    var document=java.util.UUID.fromString(env.getRequiredProperty("emitta.recovery.document-id"));
    if(!work.recoverAdmission(tenant,document,event))throw new IllegalStateException("Original event not eligible for admission recovery");
    System.out.println("Original event admitted to durable local work; no fiscal transmission performed. Event: "+event);
   } else {
    var job=java.util.UUID.fromString(env.getRequiredProperty("emitta.recovery.job-id"));
    if(!work.recover(tenant,job))throw new IllegalStateException("Job not eligible for infrastructure recovery");
    System.out.println("Durable job scheduled; no fiscal transmission performed. Job: "+job);
   }
   org.springframework.boot.SpringApplication.exit(context,()->0);context.close();
  };
 }
}
