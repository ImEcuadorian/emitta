package io.github.imecuadorian.emitta.documentsequence.domain;

public final class SequenceExhaustedException
        extends RuntimeException {

    private final SequenceScope scope;

    public SequenceExhaustedException(
            SequenceScope scope
    ) {
        super(
                "Fiscal sequence is exhausted for point of issue "
                        + scope.pointOfIssueId()
                        + ", document type "
                        + scope.documentType()
                        + " and environment "
                        + scope.environment()
        );

        this.scope = scope;
    }

    public SequenceScope getScope() {
        return scope;
    }
}