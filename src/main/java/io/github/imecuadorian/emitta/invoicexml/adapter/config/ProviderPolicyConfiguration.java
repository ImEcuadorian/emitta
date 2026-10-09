package io.github.imecuadorian.emitta.invoicexml.adapter.config;

import io.github.imecuadorian.emitta.shared.fiscal.ProviderPolicy;
import io.github.imecuadorian.emitta.shared.fiscal.ProviderPolicyPort;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Map;
import java.util.UUID;

@Configuration
@EnableConfigurationProperties(ProviderPolicyConfiguration.Properties.class)
public class ProviderPolicyConfiguration {
    @ConfigurationProperties("emitta.fiscal.provider-policy")
    public record Properties(String defaultProfile, Map<String, ProviderPolicy> profiles,
                             Map<String, String> assignments) {
        public Properties {
            profiles = profiles == null ? Map.of() : Map.copyOf(profiles);
            assignments = assignments == null ? Map.of() : Map.copyOf(assignments);
        }

        public ProviderPolicy forScope(UUID tenant, UUID taxpayer) {
            String name = assignments.getOrDefault(tenant + ":" + taxpayer, defaultProfile);
            if (name == null || name.isBlank()) return ProviderPolicy.unresolved();
            ProviderPolicy policy = profiles.get(name);
            if (policy == null) throw new IllegalStateException("Assigned provider profile does not exist");
            return policy;
        }
    }

    @Bean
    ProviderPolicyPort providerPolicyPort(JdbcClient jdbc, Properties properties) {
        return documentId -> jdbc.sql("""
                SELECT d.tenant_id, d.taxpayer_id, d.point_of_issue_id, d.environment, d.idempotency_key, t.ruc FROM documents d
                JOIN taxpayers t ON t.id=d.taxpayer_id AND t.tenant_id=d.tenant_id
                WHERE d.id=:document
                """).param("document", documentId)
                .query((rs, row) -> {
                    UUID tenant = rs.getObject("tenant_id", UUID.class), taxpayer = rs.getObject("taxpayer_id", UUID.class);
                    return properties.forScope(tenant, taxpayer);
                }).optional()
                .orElseThrow(() -> new IllegalStateException("Document fiscal scope not found"));
    }
}
