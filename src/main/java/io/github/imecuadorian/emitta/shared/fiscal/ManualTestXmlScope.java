package io.github.imecuadorian.emitta.shared.fiscal;
import java.util.*;
import java.util.function.Supplier;

/** One synchronous CLI operation only. Technical omission never represents a regulatory decision. */
public final class ManualTestXmlScope {
    private record Approval(UUID document, String ruc) {}
    private static final ThreadLocal<Approval> APPROVAL = new ThreadLocal<>();
    private ManualTestXmlScope() {}
    public static <T> T run(UUID document, String ruc, Supplier<T> operation) {
        Objects.requireNonNull(document); Objects.requireNonNull(operation);
        if(ruc==null || !ruc.matches("[0-9]{13}") || APPROVAL.get()!=null)
            throw new IllegalArgumentException("A single explicit document/RUC approval is required");
        APPROVAL.set(new Approval(document,ruc));
        try { return operation.get(); } finally { APPROVAL.remove(); }
    }
    public static boolean permitsOmission(UUID document, FiscalEnvironment environment, String ruc) {
        var approval=APPROVAL.get();
        return approval!=null && environment==FiscalEnvironment.TEST
                && approval.document().equals(document) && approval.ruc().equals(ruc);
    }
    public static boolean hasApproval(UUID document) {
        var approval = APPROVAL.get();
        return approval != null && approval.document().equals(document);
    }
}
