package com.alzswell.privacy.application;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.security.AuditActor;
import com.alzswell.privacy.api.PrivacyErrorCode;
import com.alzswell.privacy.api.PrivacyResponses.PrivacyRequest;
import com.alzswell.privacy.api.PrivacyResponses.RetentionPolicy;
import com.alzswell.privacy.api.PrivacyResponses.RetentionPolicyList;
import com.alzswell.staffaccess.application.StaffAccessPolicyService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrivacyRequestService {
    private final JdbcClient jdbc; private final Clock clock; private final StaffAccessPolicyService staffAccess;
    public PrivacyRequestService(JdbcClient jdbc,Clock clock,StaffAccessPolicyService staffAccess){this.jdbc=jdbc;this.clock=clock;this.staffAccess=staffAccess;}

    @Transactional(readOnly=true)
    public RetentionPolicyList retentionPolicies(){
        List<RetentionPolicy> items=jdbc.sql("select * from compliance_retention_policy order by policy_code")
                .query((rs,n)->new RetentionPolicy(rs.getString("policy_code"),rs.getString("resource_type"),
                        rs.getInt("retention_days"),rs.getString("legal_basis"),rs.getString("disposal_method"),
                        rs.getObject("effective_from",java.time.LocalDate.class),rs.getLong("version"))).list();
        return new RetentionPolicyList(items,false);
    }

    @Transactional
    public PrivacyRequest create(String customerId,String type,String targetType,String targetReference,
            String reasonCode,String correctedValue,String idempotencyKey,AuditActor actor){
        Boolean customerExists=jdbc.sql("select exists(select 1 from customer_profile where customer_id=?)")
                .param(customerId).query(Boolean.class).single();
        if(!Boolean.TRUE.equals(customerExists)) throw new BusinessException(PrivacyErrorCode.CUSTOMER_NOT_FOUND);
        staffAccess.require(actor,customerId,"PRIVACY_REQUEST_WRITE","PRIVACY_REQUEST",type);
        String requestHash=hash(String.join("|",type,targetType,String.valueOf(targetReference),reasonCode,String.valueOf(correctedValue)));
        OffsetDateTime now=OffsetDateTime.now(clock); UUID id=UUID.randomUUID();
        int inserted=jdbc.sql("""
                insert into customer_privacy_request(request_id,customer_id,request_type,target_type,target_reference,
                  reason_code,correction_value,status,legal_exception_code,idempotency_key,request_hash,requested_at,
                  actor_principal_id,actor_customer_id,actor_session_id,actor_type)
                values(?,?,?,?,?,?,?,'LEGAL_HOLD_REVIEW','RETENTION_POLICY_REVIEW_REQUIRED',?,?,?,?,?,?,?)
                on conflict(customer_id,request_type,idempotency_key) do nothing
                """).params(id,customerId,type,targetType,targetReference,reasonCode,correctedValue,idempotencyKey,
                        requestHash,now,actor.principalId(),actor.customerId(),actor.sessionId(),actor.actorType()).update();
        PrivacyRequest result=find(customerId,type,idempotencyKey);
        if(!sameHash(requestHash,hashFor(customerId,type,idempotencyKey))) throw new BusinessException(PrivacyErrorCode.IDEMPOTENCY_CONFLICT);
        if(inserted==1) jdbc.sql("""
                insert into customer_privacy_request_event(event_id,request_id,event_type,status_snapshot,detail,
                  occurred_at,actor_principal_id,actor_customer_id,actor_session_id,actor_type)
                values(?,?,'REQUEST_RECEIVED','LEGAL_HOLD_REVIEW',jsonb_build_object('requestHash',?),?,?,?,?,?)
                """).params(UUID.randomUUID(),id,requestHash,now,actor.principalId(),actor.customerId(),actor.sessionId(),actor.actorType()).update();
        return result;
    }

    private PrivacyRequest find(String customer,String type,String key){return jdbc.sql("""
            select request_id,customer_id,request_type,target_type,target_reference,reason_code,status,
                   legal_exception_code,requested_at from customer_privacy_request
             where customer_id=? and request_type=? and idempotency_key=?
            """).params(customer,type,key).query((rs,n)->new PrivacyRequest(rs.getObject("request_id",UUID.class),
                    rs.getString("customer_id"),rs.getString("request_type"),rs.getString("target_type"),
                    rs.getString("target_reference"),rs.getString("reason_code"),rs.getString("status"),
                    rs.getString("legal_exception_code"),rs.getObject("requested_at",OffsetDateTime.class),false,false)).single();}
    private String hashFor(String customer,String type,String key){return jdbc.sql("select request_hash from customer_privacy_request where customer_id=? and request_type=? and idempotency_key=?").params(customer,type,key).query(String.class).single();}
    private static boolean sameHash(String first,String second){
        return MessageDigest.isEqual(HexFormat.of().parseHex(first),HexFormat.of().parseHex(second));
    }
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
