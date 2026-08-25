from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


ERROR_EXIT_CODES: dict[str, int] = {
    "CONTRACT_VERSION_UNSUPPORTED": 2,
    "MANIFEST_SCHEMA_INVALID": 2,
    "MANIFEST_DUPLICATE_KEY": 2,
    "MANIFEST_ALIAS_FORBIDDEN": 2,
    "DOCUMENT_NOT_APPROVED": 3,
    "DOCUMENT_NOT_ACTIVE": 3,
    "DOCUMENT_NOT_EFFECTIVE": 3,
    "KNOWLEDGE_ROLE_DENIED": 3,
    "KNOWLEDGE_AUDIENCE_DENIED": 3,
    "REPOSITORY_ROOT_REQUIRED": 2,
    "DATABASE_CONFIGURATION_INVALID": 2,
    "API_CONFIGURATION_INVALID": 2,
    "INTERNAL_AUTHENTICATION_FAILED": 3,
    "KNOWLEDGE_PERMISSION_DENIED": 3,
    "SEARCH_REQUEST_INVALID": 2,
    "SEARCH_REQUEST_CONFLICT": 6,
    "SOURCE_PATH_OUTSIDE_CORPUS": 4,
    "SOURCE_SYMLINK_FORBIDDEN": 4,
    "SOURCE_NOT_FOUND": 4,
    "SOURCE_TOO_LARGE": 4,
    "SOURCE_ENCODING_INVALID": 4,
    "SOURCE_TYPE_UNSUPPORTED": 4,
    "SOURCE_HASH_MISMATCH": 4,
    "SOURCE_TRANSFORMATION_UNSUPPORTED": 4,
    "SOURCE_STRUCTURE_INVALID": 4,
    "SOURCE_ENCRYPTED_UNSUPPORTED": 4,
    "SOURCE_PAGE_LIMIT_EXCEEDED": 4,
    "SOURCE_ACTIVE_CONTENT_FORBIDDEN": 4,
    "EXTRACTION_FAILED": 5,
    "NO_EXTRACTABLE_CONTENT": 5,
    "OCR_REQUIRED": 5,
    "CHUNK_VALIDATION_FAILED": 5,
    "OUTPUT_WRITE_FAILED": 6,
    "STORAGE_UNAVAILABLE": 6,
    "STORAGE_CONFLICT": 6,
}


SAFE_MESSAGES: dict[str, str] = {
    "CONTRACT_VERSION_UNSUPPORTED": "지원하지 않는 지식 계약 버전입니다.",
    "MANIFEST_SCHEMA_INVALID": "Manifest가 지식 계약을 충족하지 않습니다.",
    "MANIFEST_DUPLICATE_KEY": "Manifest에 중복 키가 있습니다.",
    "MANIFEST_ALIAS_FORBIDDEN": "Manifest의 alias 또는 anchor는 허용되지 않습니다.",
    "DOCUMENT_NOT_APPROVED": "승인되지 않은 문서는 ingestion할 수 없습니다.",
    "DOCUMENT_NOT_ACTIVE": "활성 상태가 아닌 문서는 ingestion할 수 없습니다.",
    "DOCUMENT_NOT_EFFECTIVE": "기준일에 유효하지 않은 문서입니다.",
    "REPOSITORY_ROOT_REQUIRED": "저장소 루트를 명시해야 합니다.",
    "DATABASE_CONFIGURATION_INVALID": "AI 저장소 연결 설정이 올바르지 않습니다.",
    "API_CONFIGURATION_INVALID": "AI 내부 API 설정이 올바르지 않습니다.",
    "INTERNAL_AUTHENTICATION_FAILED": "내부 서비스 인증에 실패했습니다.",
    "KNOWLEDGE_PERMISSION_DENIED": "지식 검색 권한이 없습니다.",
    "SEARCH_REQUEST_INVALID": "지식 검색 요청이 계약을 충족하지 않습니다.",
    "SEARCH_REQUEST_CONFLICT": "이미 처리된 검색 요청입니다.",
    "SOURCE_PATH_OUTSIDE_CORPUS": "허용된 저장소 경계를 벗어난 원문 경로입니다.",
    "SOURCE_SYMLINK_FORBIDDEN": "원문 경로에 심볼릭 링크를 사용할 수 없습니다.",
    "SOURCE_NOT_FOUND": "원문 파일을 찾을 수 없습니다.",
    "SOURCE_TOO_LARGE": "원문 파일이 허용된 크기를 초과했습니다.",
    "SOURCE_ENCODING_INVALID": "원문 인코딩이 UTF-8 계약을 충족하지 않습니다.",
    "SOURCE_TYPE_UNSUPPORTED": "지원하지 않는 원문 형식입니다.",
    "SOURCE_HASH_MISMATCH": "원문 SHA-256이 manifest와 일치하지 않습니다.",
    "SOURCE_TRANSFORMATION_UNSUPPORTED": "지원하지 않는 출처 변환 규칙입니다.",
    "SOURCE_STRUCTURE_INVALID": "PDF 구조가 유효하지 않습니다.",
    "SOURCE_ENCRYPTED_UNSUPPORTED": "암호화된 PDF는 ingestion할 수 없습니다.",
    "SOURCE_PAGE_LIMIT_EXCEEDED": "PDF 페이지 수가 허용 범위를 벗어났습니다.",
    "SOURCE_ACTIVE_CONTENT_FORBIDDEN": "실행 가능하거나 첨부된 PDF 콘텐츠는 허용되지 않습니다.",
    "OCR_REQUIRED": "텍스트 계층이 없는 PDF는 별도 OCR 처리가 필요합니다.",
    "STORAGE_UNAVAILABLE": "AI 저장소를 사용할 수 없습니다.",
    "STORAGE_CONFLICT": "기존 파생 데이터와 충돌하여 저장할 수 없습니다.",
}


@dataclass(slots=True)
class KnowledgeContractError(Exception):
    code: str
    safe_context: dict[str, Any] = field(default_factory=dict)

    def __post_init__(self) -> None:
        if self.code not in ERROR_EXIT_CODES:
            raise ValueError(f"unknown knowledge error code: {self.code}")
        Exception.__init__(self, self.code)

    @property
    def exit_code(self) -> int:
        return ERROR_EXIT_CODES[self.code]

    @property
    def safe_message(self) -> str:
        return SAFE_MESSAGES.get(self.code, "지식 ingestion 요청을 처리할 수 없습니다.")
