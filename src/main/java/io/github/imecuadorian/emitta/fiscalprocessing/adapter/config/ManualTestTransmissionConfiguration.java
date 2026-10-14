package io.github.imecuadorian.emitta.fiscalprocessing.adapter.config;

import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.documentartifact.application.port.in.LoadDocumentArtifactUseCase;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;
import io.github.imecuadorian.emitta.fiscalsigning.adapter.out.crypto.Pkcs12CertificateValidator;
import io.github.imecuadorian.emitta.fiscalsigning.adapter.out.xml.JdkSriXadesSignatureVerifier;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.out.SigningKeyMaterialPort;
import io.github.imecuadorian.emitta.invoicexml.application.port.out.InvoiceXmlValidatorPort;
import io.github.imecuadorian.emitta.ride.application.port.in.GenerateAndStoreInvoiceRideUseCase;
import io.github.imecuadorian.emitta.shared.fiscal.*;
import io.github.imecuadorian.emitta.srireception.adapter.config.SriReceptionProperties;
import io.github.imecuadorian.emitta.srireception.application.port.in.SubmitFiscalDocumentToSriUseCase;
import io.github.imecuadorian.emitta.srireception.application.exception.SriReceptionException;
import io.github.imecuadorian.emitta.srireception.domain.SriReceptionStatus;
import io.github.imecuadorian.emitta.sriauthorization.adapter.config.SriAuthorizationProperties;
import io.github.imecuadorian.emitta.sriauthorization.application.port.in.ReconcileDocumentAuthorizationUseCase;
import io.github.imecuadorian.emitta.sriauthorization.domain.SriAuthorizationStatus;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.cert.*;
import java.time.Clock;
import java.util.*;

