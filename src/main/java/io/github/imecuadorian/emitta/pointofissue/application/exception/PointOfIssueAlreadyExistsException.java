package io.github.imecuadorian.emitta.pointofissue.application.exception;

public final class PointOfIssueAlreadyExistsException
        extends RuntimeException {

    private final String code;

    public PointOfIssueAlreadyExistsException(
            String code
    ) {
        super(
                "Point of issue already exists with code: "
                        + code
        );

        this.code = code;
    }

    public String getCode() {
        return code;
    }
}