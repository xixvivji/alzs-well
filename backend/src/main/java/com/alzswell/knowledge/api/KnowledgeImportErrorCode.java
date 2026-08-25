package com.alzswell.knowledge.api;

import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum KnowledgeImportErrorCode implements ErrorCode {
    GOVERNANCE_NOT_READY(HttpStatus.CONFLICT,"KNOWLEDGE_IMPORT_GOVERNANCE_NOT_READY","승인·활성·효력 조건을 만족하는 지식 문서 버전이 아닙니다."),
    PAYLOAD_INVALID(HttpStatus.BAD_REQUEST,"KNOWLEDGE_IMPORT_PAYLOAD_INVALID","ingestion 결과의 순서·해시·chunk ID 또는 페이지 정보가 올바르지 않습니다."),
    CATALOG_CONFLICT(HttpStatus.CONFLICT,"KNOWLEDGE_IMPORT_CATALOG_CONFLICT","이미 존재하는 권위 지식 버전 또는 ingestion 실행과 충돌합니다."),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT,"KNOWLEDGE_IMPORT_IDEMPOTENCY_CONFLICT","멱등키가 다른 import 요청에 사용됐거나 처리가 진행 중입니다.");
    private final HttpStatus status; private final String code; private final String message;
    KnowledgeImportErrorCode(HttpStatus status,String code,String message){this.status=status;this.code=code;this.message=message;}
    public HttpStatus status(){return status;} public String code(){return code;} public String message(){return message;}
}
