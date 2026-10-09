package io.github.imecuadorian.emitta.srireception.adapter.config;

import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.documentartifact.application.port.in.LoadDocumentArtifactUseCase;
import io.github.imecuadorian.emitta.shared.fiscal.ProviderPolicy;
import io.github.imecuadorian.emitta.srireception.application.port.in.*;
import io.github.imecuadorian.emitta.srireception.application.port.out.SriReceptionAttemptPort;
import io.github.imecuadorian.emitta.srireception.application.port.out.SriReceptionPort;
import io.github.imecuadorian.emitta.srireception.application.service.ProviderSubmissionGuard;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ProviderSubmissionConfigurationTest {
    @Test
    void shouldBlockBothSubmissionBeansBeforeStateAttemptArtifactOrSoapIo() {
        var config = new SriReceptionConfiguration();
        var artifacts = mock(LoadDocumentArtifactUseCase.class);
        var guard = new ProviderSubmissionGuard(ignored -> ProviderPolicy.unresolved(), artifacts);
        var mark = mock(MarkDocumentSubmittedUseCase.class);
        var submit = mock(SubmitSignedDocumentToSriUseCase.class);
        var reject = mock(MarkDocumentRejectedUseCase.class);
        var retry = mock(ScheduleDocumentRetryUseCase.class);
        var attempts = mock(SriReceptionAttemptPort.class);
        var documents = mock(DocumentRepository.class);
        var soap = mock(SriReceptionPort.class);
        UUID id = UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> config.submitFiscalDocumentToSriUseCase(
                mark, submit, reject, retry, attempts, Clock.systemUTC(), guard).submit(id));
        assertThrows(IllegalStateException.class, () -> config.submitSignedDocumentToSriUseCase(
                documents, artifacts, soap, guard).submit(id));
        verifyNoInteractions(mark, submit, reject, retry, attempts, documents, soap, artifacts);
    }
}