/** One approved TEST reception, or query-only recovery. Never exposed as a REST operation. */
@Configuration(proxyBeanMethods = false)
@Profile("manual-test-transmission")
public class ManualTestTransmissionConfiguration {
    @Bean static org.springframework.beans.factory.config.BeanFactoryPostProcessor rejectAutomaticTransmission(Environment env) {
        return factory -> requireSafeRuntime(env);
    }
    static void requireSafeRuntime(Environment env) {
        if (!"none".equalsIgnoreCase(env.getProperty("spring.main.web-application-type", ""))
                || !env.getProperty("emitta.manual.confirm-one-test-operation", Boolean.class, false))
            throw new IllegalStateException("Explicit one-operation CLI confirmation required");
        for (var flag : List.of("fiscal.worker", "outbox.publisher", "processing.jobs", "signing"))
            if (!Boolean.FALSE.equals(env.getProperty("emitta." + flag + ".enabled", Boolean.class)))
                throw new IllegalStateException("Automatic workers and signing must remain disabled");
    }
    static void requireTestEndpoint(URI endpoint, String operation) {
        if (!endpoint.equals(URI.create("https://celcer.sri.gob.ec/comprobantes-electronicos-ws/" + operation)))
            throw new IllegalStateException("Only the exact certification endpoint is permitted");
    }
    @Bean ApplicationRunner transmitOneTestDocument(Environment env, JdbcClient jdbc, DocumentRepository documents,
            LoadDocumentArtifactUseCase artifacts, InvoiceXmlValidatorPort xsd, SigningKeyMaterialPort keys,
            SubmitFiscalDocumentToSriUseCase submit, ReconcileDocumentAuthorizationUseCase reconcile,
            GenerateAndStoreInvoiceRideUseCase ride, SriReceptionProperties reception, SriAuthorizationProperties authorization,
            Clock clock, org.springframework.context.ConfigurableApplicationContext context) {
        return args -> {
            requireSafeRuntime(env);
            if (!"emitta_staging".equals(jdbc.sql("select current_database()").query(String.class).single()))
                throw new IllegalStateException("Only the dedicated staging database is permitted");
            requireTestEndpoint(reception.testEndpoint(), "RecepcionComprobantesOffline");
            requireTestEndpoint(reception.productionEndpoint(), "RecepcionComprobantesOffline");
            requireTestEndpoint(authorization.testEndpoint(), "AutorizacionComprobantesOffline");
            requireTestEndpoint(authorization.productionEndpoint(), "AutorizacionComprobantesOffline");
            UUID id = UUID.fromString(env.getRequiredProperty("emitta.manual.document-id"));
            UUID tenant = UUID.fromString(env.getRequiredProperty("emitta.manual.tenant-id"));
            var document = documents.findById(id).orElseThrow();
            boolean queryOnly = env.getProperty("emitta.manual.query-only", Boolean.class, false);
            if (!tenant.equals(document.getTenantId()) || document.getEnvironment() != FiscalEnvironment.TEST
                    || (!queryOnly && document.getStatus() != DocumentStatus.SIGNED)
                    || (queryOnly && document.getStatus() != DocumentStatus.SUBMITTED && document.getStatus() != DocumentStatus.AUTHORIZED))
                throw new IllegalStateException("Document/tenant/TEST/status does not match this operation");
            String ruc = jdbc.sql("select ruc from emitta.taxpayers where id=:id and tenant_id=:tenant and status='ACTIVE' and test_enabled=true and production_enabled=false")
                    .param("id", document.getTaxpayerId()).param("tenant", tenant).query(String.class).single();
            if (!ruc.equals(env.getRequiredProperty("emitta.manual.expected-ruc"))
                    || !document.getAccessKey().equals(env.getRequiredProperty("emitta.manual.expected-access-key")))
                throw new IllegalStateException("Issuer/access key differs from the approved document");
            long attempts = jdbc.sql("select count(*) from emitta.document_attempts where document_id=:id and operation='SRI_SUBMISSION'")
                    .param("id", id).query(Long.class).single();
            if (!queryOnly && attempts != 0) throw new IllegalStateException("An existing attempt prohibits retransmission");
            var signed = artifacts.load(id, DocumentArtifactType.SIGNED_XML);
            if (!signed.artifact().sha256().equals(env.getRequiredProperty("emitta.manual.expected-signed-sha256")))
                throw new IllegalStateException("Signed artifact differs from the approved digest");
            xsd.validate(signed.content());
            var factory = CertificateFactory.getInstance("X.509");
            Collection<X509Certificate> cas; Collection<X509CRL> crls;
            try (var input = Files.newInputStream(Path.of(env.getRequiredProperty("emitta.certificates.administration.trusted-ca-file")))) {
                cas = factory.generateCertificates(input).stream().map(c -> (X509Certificate)c).toList();
            }
            try (var input = Files.newInputStream(Path.of(env.getRequiredProperty("emitta.certificates.administration.crl-file")))) {
                crls = factory.generateCRLs(input).stream().map(c -> (X509CRL)c).toList();
            }
            var resolved = keys.loadForDocument(id, clock.instant());
            if (!resolved.certificateId().toString().equals(env.getRequiredProperty("emitta.manual.expected-certificate-id")))
                throw new IllegalStateException("Certificate differs from the approved certificate");
            try (var material = resolved.keyMaterial()) {
                var validated = Pkcs12CertificateValidator.validate(material, clock.instant(), cas, crls);
                Pkcs12CertificateValidator.requireCertifiedRuc(validated.certificate(), ruc);
                new JdkSriXadesSignatureVerifier().verify(signed.content(), validated.certificate());
            }
            Path report = Path.of(env.getRequiredProperty("emitta.manual.report-file")).toAbsolutePath();
            if (!Files.getPosixFilePermissions(report.getParent()).equals(PosixFilePermissions.fromString("rwx------")))
                throw new IllegalStateException("Report directory must be private");
            try (var channel = java.nio.channels.FileChannel.open(report, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
                java.util.function.Consumer<String> evidence = line -> {
                    try { channel.write(java.nio.ByteBuffer.wrap((line + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8))); channel.force(true); }
                    catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                };
                evidence.accept("documentId=" + id + "\naccessKey=" + document.getAccessKey() + "\nenvironment=TEST\nsignedSha256=" + signed.artifact().sha256());
                boolean consult = queryOnly;
                if (!queryOnly) {
                    evidence.accept("reception=STARTING_SINGLE_ATTEMPT\nendpoint=" + reception.testEndpoint());
                    try {
                        var received = ManualTestXmlScope.run(id, ruc, () -> submit.submit(id));
                        evidence.accept("reception=" + received.status() + "\nreceptionMessages=" + received.messages());
                        consult = received.status() == SriReceptionStatus.RECEIVED;
                    } catch (SriReceptionException unknown) {
                        evidence.accept("reception=UNKNOWN\nfailureType=" + unknown.getClass().getSimpleName());
                        consult = true;
                    }
                }
                if (consult) {
                    evidence.accept("authorization=QUERY_STARTING\nendpoint=" + authorization.testEndpoint());
                    var result = reconcile.reconcile(id);
                    evidence.accept("authorization=" + result.status() + "\nauthorizationNumber=" + result.authorizationNumber()
                            + "\nauthorizedAt=" + result.authorizedAt() + "\nauthorizationMessages=" + result.messages());
                    if (result.status() == SriAuthorizationStatus.AUTHORIZED) {
                        ride.generate(tenant, id);
                        var pdf = artifacts.load(id, DocumentArtifactType.RIDE_PDF);
                        Path target = report.resolveSibling(report.getFileName() + ".pdf");
                        Files.createFile(target, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                        Files.write(target, pdf.content());
                        evidence.accept("pdfSha256=" + pdf.artifact().sha256() + "\npdfFile=" + target.getFileName());
                    }
                }
                evidence.accept("finalStatus=" + documents.findById(id).orElseThrow().getStatus());
            }
            System.out.println("Manual TEST operation completed; private evidence retained. No automatic retransmission.");
            org.springframework.boot.SpringApplication.exit(context, () -> 0); context.close();
        };
    }
}
