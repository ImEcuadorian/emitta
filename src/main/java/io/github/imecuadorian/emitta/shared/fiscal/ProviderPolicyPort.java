package io.github.imecuadorian.emitta.shared.fiscal;

import java.util.UUID;

@FunctionalInterface
public interface ProviderPolicyPort {
    ProviderPolicy resolve(UUID documentId);
}
