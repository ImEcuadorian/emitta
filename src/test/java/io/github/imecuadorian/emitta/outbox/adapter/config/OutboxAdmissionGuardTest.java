package io.github.imecuadorian.emitta.outbox.adapter.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutboxAdmissionGuardTest {
    @Test void reportsBoundedRejectionReasonsAndFreshnessWithoutChangingLimits() {
        var jdbc=mock(JdbcTemplate.class);var metrics=new SimpleMeterRegistry();
        var now=new java.util.concurrent.atomic.AtomicLong(1);
        var guard=new OutboxAdmissionGuard(jdbc,metrics,true,100,60,now::get);
        assertThrows(OutboxAdmissionGuard.SaturatedException.class,guard::run);
        when(jdbc.queryForMap(anyString())).thenReturn(Map.of("pending",100L,"age",1));
        guard.sample();assertThrows(OutboxAdmissionGuard.SaturatedException.class,guard::run);
        when(jdbc.queryForMap(anyString())).thenReturn(Map.of("pending",1L,"age",60));
        guard.sample();assertThrows(OutboxAdmissionGuard.SaturatedException.class,guard::run);
        when(jdbc.queryForMap(anyString())).thenThrow(new IllegalStateException());
        guard.sample();assertThrows(OutboxAdmissionGuard.SaturatedException.class,guard::run);
        doReturn(Map.of("pending",1L,"age",1)).when(jdbc).queryForMap(anyString());
        guard.sample();assertDoesNotThrow(guard::run);
        now.addAndGet(java.time.Duration.ofSeconds(11).toNanos());
        assertThrows(OutboxAdmissionGuard.SaturatedException.class,guard::run);
        for(var reason:java.util.List.of("backlog","age","infrastructure"))
            assertEquals(1,metrics.counter("emitta.outbox.admission.rejected.reason","reason",reason).count());
        assertEquals(2,metrics.counter("emitta.outbox.admission.rejected.reason","reason","sample_unavailable").count());
    }
    @Test void failsClosedBeforeSampleAndOnDatabaseFailureThenRecovers() {
        var jdbc=mock(JdbcTemplate.class);var metrics=new SimpleMeterRegistry();
        var guard=new OutboxAdmissionGuard(jdbc,metrics,true,100,60);
        assertThrows(OutboxAdmissionGuard.SaturatedException.class,guard::run);
        when(jdbc.queryForMap(anyString())).thenReturn(Map.of("pending",100L,"age",1));
        guard.sample();assertThrows(OutboxAdmissionGuard.SaturatedException.class,guard::run);
        when(jdbc.queryForMap(anyString())).thenReturn(Map.of("pending",1L,"age",1));
        guard.sample();assertDoesNotThrow(guard::run);
        when(jdbc.queryForMap(anyString())).thenThrow(new IllegalStateException());
        guard.sample();assertThrows(OutboxAdmissionGuard.SaturatedException.class,guard::run);
        assertEquals(3,metrics.counter("emitta.outbox.admission.rejected").count());
    }
}
