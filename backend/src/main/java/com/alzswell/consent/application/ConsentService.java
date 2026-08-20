package com.alzswell.consent.application;

import static com.alzswell.consent.api.ConsentErrorCode.*;
import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.security.AuditActor;
import com.alzswell.consent.api.ConsentRequests.*;
import com.alzswell.consent.api.ConsentResponses.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Array;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsentService {
    private static final String POLICY_VERSION="disclosure-policy-v1.1.0";
    private static final Map<String,Set<String>> ALLOWED=Map.of(
        "FINANCIAL_ANALYSIS",Set.of("ACCOUNT_SUMMARY","TRANSACTION_SUMMARY","BASELINE_SIGNAL"),
        "PROTECTION_GUIDANCE",Set.of("BASELINE_SIGNAL","PROTECTION_CASE"),
        "TRUSTED_CONTACT_DISCLOSURE",Set.of("CONTACT_MINIMUM"));
    private final JdbcClient jdbc; private final Clock clock;
    public ConsentService(JdbcClient jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}

    @Transactional public ConsentList active(String customerId,AuditActor actor){
        ensureCustomer(customerId);OffsetDateTime now=OffsetDateTime.now(clock);
        List<Consent> items=jdbc.sql("select c.*,(select array_agg(s.scope_code order by s.scope_code) from customer_consent_scope s where s.consent_id=c.consent_id) scopes from customer_consent c where c.customer_id=:customerId and c.status='GRANTED' and c.expires_at>:now order by c.granted_at desc,c.consent_id").param("customerId",customerId).param("now",now).query(this::map).list();
        audit(customerId,null,"CONSENT_READ",actor,null,hash("active:"+customerId),"READ",now);return new ConsentList(customerId,items,items.size(),now);
    }
    @Transactional(readOnly=true) public Consent detail(String customerId,UUID consentId){return find(customerId,consentId);}
    @Transactional public Consent detailAudited(String customerId,UUID consentId,AuditActor actor){Consent value=find(customerId,consentId);audit(customerId,consentId,"CONSENT_READ",actor,null,hash("detail:"+consentId),"READ",OffsetDateTime.now(clock));return value;}

    @Transactional public Consent grant(String customerId,GrantCommand command,String idempotencyKey,AuditActor actor){
        ensureCustomer(customerId);List<String> scopes=normalize(command.scopes());validateMatrix(command.purposeCode(),scopes);
        String keyHash=hash(idempotencyKey),requestHash=hash(command.purposeCode()+":"+String.join(",",scopes)+":"+command.expiresAt());
        OffsetDateTime now=OffsetDateTime.now(clock);UUID id=UUID.randomUUID();
        int inserted=jdbc.sql("insert into customer_consent(consent_id,customer_id,purpose_code,status,granted_at,expires_at,row_version,created_at,updated_at,idempotency_key_hash,request_hash) values(?,?,?,'GRANTED',?,?,1,?,?,?,?) on conflict (customer_id,idempotency_key_hash) where idempotency_key_hash is not null do nothing").params(id,customerId,command.purposeCode(),now,command.expiresAt(),now,now,keyHash,requestHash).update();
        if(inserted==0)return replay(customerId,keyHash,requestHash);
        scopes.forEach(scope->jdbc.sql("insert into customer_consent_scope values(?,?)").params(id,scope).update());event(id,"GRANTED","GRANTED",scopes,null,actor,now,1);return find(customerId,id);
    }
    @Transactional public Consent withdraw(String customerId,UUID consentId,WithdrawCommand command,AuditActor actor){
        Consent before=find(customerId,consentId);OffsetDateTime now=OffsetDateTime.now(clock);
        int changed=jdbc.sql("update customer_consent set status='WITHDRAWN',withdrawn_at=:now,withdrawal_reason=:reason,row_version=row_version+1,updated_at=:now where customer_id=:customer and consent_id=:consent and status='GRANTED' and row_version=:version and expires_at>:now").param("now",now).param("reason",command.reason()).param("customer",customerId).param("consent",consentId).param("version",command.expectedVersion()).update();
        if(changed==0)throw new BusinessException(CONSENT_STATE_CONFLICT);event(consentId,"WITHDRAWN","WITHDRAWN",before.scopes(),command.reason(),actor,now,before.version()+1);
        List<ContactRow> contacts=jdbc.sql("select contact_id,row_version from trusted_contact where consent_id=? and customer_id=? and status='ACTIVE' for update").params(consentId,customerId).query((rs,n)->new ContactRow(rs.getObject(1,UUID.class),rs.getLong(2))).list();
        for(ContactRow contact:contacts){jdbc.sql("update trusted_contact set status='REVOKED_BY_CONSENT',revoked_at=?,revocation_reason='CONSENT_WITHDRAWN',row_version=row_version+1,updated_at=? where contact_id=?").params(now,now,contact.id()).update();contactEvent(contact.id(),actor,now,contact.version()+1);}
        return find(customerId,consentId);
    }
    @Transactional public ConsentHistory history(String customerId,UUID consentId,AuditActor actor){
        find(customerId,consentId);List<ConsentEvent> items=jdbc.sql("select * from customer_consent_event where consent_id=? order by occurred_at,event_id").param(consentId).query((rs,n)->new ConsentEvent(rs.getObject("event_id",UUID.class),rs.getString("event_type"),rs.getString("status_snapshot"),array(rs.getArray("scope_snapshot")),rs.getString("reason"),rs.getString("actor_id"),rs.getObject("occurred_at",OffsetDateTime.class),rs.getLong("row_version"))).list();
        audit(customerId,consentId,"CONSENT_HISTORY_READ",actor,null,hash("history:"+consentId),"READ",OffsetDateTime.now(clock));return new ConsentHistory(consentId,items,items.size());
    }
    @Transactional public DisclosureEvaluation evaluate(String customerId,DisclosureEvaluationCommand command,AuditActor actor){
        Consent consent=find(customerId,command.consentId());List<String> requested=normalize(command.requestedScopes());validateMatrix(command.purposeCode(),requested);OffsetDateTime now=OffsetDateTime.now(clock);
        List<String> missing=requested.stream().filter(scope->!consent.scopes().contains(scope)).toList();boolean allowed="GRANTED".equals(consent.status())&&consent.expiresAt().isAfter(now)&&consent.purposeCode().equals(command.purposeCode())&&missing.isEmpty();String decision=allowed?"ALLOW_MINIMUM_SCOPE":"DENY_BY_CONSENT";UUID evaluationId=UUID.randomUUID();
        String requestHash=hash(customerId+":"+command.consentId()+":"+command.purposeCode()+":"+String.join(",",requested)+":"+POLICY_VERSION);
        auditEvaluation(customerId,command.consentId(),actor,command.purposeCode(),requested,missing,requestHash,decision,now,evaluationId);
        return new DisclosureEvaluation(evaluationId.toString(),command.consentId(),customerId,command.purposeCode(),requested,missing,consent.status(),decision,POLICY_VERSION,allowed,false,false);
    }
    private Consent find(String customerId,UUID id){return jdbc.sql("select c.*,(select array_agg(s.scope_code order by s.scope_code) from customer_consent_scope s where s.consent_id=c.consent_id) scopes from customer_consent c where c.customer_id=? and c.consent_id=?").params(customerId,id).query(this::map).optional().orElseThrow(()->new BusinessException(CONSENT_NOT_FOUND));}
    private Consent replay(String customerId,String keyHash,String requestHash){Replay replay=jdbc.sql("select consent_id,request_hash from customer_consent where customer_id=? and idempotency_key_hash=?").params(customerId,keyHash).query((rs,n)->new Replay(rs.getObject(1,UUID.class),rs.getString(2))).single();if(!secureEquals(replay.requestHash(),requestHash))throw new BusinessException(IDEMPOTENCY_CONFLICT);return find(customerId,replay.id());}
    private Consent map(java.sql.ResultSet rs,int row)throws java.sql.SQLException{String status=rs.getString("status");OffsetDateTime expires=rs.getObject("expires_at",OffsetDateTime.class);return new Consent(rs.getObject("consent_id",UUID.class),rs.getString("customer_id"),rs.getString("purpose_code"),status,array(rs.getArray("scopes")),rs.getObject("granted_at",OffsetDateTime.class),expires,rs.getObject("withdrawn_at",OffsetDateTime.class),rs.getString("withdrawal_reason"),rs.getLong("row_version"),"GRANTED".equals(status)&&expires.isAfter(OffsetDateTime.now(clock)));}
    private void validateMatrix(String purpose,List<String> scopes){if(!ALLOWED.getOrDefault(purpose,Set.of()).containsAll(scopes))throw new BusinessException(CONSENT_SCOPE_NOT_ALLOWED);}
    private void event(UUID id,String type,String status,List<String> scopes,String reason,AuditActor actor,OffsetDateTime at,long version){jdbc.sql("insert into customer_consent_event(event_id,consent_id,event_type,status_snapshot,scope_snapshot,reason,actor_id,occurred_at,row_version,actor_principal_id,actor_customer_id,actor_session_id,actor_type) values(?,?,?,?,?::varchar[],?,?,?,?,?,?,?,?)").params(UUID.randomUUID(),id,type,status,"{"+String.join(",",scopes)+"}",reason,actor.legacyActorId(),at,version,actor.principalId(),actor.customerId(),actor.sessionId(),actor.actorType()).update();}
    private void contactEvent(UUID id,AuditActor actor,OffsetDateTime at,long version){jdbc.sql("insert into trusted_contact_event(event_id,contact_id,event_type,actor_id,reason,occurred_at,row_version,actor_principal_id,actor_customer_id,actor_session_id,actor_type) values(?,?,'REVOKED_BY_CONSENT',?,'CONSENT_WITHDRAWN',?,?,?,?,?,?)").params(UUID.randomUUID(),id,actor.legacyActorId(),at,version,actor.principalId(),actor.customerId(),actor.sessionId(),actor.actorType()).update();}
    private void audit(String customer,UUID consent,String type,AuditActor actor,String policy,String requestHash,String decision,OffsetDateTime at){audit(customer,consent,type,actor,policy,requestHash,decision,at,UUID.randomUUID());}
    private void audit(String customer,UUID consent,String type,AuditActor actor,String policy,String requestHash,String decision,OffsetDateTime at,UUID id){jdbc.sql("insert into consent_access_audit_event(evaluation_id,customer_id,consent_id,event_type,actor_principal_id,actor_customer_id,actor_session_id,actor_type,policy_version,request_hash,decision,detail,occurred_at) values(?,?,?,?,?,?,?,?,?,?,?,'{}'::jsonb,?)").params(id,customer,consent,type,actor.principalId(),actor.customerId(),actor.sessionId(),actor.actorType(),policy,requestHash,decision,at).update();}
    private void auditEvaluation(String customer,UUID consent,AuditActor actor,String purpose,List<String> requested,List<String> missing,String requestHash,String decision,OffsetDateTime at,UUID id){jdbc.sql("insert into consent_access_audit_event(evaluation_id,customer_id,consent_id,event_type,actor_principal_id,actor_customer_id,actor_session_id,actor_type,policy_version,request_hash,decision,detail,occurred_at) values(?,?,?,'DISCLOSURE_EVALUATED',?,?,?,?,?,?,?,jsonb_build_object('purposeCode',?,'requestedScopes',?::varchar[],'missingScopes',?::varchar[]),?)").params(id,customer,consent,actor.principalId(),actor.customerId(),actor.sessionId(),actor.actorType(),POLICY_VERSION,requestHash,decision,purpose,"{"+String.join(",",requested)+"}","{"+String.join(",",missing)+"}",at).update();}
    private List<String> normalize(List<String> scopes){if(scopes==null||scopes.isEmpty()||scopes.stream().anyMatch(Objects::isNull))throw new BusinessException(CONSENT_SCOPE_NOT_ALLOWED);return scopes.stream().distinct().sorted().toList();} private List<String> array(Array value)throws java.sql.SQLException{return value==null?List.of():List.of((String[])value.getArray());}
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private boolean secureEquals(String left,String right){return MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII),right.getBytes(StandardCharsets.US_ASCII));}
    private void ensureCustomer(String id){if(!jdbc.sql("select exists(select 1 from customer_profile where customer_id=?)").param(id).query(Boolean.class).single())throw new BusinessException(CUSTOMER_NOT_FOUND);}
    private record Replay(UUID id,String requestHash){} private record ContactRow(UUID id,long version){}
}
