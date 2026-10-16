package io.github.imecuadorian.emitta.auth.adapter.in.web;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.sql.Timestamp;
import java.util.Locale;
import java.util.UUID;

@Service
public final class OperatorPersonalAccess {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final String dummyHash;

    public OperatorPersonalAccess(JdbcTemplate jdbc, PasswordEncoder encoder, PlatformTransactionManager manager, Clock clock) {
        this.jdbc = jdbc; this.encoder = encoder; this.transaction = new TransactionTemplate(manager); this.clock = clock;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    public void configure(String clientId, String email, String password) {
        if (password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must contain at least 12 characters and at most 72 UTF-8 bytes");
        String hash = encoder.encode(password);
        transaction.executeWithoutResult(status -> {
            var ids = jdbc.queryForList("""
                SELECT c.id FROM emitta.api_clients c JOIN emitta.tenants t ON t.id=c.tenant_id
                WHERE c.client_id=? AND c.status='ACTIVE' AND t.status='ACTIVE'
                AND EXISTS (SELECT 1 FROM emitta.api_client_scopes s WHERE s.api_client_id=c.id AND s.scope='platform:admin')
                FOR UPDATE OF c
                """, UUID.class, clientId);
            if (ids.size()!=1) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            try {
                jdbc.update("""
                    INSERT INTO emitta.operator_personal_access(api_client_id,email,password_hash) VALUES(?,?,?)
                    ON CONFLICT(api_client_id) DO UPDATE SET email=EXCLUDED.email,password_hash=EXCLUDED.password_hash,
                    failures=0,locked_until=NULL,updated_at=CURRENT_TIMESTAMP
                    """, ids.getFirst(), normalize(email), hash);
            } catch (org.springframework.dao.DuplicateKeyException duplicate) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Identifier unavailable");
            }
        });
    }

    public String authenticate(String email, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) return null;
        return transaction.execute(status -> {
            var rows = jdbc.query("""
                SELECT p.api_client_id,p.password_hash,p.failures,p.locked_until,c.client_id,
                       c.status='ACTIVE' AND t.status='ACTIVE' AND EXISTS (
                         SELECT 1 FROM emitta.api_client_scopes s WHERE s.api_client_id=c.id AND s.scope='platform:admin') AS eligible
                FROM emitta.operator_personal_access p JOIN emitta.api_clients c ON c.id=p.api_client_id
                JOIN emitta.tenants t ON t.id=c.tenant_id WHERE p.email=? FOR UPDATE OF p
                """, (rs,n) -> new Account(rs.getObject(1,UUID.class),rs.getString(2),rs.getInt(3),rs.getTimestamp(4),rs.getString(5),rs.getBoolean(6)), normalize(email));
            if (rows.isEmpty()) { encoder.matches(password,dummyHash); return null; }
            var row=rows.getFirst(); var now=clock.instant();
            boolean matches=encoder.matches(password,row.hash());
            if (!row.eligible() || row.locked()!=null && row.locked().toInstant().isAfter(now)) return null;
            if (!matches) {
                int failures=row.locked()!=null ? 1 : row.failures()+1;
                jdbc.update("UPDATE emitta.operator_personal_access SET failures=?,locked_until=? WHERE api_client_id=?",
                        failures,failures>=5 ? Timestamp.from(now.plusSeconds(900)) : null,row.id());
                return null; // Commit failure counters; do not throw and roll them back.
            }
            jdbc.update("UPDATE emitta.operator_personal_access SET failures=0,locked_until=NULL WHERE api_client_id=?",row.id());
            return row.clientId();
        });
    }
    private static String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private record Account(UUID id,String hash,int failures,Timestamp locked,String clientId,boolean eligible) {}
}
