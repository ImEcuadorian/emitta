package io.github.imecuadorian.emitta.recovery;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import java.util.UUID;

/** Child JVM used exclusively by the isolated abrupt-worker termination test. */
public final class InterruptedWorkProcess {
    public static void main(String[] args) {
        String url=System.getenv("SYNTHETIC_DB_URL");
        if(url==null || !(url.startsWith("jdbc:postgresql://localhost:") || url.startsWith("jdbc:postgresql://127.0.0.1:")))
            throw new IllegalArgumentException("Isolated local database required");
        var source=new DriverManagerDataSource(url,System.getenv("SYNTHETIC_DB_USER"),System.getenv("SYNTHETIC_DB_PASSWORD"));
        var jobs=new PostgreSqlDurableWorkAdapter(new JdbcTemplate(source),new DataSourceTransactionManager(source),
            event->{throw new IllegalStateException("Child never publishes events");});
        var job=jobs.find(UUID.fromString(args[0]),UUID.fromString(args[1]),DurableWorkPort.Kind.RIDE).orElseThrow();
        jobs.withLock(job,()->{
            System.out.println("LOCKED");System.out.flush();
            try {Thread.sleep(java.time.Duration.ofMinutes(1));}
            catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("Interrupted synthetic work");}
        });
    }
}
