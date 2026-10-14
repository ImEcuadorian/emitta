package io.github.imecuadorian.emitta.sriauthorization.adapter.config;

import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.documentartifact.application.model.LoadedDocumentArtifact;
import io.github.imecuadorian.emitta.documentartifact.application.port.in.LoadDocumentArtifactUseCase;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifact;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.out.XmlSignatureVerifierPort;
import io.github.imecuadorian.emitta.ride.application.port.in.GenerateAndStoreInvoiceRideUseCase;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationResult;
import io.github.imecuadorian.emitta.sriauthorization.application.port.in.*;
import io.github.imecuadorian.emitta.sriauthorization.application.port.out.SriAuthorizationEvidencePort;
import io.github.imecuadorian.emitta.sriauthorization.domain.SriAuthorizationStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SriAuthorizationWorkflowConfigurationTest {
    @Test
    @SuppressWarnings("unchecked")
    void enqueueFailureKeepsCompletedAuthorizationAndDoesNotRepeatQuery() {
        UUID id=UUID.randomUUID(), tenant=UUID.randomUUID();
        String key="1".repeat(49);
        var result=new SriAuthorizationResult(SriAuthorizationStatus.AUTHORIZED,key,Instant.now(),"synthetic",List.of());
        var query=mock(QueryDocumentAuthorizationUseCase.class);
        var load=mock(LoadDocumentArtifactUseCase.class);
        var verify=mock(VerifyAuthorizedXmlUseCase.class);
        var store=mock(StoreAuthorizedDocumentXmlUseCase.class);
        var signature=mock(XmlSignatureVerifierPort.class);
        var mark=mock(MarkDocumentAuthorizedUseCase.class);
        var documents=mock(DocumentRepository.class);
        var evidence=mock(SriAuthorizationEvidencePort.class);
        var ride=mock(GenerateAndStoreInvoiceRideUseCase.class);
        ObjectProvider<GenerateAndStoreInvoiceRideUseCase> provider=mock(ObjectProvider.class);
        var artifact=new DocumentArtifact(UUID.randomUUID(),id,DocumentArtifactType.AUTHORIZED_XML,
                "application/xml","synthetic-test-object","a".repeat(64),1,Instant.now());
        var document=mock(Document.class);
        when(query.query(id)).thenReturn(result);
        when(load.load(id,DocumentArtifactType.SIGNED_XML)).thenReturn(new LoadedDocumentArtifact(artifact,new byte[]{1}));
        when(store.store(id,result)).thenReturn(artifact);
        when(documents.findById(id)).thenReturn(Optional.of(document));
        when(document.getEnvironment()).thenReturn(FiscalEnvironment.TEST);
        when(document.getTenantId()).thenReturn(tenant);
        when(evidence.save(any())).thenAnswer(call -> call.getArgument(0));
        when(provider.getIfAvailable()).thenReturn(ride);
        var jobs=mock(io.github.imecuadorian.emitta.recovery.DurableWorkPort.class);
        doThrow(new IllegalStateException("synthetic database failure")).when(jobs)
                .enqueue(io.github.imecuadorian.emitta.recovery.DurableWorkPort.Kind.RIDE,tenant,id);
        var metrics=new SimpleMeterRegistry();
        try {
            var workflow=new SriAuthorizationWorkflowConfiguration().reconcileDocumentAuthorizationUseCase(
                    query,load,verify,store,signature,mark,documents,evidence,provider,metrics,jobs);
            assertSame(result,workflow.reconcile(id));
            var order=inOrder(mark,jobs);
            order.verify(mark).markAuthorized(id);
            order.verify(jobs).enqueue(io.github.imecuadorian.emitta.recovery.DurableWorkPort.Kind.RIDE,tenant,id);
            verifyNoInteractions(ride);
            verify(query,times(1)).query(id);
            assertEquals(1.0,metrics.get("emitta.ride.enqueue").tag("result","failure").counter().count());
        } finally {
            metrics.close();
        }
    }
}
