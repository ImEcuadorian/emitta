package io.github.imecuadorian.emitta.recovery;

import io.github.imecuadorian.emitta.support.JwtTestProperties;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Real REST/JWT/Redis/PostgreSQL/outbox/Rabbit admission. Signing, RIDE and all SRI ports are OFF.
 * Fiscal execution is covered separately with synthetic XML/signature fixtures and SOAP simulators.
 */
@org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics(export=true)
@Tag("operational-load")
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@org.testcontainers.junit.jupiter.Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "emitta.outbox.publisher.enabled=true","emitta.fiscal.worker.enabled=true",
    "emitta.processing.jobs.enabled=false","emitta.signing.enabled=false","emitta.ride.enabled=false",
    "emitta.artifacts.storage.enabled=false","emitta.sri.reception.enabled=false","emitta.sri.authorization.enabled=false"
})
class OperationalLoadTest {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:18")
        .withCommand("postgres","-c","shared_preload_libraries=pg_stat_statements","-c","track_io_timing=on");
    @Container @ServiceConnection static final RabbitMQContainer RABBIT=new RabbitMQContainer("rabbitmq:4-management-alpine");
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        JwtTestProperties.register(r);
        r.add("emitta.observability.enabled",()->true);
        r.add("emitta.outbox.admission.enabled",()->true);
        String concurrency=System.getenv().getOrDefault("EMITTA_LOAD_ADMISSION_CONCURRENCY","1");
        if(!java.util.Set.of("1","2").contains(concurrency))throw new IllegalArgumentException("Isolated admission concurrency must be 1 or 2");
        r.add("emitta.processing.admission.concurrency",()->Integer.parseInt(concurrency));
        r.add("emitta.security.rate-limit.enabled",()->true);
        r.add("spring.data.redis.host",REDIS::getHost);r.add("spring.data.redis.port",()->REDIS.getMappedPort(6379));
        // Capacity experiment only: protection is still enabled with explicitly larger test quotas.
        for(String dimension:List.of("ip","ip-authentication","client","client-authentication","tenant","tenant-invoices")) {
            r.add("emitta.security.rate-limit.policies."+dimension+".capacity",()->100000);
            r.add("emitta.security.rate-limit.policies."+dimension+".refill-per-second",()->10000);
        }
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired io.micrometer.core.instrument.MeterRegistry metrics;
    @Autowired PasswordEncoder encoder;
    @Autowired JsonMapper json;
    @Autowired javax.sql.DataSource source;
    private static org.springframework.context.ConfigurableApplicationContext runningContext;
    @Autowired void rememberContext(org.springframework.context.ConfigurableApplicationContext context) {runningContext=context;}
    @AfterAll static void stopBackendWorkBeforeDisposableInfrastructure() {
        if(runningContext!=null && runningContext.isActive()) {
            runningContext.getBean(org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry.class).stop();
            runningContext.getBeansOfType(org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler.class)
                .values().forEach(org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler::shutdown);
        }
    }

