package com.alzswell.knowledge.api;

import com.alzswell.common.api.*;
import com.alzswell.knowledge.api.KnowledgeRequests.SearchCommand;
import com.alzswell.knowledge.api.KnowledgeResponses.*;
import com.alzswell.knowledge.application.KnowledgeCatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @Validated @RequestMapping("/api/v1")
public class KnowledgeController {
    private final KnowledgeCatalogService service;
    public KnowledgeController(KnowledgeCatalogService service){this.service=service;}

    @GetMapping("/knowledge/documents") @PreAuthorize("hasAuthority('KNOWLEDGE_READ')")
    // Stateless bearer auth has no ambient browser credential; the only write is append-only access audit.
    public ResponseEntity<ApiResponse<DocumentList>> documents(
            @RequestParam(required=false) @Pattern(regexp="CUSTOMER|STAFF") String audience,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate asOf,
            Authentication authentication){
        return ApiResponses.ok("KNOWLEDGE_DOCUMENTS_RETRIEVED","승인된 근거 문서를 조회했습니다.",service.documents(audience,asOf,authentication));
    }
    @GetMapping("/knowledge/documents/{documentId}") @PreAuthorize("hasAuthority('KNOWLEDGE_READ')")
    // Stateless bearer auth has no ambient browser credential; the only write is append-only access audit.
    public ResponseEntity<ApiResponse<DocumentDetail>> document(@PathVariable String documentId,Authentication authentication){
        return ApiResponses.ok("KNOWLEDGE_DOCUMENT_RETRIEVED","승인된 근거 문서를 조회했습니다.",service.document(documentId,authentication));
    }
    @GetMapping("/knowledge/documents/{documentId}/versions") @PreAuthorize("hasAuthority('KNOWLEDGE_READ')")
    // Stateless bearer auth has no ambient browser credential; the only write is append-only access audit.
    public ResponseEntity<ApiResponse<VersionList>> versions(@PathVariable String documentId,Authentication authentication){
        return ApiResponses.ok("KNOWLEDGE_VERSIONS_RETRIEVED","근거 문서 버전을 조회했습니다.",service.versions(documentId,authentication));
    }
    @PostMapping("/knowledge/search") @PreAuthorize("hasAuthority('KNOWLEDGE_SEARCH')")
    public ResponseEntity<ApiResponse<SearchResult>> search(@Valid @RequestBody SearchCommand command,Authentication authentication){
        return ApiResponses.ok("KNOWLEDGE_SEARCH_COMPLETED","승인된 유효 근거를 검색했습니다.",service.search(command,authentication));
    }
    @GetMapping("/knowledge/passages/{passageId}") @PreAuthorize("hasAuthority('KNOWLEDGE_READ')")
    // Stateless bearer auth has no ambient browser credential; the only write is append-only access audit.
    public ResponseEntity<ApiResponse<Passage>> passage(@PathVariable UUID passageId,Authentication authentication){
        return ApiResponses.ok("KNOWLEDGE_PASSAGE_RETRIEVED","인용 가능한 근거 구절을 조회했습니다.",service.passage(passageId,authentication));
    }
    @GetMapping("/guidance-candidates") @PreAuthorize("hasAuthority('GUIDANCE_CANDIDATE_READ')")
    public ResponseEntity<ApiResponse<GuidanceCandidates>> guidance(
            @RequestParam @Pattern(regexp="MISSED_RECURRING_PAYMENT|DUPLICATE_TRANSFER|REPEATED_CONFIRMATION") String reasonCode,
            Authentication authentication){
        return ApiResponses.ok("GUIDANCE_CANDIDATES_RETRIEVED","정책이 허용한 안내 후보를 조회했습니다.",
                service.guidanceCandidates(reasonCode,authentication));
    }
}
