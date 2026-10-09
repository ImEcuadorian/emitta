package io.github.imecuadorian.emitta.auth.adapter.config;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Checks both the operation scope and persisted resource ownership before controllers run. */
@Component
public final class TenantAdministrationAccess {
    private final JdbcClient jdbc;

    public TenantAdministrationAccess(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean allowsInvoice(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("SCOPE_invoices:write"))) return false;
        try {
            UUID tenant = UUID.fromString(jwt.getToken().getClaimAsString("tenant_id"));
            return jdbc.sql("SELECT count(*) FROM emitta.tenants WHERE id=:tenant AND status='ACTIVE'")
                    .param("tenant", tenant).query(Integer.class).single() == 1;
        } catch (IllegalArgumentException | NullPointerException invalidContext) { return false; }
    }

    public boolean allows(Authentication authentication, String path, String scope) {
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("SCOPE_" + scope))) {
            return false;
        }
        try {
            UUID tenant = UUID.fromString(jwt.getToken().getClaimAsString("tenant_id"));
            UUID resource = UUID.fromString(path.split("/")[4]);
            String query = switch (scope) {
                case "taxpayers:write" -> "SELECT count(*) FROM emitta.tenants WHERE id=:resource AND id=:tenant AND status='ACTIVE'";
                case "establishments:write" -> "SELECT count(*) FROM emitta.taxpayers t JOIN emitta.tenants n ON n.id=t.tenant_id WHERE t.id=:resource AND t.tenant_id=:tenant AND n.status='ACTIVE'";
                case "points-of-issue:write" -> "SELECT count(*) FROM emitta.establishments e JOIN emitta.taxpayers t ON t.id=e.taxpayer_id JOIN emitta.tenants n ON n.id=t.tenant_id WHERE e.id=:resource AND t.tenant_id=:tenant AND n.status='ACTIVE'";
                default -> throw new IllegalArgumentException("Unsupported administration scope");
            };
            return jdbc.sql(query).param("resource", resource).param("tenant", tenant).query(Integer.class).single() == 1;
        } catch (IllegalArgumentException | NullPointerException invalidContext) {
            return false;
        }
    }
}
