package com.alzswell.knowledge.application;

import com.alzswell.knowledge.application.KnowledgeAccessPolicy.AccessContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeAccessAuditService {
    private final JdbcTemplate jdbc; private final ObjectMapper mapper; private final Clock clock;
    public KnowledgeAccessAuditService(JdbcTemplate jdbc,ObjectMapper mapper,Clock clock){this.jdbc=jdbc;this.mapper=mapper;this.clock=clock;}

    public void record(String eventType,AccessContext access,String resourceId,String query,LocalDate asOf,
            List<String> returnedIds,String outcome,Map<String,Object> detail) {
        UUID id=UUID.randomUUID(); OffsetDateTime now=OffsetDateTime.now(clock);
        String queryHash=query==null?null:sha256(query.trim().toLowerCase(Locale.ROOT));
        String detailJson=json(detail);
        String integrity=sha256(id+"|"+eventType+"|"+access.actor().legacyActorId()+"|"+access.permission()+"|"
                +resourceId+"|"+queryHash+"|"+asOf+"|"+returnedIds+"|"+outcome+"|"+detailJson+"|"+now);
        jdbc.update("""
            insert into knowledge_access_audit_event values(?,?,?,?,?,string_to_array(?,','),
             string_to_array(?,','),?,?,?,string_to_array(?,','),?,?::jsonb,?,?)
            """,id,eventType,access.actor().principalId(),access.actor().legacyActorId(),access.permission(),
                access.rolesCsv(),access.audiencesCsv(),resourceId,queryHash,asOf,String.join(",",returnedIds),
                outcome,detailJson,now,integrity);
    }
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
