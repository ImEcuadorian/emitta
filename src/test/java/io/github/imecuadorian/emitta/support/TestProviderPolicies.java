package io.github.imecuadorian.emitta.support;

import io.github.imecuadorian.emitta.shared.fiscal.ProviderPolicy;

public final class TestProviderPolicies {
    public static final String HASH = "af040e9d52eaa1545b50ade55f5c3752450a036235850fd7dd17c643d258f53a";
    public static final String EVIDENCE = "src/test/resources/fiscal/provider-policy-test-evidence.txt";
    public static final String REFERENCE = "https://www.sri.gob.ec/facturacion-electronica";

    public static ProviderPolicy external(String ruc) {
        return new ProviderPolicy(ProviderPolicy.Mode.EXTERNAL_PROVIDER, ruc,
                "Synthetic test fixture only", REFERENCE, EVIDENCE, HASH);
    }

    public static ProviderPolicy omission() {
        return new ProviderPolicy(ProviderPolicy.Mode.VERIFIED_NOT_APPLICABLE, null,
                "Synthetic omission fixture only; not a Synthetic issuer regulatory decision", REFERENCE, EVIDENCE, HASH);
    }
}
