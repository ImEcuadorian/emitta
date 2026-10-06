package io.github.imecuadorian.emitta.establishment.application.exception;

public final class EstablishmentAlreadyExistsException
        extends RuntimeException {

    private final String code;

    public EstablishmentAlreadyExistsException(
            String code
    ) {
        super(
                "Establishment already exists with code: "
                        + code
        );

        this.code = code;
    }

    public String getCode() {
        return code;
    }
}