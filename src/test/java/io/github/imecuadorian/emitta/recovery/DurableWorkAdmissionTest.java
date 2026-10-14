package io.github.imecuadorian.emitta.recovery;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.fiscalprocessing.application.port.in.ProcessFiscalDocumentUseCase;
import io.github.imecuadorian.emitta.ride.application.port.in.GenerateAndStoreInvoiceRideUseCase;
import org.springframework.beans.factory.ObjectProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DurableWorkAdmissionTest {
    @Test void slowWorkDoesNotReserveFollowingJobsAndTransientFailureStopsThePoll() {
        var jobs=mock(PostgreSqlDurableWorkAdapter.class);
        var documents=mock(DocumentRepository.class);
        var fiscal=mock(ProcessFiscalDocumentUseCase.class);
        @SuppressWarnings("unchecked") ObjectProvider<GenerateAndStoreInvoiceRideUseCase> provider=mock(ObjectProvider.class);
        var ride=mock(GenerateAndStoreInvoiceRideUseCase.class);when(provider.getObject()).thenReturn(ride);
        var policy=new ProcessingPolicy(3,Duration.ofSeconds(1),Duration.ofMillis(1),Duration.ofSeconds(1),5);
        var job=new DurableWorkPort.Job(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),DurableWorkPort.Kind.RIDE,"RUNNING",1,UUID.randomUUID(),Instant.now(),null);
        var document=mock(Document.class);when(document.getTenantId()).thenReturn(job.tenantId());when(document.getStatus()).thenReturn(DocumentStatus.AUTHORIZED);
        when(documents.findById(job.documentId())).thenReturn(Optional.of(document));
        when(jobs.claim(DurableWorkPort.Kind.RIDE,policy,1)).thenReturn(List.of(job));
        when(jobs.withLock(eq(job),any())).thenAnswer(invocation->{
            verify(jobs,times(1)).claim(DurableWorkPort.Kind.RIDE,policy,1);
            ((Runnable)invocation.getArgument(1)).run();return true;
        });
        when(ride.generate(job.tenantId(),job.documentId())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic"));
        var runner=new DurableWorkRunner(jobs,policy,documents,fiscal,provider,new SimpleMeterRegistry());
        assertEquals(0,runner.run(DurableWorkPort.Kind.RIDE));
        verify(jobs,times(1)).claim(DurableWorkPort.Kind.RIDE,policy,1);
        verify(jobs).failed(job,WorkFailureClassifier.Category.TRANSIENT,policy);
        verifyNoInteractions(fiscal);
    }
}
