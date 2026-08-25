package com.alzswell.knowledge.api;

import com.alzswell.common.api.*;
import com.alzswell.common.security.AuditActor;
import com.alzswell.knowledge.api.KnowledgeImportRequests.ImportIngestionCommand;
import com.alzswell.knowledge.api.KnowledgeImportResponses.ImportResult;
import com.alzswell.knowledge.application.KnowledgeIngestionImportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @Validated @RequestMapping("/api/v1/admin/knowledge/ingestion-imports")
public class KnowledgeImportController {
    private final KnowledgeIngestionImportService service;
    public KnowledgeImportController(KnowledgeIngestionImportService service){this.service=service;}

    @PostMapping @PreAuthorize("hasAuthority('KNOWLEDGE_ADMIN_WRITE')")
    public ResponseEntity<ApiResponse<ImportResult>> importIngestion(
            @RequestHeader("Idempotency-Key") @Size(min=8,max=100) @Pattern(regexp="[A-Za-z0-9._:-]+") String key,
            @Valid @RequestBody ImportIngestionCommand command,Authentication authentication) {
        return ApiResponses.created("KNOWLEDGE_INGESTION_IMPORTED",
                "검증된 ingestion 결과를 Spring 권위 지식 카탈로그에 반영했습니다.",
                service.importIngestion(command,key,AuditActor.from(authentication)));
    }
}
