package io.github.imecuadorian.emitta.recovery;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OperationalMetricsTest {
    @Test void scrapeUsesSnapshotsAndOutagesRemainVisibleUntilRecovery() {
        var jobs = mock(PostgreSqlDurableWorkAdapter.class);
        var redis = mock(StringRedisTemplate.class);
        var factory = mock(RedisConnectionFactory.class);
        var connection = mock(RedisConnection.class);
        when(redis.getConnectionFactory()).thenReturn(factory);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");
        when(jobs.count(anyString())).thenReturn(2d);
        when(jobs.oldestPendingAge()).thenReturn(40d);
        var meters = new SimpleMeterRegistry();
        var probe = new OperationalMetrics(jobs,redis,meters);
        probe.sample();
        assertEquals(1,meters.get("emitta.database.available").gauge().value());
        assertEquals(40,meters.get("emitta.processing.oldest.pending.seconds").gauge().value());
        clearInvocations(jobs,redis);
        for(int i=0;i<100;i++)meters.get("emitta.processing.jobs").tag("state","PENDING").gauge().value();
        verifyNoInteractions(jobs,redis);
        when(jobs.count(anyString())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic"));
        when(connection.ping()).thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("synthetic"));
        probe.sample();
        assertEquals(0,meters.get("emitta.database.available").gauge().value());
        assertEquals(0,meters.get("emitta.redis.probe.available").gauge().value());
        assertTrue(Double.isNaN(meters.get("emitta.outbox.pending").gauge().value()));
        doReturn(0d).when(jobs).count(anyString());
        doReturn("PONG").when(connection).ping();
        probe.sample();
        assertEquals(1,meters.get("emitta.database.available").gauge().value());
        assertEquals(1,meters.get("emitta.redis.probe.available").gauge().value());
    }
}
