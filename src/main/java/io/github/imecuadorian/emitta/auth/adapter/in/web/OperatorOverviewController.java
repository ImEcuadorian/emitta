package io.github.imecuadorian.emitta.auth.adapter.in.web;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;
import java.util.*;

/** Bounded operational metadata only. Never returns fiscal payloads or credentials. */
@RestController
public final class OperatorOverviewController {
    private final MeterRegistry meters;
    private final JdbcClient jdbc;
    public OperatorOverviewController(MeterRegistry meters, JdbcClient jdbc) {
        this.meters = meters; this.jdbc = jdbc;
    }
    @GetMapping("/api/v1/operator/overview")
    public ResponseEntity<Map<String,Object>> overview() {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("observedAt", Instant.now().toString());
        body.put("scope", "CURRENT_INSTANCE");
        Map<String,Double> values = new LinkedHashMap<>();
        for (String name : List.of("emitta.outbox.pending", "emitta.ride.pending",
                "emitta.processing.oldest.pending.seconds", "emitta.database.available",
                "emitta.redis.probe.available", "emitta.observability.sample.timestamp.seconds",
                "hikaricp.connections.active", "hikaricp.connections.pending")) {
            var gauge = meters.find(name).gauge();
            double value = gauge == null ? Double.NaN : gauge.value();
            values.put(name, Double.isFinite(value) && value >= 0 ? value : null);
        }
        body.put("gauges", values);
        long requests = 0, errors = 0;
        for (var timer : meters.find("http.server.requests").timers()) {
            requests += timer.count();
            String status = timer.getId().getTag("status");
            if (status != null && (status.startsWith("4") || status.startsWith("5"))) errors += timer.count();
        }
        body.put("httpRequestsSinceStartup", requests);
        body.put("httpErrorsSinceStartup", errors);
        body.put("recentActivity", jdbc.sql("""
                SELECT action, created_at FROM emitta.administrative_audit_events
                ORDER BY created_at DESC, id DESC LIMIT 20
                """).query((rs, n) -> Map.of("action", rs.getString("action"),
                        "at", rs.getTimestamp("created_at").toInstant().toString())).list());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
