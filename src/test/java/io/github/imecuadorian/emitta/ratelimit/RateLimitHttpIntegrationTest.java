package io.github.imecuadorian.emitta.ratelimit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import io.github.imecuadorian.emitta.support.JwtTestProperties;
import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"emitta.fiscal.worker.enabled=false","emitta.outbox.publisher.enabled=false","emitta.security.rate-limit.policies.ip-authentication.capacity=1","emitta.security.rate-limit.policies.ip-authentication.refill-per-second=0.001","emitta.security.rate-limit.policies.client.capacity=1","emitta.security.rate-limit.policies.client.refill-per-second=0.001"})
@AutoConfigureMockMvc
@Testcontainers
class RateLimitHttpIntegrationTest {
 @Container @ServiceConnection static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:18");
 @Container @ServiceConnection static final RabbitMQContainer RABBIT=new RabbitMQContainer("rabbitmq:4-management-alpine");
 @Container @ServiceConnection(name="redis") static final GenericContainer<?> REDIS=new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);
 @DynamicPropertySource static void props(DynamicPropertyRegistry r){JwtTestProperties.register(r);r.add("emitta.security.rate-limit.enabled",()->"true");}
 @Autowired MockMvc mvc;
 @Autowired JdbcTemplate jdbc;
 @Autowired PasswordEncoder passwords;
 @Autowired tools.jackson.databind.json.JsonMapper json;
 @Autowired org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory redis;
 @MockitoBean DocumentRepository documents;
 @Test void actualSecurityChainLimitsAuthenticationAndTenantClientRequestsAndFailsClosed() throws Exception {
  UUID tenant=UUID.randomUUID(),client=UUID.randomUUID(),id=UUID.randomUUID();String clientId="synthetic-"+client;
  jdbc.update("INSERT INTO emitta.tenants(id,name,status) VALUES (?,'Synthetic traffic tenant','ACTIVE')",tenant);
  jdbc.update("INSERT INTO emitta.api_clients(id,tenant_id,client_id,client_secret_hash,name,status) VALUES (?,?,?,?,?,'ACTIVE')",client,tenant,clientId,passwords.encode("synthetic-only"),clientId);
  jdbc.update("INSERT INTO emitta.api_client_scopes(api_client_id,scope) VALUES (?,'documents:read')",client);
  String credentials="{\"clientId\":\""+clientId+"\",\"clientSecret\":\"synthetic-only\"}";
  String body=mvc.perform(post("/api/v1/auth/token").contentType("application/json").content(credentials)
    .header("X-Forwarded-For","192.0.2.1")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  mvc.perform(post("/api/v1/auth/token").contentType("application/json").content(credentials)
    .header("X-Forwarded-For","192.0.2.2")).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
  String token=json.readTree(body).get("accessToken").asText();var d=mock(Document.class);
  when(d.getTenantId()).thenReturn(tenant);when(d.getId()).thenReturn(id);when(d.getStatus()).thenReturn(DocumentStatus.QUEUED);
  when(d.getEnvironment()).thenReturn(io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment.TEST);when(documents.findById(id)).thenReturn(Optional.of(d));
  mvc.perform(get("/api/v1/documents/"+id).header("Authorization","Bearer "+token)).andExpect(status().isOk());
  mvc.perform(get("/api/v1/documents/"+id).header("Authorization","Bearer "+token)).andExpect(status().isTooManyRequests());
  REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
  try {
   mvc.perform(get("/api/v1/documents/"+id).header("Authorization","Bearer "+token)).andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After","1"));
  }finally{REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();}
  redis.resetConnection();
  // Redis is available again; the original depleted client bucket still limits rather than silently opening access.
  mvc.perform(get("/api/v1/documents/"+id).header("Authorization","Bearer "+token)).andExpect(status().isTooManyRequests());
  verify(documents,times(1)).findById(id);
 }
}
