package io.github.imecuadorian.emitta.taxpayer.adapter.in.web;

import io.github.imecuadorian.emitta.establishment.adapter.in.web.CreateEstablishmentRequest;
import io.github.imecuadorian.emitta.pointofissue.adapter.in.web.CreatePointOfIssueRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
public class FiscalConfigurationController {
    private final FiscalConfigurationService service;
    public FiscalConfigurationController(FiscalConfigurationService service){this.service=service;}
    public record Request(@NotNull @Valid CreateTaxpayerRequest taxpayer,
                          @NotNull @Valid CreateEstablishmentRequest establishment,
                          @NotNull @Valid CreatePointOfIssueRequest pointOfIssue) {}
    @PostMapping("/api/v1/fiscal-configuration")
    public ResponseEntity<Map<String,Object>> configure(JwtAuthenticationToken jwt,@Valid @RequestBody Request request){return configureTenant(UUID.fromString(jwt.getToken().getClaimAsString("tenant_id")),request);}
    @PostMapping("/api/v1/tenants/{tenantId}/fiscal-configuration")
    public ResponseEntity<Map<String,Object>> configureTenant(@PathVariable UUID tenantId,@Valid @RequestBody Request request){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.configure(tenantId,request));}
    @GetMapping("/api/v1/fiscal-configuration")
    public ResponseEntity<List<Map<String,Object>>> read(JwtAuthenticationToken jwt,@RequestParam(defaultValue="25") int limit,@RequestParam(defaultValue="0") int offset){return readTenant(UUID.fromString(jwt.getToken().getClaimAsString("tenant_id")),limit,offset);}
    @GetMapping("/api/v1/tenants/{tenantId}/fiscal-configuration")
    public ResponseEntity<List<Map<String,Object>>> readTenant(@PathVariable UUID tenantId,@RequestParam(defaultValue="25") int limit,@RequestParam(defaultValue="0") int offset){if(limit<1||limit>100||offset<0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid pagination");return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.read(tenantId,limit,offset));}
}
