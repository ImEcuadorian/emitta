package io.github.imecuadorian.emitta.outbox.adapter.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Shared DB backlog, sampled locally. A safety brake, not a hard distributed quota. */
@Component
public final class OutboxAdmissionGuard implements Runnable {
    private final JdbcTemplate jdbc;
    private final MeterRegistry metrics;
    private final boolean enabled;
    private final int maximum;
    private final long maximumAge;
    public static final String BACKLOG_SQL = """
                SELECT count(*) AS pending, COALESCE(EXTRACT(EPOCH FROM CURRENT_TIMESTAMP-min(e.created_at)),0) AS age
                FROM (
                 SELECT e.created_at FROM emitta.outbox_events e WHERE e.published_at IS NULL
                 UNION ALL
                 SELECT e.created_at FROM emitta.outbox_events e
                 JOIN emitta.documents d ON d.id=e.aggregate_id AND d.status IN ('QUEUED','GENERATING')
                 LEFT JOIN emitta.processing_jobs j ON j.document_id=e.aggregate_id AND j.kind='FISCAL'
                 WHERE e.published_at IS NOT NULL AND e.event_type='document.received.v1' AND j.id IS NULL
                ) e
                """;
    enum Reason { BACKLOG, AGE, SAMPLE_UNAVAILABLE, INFRASTRUCTURE }
    private record Snapshot(long sampledAt, Reason reason) {}
    private volatile Snapshot snapshot=new Snapshot(0,Reason.SAMPLE_UNAVAILABLE);
    private final java.util.function.LongSupplier clock;

    @org.springframework.beans.factory.annotation.Autowired
    public OutboxAdmissionGuard(JdbcTemplate jdbc, MeterRegistry metrics,
        @Value("${emitta.outbox.admission.enabled:true}") boolean enabled,
        @Value("${emitta.outbox.admission.maximum-pending:5000}") int maximum,
        @Value("${emitta.outbox.admission.maximum-age-seconds:60}") long maximumAge) {
        this(jdbc,metrics,enabled,maximum,maximumAge,System::nanoTime);
    }
    OutboxAdmissionGuard(JdbcTemplate jdbc,MeterRegistry metrics,boolean enabled,int maximum,long maximumAge,java.util.function.LongSupplier clock) {
        if(maximum<1 || maximumAge<1)throw new IllegalArgumentException("Invalid outbox admission limits");
        this.jdbc=jdbc;this.metrics=metrics;this.enabled=enabled;this.maximum=maximum;this.maximumAge=maximumAge;
        this.clock=clock;
    }

    @Scheduled(fixedDelayString="${emitta.outbox.admission.sample-ms:2000}",initialDelay=0)
    public void sample() {
        if(!enabled)return;
        var sample=io.micrometer.core.instrument.Timer.start(metrics);
        try {
            var state=jdbc.queryForMap(BACKLOG_SQL);
            Reason reason=((Number)state.get("pending")).longValue()>=maximum?Reason.BACKLOG:
                ((Number)state.get("age")).doubleValue()>=maximumAge?Reason.AGE:null;
            snapshot=new Snapshot(clock.getAsLong(),reason);
        }catch(RuntimeException unavailable){snapshot=new Snapshot(clock.getAsLong(),Reason.INFRASTRUCTURE);}
        finally {sample.stop(metrics.timer("emitta.outbox.admission.sample.duration"));}
    }

    @Override public void run() {
        // Called only for a NEW document, before insertion, within its transaction.
        // Existing idempotent requests bypass this brake and retain normal content validation.
        var state=snapshot;
        Reason reason=state.sampledAt()==0 || clock.getAsLong()-state.sampledAt()>java.time.Duration.ofSeconds(10).toNanos()?Reason.SAMPLE_UNAVAILABLE:state.reason();
        if(enabled && reason!=null) {
            metrics.counter("emitta.outbox.admission.rejected").increment();
            metrics.counter("emitta.outbox.admission.rejected.reason","reason",reason.name().toLowerCase(java.util.Locale.ROOT)).increment();
            throw new SaturatedException();
        }
    }

    public static final class SaturatedException extends RuntimeException {
        public SaturatedException(){super("Temporary admission capacity exhausted; no new document was committed");}
    }
}
