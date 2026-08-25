package com.alzswell.knowledge.application;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.security.AuditActor;
import com.alzswell.knowledge.api.KnowledgeErrorCode;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeAccessPolicy {
    public AccessContext resolve(Authentication authentication,String permission) {
        SortedSet<String> roles=new TreeSet<>();
        authentication.getAuthorities().stream().map(value->value.getAuthority())
                .filter(value->value.startsWith("ROLE_")).map(value->value.substring(5)).forEach(roles::add);
        if(roles.isEmpty()) throw new BusinessException(KnowledgeErrorCode.ACCESS_CONTEXT_INVALID);
        SortedSet<String> audiences=new TreeSet<>();
        if(roles.contains("CUSTOMER")) audiences.add("CUSTOMER");
        if(roles.contains("PROTECTION_STAFF")||roles.contains("DETECTION_ADMIN")) audiences.add("STAFF");
        if(audiences.isEmpty()) throw new BusinessException(KnowledgeErrorCode.ACCESS_CONTEXT_INVALID);
        return new AccessContext(List.copyOf(roles),List.copyOf(audiences),permission,AuditActor.from(authentication));
    }

    public record AccessContext(List<String> roles,List<String> audiences,String permission,AuditActor actor) {
        String rolesCsv(){return String.join(",",roles);}
        String audiencesCsv(){return String.join(",",audiences);}
        boolean allowsAudience(String audience){return audience==null||audiences.contains(audience);}
    }
}
