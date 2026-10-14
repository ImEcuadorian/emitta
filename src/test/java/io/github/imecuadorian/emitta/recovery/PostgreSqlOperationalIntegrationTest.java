package io.github.imecuadorian.emitta.recovery;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;
import java.nio.file.*;
import java.sql.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Uses the real role bootstrap and migrations; never reads application/Doppler database settings. */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PostgreSqlOperationalIntegrationTest {
    private static final String SYNTHETIC_PASSWORD = "isolated-operational-test";
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18")
            .withDatabaseName("emitta_operational_test")
            .withEnv("DB_MIGRATION_PASSWORD", SYNTHETIC_PASSWORD)
            .withEnv("DB_APP_PASSWORD", SYNTHETIC_PASSWORD)
            .withEnv("DB_READONLY_PASSWORD", SYNTHETIC_PASSWORD)
            .withCopyFileToContainer(MountableFile.forHostPath(
                    Path.of("docker/postgres/init/01-emitta-roles.sh").toAbsolutePath(),0755),
                    "/docker-entrypoint-initdb.d/01-emitta-roles.sh");

    private Flyway migrations(String... locations) {
        return Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),"emitta_migrator",SYNTHETIC_PASSWORD)
                .defaultSchema("emitta").schemas("emitta").locations(locations).load();
    }
    private Connection connect(String role) throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(),role,SYNTHETIC_PASSWORD);
    }
    private long count(Connection c,String sql) throws SQLException {
        try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}
    }
    @Test @Order(1) void allThirteenMigrationsAndActualRuntimePrivileges() throws Exception {
        var flyway=migrations("classpath:db/migration");
        assertEquals(13,flyway.migrate().migrationsExecuted);flyway.validate();
        try(var app=connect("emitta_app");var readonly=connect("emitta_readonly")) {
            assertEquals(13,count(readonly,"SELECT count(*) FROM emitta.flyway_schema_history WHERE success AND version IS NOT NULL"));
            app.createStatement().execute("INSERT INTO emitta.tenants(name) VALUES ('Synthetic operational tenant')");
            app.createStatement().execute("INSERT INTO emitta.administrative_audit_events(id,tenant_id,action,resource_id) SELECT gen_random_uuid(),id,'CLIENT_CREATED',gen_random_uuid() FROM emitta.tenants");
            assertEquals(1,count(readonly,"SELECT count(*) FROM emitta.administrative_audit_events"));
            assertThrows(SQLException.class,()->app.createStatement().execute("UPDATE emitta.administrative_audit_events SET action='CLIENT_REVOKED'"));
            assertThrows(SQLException.class,()->app.createStatement().execute("DELETE FROM emitta.administrative_audit_events"));
            assertEquals(1,count(readonly,"SELECT count(*) FROM emitta.tenants"));
            assertThrows(SQLException.class,()->readonly.createStatement().execute("INSERT INTO emitta.tenants(name) VALUES ('denied')"));
            assertThrows(SQLException.class,()->app.createStatement().execute("CREATE TABLE emitta.denied(id int)"));
            assertThrows(SQLException.class,()->app.createStatement().execute("UPDATE emitta.flyway_schema_history SET success=false"));
            assertEquals(1,count(app,"SELECT has_table_privilege('emitta_app','emitta.processing_jobs','INSERT')::int"));
            assertEquals(1,count(readonly,"SELECT has_table_privilege('emitta_readonly','emitta.processing_job_attempts','SELECT')::int"));
        }
    }
    @Test @Order(2) void interruptedTransactionalMigrationRollsBackWithoutRepair() throws Exception {
        Path location=Files.createTempDirectory("emitta-migration-rollback-");
        Path broken=location.resolve("V14__synthetic_interrupted.sql");
        try {
            Files.writeString(broken,"CREATE TABLE emitta.synthetic_interrupted(id int); SELECT 1/0;");
            assertThrows(org.flywaydb.core.api.FlywayException.class,()->migrations("classpath:db/migration","filesystem:"+location).migrate());
            try(var c=connect("emitta_readonly")) {
                assertEquals(0,count(c,"SELECT count(*) FROM information_schema.tables WHERE table_schema='emitta' AND table_name='synthetic_interrupted'"));
                assertEquals(13,count(c,"SELECT count(*) FROM emitta.flyway_schema_history WHERE version IS NOT NULL"));
            }
            Files.delete(broken);
            var resumed=migrations("classpath:db/migration");resumed.validate();
            assertEquals(0,resumed.migrate().migrationsExecuted);
        } finally {Files.deleteIfExists(broken);Files.deleteIfExists(location);}
    }
    @Test @Order(3) void concurrentMigratorsDoNotDuplicateHistory() throws Exception {
        try(var workers=Executors.newFixedThreadPool(2)) {
            var a=workers.submit(()->migrations("classpath:db/migration").migrate());
            var b=workers.submit(()->migrations("classpath:db/migration").migrate());
            assertEquals(0,a.get(30,TimeUnit.SECONDS).migrationsExecuted);
            assertEquals(0,b.get(30,TimeUnit.SECONDS).migrationsExecuted);
        }
        try(var c=connect("emitta_readonly")){assertEquals(13,count(c,"SELECT count(DISTINCT version) FROM emitta.flyway_schema_history WHERE version IS NOT NULL"));}
    }
    @Test @Order(4) void databaseInterruptionFailsPromptlyAndRecoversOnSameDurableDatabase() throws Exception {
        // Pause only this disposable container. The public/private PostgreSQL service is never touched.
        var docker=POSTGRES.getDockerClient();String id=POSTGRES.getContainerId();
        String url=POSTGRES.getJdbcUrl()+(POSTGRES.getJdbcUrl().contains("?")?"&":"?")+"socketTimeout=1";
        try(var c=DriverManager.getConnection(url,"emitta_app",SYNTHETIC_PASSWORD)) {
            docker.pauseContainerCmd(id).exec();
            long started=System.nanoTime();
            try {assertThrows(SQLException.class,()->count(c,"SELECT count(*) FROM emitta.tenants"));}
            finally {docker.unpauseContainerCmd(id).exec();}
            assertTrue(System.nanoTime()-started<TimeUnit.SECONDS.toNanos(5));
        }
        try(var c=connect("emitta_readonly")){assertEquals(1,count(c,"SELECT count(*) FROM emitta.tenants"));}
    }
    @Test @Order(5) void customBackupActuallyRestoresSchemaDataAndPrivilegesIntoAnotherDatabase() throws Exception {
        String root=POSTGRES.getUsername();
        assertEquals(0,POSTGRES.execInContainer("pg_dump","-U",root,"-d",POSTGRES.getDatabaseName(),"--format=custom","--file=/tmp/synthetic-operational.dump").getExitCode());
        assertEquals(0,POSTGRES.execInContainer("pg_restore","--list","/tmp/synthetic-operational.dump").getExitCode());
        assertEquals(0,POSTGRES.execInContainer("createdb","-U",root,"--owner=emitta_migrator","synthetic_restore").getExitCode());
        // pg_dump without --create does not restore database-level ACLs; explicitly restore the isolated policy.
        assertEquals(0,POSTGRES.execInContainer("psql","-U",root,"-d","synthetic_restore","-v","ON_ERROR_STOP=1","-c",
            "REVOKE ALL ON DATABASE synthetic_restore FROM PUBLIC; GRANT CONNECT ON DATABASE synthetic_restore TO emitta_migrator,emitta_app,emitta_readonly;").getExitCode());
        assertEquals(0,POSTGRES.execInContainer("pg_restore","-U",root,"--dbname=synthetic_restore","--exit-on-error","/tmp/synthetic-operational.dump").getExitCode());
        String url=POSTGRES.getJdbcUrl().replace("/"+POSTGRES.getDatabaseName(),"/synthetic_restore");
        try(var c=DriverManager.getConnection(url,"emitta_readonly",SYNTHETIC_PASSWORD)) {
            assertEquals(1,count(c,"SELECT count(*) FROM emitta.tenants"));
            assertEquals(13,count(c,"SELECT count(*) FROM emitta.flyway_schema_history WHERE version IS NOT NULL"));
            assertEquals(0,count(c,"SELECT count(*) FROM emitta.processing_jobs"));
            assertEquals(0,count(c,"SELECT has_table_privilege('emitta_app','emitta.flyway_schema_history','UPDATE')::int"));
            assertEquals(0,count(c,"SELECT has_database_privilege('emitta_app','synthetic_restore','TEMP')::int"));
        }
        var checksum=POSTGRES.execInContainer("sha256sum","/tmp/synthetic-operational.dump");assertEquals(0,checksum.getExitCode());
        var report=Path.of("build/operational/synthetic-backup-restore.txt");Files.createDirectories(report.getParent());
        Files.writeString(report,"Synthetic PostgreSQL only; custom dump restored with data/schema/grants.\n"+checksum.getStdout());
    }
}
