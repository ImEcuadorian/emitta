package io.github.imecuadorian.emitta.shared;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import io.github.imecuadorian.emitta.shared.fiscal.*;
import static org.junit.jupiter.api.Assertions.*;
class ManualTestXmlScopeTest {
    @Test void onlyPermitsExactTestDocumentAndClearsOnFailure() {
        UUID id=UUID.randomUUID();String ruc="0000000000000";
        assertFalse(ManualTestXmlScope.permitsOmission(id,FiscalEnvironment.TEST,ruc));
        assertThrows(IllegalStateException.class,()->ManualTestXmlScope.run(id,ruc,()->{
            assertTrue(ManualTestXmlScope.permitsOmission(id,FiscalEnvironment.TEST,ruc));
            assertFalse(ManualTestXmlScope.permitsOmission(UUID.randomUUID(),FiscalEnvironment.TEST,ruc));
            assertFalse(ManualTestXmlScope.permitsOmission(id,FiscalEnvironment.PRODUCTION,ruc));
            assertFalse(ManualTestXmlScope.permitsOmission(id,FiscalEnvironment.TEST,"1111111111111"));
            throw new IllegalStateException("synthetic failure");
        }));
        assertFalse(ManualTestXmlScope.permitsOmission(id,FiscalEnvironment.TEST,ruc));
        assertThrows(IllegalStateException.class,()->ProviderPolicy.unresolved().xmlProviderRuc());
    }
    @Test void approvalDoesNotPropagateToAnotherThread() throws Exception {
        var id=UUID.randomUUID();var allowed=new java.util.concurrent.atomic.AtomicBoolean(true);
        ManualTestXmlScope.run(id,"0000000000000",()->{
            var thread=new Thread(()->allowed.set(ManualTestXmlScope.permitsOmission(id,FiscalEnvironment.TEST,"0000000000000")));
            thread.start();try{thread.join();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return null;
        });
        assertFalse(allowed.get());
    }
}
