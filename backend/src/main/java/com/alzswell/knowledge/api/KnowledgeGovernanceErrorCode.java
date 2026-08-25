package com.alzswell.knowledge.api;

import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum KnowledgeGovernanceErrorCode implements ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND,"KNOWLEDGE_GOVERNANCE_NOT_FOUND","검토 중인 문서 버전을 찾을 수 없습니다."),
    DUPLICATE(HttpStatus.CONFLICT,"KNOWLEDGE_GOVERNANCE_DUPLICATE","이미 등록된 문서 버전입니다."),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT,"KNOWLEDGE_GOVERNANCE_IDEMPOTENCY_CONFLICT","멱등키가 다른 요청에 사용됐거나 처리가 진행 중입니다."),
    VERSION_CONFLICT(HttpStatus.CONFLICT,"KNOWLEDGE_GOVERNANCE_VERSION_CONFLICT","문서 검토 상태가 다른 요청에 의해 변경됐습니다."),
    STATE_CONFLICT(HttpStatus.CONFLICT,"KNOWLEDGE_GOVERNANCE_STATE_CONFLICT","현재 상태에서는 문서를 게시할 수 없습니다."),
    INVALID_PERIOD(HttpStatus.BAD_REQUEST,"KNOWLEDGE_GOVERNANCE_INVALID_PERIOD","문서 효력기간 또는 확인일이 올바르지 않습니다."),
    INVALID_SOURCE(HttpStatus.BAD_REQUEST,"KNOWLEDGE_GOVERNANCE_INVALID_SOURCE","출처 유형과 저장소 경로·URL·이용권한 조합이 올바르지 않습니다."),
    INVALID_ROLES(HttpStatus.BAD_REQUEST,"KNOWLEDGE_GOVERNANCE_INVALID_ROLES","문서 접근 역할은 중복 없이 입력해야 합니다."),
    USAGE_REVIEW_REQUIRED(HttpStatus.CONFLICT,"KNOWLEDGE_GOVERNANCE_USAGE_REVIEW_REQUIRED","이용권한 검토가 완료되지 않은 문서는 게시할 수 없습니다."),
    INVALID_SUPERSEDES(HttpStatus.BAD_REQUEST,"KNOWLEDGE_GOVERNANCE_INVALID_SUPERSEDES","대체 문서 ID와 버전은 함께 입력해야 합니다.");
    private final HttpStatus status; private final String code; private final String message;
    KnowledgeGovernanceErrorCode(HttpStatus status,String code,String message){this.status=status;this.code=code;this.message=message;}
    public HttpStatus status(){return status;} public String code(){return code;} public String message(){return message;}
}
