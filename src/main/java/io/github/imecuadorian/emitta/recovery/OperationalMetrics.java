package io.github.imecuadorian.emitta.recovery;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded-cardinality snapshots: scraping metrics must never query the fiscal database. */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name="emitta.observability.enabled",havingValue="true",matchIfMissing=true)
public final class OperationalMetrics {
    private final PostgreSqlDurableWorkAdapter jobs;
    private final StringRedisTemplate redis;
    private final MeterRegistry metrics;
    private final Map<String, Double> states = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile double databaseAvailable = -1, redisAvailable = -1;
    private volatile double outbox = Double.NaN, rides = Double.NaN, oldest = Double.NaN;
    private volatile double sampledAt = 0;

    public OperationalMetrics(PostgreSqlDurableWorkAdapter jobs, StringRedisTemplate redis, MeterRegistry metrics) {
        this.jobs = jobs;
        this.redis = redis;
        this.metrics = metrics;
        for (String state : List.of("PENDING", "RUNNING", "DEAD", "BLOCKED")) {
            states.put(state, Double.NaN);
            metrics.gauge("emitta.processing.jobs", List.of(Tag.of("state", state)), states, s -> s.get(state));
        }
        metrics.gauge("emitta.database.available", this, m -> m.databaseAvailable);
        metrics.gauge("emitta.redis.probe.available", this, m -> m.redisAvailable);
        metrics.gauge("emitta.outbox.pending", this, m -> m.outbox);
        metrics.gauge("emitta.ride.pending", this, m -> m.rides);
        metrics.gauge("emitta.processing.oldest.pending.seconds", this, m -> m.oldest);
        metrics.gauge("emitta.observability.sample.timestamp.seconds", this, m -> m.sampledAt);
    }

    @Scheduled(fixedDelayString = "${emitta.observability.sample-ms:15000}", initialDelay = 15000)
    public void sample() {
        Timer.Sample sample = Timer.start(metrics);
        try {
            Map<String, Double> snapshot = new LinkedHashMap<>();
            for (String state : states.keySet()) snapshot.put(state, jobs.count(state));
            double pendingOutbox = jobs.pendingOutbox(), pendingRides = jobs.pendingRides();
            double age = jobs.oldestPendingAge();
            states.putAll(snapshot);
            outbox = pendingOutbox;
            rides = pendingRides;
            oldest = age;
            databaseAvailable = 1;
        } catch (RuntimeException unavailable) {
            databaseAvailable = 0;
            states.replaceAll((state, count) -> Double.NaN);
            outbox = rides = oldest = Double.NaN;
        } finally {
            sample.stop(metrics.timer("emitta.database.probe"));
        }
        try (var connection = redis.getConnectionFactory().getConnection()) {
            redisAvailable = "PONG".equals(connection.ping()) ? 1 : 0;
        } catch (RuntimeException unavailable) {
            redisAvailable = 0;
        }
        sampledAt = java.time.Instant.now().getEpochSecond();
    }
}
