package com.alzswell.knowledge.api;

import com.alzswell.common.api.*;
import com.alzswell.common.security.AuditActor;
import com.alzswell.knowledge.api.KnowledgeGovernanceRequests.*;
import com.alzswell.knowledge.api.KnowledgeGovernanceResponses.GovernedDocument;
import com.alzswell.knowledge.application.KnowledgeGovernanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @Validated @RequestMapping("/api/v1/admin/knowledge/documents")
public class KnowledgeGovernanceController {
    private final KnowledgeGovernanceService service;
    public KnowledgeGovernanceController(KnowledgeGovernanceService service){this.service=service;}

    @PostMapping @PreAuthorize("hasAuthority('KNOWLEDGE_ADMIN_WRITE')")
    public ResponseEntity<ApiResponse<GovernedDocument>> register(
            @RequestHeader("Idempotency-Key") @Size(min=8,max=100) @Pattern(regexp="[A-Za-z0-9._:-]+") String key,
            @Valid @RequestBody RegisterDocumentCommand command,Authentication authentication) {
        return ApiResponses.created("KNOWLEDGE_DOCUMENT_REGISTERED_FOR_REVIEW",
                "원문을 수정하거나 외부 호출하지 않고 문서 메타데이터를 검토 대기로 등록했습니다.",
                service.register(command,key,AuditActor.from(authentication)));
    }

    @PostMapping("/{documentId}/publish") @PreAuthorize("hasAuthority('KNOWLEDGE_ADMIN_WRITE')")
    public ResponseEntity<ApiResponse<GovernedDocument>> publish(
            @PathVariable @Pattern(regexp="[A-Z0-9]+(?:-[A-Z0-9]+)*") @Size(max=80) String documentId,
            @RequestHeader("Idempotency-Key") @Size(min=8,max=100) @Pattern(regexp="[A-Za-z0-9._:-]+") String key,
            @Valid @RequestBody PublishDocumentCommand command,Authentication authentication) {
        return ApiResponses.ok("KNOWLEDGE_DOCUMENT_PUBLISHED",
                "검수된 문서 버전을 AI ingestion 가능 상태로 게시했습니다. 검색 반영은 별도 ingestion 승인 후 수행됩니다.",
                service.publish(documentId,command,key,AuditActor.from(authentication)));
    }
}
