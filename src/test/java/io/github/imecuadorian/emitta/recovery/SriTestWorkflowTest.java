package io.github.imecuadorian.emitta.recovery;

import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import io.github.imecuadorian.emitta.srireception.application.port.in.SubmitFiscalDocumentToSriUseCase;
import io.github.imecuadorian.emitta.sriauthorization.application.port.in.ReconcileDocumentAuthorizationUseCase;
import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationResult;
import io.github.imecuadorian.emitta.sriauthorization.domain.SriAuthorizationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SriTestWorkflowTest {
    final DocumentRepository documents=mock(DocumentRepository.class);
    final SubmitFiscalDocumentToSriUseCase reception=mock(SubmitFiscalDocumentToSriUseCase.class);
    final ReconcileDocumentAuthorizationUseCase authorization=mock(ReconcileDocumentAuthorizationUseCase.class);
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final UUID tenant=UUID.randomUUID(),id=UUID.randomUUID();
    final Instant cutoff=Instant.parse("2026-10-14T00:00:00Z");
    final SriTestWorkflow workflow=new SriTestWorkflow(documents,reception,authorization,jdbc,Set.of(tenant),cutoff);
    final DurableWorkPort.Job job=new DurableWorkPort.Job(UUID.randomUUID(),tenant,id,DurableWorkPort.Kind.SRI_TEST,"RUNNING",2,UUID.randomUUID(),cutoff,null);

    Document document(FiscalEnvironment environment,UUID account,Instant created,DocumentStatus status) {
        var d=mock(Document.class);when(d.getTenantId()).thenReturn(account);when(d.getEnvironment()).thenReturn(environment);
        when(d.getCreatedAt()).thenReturn(created);when(d.getStatus()).thenReturn(status);when(documents.findById(id)).thenReturn(Optional.of(d));return d;
    }
    @Test void rejectsProductionOtherTenantsAndHistoricalDocumentsBeforeExternalIo() {
        document(FiscalEnvironment.PRODUCTION,tenant,cutoff,DocumentStatus.SIGNED);
        assertThrows(IllegalArgumentException.class,()->workflow.process(job));
        document(FiscalEnvironment.TEST,UUID.randomUUID(),cutoff,DocumentStatus.SIGNED);
        assertThrows(IllegalArgumentException.class,()->workflow.process(job));
        document(FiscalEnvironment.TEST,tenant,cutoff.minusSeconds(1),DocumentStatus.SIGNED);
        assertThrows(IllegalArgumentException.class,()->workflow.process(job));
        verifyNoInteractions(reception,authorization);
    }
    @Test void resumedSubmittedWorkOnlyQueriesAndPendingResponseUsesBoundedDurableRetry() {
        document(FiscalEnvironment.TEST,tenant,cutoff,DocumentStatus.SUBMITTED);
        when(authorization.reconcile(id)).thenReturn(new SriAuthorizationResult(SriAuthorizationStatus.NOT_FOUND,null,null,null,List.of()));
        var pending=assertThrows(RuntimeException.class,()->workflow.process(job));
        assertEquals(WorkFailureClassifier.Category.TRANSIENT,WorkFailureClassifier.classify(pending));
        verifyNoInteractions(reception);verify(authorization).reconcile(id);
    }
    @Test void refusesSignedDocumentWithPriorReceptionAndEmptyEnrollment() {
        assertThrows(IllegalArgumentException.class,()->new SriTestWorkflow(documents,reception,authorization,jdbc,Set.of(),cutoff));
        document(FiscalEnvironment.TEST,tenant,cutoff,DocumentStatus.SIGNED);
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(id))).thenReturn(1);
        assertThrows(IllegalArgumentException.class,()->workflow.process(job));verifyNoInteractions(reception,authorization);
    }
}
