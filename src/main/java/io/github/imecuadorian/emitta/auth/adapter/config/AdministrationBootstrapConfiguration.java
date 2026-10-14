package io.github.imecuadorian.emitta.auth.adapter.config;

import io.github.imecuadorian.emitta.auth.application.port.out.ApiClientAdministrationPort;
import io.github.imecuadorian.emitta.tenant.application.command.CreateTenantCommand;
import io.github.imecuadorian.emitta.tenant.application.port.in.CreateTenantUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.*;

/** Explicit, one-time server-side bootstrap; never an unauthenticated HTTP endpoint. */
@Configuration
@Profile("administration-bootstrap")
public class AdministrationBootstrapConfiguration {
    @Bean CommandLineRunner bootstrapAdministrator(CreateTenantUseCase tenants,ApiClientAdministrationPort clients,
            PasswordEncoder encoder,JdbcClient jdbc,PlatformTransactionManager manager,ConfigurableApplicationContext context,
            jakarta.persistence.EntityManagerFactory entityManagerFactory,
            @Value("${emitta.bootstrap.operator-name}") String name,@Value("${emitta.bootstrap.credentials-file}") String output) {
        return arguments -> {
            Path path=Path.of(output).toAbsolutePath();
            if(Files.isSymbolicLink(path.getParent()) || !Files.isDirectory(path.getParent())
                    || !Files.getPosixFilePermissions(path.getParent()).equals(PosixFilePermissions.fromString("rwx------")))
                throw new IllegalStateException("Bootstrap directory must be private (0700)");
            byte[] random=new byte[32]; new SecureRandom().nextBytes(random);
            String secret=Base64.getUrlEncoder().withoutPadding().encodeToString(random); Arrays.fill(random,(byte)0);
            Files.createFile(path,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            try {
                new TransactionTemplate(manager).executeWithoutResult(transaction -> {
                    jdbc.sql("SELECT pg_advisory_xact_lock(1700102026)").query(Object.class).list();
                    if(jdbc.sql("SELECT count(*) FROM emitta.api_client_scopes WHERE scope='platform:admin'").query(Integer.class).single()!=0)
                        throw new IllegalStateException("Platform administrator already registered; bootstrap refused");
                    var tenant=tenants.create(new CreateTenantCommand(name));
                    // The existing tenant use case uses JPA; flush before the shared JDBC adapter reads it.
                    var entityManager=org.springframework.orm.jpa.EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory);
                    if(entityManager==null) throw new IllegalStateException("Bootstrap requires a shared JPA transaction");
                    entityManager.flush();
                    UUID id=UUID.randomUUID(); String clientId="emitta_"+id;
                    clients.create(tenant.getId(),id,clientId,"Platform operator",encoder.encode(secret),Set.of("platform:admin"));
                    try {
                        Files.writeString(path,"tenantId="+tenant.getId()+"\nclientId="+clientId+"\nclientSecret="+secret+"\n");
                    } catch(java.io.IOException failure) { throw new IllegalStateException("Cannot save private bootstrap credentials"); }
                });
            } catch(Exception failure) { Files.deleteIfExists(path); throw failure; }
            SpringApplication.exit(context,()->0);
        };
    }
}
