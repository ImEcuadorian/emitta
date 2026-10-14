package io.github.imecuadorian.emitta.ratelimit;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.core.StringRedisTemplate;
@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfiguration {
 @Bean @ConditionalOnProperty(name="emitta.security.rate-limit.enabled",havingValue="true")
 RedisTokenBucket redisTokenBucket(StringRedisTemplate redis,MeterRegistry metrics) { return new RedisTokenBucket(redis,metrics); }
}
