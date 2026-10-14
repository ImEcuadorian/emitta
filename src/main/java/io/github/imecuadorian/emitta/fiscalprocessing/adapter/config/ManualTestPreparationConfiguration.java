package io.github.imecuadorian.emitta.fiscalprocessing.adapter.config;

import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.documentartifact.application.port.in.LoadDocumentArtifactUseCase;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;
import io.github.imecuadorian.emitta.fiscalprocessing.application.command.ProcessFiscalDocumentCommand;
import io.github.imecuadorian.emitta.fiscalprocessing.application.port.in.ProcessFiscalDocumentUseCase;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.out.SigningKeyMaterialPort;
import io.github.imecuadorian.emitta.fiscalsigning.adapter.out.crypto.Pkcs12CertificateValidator;
import io.github.imecuadorian.emitta.fiscalsigning.adapter.out.xml.JdkSriXadesSignatureVerifier;
import io.github.imecuadorian.emitta.invoicexml.application.port.out.InvoiceXmlValidatorPort;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import io.github.imecuadorian.emitta.shared.fiscal.ProviderPolicyPort;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.cert.*;
import java.time.Clock;
import java.util.*;

/** Explicit one-document staging command. Never publishes Outbox or invokes SRI. */
@Configuration(proxyBeanMethods = false)
@Profile("manual-test-preparation")
public class ManualTestPreparationConfiguration {
    @Bean static org.springframework.beans.factory.config.BeanFactoryPostProcessor rejectUnsafeFlagsBeforeBeans(Environment env) {
        // Fail before any worker, scheduler or SOAP workflow bean can start.
        return beanFactory -> requireSafeRuntime(env, "emitta_staging");
    }
    static void requireSafeRuntime(Environment env, String database) {
        if (!"emitta_staging".equals(database)
                || !"none".equalsIgnoreCase(env.getProperty("spring.main.web-application-type", "")))
            throw new IllegalStateException("Manual preparation requires the dedicated staging database and no HTTP server");
        for (String flag : List.of("fiscal.worker", "outbox.publisher", "processing.jobs", "ride",
                "sri.reception", "sri.authorization")) {
            if (!Boolean.FALSE.equals(env.getProperty("emitta." + flag + ".enabled", Boolean.class)))
                throw new IllegalStateException("Automatic processing and SRI must be explicitly disabled");
        }
        if (!env.getProperty("emitta.signing.enabled", Boolean.class, false)
                || !env.getProperty("emitta.artifacts.storage.enabled", Boolean.class, false))
            throw new IllegalStateException("Signing and durable artifact storage must be enabled for this command");
    }

    @Bean ApplicationRunner prepareOneTestDocument(Environment env, JdbcClient jdbc, DocumentRepository documents,
            ProcessFiscalDocumentUseCase processor, SigningKeyMaterialPort keys, LoadDocumentArtifactUseCase artifacts,
            InvoiceXmlValidatorPort xsd, ProviderPolicyPort providerPolicy, Clock clock, org.springframework.context.ConfigurableApplicationContext context) {
        return args -> {
            requireSafeRuntime(env, jdbc.sql("select current_database()").query(String.class).single());
            UUID tenant = UUID.fromString(env.getRequiredProperty("emitta.manual.tenant-id"));
            UUID id = UUID.fromString(env.getRequiredProperty("emitta.manual.document-id"));
            var document = documents.findById(id).orElseThrow();
            if (!tenant.equals(document.getTenantId()) || document.getEnvironment() != FiscalEnvironment.TEST
                    || (document.getStatus() != DocumentStatus.QUEUED && document.getStatus() != DocumentStatus.SIGNED))
                throw new IllegalStateException("Only this tenant's QUEUED or already SIGNED TEST document is eligible");
            boolean technicalOmission=env.getProperty("emitta.manual.acknowledge-provider-unresolved",Boolean.class,false);
            // Resolve before state changes unless this exact CLI operation explicitly acknowledges the technical test.
            if (document.getStatus() == DocumentStatus.QUEUED && !technicalOmission) providerPolicy.resolve(id).xmlProviderRuc();
            Path report = Path.of(env.getRequiredProperty("emitta.manual.report-file")).toAbsolutePath();
            if (Files.exists(report) || !Files.isDirectory(report.getParent())
                    || !Files.getPosixFilePermissions(report.getParent()).equals(PosixFilePermissions.fromString("rwx------")))
                throw new IllegalStateException("Report requires a private existing directory and a new filename");
            String ruc = jdbc.sql("select ruc from emitta.taxpayers where id=:id and tenant_id=:tenant and status='ACTIVE' and test_enabled=true and production_enabled=false")
                    .param("id", document.getTaxpayerId()).param("tenant", tenant).query(String.class).single();
            if(!ruc.equals(env.getRequiredProperty("emitta.manual.expected-ruc")))
                throw new IllegalStateException("Taxpayer does not match the explicitly approved RUC");
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            Collection<X509Certificate> cas;
            Collection<X509CRL> crls;
            try (var input = Files.newInputStream(Path.of(env.getRequiredProperty("emitta.certificates.administration.trusted-ca-file")))) {
                cas = factory.generateCertificates(input).stream().map(c -> (X509Certificate)c).toList();
            }
            try (var input = Files.newInputStream(Path.of(env.getRequiredProperty("emitta.certificates.administration.crl-file")))) {
                crls = factory.generateCRLs(input).stream().map(c -> (X509CRL)c).toList();
            }
            var resolved = keys.loadForDocument(id, clock.instant());
            try (var material = resolved.keyMaterial()) {
                var validated = Pkcs12CertificateValidator.validate(material, clock.instant(), cas, crls);
                Pkcs12CertificateValidator.requireCertifiedRuc(validated.certificate(), ruc);
                if (document.getStatus() == DocumentStatus.QUEUED) {
                    if(technicalOmission) io.github.imecuadorian.emitta.shared.fiscal.ManualTestXmlScope.run(id,ruc,
                            () -> processor.process(new ProcessFiscalDocumentCommand(id)));
                    else processor.process(new ProcessFiscalDocumentCommand(id));
                }
                document = documents.findById(id).orElseThrow();
                if (document.getStatus() != DocumentStatus.SIGNED)
                    throw new IllegalStateException("Preparation did not reach SIGNED; inspect durable state, never force it");
                var unsigned = artifacts.load(id, DocumentArtifactType.UNSIGNED_XML);
                var signed = artifacts.load(id, DocumentArtifactType.SIGNED_XML);
                xsd.validate(unsigned.content()); xsd.validate(signed.content());
                new JdkSriXadesSignatureVerifier().verify(signed.content(), validated.certificate());
                String result = "documentId=" + id + "\nstatus=SIGNED\nenvironment=TEST\naccessKey=" + document.getAccessKey()
                        + "\ncertificateId=" + resolved.certificateId() + "\nunsignedSha256=" + unsigned.artifact().sha256()
                        + "\nsignedSha256=" + signed.artifact().sha256()
                        + "\nproviderPolicy=" + providerPolicy.resolve(id).mode()
                        + "\ntechnicalOmissionRequested=" + technicalOmission + "\n";
                try (var channel = Files.newByteChannel(report, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
                    channel.write(java.nio.ByteBuffer.wrap(result.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                }
            }
            System.out.println("One TEST document prepared and independently verified. Private report written; no SRI call.");
            org.springframework.boot.SpringApplication.exit(context, () -> 0); context.close();
        };
    }
}
