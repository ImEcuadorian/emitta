package io.github.imecuadorian.emitta.shared.fiscal;

public enum FiscalEnvironment {

    TEST("1"),
    PRODUCTION("2");

    private final String sriCode;

    FiscalEnvironment(String sriCode) {
        this.sriCode = sriCode;
    }

    public String sriCode() {
        return sriCode;
    }
}