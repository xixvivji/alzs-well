package com.alzswell.trustedcontact.application;

import static com.alzswell.trustedcontact.api.TrustedContactErrorCode.*;
import com.alzswell.common.exception.BusinessException;
import com.alzswell.trustedcontact.api.TrustedContactRequests.*;
import com.alzswell.trustedcontact.api.TrustedContactResponses.*;
import java.time.*;import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;

@Service
public class TrustedContactService {
    private final JdbcClient jdbc;private final Clock clock;
    public TrustedContactService(JdbcClient jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}
    @Transactional(readOnly=true)
    public ContactList list(String customerId){
        OffsetDateTime now=OffsetDateTime.now(clock);
        List<Contact> items=jdbc.sql("select * from trusted_contact where customer_id=? and status='ACTIVE' and expires_at>? order by created_at,contact_id")
                .params(customerId,now).query(this::map).list();
        return new ContactList(customerId,items,items.size(),false);
    }
    @Transactional(readOnly=true)
    public Contact detail(String customerId,UUID id){
        return jdbc.sql("select * from trusted_contact where customer_id=? and contact_id=?").params(customerId,id)
                .query(this::map).optional().orElseThrow(()->new BusinessException(CONTACT_NOT_FOUND));
    }
    @Transactional
    public Contact create(String customerId,CreateCommand command,String actor){
        OffsetDateTime now=OffsetDateTime.now(clock);ensureConsent(customerId,command.consentId(),command.expiresAt(),now);
        UUID id=UUID.randomUUID();List<String> scopes=normalize(command.scopes());
        jdbc.sql("""
          insert into trusted_contact(contact_id,customer_id,consent_id,display_name,relationship_code,masked_contact,
            recipient_accepted,status,valid_from,expires_at,row_version,created_at,updated_at)
          values(?,?,?,?,?,?,true,'ACTIVE',?,?,1,?,?)
          """).params(id,customerId,command.consentId(),command.displayName(),command.relationshipCode(),
                command.maskedContact(),now,command.expiresAt(),now,now).update();
        replaceScopes(id,scopes);event(id,"CREATED",actor,null,now,1);return detail(customerId,id);
    }
    @Transactional
    public Contact update(String customerId,UUID id,UpdateCommand command,String actor){
        Contact before=detail(customerId,id);OffsetDateTime now=OffsetDateTime.now(clock);
        ensureConsent(customerId,before.consentId(),command.expiresAt(),now);
        int changed=jdbc.sql("""
          update trusted_contact set expires_at=:expires,row_version=row_version+1,updated_at=:now
          where contact_id=:id and customer_id=:customer and status='ACTIVE' and row_version=:version and expires_at>:now
          """).param("expires",command.expiresAt()).param("now",now).param("id",id).param("customer",customerId)
                .param("version",command.expectedVersion()).update();
        if(changed==0)throw new BusinessException(STATE_CONFLICT);
        replaceScopes(id,normalize(command.scopes()));event(id,"UPDATED",actor,null,now,before.version()+1);
        return detail(customerId,id);
    }
    @Transactional
    public Contact revoke(String customerId,UUID id,long expectedVersion,String reason,String actor){
        Contact before=detail(customerId,id);OffsetDateTime now=OffsetDateTime.now(clock);
        int changed=jdbc.sql("""
          update trusted_contact set status='REVOKED',revoked_at=:now,revocation_reason=:reason,
            row_version=row_version+1,updated_at=:now
          where contact_id=:id and customer_id=:customer and status='ACTIVE' and row_version=:version
          """).param("now",now).param("reason",reason).param("id",id).param("customer",customerId)
                .param("version",expectedVersion).update();
        if(changed==0)throw new BusinessException(STATE_CONFLICT);
        event(id,"REVOKED",actor,reason,now,before.version()+1);return detail(customerId,id);
    }
    private void ensureConsent(String customerId,UUID consentId,OffsetDateTime contactExpiry,OffsetDateTime now){
        boolean eligible=jdbc.sql("""
          select exists(select 1 from customer_consent c join customer_consent_scope s on s.consent_id=c.consent_id
            where c.customer_id=:customer and c.consent_id=:consent and c.purpose_code='TRUSTED_CONTACT_DISCLOSURE'
              and c.status='GRANTED' and c.expires_at>=:contactExpiry and c.expires_at>:now and s.scope_code='CONTACT_MINIMUM')
          """).param("customer",customerId).param("consent",consentId).param("contactExpiry",contactExpiry)
                .param("now",now).query(Boolean.class).single();
        if(!eligible)throw new BusinessException(CONSENT_NOT_ELIGIBLE);
    }
    private Contact map(java.sql.ResultSet rs,int row)throws java.sql.SQLException{
        UUID id=rs.getObject("contact_id",UUID.class);
        List<String> scopes=jdbc.sql("select scope_code from trusted_contact_scope where contact_id=? order by scope_code")
                .param(id).query(String.class).list();
        return new Contact(id,rs.getString("customer_id"),rs.getObject("consent_id",UUID.class),rs.getString("display_name"),
                rs.getString("relationship_code"),rs.getString("masked_contact"),rs.getBoolean("recipient_accepted"),
                rs.getString("status"),scopes,rs.getObject("valid_from",OffsetDateTime.class),
                rs.getObject("expires_at",OffsetDateTime.class),rs.getLong("row_version"),false,false);
    }
    private void replaceScopes(UUID id,List<String> scopes){jdbc.sql("delete from trusted_contact_scope where contact_id=?").param(id).update();
        scopes.forEach(scope->jdbc.sql("insert into trusted_contact_scope values(?,?)").params(id,scope).update());}
    private List<String> normalize(List<String> scopes){return scopes.stream().distinct().sorted().toList();}
    private void event(UUID id,String type,String actor,String reason,OffsetDateTime at,long version){
        jdbc.sql("insert into trusted_contact_event values(?,?,?,?,?,?,?)")
                .params(UUID.randomUUID(),id,type,actor,reason,at,version).update();}
}
