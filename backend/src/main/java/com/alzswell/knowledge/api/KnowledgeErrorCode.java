package com.alzswell.knowledge.api;

import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum KnowledgeErrorCode implements ErrorCode {
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND,"KNOWLEDGE_DOCUMENT_NOT_FOUND","승인된 문서를 찾을 수 없습니다."),
    PASSAGE_NOT_FOUND(HttpStatus.NOT_FOUND,"KNOWLEDGE_PASSAGE_NOT_FOUND","인용 가능한 구절을 찾을 수 없습니다."),
    ACCESS_CONTEXT_INVALID(HttpStatus.FORBIDDEN,"KNOWLEDGE_ACCESS_CONTEXT_INVALID","지식 문서 접근 역할을 확인할 수 없습니다.");
    private final HttpStatus status; private final String code; private final String message;
    KnowledgeErrorCode(HttpStatus status,String code,String message){this.status=status;this.code=code;this.message=message;}
    @Override public HttpStatus status(){return status;}
    @Override public String code(){return code;}
    @Override public String message(){return message;}
}
