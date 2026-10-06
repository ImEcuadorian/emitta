package io.github.imecuadorian.emitta.shared.fiscal;

public enum DocumentType {

    INVOICE("01"),
    PURCHASE_SETTLEMENT("03"),
    CREDIT_NOTE("04"),
    DEBIT_NOTE("05"),
    WITHHOLDING("07"),
    DISPATCH_GUIDE("06");

    private final String sriCode;

    DocumentType(String sriCode) {
        this.sriCode = sriCode;
    }

    public String sriCode() {
        return sriCode;
    }
}