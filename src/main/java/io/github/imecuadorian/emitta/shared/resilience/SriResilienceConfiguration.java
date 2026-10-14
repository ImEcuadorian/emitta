package io.github.imecuadorian.emitta.shared.resilience;
import org.springframework.context.annotation.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import io.micrometer.core.instrument.MeterRegistry;
@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(SriResilienceProperties.class)
public class SriResilienceConfiguration {
 @Bean SriCallGuard sriReceptionGuard(SriResilienceProperties p,MeterRegistry metrics) { return new SriCallGuard("reception",p.reception(),metrics); }
 @Bean SriCallGuard sriAuthorizationGuard(SriResilienceProperties p,MeterRegistry metrics) { return new SriCallGuard("authorization",p.authorization(),metrics); }
}
