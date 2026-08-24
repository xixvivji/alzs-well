package com.alzswell.intent.application;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.security.AuditActor;
import com.alzswell.common.security.SensitiveTextPolicy;
import com.alzswell.intent.api.FinancialIntentErrorCode;
import com.alzswell.intent.api.FinancialIntentRequests.*;
import com.alzswell.intent.api.FinancialIntentResponses.*;
import com.alzswell.staffaccess.application.StaffAccessPolicyService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialIntentService {
 private final JdbcClient jdbc; private final Clock clock; private final StaffAccessPolicyService staffAccess; private final SensitiveTextPolicy sensitiveTextPolicy;
 public FinancialIntentService(JdbcClient jdbc,Clock clock,StaffAccessPolicyService staffAccess,SensitiveTextPolicy sensitiveTextPolicy){this.jdbc=jdbc;this.clock=clock;this.staffAccess=staffAccess;this.sensitiveTextPolicy=sensitiveTextPolicy;}

 @Transactional(readOnly=true) public Preparation preparation(String customer){
  List<Intent> rows=jdbc.sql("select * from financial_intent where customer_id=? and status='APPROVED'").param(customer).query(this::map).list();
  return new Preparation(rows.isEmpty()?"NOT_PREPARED":"READY",rows.isEmpty()?null:rows.getFirst(),true);
 }
 @Transactional public Intent create(String customer,Draft c,String key,AuditActor actor){
  String requestHash=hash(c.paymentContinuity()+"|"+c.explanationMode()+"|"+c.helpCondition()+"|"+String.join(",",c.shareScopes().stream().sorted().toList())); return command(customer,customer+":CREATE",customer+":CREATE",key,requestHash,hash(c.toString()),()->{
   OffsetDateTime now=OffsetDateTime.now(clock);UUID id=UUID.randomUUID();
   int count=jdbc.sql("insert into financial_intent(intent_id,customer_id,status,version,payment_continuity,explanation_mode,help_condition,share_scopes,disclaimer_accepted,created_at,updated_at) values(?,?,'DRAFT',1,?,?,?,?::varchar[],false,?,?) on conflict do nothing")
    .params(id,customer,c.paymentContinuity(),c.explanationMode(),c.helpCondition(),array(c.shareScopes()),now,now).update();
   if(count!=1)throw new BusinessException(FinancialIntentErrorCode.INVALID_STATE);
   snapshot(id,1,"DRAFT",c.paymentContinuity(),c.explanationMode(),c.helpCondition(),c.shareScopes(),false,now);event(id,"DRAFT_CREATED","DRAFT",1,actor,null,now);return find(customer,id);
  });
 }
 @Transactional public Intent update(String customer,UUID id,Update c,String key,AuditActor actor){
  return command(customer,customer+":"+id+":UPDATE",id+":UPDATE",key,hash(c.paymentContinuity()+"|"+c.explanationMode()+"|"+c.helpCondition()+"|"+String.join(",",c.shareScopes().stream().sorted().toList())+"|"+c.expectedVersion()),hash(c.toString()),()->{
   Intent old=find(customer,id);if(!old.status().equals("DRAFT"))throw new BusinessException(FinancialIntentErrorCode.INVALID_STATE);
   long next=c.expectedVersion()+1;OffsetDateTime now=OffsetDateTime.now(clock);
   int n=jdbc.sql("update financial_intent set version=?,payment_continuity=?,explanation_mode=?,help_condition=?,share_scopes=?::varchar[],updated_at=? where intent_id=? and customer_id=? and status='DRAFT' and version=?")
    .params(next,c.paymentContinuity(),c.explanationMode(),c.helpCondition(),array(c.shareScopes()),now,id,customer,c.expectedVersion()).update();
   if(n!=1)throw new BusinessException(FinancialIntentErrorCode.VERSION_CONFLICT);
   snapshot(id,next,"DRAFT",c.paymentContinuity(),c.explanationMode(),c.helpCondition(),c.shareScopes(),false,now);event(id,"DRAFT_UPDATED","DRAFT",next,actor,null,now);return find(customer,id);
  });
 }
 @Transactional public Intent approve(String customer,UUID id,Approve c,String key,AuditActor actor){
  return command(customer,customer+":"+id+":APPROVE",id+":APPROVE",key,hash("APPROVE|"+c.expectedVersion()),hash(c.toString()),()->transition(customer,id,c.expectedVersion(),"DRAFT","APPROVED","APPROVED",null,actor));
 }
 @Transactional public Intent revoke(String customer,UUID id,Revoke c,String key,AuditActor actor){
  String safeReason=sensitiveTextPolicy.validate(c.reason(),"철회 사유");
  return command(customer,customer+":"+id+":REVOKE",id+":REVOKE",key,hash("REVOKE|"+c.expectedVersion()+"|"+safeReason),hash(c.toString()),()->transition(customer,id,c.expectedVersion(),"APPROVED","REVOKED","REVOKED",safeReason,actor));
 }
 @Transactional(readOnly=true) public Versions versions(String customer){List<Intent> items=jdbc.sql("select f.customer_id,r.*,f.created_at,f.approved_at,f.revoked_at from financial_intent_revision r join financial_intent f using(intent_id) where f.customer_id=? order by r.recorded_at desc").param(customer).query(this::mapRevision).list();return new Versions(items,items.size());}
 @Transactional public StaffSummary staff(String customer,AuditActor actor){staffAccess.require(actor,customer,"FINANCIAL_INTENT_REVIEW","FINANCIAL_INTENT_READ","FINANCIAL_INTENT",null);Intent i=preparation(customer).latestApproved();if(i==null)throw new BusinessException(FinancialIntentErrorCode.NOT_FOUND);List<String>s=i.shareScopes();return new StaffSummary(i.intentId(),customer,i.version(),s.contains("PAYMENT_PREFERENCE")?i.paymentContinuity():null,s.contains("EXPLANATION_PREFERENCE")?i.explanationMode():null,s.contains("HELP_CONDITION")?i.helpCondition():null,s,false,true);}

 private Intent transition(String customer,UUID id,long expected,String from,String to,String event,String reason,AuditActor actor){if(to.equals("APPROVED")){jdbc.sql("select 1 from (select pg_advisory_xact_lock(hashtextextended(?,0))) locked").param("financial-intent-approval:"+customer).query(Integer.class).single();Boolean exists=jdbc.sql("select exists(select 1 from financial_intent where customer_id=? and status='APPROVED' and intent_id<>?)").params(customer,id).query(Boolean.class).single();if(Boolean.TRUE.equals(exists))throw new BusinessException(FinancialIntentErrorCode.APPROVED_ALREADY_EXISTS);}Intent old=find(customer,id);if(!old.status().equals(from))throw new BusinessException(FinancialIntentErrorCode.INVALID_STATE);long next=expected+1;OffsetDateTime now=OffsetDateTime.now(clock);String timeColumn=to.equals("APPROVED")?"approved_at":"revoked_at";int n=jdbc.sql("update financial_intent set status=?,version=?,disclaimer_accepted=case when ?='APPROVED' then true else disclaimer_accepted end,"+timeColumn+"=?,updated_at=? where intent_id=? and customer_id=? and status=? and version=?").params(to,next,to,now,now,id,customer,from,expected).update();if(n!=1)throw new BusinessException(FinancialIntentErrorCode.VERSION_CONFLICT);Intent value=find(customer,id);snapshot(id,next,to,value.paymentContinuity(),value.explanationMode(),value.helpCondition(),value.shareScopes(),value.disclaimerAccepted(),now);event(id,event,to,next,actor,reason,now);return value;}
 private Intent command(String customer,String scope,String legacyScope,String key,String requestHash,String legacyRequestHash,Supplier<Intent> action){String legacyKeyHash=jdbc.sql("select md5(?) || md5('alzs-well:' || ?)").params(key,key).query(String.class).single();var legacy=findCommand(legacyScope,legacyKeyHash);if(legacy!=null)return replayCommand(customer,legacy,legacyRequestHash);String keyHash=hash(key);int inserted=jdbc.sql("insert into financial_intent_command(command_scope,idempotency_key_hash,request_hash,created_at) values(?,?,?,?) on conflict do nothing").params(scope,keyHash,requestHash,OffsetDateTime.now(clock)).update();if(inserted==0)return replayCommand(customer,findCommand(scope,keyHash),requestHash);Intent result=action.get();jdbc.sql("update financial_intent_command set result_intent_id=?,result_version=? where command_scope=? and idempotency_key_hash=?").params(result.intentId(),result.version(),scope,keyHash).update();return result;}
 private CommandReplay findCommand(String scope,String keyHash){return jdbc.sql("select request_hash,result_intent_id,result_version from financial_intent_command where command_scope=? and idempotency_key_hash=?").params(scope,keyHash).query((r,n)->new CommandReplay(r.getString(1),r.getObject(2,UUID.class),r.getLong(3))).optional().orElse(null);}
 private Intent replayCommand(String customer,CommandReplay row,String expectedHash){if(row==null||!same(expectedHash,row.requestHash()))throw new BusinessException(FinancialIntentErrorCode.IDEMPOTENCY_CONFLICT);return jdbc.sql("select f.customer_id,r.*,f.created_at,f.approved_at,f.revoked_at from financial_intent_revision r join financial_intent f using(intent_id) where f.customer_id=? and r.intent_id=? and r.version=?").params(customer,row.intentId(),row.version()).query(this::mapRevision).optional().orElseThrow(()->new BusinessException(FinancialIntentErrorCode.NOT_FOUND));}
 private Intent find(String customer,UUID id){return jdbc.sql("select * from financial_intent where customer_id=? and intent_id=?").params(customer,id).query(this::map).optional().orElseThrow(()->new BusinessException(FinancialIntentErrorCode.NOT_FOUND));}
 private Intent map(java.sql.ResultSet r,int n)throws java.sql.SQLException{return new Intent(r.getObject("intent_id",UUID.class),r.getString("customer_id"),r.getString("status"),r.getLong("version"),r.getString("payment_continuity"),r.getString("explanation_mode"),r.getString("help_condition"),List.of((String[])r.getArray("share_scopes").getArray()),r.getBoolean("disclaimer_accepted"),r.getObject("created_at",OffsetDateTime.class),r.getObject("updated_at",OffsetDateTime.class),r.getObject("approved_at",OffsetDateTime.class),r.getObject("revoked_at",OffsetDateTime.class),false,false);}
 private Intent mapRevision(java.sql.ResultSet r,int n)throws java.sql.SQLException{String status=r.getString("status");OffsetDateTime recorded=r.getObject("recorded_at",OffsetDateTime.class);return new Intent(r.getObject("intent_id",UUID.class),r.getString("customer_id"),status,r.getLong("version"),r.getString("payment_continuity"),r.getString("explanation_mode"),r.getString("help_condition"),List.of((String[])r.getArray("share_scopes").getArray()),r.getBoolean("disclaimer_accepted"),r.getObject("created_at",OffsetDateTime.class),recorded,status.equals("APPROVED")?recorded:null,status.equals("REVOKED")?recorded:null,false,false);}
 private void snapshot(UUID id,long v,String status,String p,String e,String h,List<String>s,boolean d,OffsetDateTime now){jdbc.sql("insert into financial_intent_revision values(?,?,?,?,?,?,?::varchar[],?,?)").params(id,v,status,p,e,h,array(s),d,now).update();}
 private void event(UUID id,String type,String status,long v,AuditActor a,String reason,OffsetDateTime now){jdbc.sql("insert into financial_intent_event(event_id,intent_id,event_type,status_snapshot,version,actor_principal_id,actor_customer_id,actor_session_id,actor_type,detail,occurred_at) values(?,?,?,?,?,?,?,?,?,jsonb_build_object('reason',cast(? as varchar)),?)").params(UUID.randomUUID(),id,type,status,v,a.principalId(),a.customerId(),a.sessionId(),a.actorType(),reason,now).update();}
 private static String array(List<String>s){return "{"+String.join(",",s)+"}";}private static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}private static boolean same(String a,String b){return MessageDigest.isEqual(HexFormat.of().parseHex(a),HexFormat.of().parseHex(b));}
 private record CommandReplay(String requestHash,UUID intentId,long version){}
}
