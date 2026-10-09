package io.github.imecuadorian.emitta.invoicexml.adapter.config;

import io.github.imecuadorian.emitta.shared.fiscal.ProviderPolicy;
import io.github.imecuadorian.emitta.support.TestProviderPolicies;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ProviderPolicyConfigurationTest {
    @TempDir Path directory;

    @Test
    void shouldDefaultToUnresolvedDespiteLegacyProviderRucAndBindExactTenantTaxpayerAssignment() {
        UUID tenant = UUID.randomUUID(), taxpayer = UUID.randomUUID();
        new ApplicationContextRunner().withUserConfiguration(ProviderPolicyConfiguration.class)
                .withBean(JdbcClient.class, () -> mock(JdbcClient.class))
                .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                        new org.springframework.core.env.MapPropertySource("scoped-assignment", Map.of(
                                "emitta.fiscal.provider-policy.assignments[" + tenant + ":" + taxpayer + "]", "pilot"))))
                .withPropertyValues("emitta.fiscal.provider-ruc=1799999999001",
                        "emitta.fiscal.provider-policy.profiles.pilot.mode=UNRESOLVED")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    var properties = context.getBean(ProviderPolicyConfiguration.Properties.class);
                    assertEquals("pilot", properties.assignments().get(tenant + ":" + taxpayer));
                    assertThrows(IllegalStateException.class, () -> properties.forScope(tenant, taxpayer).xmlProviderRuc());
                    assertThrows(IllegalStateException.class,
                            () -> properties.forScope(UUID.randomUUID(), taxpayer).xmlProviderRuc());
                });
    }

    @Test
    void shouldSelectDifferentProvidersAndNeverReuseAssignmentAcrossTenantOrTaxpayer() {
        UUID tenant = UUID.randomUUID(), taxpayer = UUID.randomUUID(), otherTenant = UUID.randomUUID();
        var properties = new ProviderPolicyConfiguration.Properties(null,
                Map.of("a", TestProviderPolicies.external("1799999999001"),
                        "b", TestProviderPolicies.external("1790012345001")),
                Map.of(tenant + ":" + taxpayer, "a", otherTenant + ":" + taxpayer, "b"));
        assertEquals("1799999999001", properties.forScope(tenant, taxpayer).xmlProviderRuc());
        assertEquals("1790012345001", properties.forScope(otherTenant, taxpayer).xmlProviderRuc());
        assertThrows(IllegalStateException.class, () -> properties.forScope(tenant, UUID.randomUUID()).xmlProviderRuc());
    }

    @Test
    void shouldRequireRealReviewFileMatchingHashAndOfficialReferenceForOmission() throws Exception {
        var valid = TestProviderPolicies.omission();
        assertNull(valid.xmlProviderRuc());
        Path altered = directory.resolve("review.txt");
        Files.writeString(altered, "altered review");
        assertThrows(IllegalStateException.class, () -> new ProviderPolicy(valid.mode(), null,
                valid.justification(), valid.normativeReference(), altered.toString(), valid.evidenceSha256()).xmlProviderRuc());
        assertThrows(IllegalStateException.class, () -> new ProviderPolicy(valid.mode(), null,
                "Free pilot", "https://example.com/exemption", valid.evidencePath(), valid.evidenceSha256()).xmlProviderRuc());
        assertThrows(IllegalStateException.class, () -> new ProviderPolicy(valid.mode(), null,
                "Free pilot", valid.normativeReference(), directory.resolve("absent").toString(), valid.evidenceSha256()).xmlProviderRuc());
        assertThrows(IllegalArgumentException.class, () -> TestProviderPolicies.external(null));
    }
}
