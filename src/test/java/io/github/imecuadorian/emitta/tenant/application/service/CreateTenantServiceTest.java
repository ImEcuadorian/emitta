package io.github.imecuadorian.emitta.tenant.application.service;

import io.github.imecuadorian.emitta.tenant.application.command.CreateTenantCommand;
import io.github.imecuadorian.emitta.tenant.application.port.out.TenantRepository;
import io.github.imecuadorian.emitta.tenant.domain.Tenant;
import io.github.imecuadorian.emitta.tenant.domain.TenantStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CreateTenantServiceTest {

    private static final UUID TENANT_ID =
            UUID.fromString(
                    "4d34da33-c6e4-4379-8ab0-19fef817cc67"
            );

    private static final Instant NOW =
            Instant.parse("2026-10-04T22:00:00Z");

    @Test
    void shouldCreateAndPersistTenant() {

        InMemoryTenantRepository repository =
                new InMemoryTenantRepository();

        Clock clock = Clock.fixed(
                NOW,
                ZoneOffset.UTC
        );

        CreateTenantService service =
                new CreateTenantService(
                        repository,
                        clock,
                        () -> TENANT_ID
                );

        Tenant tenant = service.create(
                new CreateTenantCommand("NEXORF")
        );

        assertEquals(
                TENANT_ID,
                tenant.getId()
        );

        assertEquals(
                "NEXORF",
                tenant.getName()
        );

        assertEquals(
                TenantStatus.ACTIVE,
                tenant.getStatus()
        );

        assertEquals(
                NOW,
                tenant.getCreatedAt()
        );

        assertEquals(
                NOW,
                tenant.getUpdatedAt()
        );

        Tenant persisted =
                repository
                        .findById(TENANT_ID)
                        .orElseThrow();

        assertEquals(
                TENANT_ID,
                persisted.getId()
        );
    }


    private static final class InMemoryTenantRepository
            implements TenantRepository {

        private final Map<UUID, Tenant> storage =
                new HashMap<>();

        @Override
        public Tenant save(Tenant tenant) {
            storage.put(
                    tenant.getId(),
                    tenant
            );

            return tenant;
        }

        @Override
        public Optional<Tenant> findById(UUID id) {
            return Optional.ofNullable(
                    storage.get(id)
            );
        }

        @Override
        public boolean existsById(UUID id) {
            return storage.containsKey(id);
        }
    }
}