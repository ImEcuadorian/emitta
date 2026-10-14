package io.github.imecuadorian.emitta.auth.adapter.out.persistence;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Participates in the caller's transaction. Stores identifiers only, never payloads or secrets. */
public final class AdministrationAuditRecorder {
    private AdministrationAuditRecorder() {}
    public static void record(JdbcClient jdbc, UUID tenant, UUID resource, String action) {
        UUID actor=null;
        if(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
            actor=jdbc.sql("SELECT id FROM emitta.api_clients WHERE client_id=:client")
                    .param("client",jwt.getToken().getSubject()).query(UUID.class).optional().orElse(null);
        }
        jdbc.sql("INSERT INTO emitta.administrative_audit_events(id,tenant_id,actor_client_id,action,resource_id) VALUES(:id,:tenant,:actor,:action,:resource)")
                .param("id",UUID.randomUUID()).param("tenant",tenant).param("actor",actor,java.sql.Types.OTHER)
                .param("action",action).param("resource",resource).update();
    }
}