    @Test void syntheticLoadPreservesEveryAcceptedDocumentAndIdempotentEvent() throws Exception {
        String profile=System.getenv().getOrDefault("EMITTA_LOAD_PROFILE","normal");
        String vus=System.getenv().getOrDefault("EMITTA_LOAD_VUS","20");
        String duration=System.getenv().getOrDefault("EMITTA_LOAD_DURATION",profile.equals("sustained")?"10m":profile.equals("academic-sustained")?"5m":profile.equals("academic-spike")?"1m":"30s");
        if(!List.of("normal","sustained","spike","academic-sustained","academic-spike").contains(profile))throw new IllegalArgumentException("Invalid load profile");
        if(!duration.matches("^[1-9][0-9]*(s|m)$"))throw new IllegalArgumentException("Invalid controlled duration");
        long durationSeconds=Long.parseLong(duration.substring(0,duration.length()-1));
        if(duration.endsWith("m"))durationSeconds=Math.multiplyExact(durationSeconds,60);
        if(durationSeconds>600)throw new IllegalArgumentException("Maximum duration is 10m without token renewal");
        if((profile.equals("spike") || profile.equals("academic-spike")) && !"true".equals(System.getenv("EMITTA_LOAD_SPIKE_DIMENSIONED")))
            throw new IllegalArgumentException("500 VU spike requires EMITTA_LOAD_SPIKE_DIMENSIONED=true after resource review");
        String run=UUID.randomUUID().toString();
        Path directory=Path.of("build/operational/load",profile+"-"+vus+"-"+run).toAbsolutePath();Files.createDirectories(directory);
        Files.writeString(directory.resolve("experiment.json"),json.writeValueAsString(Map.of("profiling",true,"admissionConcurrency",System.getenv().getOrDefault("EMITTA_LOAD_ADMISSION_CONCURRENCY","1"))));
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS pg_stat_statements");
        UUID tenant=UUID.randomUUID(),taxpayer=UUID.randomUUID(),establishment=UUID.randomUUID(),point=UUID.randomUUID(),client=UUID.randomUUID();
        String clientId="synthetic-load-"+run,secret=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO emitta.tenants(id,name,status) VALUES (?,'SYNTHETIC LOAD ONLY','ACTIVE')",tenant);
        jdbc.update("INSERT INTO emitta.taxpayers(id,tenant_id,ruc,legal_name,main_address,status,test_enabled,production_enabled) VALUES (?,?,'1790012345001','SYNTHETIC LOAD ONLY','Synthetic address','ACTIVE',true,false)",taxpayer,tenant);
        jdbc.update("INSERT INTO emitta.establishments(id,taxpayer_id,code,status) VALUES (?,?,'001','ACTIVE')",establishment,taxpayer);
        jdbc.update("INSERT INTO emitta.points_of_issue(id,establishment_id,code,status) VALUES (?,?,'001','ACTIVE')",point,establishment);
        jdbc.update("INSERT INTO emitta.api_clients(id,tenant_id,client_id,client_secret_hash,name,status) VALUES (?,?,?,?,'Synthetic load client','ACTIVE')",client,tenant,clientId,encoder.encode(secret));
        for(String scope:List.of("invoices:write","documents:read"))jdbc.update("INSERT INTO emitta.api_client_scopes(api_client_id,scope) VALUES (?,?)",client,scope);
        Map<String,Object> invoice=Map.of("pointOfIssueId",point,"environment","TEST",
            "buyer",Map.of("identificationType","07","identification","9999999999999","name","CONSUMIDOR FINAL"),
            "items",List.of(Map.of("sku","LOAD","description","Synthetic load item","quantity",1,"unitPrice",10,"discount",0,
                "taxes",List.of(Map.of("taxCode","2","percentageCode","4","rate",15)))),
            "payments",List.of(Map.of("paymentMethod","01","total",11.50)),"expectedTotal",11.50);
        Path fixture=directory.resolve("fixture.json");
        Files.writeString(fixture,json.writeValueAsString(Map.of("runId",run,"clientId",clientId,"clientSecret",secret,"invoice",invoice)));
        Testcontainers.exposeHostPorts(port);
        var pool=source.unwrap(com.zaxxer.hikari.HikariDataSource.class);
        var peakActive=new AtomicInteger();var peakPending=new AtomicInteger();var peakHeap=new java.util.concurrent.atomic.AtomicLong();
        var samples=Collections.synchronizedList(new ArrayList<String>());
        var containerSamples=Collections.synchronizedList(new ArrayList<String>());
        var operatingSystem=(com.sun.management.OperatingSystemMXBean)java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        Files.writeString(directory.resolve("environment.json"),json.writeValueAsString(Map.of(
            "java",Runtime.version().toString(),"hostLogicalProcessors",operatingSystem.getAvailableProcessors(),
            "hostMemoryBytes",operatingSystem.getTotalMemorySize(),"testJvmMaxHeapBytes",Runtime.getRuntime().maxMemory(),
            "k6Image","grafana/k6:1.7.1","k6CpuLimit",2,"k6MemoryLimitBytes",512L*1024*1024,
            "postgresImage","postgres:18","rabbitImage","rabbitmq:4-management-alpine","redisImage","redis:8-alpine")));
        samples.add("elapsed_ms,active,pending,heap_bytes,process_cpu_ratio,outbox_pending,jobs_pending,documents,events,published,oldest_seconds,confirmed,confirm_seconds,claim_seconds,mark_seconds,acquire_seconds");
        Files.write(directory.resolve("resources.csv"),samples);
        long started=System.nanoTime();
        long exitCode;
        try(var sampler=Executors.newScheduledThreadPool(2);var k6=new GenericContainer<>("grafana/k6:1.7.1")
            .withCreateContainerCmdModifier(c->c.getHostConfig().withMemory(512L*1024*1024).withNanoCPUs(2_000_000_000L))
            .withAccessToHost(true)
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("tests/load/traffic.ts").toAbsolutePath()),"/work/traffic.ts")
            .withCopyFileToContainer(MountableFile.forHostPath(fixture),"/work/fixture.json")
            .withEnv("BASE_URL","http://host.testcontainers.internal:"+port)
            .withEnv("PROFILE",profile).withEnv("VUS",vus).withEnv("DURATION",duration)
            .withCommand("run","--summary-export","/tmp/summary.json","/work/traffic.ts")) {
            sampler.scheduleAtFixedRate(()->{
                try {
                    var mx=pool.getHikariPoolMXBean();int active=mx.getActiveConnections(),pending=mx.getThreadsAwaitingConnection();
                    long heap=java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
                    peakActive.accumulateAndGet(active,Math::max);peakPending.accumulateAndGet(pending,Math::max);peakHeap.accumulateAndGet(heap,Math::max);
                    long outbox=jdbc.queryForObject("SELECT count(*) FROM emitta.outbox_events WHERE published_at IS NULL",Long.class);
                    long jobs=jdbc.queryForObject("SELECT count(*) FROM emitta.processing_jobs WHERE state='PENDING'",Long.class);
                    double cpu=((com.sun.management.OperatingSystemMXBean)java.lang.management.ManagementFactory.getOperatingSystemMXBean()).getProcessCpuLoad();
                    var counts=jdbc.queryForMap("SELECT count(*) AS events,count(*) FILTER(WHERE published_at IS NOT NULL) AS published,COALESCE(EXTRACT(EPOCH FROM CURRENT_TIMESTAMP-min(created_at) FILTER(WHERE published_at IS NULL)),0) AS oldest FROM emitta.outbox_events");
                    long docs=jdbc.queryForObject("SELECT count(*) FROM emitta.documents",Long.class);
                    var waits=jdbc.queryForList("SELECT state,wait_event_type,wait_event,count(*) AS connections,max(EXTRACT(EPOCH FROM clock_timestamp()-xact_start)) AS longest_transaction_seconds FROM pg_stat_activity WHERE datname=current_database() AND pid<>pg_backend_pid() GROUP BY state,wait_event_type,wait_event");
                    Files.writeString(directory.resolve("postgres-waits.jsonl"),json.writeValueAsString(Map.of("elapsed_ms",(System.nanoTime()-started)/1_000_000,"waits",waits))+System.lineSeparator(),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
                    String row=(System.nanoTime()-started)/1_000_000+","+active+","+pending+","+heap+","+cpu+","+outbox+","+jobs+","+docs+","+counts.get("events")+","+counts.get("published")+","+counts.get("oldest")+","+counter("emitta.outbox.confirmed")+","+seconds("emitta.outbox.confirm.duration")+","+seconds("emitta.outbox.claim.duration")+","+seconds("emitta.outbox.mark.duration")+","+seconds("hikaricp.connections.acquire");
                    samples.add(row);
                    java.nio.file.Files.writeString(directory.resolve("resources.csv"),row+System.lineSeparator(),StandardOpenOption.APPEND);
                }catch(java.io.IOException sampleUnavailable){/* Final in-memory snapshot still records the run. */
                }catch(RuntimeException unavailable){samples.add((System.nanoTime()-started)/1_000_000+",unavailable");}
            },0,1,TimeUnit.SECONDS);
            sampler.scheduleWithFixedDelay(()->{
                sampleRabbit(directory);
                try {
                    var command=new ArrayList<>(List.of("docker","stats","--no-stream","--format","{{json .}}",
                        POSTGRES.getContainerId(),RABBIT.getContainerId(),REDIS.getContainerId()));
                    if(k6.getContainerId()!=null)command.add(k6.getContainerId());
                    var process=new ProcessBuilder(command).redirectErrorStream(true).start();
                    if(!process.waitFor(10,TimeUnit.SECONDS)){process.destroyForcibly();return;}
                    if(process.exitValue()==0)containerSamples.add(new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
                }catch(java.io.IOException unavailable){containerSamples.add("stats unavailable");}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
            },5,5,TimeUnit.SECONDS);
            try {
                k6.start();
                org.awaitility.Awaitility.await().atMost(Duration.ofMinutes(25)).pollInterval(Duration.ofSeconds(1))
                    .until(()->!k6.isRunning());
                exitCode=k6.getCurrentContainerInfo().getState().getExitCodeLong();
            }
            finally {
                Files.writeString(directory.resolve("k6.log"),k6.getLogs());
                try {k6.copyFileFromContainer("/tmp/summary.json",directory.resolve("summary.json").toString());}
                catch(RuntimeException missingSummary){/* Failure still recorded by startup check and k6 log. */}
                sampler.shutdownNow();
                sampler.awaitTermination(12,TimeUnit.SECONDS);
                Files.write(directory.resolve("resources.csv"),samples);
                Files.write(directory.resolve("container-resources.jsonl"),containerSamples);
            }
        } finally {Files.deleteIfExists(fixture);}
        writeMetricSnapshot(directory);
        long drainStarted=System.nanoTime();
        boolean drained=true;
        try {
        org.awaitility.Awaitility.await().atMost(Duration.ofMinutes(5)).pollInterval(Duration.ofSeconds(1)).untilAsserted(()-> {
            long pending=jdbc.queryForObject("SELECT count(*) FROM emitta.outbox_events WHERE published_at IS NULL",Long.class);
            long admitted=jdbc.queryForObject("SELECT count(*) FROM emitta.processing_jobs",Long.class);
            try {Files.writeString(directory.resolve("drain.csv"),(System.nanoTime()-drainStarted)/1_000_000_000d+","+pending+","+admitted+System.lineSeparator(),StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(java.io.IOException ignored){}
            assertEquals(0,pending);
            assertEquals(jdbc.queryForObject("SELECT count(*) FROM emitta.documents",Integer.class),jdbc.queryForObject("SELECT count(*) FROM emitta.processing_jobs",Integer.class));
            assertTrue(sampleRabbit(directory),"Rabbit ready/unacked must drain too");
        });
        } catch(org.awaitility.core.ConditionTimeoutException | AssertionError timeout) {drained=false;}
        int documents=jdbc.queryForObject("SELECT count(*) FROM emitta.documents",Integer.class);
        int durableJobs=jdbc.queryForObject("SELECT count(*) FROM emitta.processing_jobs",Integer.class);
        int pendingOutbox=jdbc.queryForObject("SELECT count(*) FROM emitta.outbox_events WHERE published_at IS NULL",Integer.class);
        assertTrue(documents>0);
        assertEquals(documents,jdbc.queryForObject("SELECT count(*) FROM emitta.invoices",Integer.class));
        assertEquals(documents,jdbc.queryForObject("SELECT count(*) FROM emitta.outbox_events WHERE event_type='document.received.v1'",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM emitta.documents WHERE status<>'QUEUED' OR environment<>'TEST'",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM emitta.sri_authorizations",Integer.class));
        var summary=json.readTree(Files.readString(directory.resolve("summary.json")));
        var accepted=summary.path("metrics").path("invoices_accepted");
        int acknowledged=accepted.has("values")?accepted.path("values").path("count").asInt():accepted.path("count").asInt();
        assertTrue(documents>=acknowledged,"Every HTTP-acknowledged invoice must be durably present");
        Files.writeString(directory.resolve("invariants.json"),json.writeValueAsString(Map.ofEntries(
            Map.entry("httpAcknowledged",acknowledged),Map.entry("notHttpAcknowledged",documents-acknowledged),
            Map.entry("drainSeconds",(System.nanoTime()-drainStarted)/1_000_000_000d),
            Map.entry("drained",drained),Map.entry("outboxPending",pendingOutbox),
            Map.entry("k6ExitCode",exitCode),
            Map.entry("documents",documents),Map.entry("jobs",durableJobs),Map.entry("events",documents),Map.entry("poolMax",pool.getMaximumPoolSize()),
            Map.entry("peakActive",peakActive.get()),Map.entry("peakPending",peakPending.get()),Map.entry("peakHeapBytes",peakHeap.get()),
            Map.entry("jvmAvailableProcessors",Runtime.getRuntime().availableProcessors()),Map.entry("sriCalls",0),Map.entry("scope","REST admission only"))));
        writeMetricSnapshot(directory);
        writeSqlProfile(directory);
        assertTrue(drained,"Outbox/admission did not drain within five minutes; inspect preserved persistence evidence");
        assertEquals(documents,durableJobs);assertEquals(0,pendingOutbox);
        assertEquals(0,exitCode,"k6 thresholds failed; persistence evidence and summary were preserved");
    }
    private double counter(String name) {var c=metrics.find(name).counter();return c==null?0:c.count();}
    private void writeSqlProfile(Path directory) throws java.io.IOException {
        // Only normalized SQL from this disposable synthetic database. Never query private services.
        Files.writeString(directory.resolve("postgres-statements.json"),json.writeValueAsString(jdbc.queryForList("SELECT query,calls,total_exec_time,mean_exec_time,rows,shared_blks_hit,shared_blks_read,shared_blk_read_time,shared_blk_write_time FROM pg_stat_statements WHERE dbid=(SELECT oid FROM pg_database WHERE datname=current_database()) ORDER BY total_exec_time DESC")));
        var sql="EXPLAIN (ANALYZE,BUFFERS,FORMAT JSON) "+io.github.imecuadorian.emitta.outbox.adapter.config.OutboxAdmissionGuard.BACKLOG_SQL;
        Files.writeString(directory.resolve("guard-plan.json"),jdbc.queryForObject(sql,String.class));
    }
    private double seconds(String name) {return metrics.find(name).timers().stream().mapToDouble(t->t.totalTime(TimeUnit.SECONDS)).sum();}
    private void writeMetricSnapshot(Path directory) throws java.io.IOException {
        var rows=new ArrayList<Map<String,Object>>();
        for(var m:metrics.getMeters()) if(m.getId().getName().startsWith("emitta.outbox") || m.getId().getName().startsWith("hikaricp.connections") || m.getId().getName().startsWith("emitta.invoice") || m.getId().getName().startsWith("emitta.rabbit.admission") || m.getId().getName().startsWith("emitta.processing.admitted")) {
            var values=new LinkedHashMap<String,Double>();for(var v:m.measure())values.put(v.getStatistic().toString(),v.getValue());
            rows.add(Map.of("name",m.getId().getName(),"tags",m.getId().getTags().toString(),"values",values));
        }
        Files.writeString(directory.resolve("pipeline-metrics.json"),json.writeValueAsString(rows));
    }    private boolean sampleRabbit(Path directory) {
        try(var client=java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
            String credentials=java.util.Base64.getEncoder().encodeToString((RABBIT.getAdminUsername()+":"+RABBIT.getAdminPassword()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://"+RABBIT.getHost()+":"+RABBIT.getMappedPort(15672)+"/api/queues/%2F"))
                .timeout(Duration.ofSeconds(2)).header("Authorization","Basic "+credentials).GET().build();
            var response=client.send(request,java.net.http.HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200)return false;
            var entries=new ArrayList<Map<String,Object>>();
            for(var q:json.readTree(response.body()))entries.add(Map.of("queue",q.path("name").asText(),"ready",q.path("messages_ready").asLong(),"unacked",q.path("messages_unacknowledged").asLong()));
            Files.writeString(directory.resolve("rabbit-queues.jsonl"),json.writeValueAsString(Map.of("timestamp",java.time.Instant.now().toString(),"queues",entries))+System.lineSeparator(),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            return entries.stream().allMatch(q->((Number)q.get("ready")).longValue()==0 && ((Number)q.get("unacked")).longValue()==0);
        }catch(Exception unavailable){return false; /* Never log credentials. */}
    }}
