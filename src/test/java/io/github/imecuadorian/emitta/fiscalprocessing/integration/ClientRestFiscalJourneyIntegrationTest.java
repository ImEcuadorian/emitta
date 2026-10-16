package io.github.imecuadorian.emitta.fiscalprocessing.integration;

import com.sun.net.httpserver.HttpServer;
import io.github.imecuadorian.emitta.support.JwtTestProperties;
import io.github.imecuadorian.emitta.support.TestSigningCertificates;
import io.github.imecuadorian.emitta.srireception.application.port.in.SubmitFiscalDocumentToSriUseCase;
import io.github.imecuadorian.emitta.srireception.application.exception.SriReceptionException;
import io.github.imecuadorian.emitta.sriauthorization.application.port.in.ReconcileDocumentAuthorizationUseCase;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import software.amazon.awssdk.services.s3.S3Client;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real REST, crypto, PostgreSQL, RabbitMQ and MinIO. Only SRI is a loopback SOAP simulator. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "spring.datasource.hikari.schema=emitta",
    "emitta.outbox.publisher.enabled=true", "emitta.fiscal.worker.enabled=true",
    "emitta.processing.jobs.enabled=true", "emitta.processing.jobs.poll-ms=100",
    "emitta.signing.enabled=true", "emitta.ride.enabled=true", "emitta.artifacts.storage.enabled=true",
    "emitta.certificates.administration.enabled=true", "emitta.sri.reception.enabled=true",
    "emitta.sri.authorization.enabled=true", "emitta.sri.automatic-test.enabled=true", "emitta.sri.reception.request-timeout=PT1S",
    "emitta.sri.resilience.authorization.max-attempts=1",
    "emitta.fiscal.provider-policy.default-profile=external-test",
    "emitta.fiscal.provider-policy.profiles.external-test.mode=EXTERNAL_PROVIDER",
    "emitta.fiscal.provider-policy.profiles.external-test.ruc=1799999999001",
    "emitta.fiscal.provider-policy.profiles.external-test.justification=Synthetic loopback test only",
    "emitta.fiscal.provider-policy.profiles.external-test.normative-reference=https://www.sri.gob.ec/facturacion-electronica",
    "emitta.fiscal.provider-policy.profiles.external-test.evidence-path=src/test/resources/fiscal/provider-policy-test-evidence.txt",
    "emitta.fiscal.provider-policy.profiles.external-test.evidence-sha256=af040e9d52eaa1545b50ade55f5c3752450a036235850fd7dd17c643d258f53a"
})
@Testcontainers @Tag("object-storage-integration") @DirtiesContext
class ClientRestFiscalJourneyIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:18");
    @Container @ServiceConnection static final RabbitMQContainer RABBIT=new RabbitMQContainer("rabbitmq:4-management-alpine");
    private static final UUID ENROLLED_TENANT=UUID.randomUUID();
    private static final Instant CUTOFF=Instant.now();
    private static final String SECRET="synthetic-journey-secret";
    private static final TestSigningCertificates.Fixture CERT=TestSigningCertificates.create(SECRET);
    private static final AtomicReference<byte[]> RECEIVED=new AtomicReference<>();
    private static final AtomicInteger POSTS=new AtomicInteger(), QUERIES=new AtomicInteger(), PRODUCTION=new AtomicInteger();
    private static final HttpServer SOAP=startSoap();
    private static final Path TRUST=trustDirectory();
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;
    @Autowired S3Client s3;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @DynamicPropertySource static void configuration(DynamicPropertyRegistry r) {
        JwtTestProperties.register(r);
        r.add("emitta.sri.automatic-test.tenant-ids",()->ENROLLED_TENANT.toString());
        r.add("emitta.sri.automatic-test.not-before",()->CUTOFF.toString());
        r.add("EMITTA_CERTIFICATE_MASTER_KEY_B64",()->Base64.getEncoder().encodeToString(new byte[32]));
        r.add("emitta.certificates.administration.trusted-ca-file",()->TRUST.resolve("ca.der").toString());
        r.add("emitta.certificates.administration.crl-file",()->TRUST.resolve("crl.der").toString());
        for(String op:List.of("reception","authorization")) {
            r.add("emitta.sri."+op+".test-endpoint",()->"http://127.0.0.1:"+SOAP.getAddress().getPort()+"/"+op);
            r.add("emitta.sri."+op+".production-endpoint",()->"http://127.0.0.1:"+SOAP.getAddress().getPort()+"/forbidden");
        }
        for(String name:List.of("endpoint","region","bucket","access-key","secret-key","path-style")) {
            String property=name.equals("path-style")?"path-style-access":name;
            String value=Objects.requireNonNull(System.getenv("EMITTA_ARTIFACT_STORAGE_"+name.toUpperCase(Locale.ROOT).replace('-','_')));
            r.add("emitta.artifacts.storage."+property,()->value);
        }
    }

    @AfterAll static void clean() throws Exception {
        SOAP.stop(0);
        for(Path p:List.of(TRUST.resolve("ca.der"),TRUST.resolve("crl.der"),TRUST))Files.deleteIfExists(p);
    }

    @Test void customerCanOnboardSignReconcileAmbiguousReceptionAndDownloadOriginalArtifacts() throws Exception {
        String bucket=System.getenv("EMITTA_ARTIFACT_STORAGE_BUCKET");
        try{s3.headBucket(b->b.bucket(bucket));}catch(software.amazon.awssdk.services.s3.model.NoSuchBucketException absent){s3.createBucket(b->b.bucket(bucket));}
        String token=account("self-service");
        String stranger=account("other-customer");
        String setup="""
            {"taxpayer":{"ruc":"1790012345001","legalName":"SYNTHETIC ISSUER","mainAddress":"Synthetic address"},
             "establishment":{"code":"001","address":"Synthetic address"},"pointOfIssue":{"code":"001"}}
            """;
        var configuration=json.readTree(call("POST","/api/v1/fiscal-configuration",token,setup,null,200));
        assertEquals(configuration,json.readTree(call("POST","/api/v1/fiscal-configuration",token,setup,null,200)));
        String taxpayer=configuration.get("taxpayerId").asText(),point=configuration.get("pointOfIssueId").asText();
        call("GET","/api/v1/taxpayers/"+taxpayer+"/certificates",stranger,null,null,403);
        upload(token,taxpayer);
        String metadata=new String(call("GET","/api/v1/taxpayers/"+taxpayer+"/certificates",token,null,null,200),StandardCharsets.UTF_8);
        assertFalse(metadata.contains(SECRET));assertFalse(metadata.contains("ciphertext"));
        String invoice="""
            {"pointOfIssueId":"%s","environment":"TEST","issuedAt":"%s",
             "buyer":{"identificationType":"07","identification":"9999999999999","name":"CONSUMIDOR FINAL"},
             "items":[{"sku":"SYNTHETIC","description":"Synthetic service","quantity":2,"unitPrice":10,"discount":2,
               "taxes":[{"taxCode":"2","percentageCode":"4","rate":15}]}],
             "payments":[{"paymentMethod":"01","total":20.70}],"expectedTotal":20.70}
            """.formatted(point,Instant.now().toString());
        var accepted=json.readTree(call("POST","/api/v1/invoices",token,invoice,"journey-once",202));
        UUID id=UUID.fromString(accepted.get("id").asText());
        var duplicate=json.readTree(call("POST","/api/v1/invoices",token,invoice,"journey-once",200));
        assertEquals(id.toString(),duplicate.get("id").asText());assertFalse(duplicate.get("created").asBoolean());
        // Outbox -> RabbitMQ -> signing -> one ambiguous SOAP reception -> query -> durable RIDE.
        // No direct service invocation or document state updates drive this journey.
        await(()->jdbc.queryForObject("select count(*) from emitta.document_artifacts where document_id=? and artifact_type='RIDE_PDF'",Integer.class,id)==1);
        var document=json.readTree(call("GET","/api/v1/documents/"+id,token,null,null,200));
        assertEquals("AUTHORIZED",document.get("status").asText());assertEquals("TEST",document.get("environment").asText());
        byte[] xml=call("GET","/api/v1/documents/"+id+"/artifacts/authorized-xml",token,null,null,200);
        byte[] pdf=call("GET","/api/v1/documents/"+id+"/artifacts/ride-pdf",token,null,null,200);
        assertArrayEquals(RECEIVED.get(),xml);
        assertTrue(new String(pdf,0,5,StandardCharsets.US_ASCII).startsWith("%PDF-"));
        for(var entry:Map.of("AUTHORIZED_XML",xml,"RIDE_PDF",pdf).entrySet())
            assertEquals(jdbc.queryForObject("select sha256 from emitta.document_artifacts where document_id=? and artifact_type=?",String.class,id,entry.getKey()),
                HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(entry.getValue())));
        call("GET","/api/v1/documents/"+id+"/artifacts/ride-pdf",stranger,null,null,404);
        call("GET","/api/v1/documents/"+id+"/artifacts/ride-pdf",null,null,null,401);
        assertEquals(1,jdbc.queryForObject("select count(*) from emitta.documents",Integer.class));
        assertEquals(1,jdbc.queryForObject("select count(*) from emitta.document_attempts where operation='SRI_SUBMISSION' and document_id=?",Integer.class,id));
        assertEquals(1,POSTS.get());assertEquals(2,QUERIES.get());assertEquals(0,PRODUCTION.get());
        await(()->jdbc.queryForObject("select count(*) from emitta.outbox_events where published_at is null",Integer.class)==0);
    }

    private String account(String name) throws Exception {
        UUID tenant=name.equals("self-service")?ENROLLED_TENANT:UUID.randomUUID(),client=UUID.randomUUID();
        jdbc.update("insert into emitta.tenants(id,name,status) values (?,?,'ACTIVE')",tenant,name);
        jdbc.update("insert into emitta.api_clients(id,tenant_id,client_id,client_secret_hash,name,status) values (?,?,?,?,?,'ACTIVE')",client,tenant,name,passwords.encode(SECRET),name);
        for(String scope:List.of("taxpayers:write","establishments:write","points-of-issue:write","certificates:read","certificates:write","invoices:write","documents:read","artifacts:read"))
            jdbc.update("insert into emitta.api_client_scopes(api_client_id,scope) values (?,?)",client,scope);
        return json.readTree(call("POST","/api/v1/auth/token",null,json.writeValueAsString(Map.of("clientId",name,"clientSecret",SECRET)),null,200)).get("accessToken").asText();
    }
    private byte[] call(String method,String path,String token,String body,String idempotency,int status) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(15));
        if(token!=null)request.header("Authorization","Bearer "+token);
        if(idempotency!=null)request.header("Idempotency-Key",idempotency);
        if(body!=null)request.header("Content-Type","application/json");
        var response=http.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(status,response.statusCode(),method+" "+path+" status");return response.body();
    }
    private void upload(String token,String taxpayer) throws Exception {
        String boundary="emitta-test-"+UUID.randomUUID();
        var body=new java.io.ByteArrayOutputStream();
        for(var entry:Map.of("password",SECRET,"authorizationReviewed","true").entrySet())
            body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+entry.getKey()+"\"\r\n\r\n"+entry.getValue()+"\r\n").getBytes(StandardCharsets.UTF_8));
        for(var entry:Map.of("certificate",CERT.pkcs12(),"authorizationEvidence","Synthetic reviewed authority".getBytes(StandardCharsets.UTF_8)).entrySet()) {
            body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+entry.getKey()+"\"; filename=\"synthetic.bin\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(entry.getValue());body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        var response=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/taxpayers/"+taxpayer+"/certificates"))
            .header("Authorization","Bearer "+token).header("Content-Type","multipart/form-data; boundary="+boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),HttpResponse.BodyHandlers.discarding());
        assertEquals(201,response.statusCode());
    }
    private static Path trustDirectory() {
        try {var path=Files.createTempDirectory("emitta-journey-trust-");Files.write(path.resolve("ca.der"),CERT.ca().getEncoded());Files.write(path.resolve("crl.der"),CERT.crls().getFirst().getEncoded());return path;}
        catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    private static HttpServer startSoap() {
        try {
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/reception",exchange->{
                POSTS.incrementAndGet();
                var request=parse(exchange.getRequestBody().readAllBytes());
                RECEIVED.set(Base64.getDecoder().decode(request.getElementsByTagName("xml").item(0).getTextContent()));
                try{Thread.sleep(1300);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
                exchange.close();
            });
            server.createContext("/authorization",exchange->{
                String key=parse(exchange.getRequestBody().readAllBytes()).getElementsByTagName("claveAccesoComprobante").item(0).getTextContent();
                boolean pending=QUERIES.incrementAndGet()==1;
                String entry=pending?"":"<autorizacion><estado>AUTORIZADO</estado><numeroAutorizacion>"+key+"</numeroAutorizacion><fechaAutorizacion>"+Instant.now()+"</fechaAutorizacion><comprobante><![CDATA["+new String(RECEIVED.get(),StandardCharsets.UTF_8)+"]]></comprobante><mensajes/></autorizacion>";
                byte[] response=("<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><a:autorizacionComprobanteResponse xmlns:a=\"http://ec.gob.sri.ws.autorizacion\"><RespuestaAutorizacionComprobante><claveAccesoConsultada>"+key+"</claveAccesoConsultada><numeroComprobantes>"+(pending?0:1)+"</numeroComprobantes><autorizaciones>"+entry+"</autorizaciones></RespuestaAutorizacionComprobante></a:autorizacionComprobanteResponse></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200,response.length);try(var output=exchange.getResponseBody()){output.write(response);}
            });
            server.createContext("/forbidden",exchange->{PRODUCTION.incrementAndGet();exchange.sendResponseHeaders(503,-1);exchange.close();});
            server.start();return server;
        }catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    private static org.w3c.dom.Document parse(byte[] xml) {
        try {var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);return factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml));}
        catch(Exception e){throw new IllegalStateException("Invalid synthetic SOAP",e);}
    }
    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline=System.nanoTime()+Duration.ofSeconds(45).toNanos();
        while(!condition.getAsBoolean()&&System.nanoTime()<deadline)Thread.sleep(100);
        assertTrue(condition.getAsBoolean(),"Durable pipeline did not complete in time");
    }
}
