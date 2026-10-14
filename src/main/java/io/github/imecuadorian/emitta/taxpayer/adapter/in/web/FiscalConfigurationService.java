package io.github.imecuadorian.emitta.taxpayer.adapter.in.web;

import io.github.imecuadorian.emitta.taxpayer.application.port.in.CreateTaxpayerUseCase;
import io.github.imecuadorian.emitta.taxpayer.application.command.CreateTaxpayerCommand;
import io.github.imecuadorian.emitta.establishment.application.port.in.CreateEstablishmentUseCase;
import io.github.imecuadorian.emitta.establishment.application.command.CreateEstablishmentCommand;
import io.github.imecuadorian.emitta.pointofissue.application.port.in.CreatePointOfIssueUseCase;
import io.github.imecuadorian.emitta.pointofissue.application.command.CreatePointOfIssueCommand;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

/** Transactional orchestration of existing creation use cases; never allocates fiscal sequences. */
@Service
public class FiscalConfigurationService {
    private final JdbcClient jdbc;
    private final CreateTaxpayerUseCase taxpayers;
    private final CreateEstablishmentUseCase establishments;
    private final CreatePointOfIssueUseCase points;
    public FiscalConfigurationService(JdbcClient jdbc, CreateTaxpayerUseCase taxpayers,
            CreateEstablishmentUseCase establishments, CreatePointOfIssueUseCase points) {
        this.jdbc=jdbc;this.taxpayers=taxpayers;this.establishments=establishments;this.points=points;
    }
    @Transactional
    public Map<String,Object> configure(UUID tenant, FiscalConfigurationController.Request request) {
        // Serializes aggregate onboarding for this tenant, including concurrent repeated requests.
        if (jdbc.sql("select id from emitta.tenants where id=:tenant and status='ACTIVE' for update")
                .param("tenant",tenant).query(UUID.class).optional().isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Active account not found");
        var t=request.taxpayer();var e=request.establishment();var p=request.pointOfIssue();
        var existing=jdbc.sql("select id,legal_name,trade_name,main_address,status,test_enabled,production_enabled from emitta.taxpayers where tenant_id=:tenant and ruc=:ruc")
                .param("tenant",tenant).param("ruc",t.ruc()).query(new org.springframework.jdbc.core.ColumnMapRowMapper()).optional();
        UUID taxpayer;
        if(existing.isEmpty()) taxpayer=taxpayers.create(new CreateTaxpayerCommand(tenant,t.ruc(),t.legalName(),t.tradeName(),t.mainAddress())).getId();
        else {
            var row=existing.get();requireActive(row);
            if(!Boolean.TRUE.equals(row.get("test_enabled")) || Boolean.TRUE.equals(row.get("production_enabled"))) conflict();
            requireEqual(t.legalName(),row.get("legal_name"));requireEqual(t.mainAddress(),row.get("main_address"));
            if(t.tradeName()!=null)requireEqual(t.tradeName(),row.get("trade_name"));
            taxpayer=(UUID)row.get("id");
        }
        var establishment=jdbc.sql("select id,status,name,address from emitta.establishments where taxpayer_id=:id and code=:code")
                .param("id",taxpayer).param("code",e.code()).query(new org.springframework.jdbc.core.ColumnMapRowMapper()).optional();
        UUID establishmentId;
        if(establishment.isEmpty()) establishmentId=establishments.create(new CreateEstablishmentCommand(taxpayer,e.code(),e.name(),e.address())).getId();
        else {var row=establishment.get();requireActive(row);if(e.name()!=null)requireEqual(e.name(),row.get("name"));if(e.address()!=null)requireEqual(e.address(),row.get("address"));establishmentId=(UUID)row.get("id");}
        var point=jdbc.sql("select id,status,name from emitta.points_of_issue where establishment_id=:id and code=:code")
                .param("id",establishmentId).param("code",p.code()).query(new org.springframework.jdbc.core.ColumnMapRowMapper()).optional();
        UUID pointId;
        if(point.isEmpty()) pointId=points.create(new CreatePointOfIssueCommand(establishmentId,p.code(),p.name())).getId();
        else {var row=point.get();requireActive(row);if(p.name()!=null)requireEqual(p.name(),row.get("name"));pointId=(UUID)row.get("id");}
        io.github.imecuadorian.emitta.auth.adapter.out.persistence.AdministrationAuditRecorder.record(jdbc,tenant,pointId,"FISCAL_CONFIGURATION_RESOLVED");
        return Map.of("taxpayerId",taxpayer,"establishmentId",establishmentId,"pointOfIssueId",pointId,"environment","TEST");
    }
    @Transactional
    public List<Map<String,Object>> read(UUID tenant,int limit,int offset) {
        var rows=jdbc.sql("""
                select t.id as "taxpayerId",t.ruc,t.legal_name as "legalName",t.trade_name as "tradeName",
                  t.main_address as "mainAddress",t.status as "taxpayerStatus",t.test_enabled as "testEnabled",
                  t.production_enabled as "productionEnabled",e.id as "establishmentId",e.code as "establishmentCode",
                  e.address as "establishmentAddress",e.status as "establishmentStatus",p.id as "pointOfIssueId",
                  p.code as "pointOfIssueCode",p.status as "pointOfIssueStatus",c.id as "certificateId",
                  c.status as "certificateStatus",c.valid_until as "certificateValidUntil"
                from emitta.taxpayers t left join emitta.establishments e on e.taxpayer_id=t.id
                left join emitta.points_of_issue p on p.establishment_id=e.id
                left join emitta.certificates c on c.id=t.active_signing_certificate_id and c.taxpayer_id=t.id
                where t.tenant_id=:tenant order by t.created_at,t.id,e.code,p.code limit :limit offset :offset
                """).param("tenant",tenant).param("limit",limit).param("offset",offset).query(new org.springframework.jdbc.core.ColumnMapRowMapper()).list();
        for(var row:rows)if(row.get("certificateValidUntil") instanceof java.sql.Timestamp stamp)row.put("certificateValidUntil",stamp.toInstant().toString());
        io.github.imecuadorian.emitta.auth.adapter.out.persistence.AdministrationAuditRecorder.record(jdbc,tenant,tenant,"FISCAL_CONFIGURATION_READ");
        return rows;
    }
    private static void requireActive(Map<String,Object> row){if(!"ACTIVE".equals(row.get("status")))conflict();}
    private static void requireEqual(String input,Object saved){if(!Objects.equals(normalize(input),normalize(saved)))conflict();}
    private static String normalize(Object value){return value==null?"":value.toString().trim();}
    private static void conflict(){throw new ResponseStatusException(HttpStatus.CONFLICT,"Existing fiscal configuration differs or is inactive; no records changed");}
}
