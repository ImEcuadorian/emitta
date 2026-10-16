package io.github.imecuadorian.emitta.recovery;

import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import io.github.imecuadorian.emitta.srireception.application.exception.SriReceptionException;
import io.github.imecuadorian.emitta.srireception.application.port.in.SubmitFiscalDocumentToSriUseCase;
import io.github.imecuadorian.emitta.sriauthorization.application.port.in.ReconcileDocumentAuthorizationUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Explicitly enrolled, new TEST documents only. Reception is never replayed after an uncertain result. */
@Component
@ConditionalOnProperty(name="emitta.sri.automatic-test.enabled",havingValue="true")
public final class SriTestWorkflow {
    private final DocumentRepository documents;
    private final SubmitFiscalDocumentToSriUseCase reception;
    private final ReconcileDocumentAuthorizationUseCase authorization;
    private final JdbcTemplate jdbc;
    private final Set<UUID> tenants;
    private final Instant notBefore;

    public SriTestWorkflow(DocumentRepository documents,SubmitFiscalDocumentToSriUseCase reception,
            ReconcileDocumentAuthorizationUseCase authorization,JdbcTemplate jdbc,
            @Value("${emitta.sri.automatic-test.tenant-ids}") Set<UUID> tenants,
            @Value("${emitta.sri.automatic-test.not-before}") Instant notBefore) {
        if(tenants==null||tenants.isEmpty()||notBefore==null)throw new IllegalArgumentException("Explicit TEST enrollment and cutoff required");
        this.documents=documents;this.reception=reception;this.authorization=authorization;this.jdbc=jdbc;
        this.tenants=Set.copyOf(tenants);this.notBefore=notBefore;
    }
    public void discover() {
        for(UUID tenant:tenants)jdbc.update("""
            INSERT INTO emitta.processing_jobs(tenant_id,document_id,kind)
            SELECT tenant_id,id,'SRI_TEST' FROM emitta.documents d
            WHERE tenant_id=? AND environment='TEST' AND created_at>=? AND status IN ('SIGNED','SUBMITTED')
              AND NOT EXISTS(SELECT 1 FROM emitta.processing_jobs j WHERE j.document_id=d.id AND j.kind='SRI_TEST')
            ORDER BY created_at LIMIT 50 ON CONFLICT(document_id,kind) DO NOTHING
            """,tenant,java.sql.Timestamp.from(notBefore));
    }
    public void process(DurableWorkPort.Job job) {
        var document=documents.findById(job.documentId()).orElseThrow(()->new IllegalArgumentException("Document unavailable"));
        if(!tenants.contains(job.tenantId())||!document.getTenantId().equals(job.tenantId())
                ||document.getEnvironment()!=FiscalEnvironment.TEST||document.getCreatedAt().isBefore(notBefore))
            throw new IllegalArgumentException("Document outside explicit TEST enrollment");
        if(document.getStatus()==DocumentStatus.AUTHORIZED)return;
        if(document.getStatus()==DocumentStatus.SIGNED) {
            int attempts=jdbc.queryForObject("SELECT count(*) FROM emitta.document_attempts WHERE operation='SRI_SUBMISSION' AND document_id=?",Integer.class,job.documentId());
            if(attempts!=0)throw new IllegalArgumentException("Existing reception requires reconciliation review");
            // Existing use case enforces provider policy and persists SUBMITTED before any external I/O.
            try {reception.submit(job.documentId());}
            catch(SriReceptionException unknown) { /* Only a query by the original access key follows. */ }
        } else if(document.getStatus()!=DocumentStatus.SUBMITTED) {
            throw new IllegalArgumentException("Fiscal state requires review");
        }
        var current=documents.findById(job.documentId()).orElseThrow();
        if(current.getStatus()!=DocumentStatus.SUBMITTED)throw new IllegalArgumentException("Reception did not admit document");
        var result=authorization.reconcile(job.documentId());
        switch(result.status()) {
            case AUTHORIZED -> { }
            case NOT_FOUND -> throw new org.springframework.dao.TransientDataAccessResourceException("Authorization pending; query only on next attempt");
            default -> throw new IllegalArgumentException("Definitive authorization response requires review");
        }
    }
}
