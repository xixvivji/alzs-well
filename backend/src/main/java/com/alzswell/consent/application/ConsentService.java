package com.alzswell.consent.application;

import static com.alzswell.consent.api.ConsentErrorCode.*;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.consent.api.ConsentRequests.*;
import com.alzswell.consent.api.ConsentResponses.*;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsentService {
    private static final String POLICY_VERSION="disclosure-policy-v1.0.0";
    private final JdbcClient jdbc; private final Clock clock;
    public ConsentService(JdbcClient jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}

    @Transactional(readOnly=true)
    public ConsentList active(String customerId){
        ensureCustomer(customerId); OffsetDateTime now=OffsetDateTime.now(clock);
        List<Consent> items=jdbc.sql("""
                select * from customer_consent where customer_id=:customerId and status='GRANTED'
                  and expires_at>:now order by granted_at desc,consent_id
                """).param("customerId",customerId).param("now",now).query(this::mapConsent).list();
        return new ConsentList(customerId,items,items.size(),now);
    }

    @Transactional(readOnly=true)
    public Consent detail(String customerId,UUID consentId){
        return jdbc.sql("select * from customer_consent where customer_id=? and consent_id=?")
                .param(customerId).param(consentId).query(this::mapConsent).optional()
                .orElseThrow(()->new BusinessException(CONSENT_NOT_FOUND));
    }

    @Transactional
    public Consent grant(String customerId,GrantCommand command,String actorId){
        ensureCustomer(customerId); OffsetDateTime now=OffsetDateTime.now(clock);
        List<String> scopes=normalize(command.scopes()); UUID id=UUID.randomUUID();
        jdbc.sql("""
                insert into customer_consent(consent_id,customer_id,purpose_code,status,granted_at,expires_at,
                    row_version,created_at,updated_at) values(?,?,?,'GRANTED',?,?,1,?,?)
                """).params(id,customerId,command.purposeCode(),now,command.expiresAt(),now,now).update();
        scopes.forEach(scope->jdbc.sql("insert into customer_consent_scope values(?,?)").params(id,scope).update());
        event(id,"GRANTED","GRANTED",scopes,null,actorId,now,1);
        return detail(customerId,id);
    }

    @Transactional
    public Consent withdraw(String customerId,UUID consentId,WithdrawCommand command,String actorId){
        Consent before=detail(customerId,consentId); OffsetDateTime now=OffsetDateTime.now(clock);
        int changed=jdbc.sql("""
                update customer_consent set status='WITHDRAWN',withdrawn_at=:now,withdrawal_reason=:reason,
                    row_version=row_version+1,updated_at=:now
                 where customer_id=:customerId and consent_id=:consentId and status='GRANTED'
                   and row_version=:version and expires_at>:now
                """).param("now",now).param("reason",command.reason()).param("customerId",customerId)
                .param("consentId",consentId).param("version",command.expectedVersion()).update();
        if(changed==0) throw new BusinessException(CONSENT_STATE_CONFLICT);
        event(consentId,"WITHDRAWN","WITHDRAWN",before.scopes(),command.reason(),actorId,now,before.version()+1);
        return detail(customerId,consentId);
    }

    @Transactional(readOnly=true)
    public ConsentHistory history(String customerId,UUID consentId){
        detail(customerId,consentId);
        List<ConsentEvent> items=jdbc.sql("""
                select * from customer_consent_event where consent_id=? order by occurred_at,event_id
                """).param(consentId).query((rs,n)->new ConsentEvent(rs.getObject("event_id",UUID.class),
                        rs.getString("event_type"),rs.getString("status_snapshot"),array(rs.getArray("scope_snapshot")),
                        rs.getString("reason"),rs.getString("actor_id"),
                        rs.getObject("occurred_at",OffsetDateTime.class),rs.getLong("row_version"))).list();
        return new ConsentHistory(consentId,items,items.size());
    }

    @Transactional(readOnly=true)
    public DisclosureEvaluation evaluate(String customerId,DisclosureEvaluationCommand command){
        Consent consent=detail(customerId,command.consentId()); OffsetDateTime now=OffsetDateTime.now(clock);
        List<String> requested=normalize(command.requestedScopes());
        List<String> missing=requested.stream().filter(scope->!consent.scopes().contains(scope)).toList();
        boolean allowed="GRANTED".equals(consent.status()) && consent.expiresAt().isAfter(now)
                && consent.purposeCode().equals(command.purposeCode()) && missing.isEmpty();
        String decision=allowed?"ALLOW_MINIMUM_SCOPE":"DENY_BY_CONSENT";
        String stable=customerId+":"+command.consentId()+":"+command.purposeCode()+":"+String.join(",",requested)
                +":"+POLICY_VERSION;
        return new DisclosureEvaluation(UUID.nameUUIDFromBytes(stable.getBytes(StandardCharsets.UTF_8)).toString(),
                command.consentId(),customerId,command.purposeCode(),requested,missing,consent.status(),decision,
                POLICY_VERSION,allowed,false,false);
    }

    private Consent mapConsent(java.sql.ResultSet rs,int row)throws java.sql.SQLException{
        UUID id=rs.getObject("consent_id",UUID.class);
        List<String> scopes=jdbc.sql("select scope_code from customer_consent_scope where consent_id=? order by scope_code")
                .param(id).query(String.class).list();
        String status=rs.getString("status"); OffsetDateTime expires=rs.getObject("expires_at",OffsetDateTime.class);
        return new Consent(id,rs.getString("customer_id"),rs.getString("purpose_code"),status,scopes,
                rs.getObject("granted_at",OffsetDateTime.class),expires,rs.getObject("withdrawn_at",OffsetDateTime.class),
                rs.getString("withdrawal_reason"),rs.getLong("row_version"),
                "GRANTED".equals(status)&&expires.isAfter(OffsetDateTime.now(clock)));
    }
    private void event(UUID id,String type,String status,List<String> scopes,String reason,String actor,
            OffsetDateTime at,long version){
        jdbc.sql("""
                insert into customer_consent_event(event_id,consent_id,event_type,status_snapshot,scope_snapshot,
                    reason,actor_id,occurred_at,row_version) values(?,?,?,?,?::varchar[],?,?,?,?)
                """).params(UUID.randomUUID(),id,type,status,sqlArray(scopes),reason,actor,at,version).update();
    }
    private String sqlArray(List<String> values){return "{"+String.join(",",values)+"}";}
    private List<String> array(Array value)throws java.sql.SQLException{
        return value==null?List.of():List.of((String[])value.getArray());
    }
    private List<String> normalize(List<String> scopes){return scopes.stream().distinct().sorted().toList();}
    private void ensureCustomer(String customerId){
        boolean exists=jdbc.sql("select exists(select 1 from customer_profile where customer_id=?)")
                .param(customerId).query(Boolean.class).single();
        if(!exists)throw new BusinessException(CUSTOMER_NOT_FOUND);
    }
}
