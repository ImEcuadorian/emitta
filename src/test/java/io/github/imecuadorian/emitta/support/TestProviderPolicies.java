package io.github.imecuadorian.emitta.support;

import io.github.imecuadorian.emitta.shared.fiscal.ProviderPolicy;

public final class TestProviderPolicies {
    public static final String HASH = "511375276ef85f7cce42260d89a10a30bbfa1c12f6a4cf93c4b3a55aa781c821";
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
