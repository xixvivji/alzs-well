package com.alzswell.compliance.application;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.security.AuditActor;
import com.alzswell.compliance.api.ComplianceErrorCode;
import com.alzswell.compliance.api.ComplianceRequests;
import com.alzswell.compliance.api.ComplianceResponses.AuditExportRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditExportRequestService {
    private final JdbcClient jdbc; private final Clock clock;
    public AuditExportRequestService(JdbcClient jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}

    @Transactional
    public AuditExportRequest create(ComplianceRequests.AuditExportRequest command,String key,AuditActor actor){
        if(!command.from().isBefore(command.to()) || command.to().isAfter(OffsetDateTime.now(clock)))
            throw new BusinessException(ComplianceErrorCode.EXPORT_RANGE_INVALID);
        String sources="{"+String.join(",",command.sourceTypes())+"}";
        String requestHash=hash(command.from()+"|"+command.to()+"|"+sources+"|"+command.purposeCode()+"|"+command.approvalReference());
        UUID id=UUID.randomUUID(); OffsetDateTime now=OffsetDateTime.now(clock); OffsetDateTime expires=now.plusDays(7);
        int inserted=jdbc.sql("""
                insert into audit_export_request(request_id,requested_by,actor_customer_id,actor_session_id,actor_type,
                  from_at,to_at,source_types,purpose_code,approval_reference,status,idempotency_key,request_hash,requested_at,expires_at)
                values(?,?,?,?,?,?,?,?::varchar[],?,?,'PENDING_APPROVAL',?,?,?,?)
                on conflict (coalesce(requested_by, '00000000-0000-0000-0000-000000000000'::uuid), idempotency_key) do nothing
                """).params(id,actor.principalId(),actor.customerId(),actor.sessionId(),actor.actorType(),command.from(),command.to(),sources,
                        command.purposeCode(),command.approvalReference(),key,requestHash,now,expires).update();
        AuditExportRequest result=find(actor.principalId(),key);
        String stored=hashFor(actor.principalId(),key);
        if(!MessageDigest.isEqual(HexFormat.of().parseHex(requestHash),HexFormat.of().parseHex(stored)))
            throw new BusinessException(ComplianceErrorCode.EXPORT_IDEMPOTENCY_CONFLICT);
        if(inserted==1) jdbc.sql("""
                insert into audit_export_request_event(event_id,request_id,event_type,status_snapshot,actor_principal_id,
                  actor_customer_id,actor_session_id,actor_type,detail,occurred_at)
                values(?,?,'REQUEST_CREATED','PENDING_APPROVAL',?,?,?,?,jsonb_build_object('requestHash',?),?)
                """).params(UUID.randomUUID(),id,actor.principalId(),actor.customerId(),actor.sessionId(),actor.actorType(),requestHash,now).update();
        return result;
    }

    private AuditExportRequest find(UUID principal,String key){return jdbc.sql("""
            select request_id,status,from_at,to_at,source_types,purpose_code,approval_reference,requested_at,expires_at
              from audit_export_request where coalesce(requested_by,'00000000-0000-0000-0000-000000000000'::uuid)=coalesce(?,'00000000-0000-0000-0000-000000000000'::uuid) and idempotency_key=?
            """).params(principal,key).query((rs,n)->new AuditExportRequest(rs.getObject("request_id",UUID.class),rs.getString("status"),
                    rs.getObject("from_at",OffsetDateTime.class),rs.getObject("to_at",OffsetDateTime.class),
                    java.util.List.of((String[])rs.getArray("source_types").getArray()),rs.getString("purpose_code"),
                    rs.getString("approval_reference"),rs.getObject("requested_at",OffsetDateTime.class),
                    rs.getObject("expires_at",OffsetDateTime.class),false,false,false)).single();}
    private String hashFor(UUID principal,String key){return jdbc.sql("select request_hash from audit_export_request where coalesce(requested_by,'00000000-0000-0000-0000-000000000000'::uuid)=coalesce(?,'00000000-0000-0000-0000-000000000000'::uuid) and idempotency_key=?").params(principal,key).query(String.class).single();}
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
