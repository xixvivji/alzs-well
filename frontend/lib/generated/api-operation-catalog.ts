// 이 파일은 scripts/generate-api-catalog.mjs로 생성합니다. 직접 수정하지 마세요.

export type ApiMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
export type ApiPriority = "P0-A" | "P0-B" | "P1" | "P2";
export type ApiBoundary = "OWNED" | "EXTERNAL_INTEGRATION" | "REFERENCE_ONLY";
export type ApiImplementation = "IMPLEMENTED" | "PLANNED" | "REFERENCE_ONLY";
export type ApiAudience = "PUBLIC" | "CUSTOMER" | "STAFF" | "ADMIN";
export type ApiAuthorityMode = "PUBLIC" | "DEMO_CAPABILITY" | "STAFF_BOOTSTRAP" | "BEARER";

export type ApiOperationDefinition = {
  key: string;
  method: ApiMethod;
  path: string;
  purpose: string;
  domain: string;
  domainId: string;
  priority: ApiPriority;
  boundary: ApiBoundary;
  implementation: ApiImplementation;
  audience: ApiAudience;
  authorityMode: ApiAuthorityMode;
  pathParameters: readonly string[];
  externalActionAllowed: false;
};

export const API_OPERATION_CATALOG = [
  {
    "key": "GET /api/v1/system/health",
    "method": "GET",
    "path": "/api/v1/system/health",
    "purpose": "상태와 데모 안전 가드레일 확인",
    "domain": "시스템·데모",
    "domainId": "시스템-데모",
    "priority": "P0-A",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "PUBLIC",
    "authorityMode": "PUBLIC",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/system/readiness",
    "method": "GET",
    "path": "/api/v1/system/readiness",
    "purpose": "DB·Flyway·필수 구성 준비상태",
    "domain": "시스템·데모",
    "domainId": "시스템-데모",
    "priority": "P0-B",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "PUBLIC",
    "authorityMode": "PUBLIC",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/system/public-config",
    "method": "GET",
    "path": "/api/v1/system/public-config",
    "purpose": "공개 프론트 설정과 합성데이터 모드",
    "domain": "시스템·데모",
    "domainId": "시스템-데모",
    "priority": "P0-B",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "PUBLIC",
    "authorityMode": "PUBLIC",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/system/versions",
    "method": "GET",
    "path": "/api/v1/system/versions",
    "purpose": "알고리즘·정책·API 버전",
    "domain": "시스템·데모",
    "domainId": "시스템-데모",
    "priority": "P0-B",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "PUBLIC",
    "authorityMode": "PUBLIC",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/demo/sessions",
    "method": "POST",
    "path": "/api/v1/demo/sessions",
    "purpose": "익명 데모 세션 생성",
    "domain": "시스템·데모",
    "domainId": "시스템-데모",
    "priority": "P0-A",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "PUBLIC",
    "authorityMode": "PUBLIC",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/demo/sessions/{sessionId}",
    "method": "GET",
    "path": "/api/v1/demo/sessions/{sessionId}",
    "purpose": "세션 상태·만료·적재 시나리오 조회",
    "domain": "시스템·데모",
    "domainId": "시스템-데모",
    "priority": "P0-B",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "PUBLIC",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "DELETE /api/v1/demo/sessions/{sessionId}",
    "method": "DELETE",
    "path": "/api/v1/demo/sessions/{sessionId}",
    "purpose": "익명 데모 세션 조기 폐기",
    "domain": "시스템·데모",
    "domainId": "시스템-데모",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "PUBLIC",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/demo/sessions/{sessionId}/reset",
    "method": "POST",
    "path": "/api/v1/demo/sessions/{sessionId}/reset",
    "purpose": "동일 seed·snapshot 복원",
    "domain": "시스템·데모",
    "domainId": "시스템-데모",
    "priority": "P0-A",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "PUBLIC",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/demo/scenarios",
    "method": "GET",
    "path": "/api/v1/demo/scenarios",
    "purpose": "사용 가능한 합성 시나리오 목록",
    "domain": "시스템·데모",
    "domainId": "시스템-데모",
    "priority": "P0-B",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "PUBLIC",
    "authorityMode": "PUBLIC",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/demo/sessions/{sessionId}/scenarios/{scenarioId}/ingest",
    "method": "POST",
    "path": "/api/v1/demo/sessions/{sessionId}/scenarios/{scenarioId}/ingest",
    "purpose": "고정 합성 시나리오 적재",
    "domain": "시스템·데모",
    "domainId": "시스템-데모",
    "priority": "P0-A",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "PUBLIC",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId",
      "scenarioId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/demo/sessions/{sessionId}/customers/{customerId}/financial-summary",
    "method": "GET",
    "path": "/api/v1/demo/sessions/{sessionId}/customers/{customerId}/financial-summary",
    "purpose": "세션 격리 통합자산·현금흐름 요약",
    "domain": "시스템·데모",
    "domainId": "시스템-데모",
    "priority": "P0-B",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "PUBLIC",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId",
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/demo/sessions/{sessionId}/customers/{customerId}/accounts",
    "method": "GET",
    "path": "/api/v1/demo/sessions/{sessionId}/customers/{customerId}/accounts",
    "purpose": "세션 격리 합성 계좌 목록",
    "domain": "시스템·데모",
    "domainId": "시스템-데모",
    "priority": "P0-B",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "PUBLIC",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId",
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/demo/sessions/{sessionId}/accounts/{accountId}/transactions",
    "method": "GET",
    "path": "/api/v1/demo/sessions/{sessionId}/accounts/{accountId}/transactions",
    "purpose": "세션 격리 합성 거래내역",
    "domain": "시스템·데모",
    "domainId": "시스템-데모",
    "priority": "P0-B",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "PUBLIC",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId",
      "accountId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/demo/sessions/{sessionId}/customers/{customerId}/baselines",
    "method": "GET",
    "path": "/api/v1/demo/sessions/{sessionId}/customers/{customerId}/baselines",
    "purpose": "세션 격리 개인 기준선",
    "domain": "시스템·데모",
    "domainId": "시스템-데모",
    "priority": "P0-B",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "PUBLIC",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId",
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/demo/sessions/{sessionId}/protection-actions",
    "method": "GET",
    "path": "/api/v1/demo/sessions/{sessionId}/protection-actions",
    "purpose": "세션 데모용 공식 보호수단",
    "domain": "시스템·데모",
    "domainId": "시스템-데모",
    "priority": "P0-B",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "PUBLIC",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/demo/sessions/{sessionId}/customers/{customerId}/connections/consent-summary",
    "method": "GET",
    "path": "/api/v1/demo/sessions/{sessionId}/customers/{customerId}/connections/consent-summary",
    "purpose": "세션 격리 연결·동의 요약",
    "domain": "시스템·데모",
    "domainId": "시스템-데모",
    "priority": "P0-B",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "PUBLIC",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId",
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/auth/login",
    "method": "POST",
    "path": "/api/v1/auth/login",
    "purpose": "기업 SSO 또는 인증 공급자 로그인",
    "domain": "인증·세션·권한",
    "domainId": "인증-세션-권한",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "PUBLIC",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/auth/token/refresh",
    "method": "POST",
    "path": "/api/v1/auth/token/refresh",
    "purpose": "애플리케이션 토큰 갱신",
    "domain": "인증·세션·권한",
    "domainId": "인증-세션-권한",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "PUBLIC",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/auth/logout",
    "method": "POST",
    "path": "/api/v1/auth/logout",
    "purpose": "현재 세션 종료",
    "domain": "인증·세션·권한",
    "domainId": "인증-세션-권한",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/auth/logout-all",
    "method": "POST",
    "path": "/api/v1/auth/logout-all",
    "purpose": "현재 사용자의 모든 인증 세션 종료",
    "domain": "인증·세션·권한",
    "domainId": "인증-세션-권한",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/auth/me",
    "method": "GET",
    "path": "/api/v1/auth/me",
    "purpose": "현재 사용자·직원 정보",
    "domain": "인증·세션·권한",
    "domainId": "인증-세션-권한",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/auth/me/permissions",
    "method": "GET",
    "path": "/api/v1/auth/me/permissions",
    "purpose": "역할·세부 권한 조회",
    "domain": "인증·세션·권한",
    "domainId": "인증-세션-권한",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/auth/sessions",
    "method": "GET",
    "path": "/api/v1/auth/sessions",
    "purpose": "로그인 세션 목록 (`IMPLEMENTED`)",
    "domain": "인증·세션·권한",
    "domainId": "인증-세션-권한",
    "priority": "P2",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "DELETE /api/v1/auth/sessions/{authSessionId}",
    "method": "DELETE",
    "path": "/api/v1/auth/sessions/{authSessionId}",
    "purpose": "선택한 로그인 세션 폐기 (`IMPLEMENTED`)",
    "domain": "인증·세션·권한",
    "domainId": "인증-세션-권한",
    "priority": "P2",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "authSessionId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/auth/step-up/challenges",
    "method": "POST",
    "path": "/api/v1/auth/step-up/challenges",
    "purpose": "중요화면 추가인증 시작",
    "domain": "인증·세션·권한",
    "domainId": "인증-세션-권한",
    "priority": "P2",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "PLANNED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/auth/step-up/challenges/{challengeId}/verify",
    "method": "POST",
    "path": "/api/v1/auth/step-up/challenges/{challengeId}/verify",
    "purpose": "추가인증 검증",
    "domain": "인증·세션·권한",
    "domainId": "인증-세션-권한",
    "priority": "P2",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "PLANNED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "challengeId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}",
    "purpose": "비식별 고객 요약",
    "domain": "고객 프로필·접근성",
    "domainId": "고객-프로필-접근성",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "PATCH /api/v1/customers/{customerId}/display-profile",
    "method": "PATCH",
    "path": "/api/v1/customers/{customerId}/display-profile",
    "purpose": "별칭 등 표시 전용 정보 변경",
    "domain": "고객 프로필·접근성",
    "domainId": "고객-프로필-접근성",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/preferences",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/preferences",
    "purpose": "서비스 환경설정 조회",
    "domain": "고객 프로필·접근성",
    "domainId": "고객-프로필-접근성",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "PATCH /api/v1/customers/{customerId}/preferences",
    "method": "PATCH",
    "path": "/api/v1/customers/{customerId}/preferences",
    "purpose": "서비스 환경설정 변경",
    "domain": "고객 프로필·접근성",
    "domainId": "고객-프로필-접근성",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/accessibility-settings",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/accessibility-settings",
    "purpose": "쉬운 금융 모드 설정 조회",
    "domain": "고객 프로필·접근성",
    "domainId": "고객-프로필-접근성",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "PUT /api/v1/customers/{customerId}/accessibility-settings",
    "method": "PUT",
    "path": "/api/v1/customers/{customerId}/accessibility-settings",
    "purpose": "글자·대비·읽기 흐름 설정",
    "domain": "고객 프로필·접근성",
    "domainId": "고객-프로필-접근성",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/data-summary",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/data-summary",
    "purpose": "서비스가 보유한 데이터 범위 확인",
    "domain": "고객 프로필·접근성",
    "domainId": "고객-프로필-접근성",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/customers/{customerId}/data-export-requests",
    "method": "POST",
    "path": "/api/v1/customers/{customerId}/data-export-requests",
    "purpose": "고객 데이터 사본 요청",
    "domain": "고객 프로필·접근성",
    "domainId": "고객-프로필-접근성",
    "priority": "P2",
    "boundary": "OWNED",
    "implementation": "PLANNED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/continuity-preparation",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/continuity-preparation",
    "purpose": "준비상태와 최신 승인 의향 조회",
    "domain": "금융생활 준비·의향",
    "domainId": "금융생활-준비-의향",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/customers/{customerId}/financial-intents/drafts",
    "method": "POST",
    "path": "/api/v1/customers/{customerId}/financial-intents/drafts",
    "purpose": "고객 확인 전 구조화 초안 생성",
    "domain": "금융생활 준비·의향",
    "domainId": "금융생활-준비-의향",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "PUT /api/v1/customers/{customerId}/financial-intents/{intentId}/draft",
    "method": "PUT",
    "path": "/api/v1/customers/{customerId}/financial-intents/{intentId}/draft",
    "purpose": "승인 전 초안 수정",
    "domain": "금융생활 준비·의향",
    "domainId": "금융생활-준비-의향",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId",
      "intentId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/customers/{customerId}/financial-intents/{intentId}/approve",
    "method": "POST",
    "path": "/api/v1/customers/{customerId}/financial-intents/{intentId}/approve",
    "purpose": "법적 효력 제한 확인 후 고객 승인",
    "domain": "금융생활 준비·의향",
    "domainId": "금융생활-준비-의향",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId",
      "intentId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/financial-intents/versions",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/financial-intents/versions",
    "purpose": "불변 버전 이력 조회",
    "domain": "금융생활 준비·의향",
    "domainId": "금융생활-준비-의향",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/customers/{customerId}/financial-intents/{intentId}/revoke",
    "method": "POST",
    "path": "/api/v1/customers/{customerId}/financial-intents/{intentId}/revoke",
    "purpose": "최신 승인 의향 철회",
    "domain": "금융생활 준비·의향",
    "domainId": "금융생활-준비-의향",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId",
      "intentId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/staff/customers/{customerId}/financial-intent-summary",
    "method": "GET",
    "path": "/api/v1/staff/customers/{customerId}/financial-intent-summary",
    "purpose": "동의한 항목만 행원 요약 조회",
    "domain": "금융생활 준비·의향",
    "domainId": "금융생활-준비-의향",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "STAFF",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/demo/sessions/{sessionId}/customers/{customerId}/ai-financial-assistance/intent-suggestions",
    "method": "POST",
    "path": "/api/v1/demo/sessions/{sessionId}/customers/{customerId}/ai-financial-assistance/intent-suggestions",
    "purpose": "고객 발화를 확인 가능한 의향 초안으로 구조화",
    "domain": "데모 AI 금융생활 지원",
    "domainId": "데모-ai-금융생활-지원",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "PUBLIC",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId",
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "PUT /api/v1/demo/sessions/{sessionId}/customers/{customerId}/ai-financial-assistance/intent",
    "method": "PUT",
    "path": "/api/v1/demo/sessions/{sessionId}/customers/{customerId}/ai-financial-assistance/intent",
    "purpose": "고객이 수정한 데모 의향 초안 저장",
    "domain": "데모 AI 금융생활 지원",
    "domainId": "데모-ai-금융생활-지원",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "PUBLIC",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId",
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/demo/sessions/{sessionId}/customers/{customerId}/ai-financial-assistance/intent/approve",
    "method": "POST",
    "path": "/api/v1/demo/sessions/{sessionId}/customers/{customerId}/ai-financial-assistance/intent/approve",
    "purpose": "법적 효력 제한 확인 후 데모 의향 승인",
    "domain": "데모 AI 금융생활 지원",
    "domainId": "데모-ai-금융생활-지원",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "PUBLIC",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId",
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/demo/sessions/{sessionId}/customers/{customerId}/ai-financial-assistance/intent",
    "method": "GET",
    "path": "/api/v1/demo/sessions/{sessionId}/customers/{customerId}/ai-financial-assistance/intent",
    "purpose": "현재 데모 의향 조회",
    "domain": "데모 AI 금융생활 지원",
    "domainId": "데모-ai-금융생활-지원",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "PUBLIC",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId",
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/demo/sessions/{sessionId}/customers/{customerId}/ai-financial-assistance/change-analysis",
    "method": "GET",
    "path": "/api/v1/demo/sessions/{sessionId}/customers/{customerId}/ai-financial-assistance/change-analysis",
    "purpose": "30·60·90일 설명 가능한 장기 변화 분석",
    "domain": "데모 AI 금융생활 지원",
    "domainId": "데모-ai-금융생활-지원",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "PUBLIC",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId",
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/demo/sessions/{sessionId}/customers/{customerId}/ai-financial-assistance/plain-language",
    "method": "POST",
    "path": "/api/v1/demo/sessions/{sessionId}/customers/{customerId}/ai-financial-assistance/plain-language",
    "purpose": "고객 선호에 맞는 쉬운말·음성용 문장 생성",
    "domain": "데모 AI 금융생활 지원",
    "domainId": "데모-ai-금융생활-지원",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "PUBLIC",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId",
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/financial-institutions",
    "method": "GET",
    "path": "/api/v1/financial-institutions",
    "purpose": "연결 가능한 금융기관 목록",
    "domain": "금융기관·데이터 연결",
    "domainId": "금융기관-데이터-연결",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/financial-institutions/{institutionId}",
    "method": "GET",
    "path": "/api/v1/financial-institutions/{institutionId}",
    "purpose": "기관·지원 데이터 범위",
    "domain": "금융기관·데이터 연결",
    "domainId": "금융기관-데이터-연결",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "institutionId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/connections",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/connections",
    "purpose": "고객 데이터 연결 목록",
    "domain": "금융기관·데이터 연결",
    "domainId": "금융기관-데이터-연결",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/connections/{connectionId}",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/connections/{connectionId}",
    "purpose": "연결 상태·동의 범위",
    "domain": "금융기관·데이터 연결",
    "domainId": "금융기관-데이터-연결",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId",
      "connectionId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/customers/{customerId}/connections",
    "method": "POST",
    "path": "/api/v1/customers/{customerId}/connections",
    "purpose": "마이데이터 연결 절차 시작",
    "domain": "금융기관·데이터 연결",
    "domainId": "금융기관-데이터-연결",
    "priority": "P2",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "PLANNED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/connections/{connectionId}/sync",
    "method": "POST",
    "path": "/api/v1/connections/{connectionId}/sync",
    "purpose": "데이터 동기화 요청",
    "domain": "금융기관·데이터 연결",
    "domainId": "금융기관-데이터-연결",
    "priority": "P2",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "PLANNED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "connectionId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/connections/{connectionId}/sync-runs",
    "method": "GET",
    "path": "/api/v1/connections/{connectionId}/sync-runs",
    "purpose": "연결별 동기화 이력",
    "domain": "금융기관·데이터 연결",
    "domainId": "금융기관-데이터-연결",
    "priority": "P2",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "PLANNED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "connectionId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "DELETE /api/v1/customers/{customerId}/connections/{connectionId}",
    "method": "DELETE",
    "path": "/api/v1/customers/{customerId}/connections/{connectionId}",
    "purpose": "연결·수집 동의 해제",
    "domain": "금융기관·데이터 연결",
    "domainId": "금융기관-데이터-연결",
    "priority": "P2",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "PLANNED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId",
      "connectionId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/accounts",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/accounts",
    "purpose": "계좌 목록",
    "domain": "계좌",
    "domainId": "계좌",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/accounts/{accountId}",
    "method": "GET",
    "path": "/api/v1/accounts/{accountId}",
    "purpose": "마스킹된 계좌 상세",
    "domain": "계좌",
    "domainId": "계좌",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "accountId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/accounts/{accountId}/balance",
    "method": "GET",
    "path": "/api/v1/accounts/{accountId}/balance",
    "purpose": "현재·가용 잔액",
    "domain": "계좌",
    "domainId": "계좌",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "accountId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/accounts/{accountId}/balance-history",
    "method": "GET",
    "path": "/api/v1/accounts/{accountId}/balance-history",
    "purpose": "기간별 잔액 추세",
    "domain": "계좌",
    "domainId": "계좌",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "accountId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/accounts/{accountId}/restrictions",
    "method": "GET",
    "path": "/api/v1/accounts/{accountId}/restrictions",
    "purpose": "계좌 상태·제약 읽기",
    "domain": "계좌",
    "domainId": "계좌",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "accountId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/accounts/{accountId}/interest-summary",
    "method": "GET",
    "path": "/api/v1/accounts/{accountId}/interest-summary",
    "purpose": "이자 요약",
    "domain": "계좌",
    "domainId": "계좌",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "accountId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/accounts/{accountId}/statements",
    "method": "GET",
    "path": "/api/v1/accounts/{accountId}/statements",
    "purpose": "거래명세서 목록",
    "domain": "계좌",
    "domainId": "계좌",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "accountId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/accounts/{accountId}/statements/{statementId}",
    "method": "GET",
    "path": "/api/v1/accounts/{accountId}/statements/{statementId}",
    "purpose": "거래명세서 상세",
    "domain": "계좌",
    "domainId": "계좌",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "accountId",
      "statementId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/accounts/{accountId}/recurring-counterparties",
    "method": "GET",
    "path": "/api/v1/accounts/{accountId}/recurring-counterparties",
    "purpose": "반복 거래 상대 분석",
    "domain": "계좌",
    "domainId": "계좌",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "accountId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "PATCH /api/v1/accounts/{accountId}/display-settings",
    "method": "PATCH",
    "path": "/api/v1/accounts/{accountId}/display-settings",
    "purpose": "`Idempotency-Key` 기반 계좌 별칭·노출 순서 변경",
    "domain": "계좌",
    "domainId": "계좌",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "accountId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/account-groups",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/account-groups",
    "purpose": "고객 지정 계좌 그룹",
    "domain": "계좌",
    "domainId": "계좌",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/financial-summary",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/financial-summary",
    "purpose": "전 금융기관 자산·부채·현금흐름 요약",
    "domain": "통합자산·현금흐름",
    "domainId": "통합자산-현금흐름",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/asset-breakdown",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/asset-breakdown",
    "purpose": "기관·상품·자산군별 구성",
    "domain": "통합자산·현금흐름",
    "domainId": "통합자산-현금흐름",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/asset-trends",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/asset-trends",
    "purpose": "기간별 총자산·순자산 추세",
    "domain": "통합자산·현금흐름",
    "domainId": "통합자산-현금흐름",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/liabilities",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/liabilities",
    "purpose": "대출·카드대금 등 부채 통합 요약",
    "domain": "통합자산·현금흐름",
    "domainId": "통합자산-현금흐름",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/cashflow-summary",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/cashflow-summary",
    "purpose": "기간별 수입·지출·순현금흐름",
    "domain": "통합자산·현금흐름",
    "domainId": "통합자산-현금흐름",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/expense-summary",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/expense-summary",
    "purpose": "범주·기관·기간별 지출 분석",
    "domain": "통합자산·현금흐름",
    "domainId": "통합자산-현금흐름",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/asset-calendar",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/asset-calendar",
    "purpose": "급여·이자·납부·만기 통합 일정",
    "domain": "통합자산·현금흐름",
    "domainId": "통합자산-현금흐름",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/data-freshness",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/data-freshness",
    "purpose": "기관별 최종 동기화·완전성·지연 상태",
    "domain": "통합자산·현금흐름",
    "domainId": "통합자산-현금흐름",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/accounts/{accountId}/transactions",
    "method": "GET",
    "path": "/api/v1/accounts/{accountId}/transactions",
    "purpose": "계좌 거래내역",
    "domain": "거래내역·검색",
    "domainId": "거래내역-검색",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "accountId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/transactions/{transactionId}",
    "method": "GET",
    "path": "/api/v1/transactions/{transactionId}",
    "purpose": "마스킹된 거래 상세",
    "domain": "거래내역·검색",
    "domainId": "거래내역-검색",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "transactionId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/transactions/search",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/transactions/search",
    "purpose": "전 계좌 자연어·조건 검색",
    "domain": "거래내역·검색",
    "domainId": "거래내역-검색",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/transactions/summary",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/transactions/summary",
    "purpose": "기간·범주별 거래 요약",
    "domain": "거래내역·검색",
    "domainId": "거래내역-검색",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/counterparties",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/counterparties",
    "purpose": "거래 상대 목록·신규성",
    "domain": "거래내역·검색",
    "domainId": "거래내역-검색",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/counterparties/{counterpartyId}/transaction-history",
    "method": "GET",
    "path": "/api/v1/counterparties/{counterpartyId}/transaction-history",
    "purpose": "상대별 거래 추세",
    "domain": "거래내역·검색",
    "domainId": "거래내역-검색",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "counterpartyId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/transactions/{transactionId}/enrichment",
    "method": "GET",
    "path": "/api/v1/transactions/{transactionId}/enrichment",
    "purpose": "범주·정규화·분석 부가정보",
    "domain": "거래내역·검색",
    "domainId": "거래내역-검색",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "transactionId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "PUT /api/v1/transactions/{transactionId}/category",
    "method": "PUT",
    "path": "/api/v1/transactions/{transactionId}/category",
    "purpose": "`Idempotency-Key` 기반 고객 지정 범주 보정",
    "domain": "거래내역·검색",
    "domainId": "거래내역-검색",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "transactionId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "PUT /api/v1/transactions/{transactionId}/note",
    "method": "PUT",
    "path": "/api/v1/transactions/{transactionId}/note",
    "purpose": "`Idempotency-Key` 기반 금융 기억노트 작성",
    "domain": "거래내역·검색",
    "domainId": "거래내역-검색",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "transactionId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/customers/{customerId}/transaction-export-requests",
    "method": "POST",
    "path": "/api/v1/customers/{customerId}/transaction-export-requests",
    "purpose": "거래내역 파일 생성 요청",
    "domain": "거래내역·검색",
    "domainId": "거래내역-검색",
    "priority": "P2",
    "boundary": "OWNED",
    "implementation": "PLANNED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/recurring-payments",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/recurring-payments",
    "purpose": "정기납부·구독 목록",
    "domain": "정기납부·구독·청구",
    "domainId": "정기납부-구독-청구",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/recurring-payments/{recurringPaymentId}",
    "method": "GET",
    "path": "/api/v1/recurring-payments/{recurringPaymentId}",
    "purpose": "추정 주기·금액·상태",
    "domain": "정기납부·구독·청구",
    "domainId": "정기납부-구독-청구",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "recurringPaymentId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/recurring-payments/calendar",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/recurring-payments/calendar",
    "purpose": "예상 납부 일정",
    "domain": "정기납부·구독·청구",
    "domainId": "정기납부-구독-청구",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/recurring-payments/missed",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/recurring-payments/missed",
    "purpose": "미발생 정기납부 후보",
    "domain": "정기납부·구독·청구",
    "domainId": "정기납부-구독-청구",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/recurring-payments/duplicates",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/recurring-payments/duplicates",
    "purpose": "중복 구독·납부 후보",
    "domain": "정기납부·구독·청구",
    "domainId": "정기납부-구독-청구",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/recurring-payments/{recurringPaymentId}/occurrences",
    "method": "GET",
    "path": "/api/v1/recurring-payments/{recurringPaymentId}/occurrences",
    "purpose": "과거·예상 발생 내역",
    "domain": "정기납부·구독·청구",
    "domainId": "정기납부-구독-청구",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "recurringPaymentId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "PUT /api/v1/recurring-payments/{recurringPaymentId}/reminder-settings",
    "method": "PUT",
    "path": "/api/v1/recurring-payments/{recurringPaymentId}/reminder-settings",
    "purpose": "`Idempotency-Key` 기반 납부 확인 알림 설정",
    "domain": "정기납부·구독·청구",
    "domainId": "정기납부-구독-청구",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "recurringPaymentId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/recurring-payments/{recurringPaymentId}/cancellation-guidance",
    "method": "POST",
    "path": "/api/v1/recurring-payments/{recurringPaymentId}/cancellation-guidance",
    "purpose": "해지 방법 안내만 생성",
    "domain": "정기납부·구독·청구",
    "domainId": "정기납부-구독-청구",
    "priority": "P2",
    "boundary": "REFERENCE_ONLY",
    "implementation": "REFERENCE_ONLY",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "recurringPaymentId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/beneficiaries",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/beneficiaries",
    "purpose": "마스킹된 수취인 목록",
    "domain": "이체·지급",
    "domainId": "이체-지급",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/transfer-limits",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/transfer-limits",
    "purpose": "금융회사 이체한도 조회",
    "domain": "이체·지급",
    "domainId": "이체-지급",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/transfer-simulations",
    "method": "POST",
    "path": "/api/v1/transfer-simulations",
    "purpose": "합성 이체 결과·수수료 모의계산",
    "domain": "이체·지급",
    "domainId": "이체-지급",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/transfer-validations",
    "method": "POST",
    "path": "/api/v1/transfer-validations",
    "purpose": "형식·정책 사전검사, 실행 없음",
    "domain": "이체·지급",
    "domainId": "이체-지급",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/transfer-templates",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/transfer-templates",
    "purpose": "고객 저장 이체 양식 (`IMPLEMENTED`)",
    "domain": "이체·지급",
    "domainId": "이체-지급",
    "priority": "P2",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/customers/{customerId}/transfer-templates",
    "method": "POST",
    "path": "/api/v1/customers/{customerId}/transfer-templates",
    "purpose": "이체 양식 저장 (`IMPLEMENTED`)",
    "domain": "이체·지급",
    "domainId": "이체-지급",
    "priority": "P2",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "DELETE /api/v1/customers/{customerId}/transfer-templates/{templateId}",
    "method": "DELETE",
    "path": "/api/v1/customers/{customerId}/transfer-templates/{templateId}",
    "purpose": "이체 양식 삭제 (`IMPLEMENTED`)",
    "domain": "이체·지급",
    "domainId": "이체-지급",
    "priority": "P2",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId",
      "templateId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/transfers",
    "method": "POST",
    "path": "/api/v1/transfers",
    "purpose": "실제 이체 접수 기능 참조",
    "domain": "이체·지급",
    "domainId": "이체-지급",
    "priority": "P2",
    "boundary": "REFERENCE_ONLY",
    "implementation": "REFERENCE_ONLY",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/transfers/{transferId}/confirm",
    "method": "POST",
    "path": "/api/v1/transfers/{transferId}/confirm",
    "purpose": "실제 이체 승인 기능 참조",
    "domain": "이체·지급",
    "domainId": "이체-지급",
    "priority": "P2",
    "boundary": "REFERENCE_ONLY",
    "implementation": "REFERENCE_ONLY",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "transferId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/transfers/{transferId}/cancel",
    "method": "POST",
    "path": "/api/v1/transfers/{transferId}/cancel",
    "purpose": "이체 취소 기능 참조",
    "domain": "이체·지급",
    "domainId": "이체-지급",
    "priority": "P2",
    "boundary": "REFERENCE_ONLY",
    "implementation": "REFERENCE_ONLY",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "transferId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/cards",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/cards",
    "purpose": "마스킹된 보유 카드",
    "domain": "카드",
    "domainId": "카드",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/cards/{cardId}",
    "method": "GET",
    "path": "/api/v1/cards/{cardId}",
    "purpose": "카드 상태·결제일·브랜드",
    "domain": "카드",
    "domainId": "카드",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "cardId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/cards/{cardId}/transactions",
    "method": "GET",
    "path": "/api/v1/cards/{cardId}/transactions",
    "purpose": "카드 이용내역",
    "domain": "카드",
    "domainId": "카드",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "cardId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/cards/{cardId}/statements",
    "method": "GET",
    "path": "/api/v1/cards/{cardId}/statements",
    "purpose": "카드 청구서",
    "domain": "카드",
    "domainId": "카드",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "cardId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/cards/{cardId}/payment-due",
    "method": "GET",
    "path": "/api/v1/cards/{cardId}/payment-due",
    "purpose": "결제예정 금액",
    "domain": "카드",
    "domainId": "카드",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "cardId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/cards/{cardId}/limits",
    "method": "GET",
    "path": "/api/v1/cards/{cardId}/limits",
    "purpose": "이용한도 조회",
    "domain": "카드",
    "domainId": "카드",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "cardId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/cards/{cardId}/benefits",
    "method": "GET",
    "path": "/api/v1/cards/{cardId}/benefits",
    "purpose": "혜택·실적 정보",
    "domain": "카드",
    "domainId": "카드",
    "priority": "P2",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "PLANNED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "cardId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/cards/{cardId}/lock",
    "method": "POST",
    "path": "/api/v1/cards/{cardId}/lock",
    "purpose": "카드 사용정지 기능 참조",
    "domain": "카드",
    "domainId": "카드",
    "priority": "P2",
    "boundary": "REFERENCE_ONLY",
    "implementation": "REFERENCE_ONLY",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "cardId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/cards/{cardId}/unlock",
    "method": "POST",
    "path": "/api/v1/cards/{cardId}/unlock",
    "purpose": "카드 정지해제 기능 참조",
    "domain": "카드",
    "domainId": "카드",
    "priority": "P2",
    "boundary": "REFERENCE_ONLY",
    "implementation": "REFERENCE_ONLY",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "cardId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/cards/{cardId}/replacement-requests",
    "method": "POST",
    "path": "/api/v1/cards/{cardId}/replacement-requests",
    "purpose": "재발급 기능 참조",
    "domain": "카드",
    "domainId": "카드",
    "priority": "P2",
    "boundary": "REFERENCE_ONLY",
    "implementation": "REFERENCE_ONLY",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "cardId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/deposit-products",
    "method": "GET",
    "path": "/api/v1/deposit-products",
    "purpose": "예금·적금 상품 목록",
    "domain": "예금·적금",
    "domainId": "예금-적금",
    "priority": "P2",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/deposit-products/{productId}",
    "method": "GET",
    "path": "/api/v1/deposit-products/{productId}",
    "purpose": "상품 조건·유의사항",
    "domain": "예금·적금",
    "domainId": "예금-적금",
    "priority": "P2",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "productId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/deposit-products/{productId}/rates",
    "method": "GET",
    "path": "/api/v1/deposit-products/{productId}/rates",
    "purpose": "적용 금리표",
    "domain": "예금·적금",
    "domainId": "예금-적금",
    "priority": "P2",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "productId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/deposit-products/{productId}/interest-simulations",
    "method": "POST",
    "path": "/api/v1/deposit-products/{productId}/interest-simulations",
    "purpose": "비개인화 이자 계산",
    "domain": "예금·적금",
    "domainId": "예금-적금",
    "priority": "P2",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "productId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/deposit-holdings",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/deposit-holdings",
    "purpose": "보유 예금·적금 목록",
    "domain": "예금·적금",
    "domainId": "예금-적금",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/deposit-holdings/{holdingId}",
    "method": "GET",
    "path": "/api/v1/deposit-holdings/{holdingId}",
    "purpose": "보유상품 잔액·만기",
    "domain": "예금·적금",
    "domainId": "예금-적금",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "holdingId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/deposit-holdings/{holdingId}/maturity-options",
    "method": "GET",
    "path": "/api/v1/deposit-holdings/{holdingId}/maturity-options",
    "purpose": "만기 처리 선택지 조회",
    "domain": "예금·적금",
    "domainId": "예금-적금",
    "priority": "P2",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "holdingId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/deposit-applications",
    "method": "POST",
    "path": "/api/v1/deposit-applications",
    "purpose": "계좌개설 기능 참조",
    "domain": "예금·적금",
    "domainId": "예금-적금",
    "priority": "P2",
    "boundary": "REFERENCE_ONLY",
    "implementation": "REFERENCE_ONLY",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/loan-products",
    "method": "GET",
    "path": "/api/v1/loan-products",
    "purpose": "대출 상품 목록",
    "domain": "대출·신용",
    "domainId": "대출-신용",
    "priority": "P2",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/loan-products/{productId}",
    "method": "GET",
    "path": "/api/v1/loan-products/{productId}",
    "purpose": "금리·조건·유의사항",
    "domain": "대출·신용",
    "domainId": "대출-신용",
    "priority": "P2",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "productId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/loan-products/{productId}/repayment-simulations",
    "method": "POST",
    "path": "/api/v1/loan-products/{productId}/repayment-simulations",
    "purpose": "비개인화 상환 계산",
    "domain": "대출·신용",
    "domainId": "대출-신용",
    "priority": "P2",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "productId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/loan-holdings",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/loan-holdings",
    "purpose": "보유 대출 목록",
    "domain": "대출·신용",
    "domainId": "대출-신용",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/loan-holdings/{loanId}",
    "method": "GET",
    "path": "/api/v1/loan-holdings/{loanId}",
    "purpose": "대출 잔액·조건",
    "domain": "대출·신용",
    "domainId": "대출-신용",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "loanId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/loan-holdings/{loanId}/repayment-schedule",
    "method": "GET",
    "path": "/api/v1/loan-holdings/{loanId}/repayment-schedule",
    "purpose": "원리금 상환 일정",
    "domain": "대출·신용",
    "domainId": "대출-신용",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "loanId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/loan-applications",
    "method": "POST",
    "path": "/api/v1/loan-applications",
    "purpose": "대출 신청 기능 참조",
    "domain": "대출·신용",
    "domainId": "대출-신용",
    "priority": "P2",
    "boundary": "REFERENCE_ONLY",
    "implementation": "REFERENCE_ONLY",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/loan-applications/{applicationId}/submit",
    "method": "POST",
    "path": "/api/v1/loan-applications/{applicationId}/submit",
    "purpose": "대출 심사 제출 기능 참조",
    "domain": "대출·신용",
    "domainId": "대출-신용",
    "priority": "P2",
    "boundary": "REFERENCE_ONLY",
    "implementation": "REFERENCE_ONLY",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "applicationId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/investment-accounts",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/investment-accounts",
    "purpose": "증권계좌 목록",
    "domain": "투자·증권",
    "domainId": "투자-증권",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/investment-accounts/{accountId}/portfolio",
    "method": "GET",
    "path": "/api/v1/investment-accounts/{accountId}/portfolio",
    "purpose": "자산배분·평가액",
    "domain": "투자·증권",
    "domainId": "투자-증권",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "accountId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/investment-accounts/{accountId}/positions",
    "method": "GET",
    "path": "/api/v1/investment-accounts/{accountId}/positions",
    "purpose": "종목별 보유내역",
    "domain": "투자·증권",
    "domainId": "투자-증권",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "accountId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/investment-accounts/{accountId}/orders",
    "method": "GET",
    "path": "/api/v1/investment-accounts/{accountId}/orders",
    "purpose": "주문·체결 이력",
    "domain": "투자·증권",
    "domainId": "투자-증권",
    "priority": "P2",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "accountId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/market-instruments/{instrumentId}/quote",
    "method": "GET",
    "path": "/api/v1/market-instruments/{instrumentId}/quote",
    "purpose": "종목 시세",
    "domain": "투자·증권",
    "domainId": "투자-증권",
    "priority": "P2",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "instrumentId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/market-instruments/{instrumentId}/chart",
    "method": "GET",
    "path": "/api/v1/market-instruments/{instrumentId}/chart",
    "purpose": "종목 차트 데이터",
    "domain": "투자·증권",
    "domainId": "투자-증권",
    "priority": "P2",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "instrumentId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/watchlist",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/watchlist",
    "purpose": "관심종목",
    "domain": "투자·증권",
    "domainId": "투자-증권",
    "priority": "P2",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "PUT /api/v1/customers/{customerId}/watchlist",
    "method": "PUT",
    "path": "/api/v1/customers/{customerId}/watchlist",
    "purpose": "관심종목 변경",
    "domain": "투자·증권",
    "domainId": "투자-증권",
    "priority": "P2",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/investment-orders",
    "method": "POST",
    "path": "/api/v1/investment-orders",
    "purpose": "실제 매매 주문 기능 참조",
    "domain": "투자·증권",
    "domainId": "투자-증권",
    "priority": "P2",
    "boundary": "REFERENCE_ONLY",
    "implementation": "REFERENCE_ONLY",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "DELETE /api/v1/investment-orders/{orderId}",
    "method": "DELETE",
    "path": "/api/v1/investment-orders/{orderId}",
    "purpose": "주문 취소 기능 참조",
    "domain": "투자·증권",
    "domainId": "투자-증권",
    "priority": "P2",
    "boundary": "REFERENCE_ONLY",
    "implementation": "REFERENCE_ONLY",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "orderId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/pension-holdings",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/pension-holdings",
    "purpose": "연금 보유현황",
    "domain": "연금·신탁·보호수단",
    "domainId": "연금-신탁-보호수단",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/pension-holdings/{holdingId}/projection",
    "method": "GET",
    "path": "/api/v1/pension-holdings/{holdingId}/projection",
    "purpose": "금융사 제공 연금 전망",
    "domain": "연금·신탁·보호수단",
    "domainId": "연금-신탁-보호수단",
    "priority": "P2",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "holdingId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/trust-holdings",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/trust-holdings",
    "purpose": "신탁 보유현황",
    "domain": "연금·신탁·보호수단",
    "domainId": "연금-신탁-보호수단",
    "priority": "P2",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/trust-holdings/{trustId}",
    "method": "GET",
    "path": "/api/v1/trust-holdings/{trustId}",
    "purpose": "신탁 계약 상세",
    "domain": "연금·신탁·보호수단",
    "domainId": "연금-신탁-보호수단",
    "priority": "P2",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "trustId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/protection-actions",
    "method": "GET",
    "path": "/api/v1/protection-actions",
    "purpose": "공식 보호수단 카탈로그",
    "domain": "연금·신탁·보호수단",
    "domainId": "연금-신탁-보호수단",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/protection-actions/{actionCode}",
    "method": "GET",
    "path": "/api/v1/protection-actions/{actionCode}",
    "purpose": "출처·시행일·적용조건",
    "domain": "연금·신탁·보호수단",
    "domainId": "연금-신탁-보호수단",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "actionCode"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/protection-actions/{actionCode}/eligibility-evaluations",
    "method": "POST",
    "path": "/api/v1/protection-actions/{actionCode}/eligibility-evaluations",
    "purpose": "규칙 기반 안내 가능성 평가",
    "domain": "연금·신탁·보호수단",
    "domainId": "연금-신탁-보호수단",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "actionCode"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/protection-enrollments",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/protection-enrollments",
    "purpose": "금융사 보호수단 가입상태 읽기",
    "domain": "연금·신탁·보호수단",
    "domainId": "연금-신탁-보호수단",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/protection-enrollments",
    "method": "POST",
    "path": "/api/v1/protection-enrollments",
    "purpose": "실제 보호수단 신청 참조",
    "domain": "연금·신탁·보호수단",
    "domainId": "연금-신탁-보호수단",
    "priority": "P2",
    "boundary": "REFERENCE_ONLY",
    "implementation": "REFERENCE_ONLY",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "DELETE /api/v1/protection-enrollments/{enrollmentId}",
    "method": "DELETE",
    "path": "/api/v1/protection-enrollments/{enrollmentId}",
    "purpose": "실제 해지 기능 참조",
    "domain": "연금·신탁·보호수단",
    "domainId": "연금-신탁-보호수단",
    "priority": "P2",
    "boundary": "REFERENCE_ONLY",
    "implementation": "REFERENCE_ONLY",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "enrollmentId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/consents",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/consents",
    "purpose": "유효한 동의 목록",
    "domain": "동의·신뢰연락인·정보제공",
    "domainId": "동의-신뢰연락인-정보제공",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/consents/{consentId}",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/consents/{consentId}",
    "purpose": "범위·기간·철회조건",
    "domain": "동의·신뢰연락인·정보제공",
    "domainId": "동의-신뢰연락인-정보제공",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId",
      "consentId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/customers/{customerId}/consents",
    "method": "POST",
    "path": "/api/v1/customers/{customerId}/consents",
    "purpose": "세분화된 동의 등록",
    "domain": "동의·신뢰연락인·정보제공",
    "domainId": "동의-신뢰연락인-정보제공",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/customers/{customerId}/consents/{consentId}/withdraw",
    "method": "POST",
    "path": "/api/v1/customers/{customerId}/consents/{consentId}/withdraw",
    "purpose": "동의 철회",
    "domain": "동의·신뢰연락인·정보제공",
    "domainId": "동의-신뢰연락인-정보제공",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId",
      "consentId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/consents/{consentId}/history",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/consents/{consentId}/history",
    "purpose": "동의 변경 불변 이력",
    "domain": "동의·신뢰연락인·정보제공",
    "domainId": "동의-신뢰연락인-정보제공",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId",
      "consentId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/customers/{customerId}/disclosure-evaluations",
    "method": "POST",
    "path": "/api/v1/customers/{customerId}/disclosure-evaluations",
    "purpose": "정보제공 가능 여부 정책 평가",
    "domain": "동의·신뢰연락인·정보제공",
    "domainId": "동의-신뢰연락인-정보제공",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/trusted-contacts",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/trusted-contacts",
    "purpose": "신뢰연락인과 권한 없는 상태 표시",
    "domain": "동의·신뢰연락인·정보제공",
    "domainId": "동의-신뢰연락인-정보제공",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/customers/{customerId}/trusted-contacts",
    "method": "POST",
    "path": "/api/v1/customers/{customerId}/trusted-contacts",
    "purpose": "신뢰연락인 지정",
    "domain": "동의·신뢰연락인·정보제공",
    "domainId": "동의-신뢰연락인-정보제공",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/trusted-contacts/{contactId}",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/trusted-contacts/{contactId}",
    "purpose": "동의 범위·유효기간 조회",
    "domain": "동의·신뢰연락인·정보제공",
    "domainId": "동의-신뢰연락인-정보제공",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId",
      "contactId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "PATCH /api/v1/customers/{customerId}/trusted-contacts/{contactId}",
    "method": "PATCH",
    "path": "/api/v1/customers/{customerId}/trusted-contacts/{contactId}",
    "purpose": "최소정보 범위 수정",
    "domain": "동의·신뢰연락인·정보제공",
    "domainId": "동의-신뢰연락인-정보제공",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId",
      "contactId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/customers/{customerId}/trusted-contacts/{contactId}/revoke",
    "method": "POST",
    "path": "/api/v1/customers/{customerId}/trusted-contacts/{contactId}/revoke",
    "purpose": "JSON 본문으로 지정 철회",
    "domain": "동의·신뢰연락인·정보제공",
    "domainId": "동의-신뢰연락인-정보제공",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId",
      "contactId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/customers/{customerId}/trusted-contacts/{contactId}/contact-attempts",
    "method": "POST",
    "path": "/api/v1/customers/{customerId}/trusted-contacts/{contactId}/contact-attempts",
    "purpose": "실제 외부 연락 기능 참조",
    "domain": "동의·신뢰연락인·정보제공",
    "domainId": "동의-신뢰연락인-정보제공",
    "priority": "P2",
    "boundary": "REFERENCE_ONLY",
    "implementation": "REFERENCE_ONLY",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId",
      "contactId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/baselines",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/baselines",
    "purpose": "고객 개인 기준선 목록",
    "domain": "기준선·신호·경보·생활맥락",
    "domainId": "기준선-신호-경보-생활맥락",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/baselines/{baselineId}",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/baselines/{baselineId}",
    "purpose": "기준기간·준비상태·버전",
    "domain": "기준선·신호·경보·생활맥락",
    "domainId": "기준선-신호-경보-생활맥락",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId",
      "baselineId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/baselines/{baselineId}/features",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/baselines/{baselineId}/features",
    "purpose": "기준선 특징값",
    "domain": "기준선·신호·경보·생활맥락",
    "domainId": "기준선-신호-경보-생활맥락",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId",
      "baselineId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/customers/{customerId}/baseline-calculations",
    "method": "POST",
    "path": "/api/v1/customers/{customerId}/baseline-calculations",
    "purpose": "기준선 계산 작업 생성",
    "domain": "기준선·신호·경보·생활맥락",
    "domainId": "기준선-신호-경보-생활맥락",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/signals",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/signals",
    "purpose": "변화신호 목록",
    "domain": "기준선·신호·경보·생활맥락",
    "domainId": "기준선-신호-경보-생활맥락",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/signals/{signalId}",
    "method": "GET",
    "path": "/api/v1/signals/{signalId}",
    "purpose": "평소값·현재값·사유코드",
    "domain": "기준선·신호·경보·생활맥락",
    "domainId": "기준선-신호-경보-생활맥락",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "signalId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/signals/{signalId}/evidence",
    "method": "GET",
    "path": "/api/v1/signals/{signalId}/evidence",
    "purpose": "불변 근거 거래",
    "domain": "기준선·신호·경보·생활맥락",
    "domainId": "기준선-신호-경보-생활맥락",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "signalId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/alerts",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/alerts",
    "purpose": "운영 고객 경보 목록",
    "domain": "기준선·신호·경보·생활맥락",
    "domainId": "기준선-신호-경보-생활맥락",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/alerts/{alertId}",
    "method": "GET",
    "path": "/api/v1/alerts/{alertId}",
    "purpose": "운영 경보 상세",
    "domain": "기준선·신호·경보·생활맥락",
    "domainId": "기준선-신호-경보-생활맥락",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "alertId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/alerts/{alertId}/context-options",
    "method": "GET",
    "path": "/api/v1/alerts/{alertId}/context-options",
    "purpose": "허용 고객 응답·질문",
    "domain": "기준선·신호·경보·생활맥락",
    "domainId": "기준선-신호-경보-생활맥락",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "alertId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/alerts/{alertId}/context-responses",
    "method": "POST",
    "path": "/api/v1/alerts/{alertId}/context-responses",
    "purpose": "운영 생활맥락 제출·재평가",
    "domain": "기준선·신호·경보·생활맥락",
    "domainId": "기준선-신호-경보-생활맥락",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "alertId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/alerts/{alertId}/defer",
    "method": "POST",
    "path": "/api/v1/alerts/{alertId}/defer",
    "purpose": "확인 연기",
    "domain": "기준선·신호·경보·생활맥락",
    "domainId": "기준선-신호-경보-생활맥락",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "alertId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/alerts/{alertId}/appeals",
    "method": "POST",
    "path": "/api/v1/alerts/{alertId}/appeals",
    "purpose": "고객 이의·재검토 요청",
    "domain": "기준선·신호·경보·생활맥락",
    "domainId": "기준선-신호-경보-생활맥락",
    "priority": "P2",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "alertId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/alerts/{alertId}/audit",
    "method": "GET",
    "path": "/api/v1/alerts/{alertId}/audit",
    "purpose": "운영 경보 판단이력",
    "domain": "기준선·신호·경보·생활맥락",
    "domainId": "기준선-신호-경보-생활맥락",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "alertId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/demo/sessions/{sessionId}/customers/{customerId}/alerts",
    "method": "GET",
    "path": "/api/v1/demo/sessions/{sessionId}/customers/{customerId}/alerts",
    "purpose": "기존 데모 고객 경보 목록",
    "domain": "기준선·신호·경보·생활맥락",
    "domainId": "기준선-신호-경보-생활맥락",
    "priority": "P0-A",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "PUBLIC",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId",
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/demo/sessions/{sessionId}/alerts/{alertId}",
    "method": "GET",
    "path": "/api/v1/demo/sessions/{sessionId}/alerts/{alertId}",
    "purpose": "기존 데모 경보 상세",
    "domain": "기준선·신호·경보·생활맥락",
    "domainId": "기준선-신호-경보-생활맥락",
    "priority": "P0-A",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "PUBLIC",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId",
      "alertId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/demo/sessions/{sessionId}/alerts/{alertId}/context",
    "method": "POST",
    "path": "/api/v1/demo/sessions/{sessionId}/alerts/{alertId}/context",
    "purpose": "기존 데모 맥락 응답·재평가",
    "domain": "기준선·신호·경보·생활맥락",
    "domainId": "기준선-신호-경보-생활맥락",
    "priority": "P0-A",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "PUBLIC",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId",
      "alertId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/demo/sessions/{sessionId}/alerts/{alertId}/audit",
    "method": "GET",
    "path": "/api/v1/demo/sessions/{sessionId}/alerts/{alertId}/audit",
    "purpose": "기존 데모 판단·동의 감사이력",
    "domain": "기준선·신호·경보·생활맥락",
    "domainId": "기준선-신호-경보-생활맥락",
    "priority": "P0-A",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "PUBLIC",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId",
      "alertId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/staff/cases",
    "method": "GET",
    "path": "/api/v1/staff/cases",
    "purpose": "운영 행원 사건큐",
    "domain": "행원 사건·코파일럿·후속관리",
    "domainId": "행원-사건-코파일럿-후속관리",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "STAFF",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/staff/cases/{caseId}",
    "method": "GET",
    "path": "/api/v1/staff/cases/{caseId}",
    "purpose": "운영 사건 상세",
    "domain": "행원 사건·코파일럿·후속관리",
    "domainId": "행원-사건-코파일럿-후속관리",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "STAFF",
    "authorityMode": "BEARER",
    "pathParameters": [
      "caseId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "PUT /api/v1/staff/cases/{caseId}/assignment",
    "method": "PUT",
    "path": "/api/v1/staff/cases/{caseId}/assignment",
    "purpose": "담당자·팀 배정",
    "domain": "행원 사건·코파일럿·후속관리",
    "domainId": "행원-사건-코파일럿-후속관리",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "STAFF",
    "authorityMode": "BEARER",
    "pathParameters": [
      "caseId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/staff/cases/{caseId}/timeline",
    "method": "GET",
    "path": "/api/v1/staff/cases/{caseId}/timeline",
    "purpose": "운영 사건·경보·검토 통합 타임라인",
    "domain": "행원 사건·코파일럿·후속관리",
    "domainId": "행원-사건-코파일럿-후속관리",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "STAFF",
    "authorityMode": "BEARER",
    "pathParameters": [
      "caseId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/staff/cases/{caseId}/evidence",
    "method": "GET",
    "path": "/api/v1/staff/cases/{caseId}/evidence",
    "purpose": "운영 사건의 불변 합성 근거 묶음",
    "domain": "행원 사건·코파일럿·후속관리",
    "domainId": "행원-사건-코파일럿-후속관리",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "STAFF",
    "authorityMode": "BEARER",
    "pathParameters": [
      "caseId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/staff/cases/{caseId}/notes",
    "method": "GET",
    "path": "/api/v1/staff/cases/{caseId}/notes",
    "purpose": "운영 사건 추가 전용 내부 메모 목록",
    "domain": "행원 사건·코파일럿·후속관리",
    "domainId": "행원-사건-코파일럿-후속관리",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "STAFF",
    "authorityMode": "BEARER",
    "pathParameters": [
      "caseId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/staff/cases/{caseId}/notes",
    "method": "POST",
    "path": "/api/v1/staff/cases/{caseId}/notes",
    "purpose": "운영 사건 내부 메모 등록",
    "domain": "행원 사건·코파일럿·후속관리",
    "domainId": "행원-사건-코파일럿-후속관리",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "STAFF",
    "authorityMode": "BEARER",
    "pathParameters": [
      "caseId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/staff/cases/{caseId}/follow-ups",
    "method": "GET",
    "path": "/api/v1/staff/cases/{caseId}/follow-ups",
    "purpose": "운영 사건 후속 일정 목록",
    "domain": "행원 사건·코파일럿·후속관리",
    "domainId": "행원-사건-코파일럿-후속관리",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "STAFF",
    "authorityMode": "BEARER",
    "pathParameters": [
      "caseId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/staff/cases/{caseId}/follow-ups",
    "method": "POST",
    "path": "/api/v1/staff/cases/{caseId}/follow-ups",
    "purpose": "외부 연락 없는 운영 후속 일정 등록",
    "domain": "행원 사건·코파일럿·후속관리",
    "domainId": "행원-사건-코파일럿-후속관리",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "STAFF",
    "authorityMode": "BEARER",
    "pathParameters": [
      "caseId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "PATCH /api/v1/staff/follow-ups/{followUpId}",
    "method": "PATCH",
    "path": "/api/v1/staff/follow-ups/{followUpId}",
    "purpose": "운영 후속 일정·결과 상태 변경",
    "domain": "행원 사건·코파일럿·후속관리",
    "domainId": "행원-사건-코파일럿-후속관리",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "STAFF",
    "authorityMode": "BEARER",
    "pathParameters": [
      "followUpId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/demo/sessions/{sessionId}/cases/{caseId}/timeline",
    "method": "GET",
    "path": "/api/v1/demo/sessions/{sessionId}/cases/{caseId}/timeline",
    "purpose": "사건·신호·맥락·감사 타임라인",
    "domain": "행원 사건·코파일럿·후속관리",
    "domainId": "행원-사건-코파일럿-후속관리",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "STAFF",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId",
      "caseId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/demo/sessions/{sessionId}/cases/{caseId}/evidence",
    "method": "GET",
    "path": "/api/v1/demo/sessions/{sessionId}/cases/{caseId}/evidence",
    "purpose": "합성 근거 거래·신호·공식 출처 묶음",
    "domain": "행원 사건·코파일럿·후속관리",
    "domainId": "행원-사건-코파일럿-후속관리",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "STAFF",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId",
      "caseId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/demo/sessions/{sessionId}/cases/{caseId}/notes",
    "method": "GET",
    "path": "/api/v1/demo/sessions/{sessionId}/cases/{caseId}/notes",
    "purpose": "행원 내부 메모 목록",
    "domain": "행원 사건·코파일럿·후속관리",
    "domainId": "행원-사건-코파일럿-후속관리",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "STAFF",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId",
      "caseId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/staff/cases/{caseId}/reviews",
    "method": "POST",
    "path": "/api/v1/staff/cases/{caseId}/reviews",
    "purpose": "검토 상태전이",
    "domain": "행원 사건·코파일럿·후속관리",
    "domainId": "행원-사건-코파일럿-후속관리",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "STAFF",
    "authorityMode": "BEARER",
    "pathParameters": [
      "caseId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/demo/sessions/{sessionId}/cases/{caseId}/notes",
    "method": "POST",
    "path": "/api/v1/demo/sessions/{sessionId}/cases/{caseId}/notes",
    "purpose": "행원 내부 메모 등록",
    "domain": "행원 사건·코파일럿·후속관리",
    "domainId": "행원-사건-코파일럿-후속관리",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "STAFF",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId",
      "caseId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/demo/sessions/{sessionId}/cases/{caseId}/copilot-drafts",
    "method": "POST",
    "path": "/api/v1/demo/sessions/{sessionId}/cases/{caseId}/copilot-drafts",
    "purpose": "결정론적 질문·상담기록 초안 생성",
    "domain": "행원 사건·코파일럿·후속관리",
    "domainId": "행원-사건-코파일럿-후속관리",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "STAFF",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId",
      "caseId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/demo/sessions/{sessionId}/cases/{caseId}/follow-ups",
    "method": "GET",
    "path": "/api/v1/demo/sessions/{sessionId}/cases/{caseId}/follow-ups",
    "purpose": "내부 재확인 일정 목록",
    "domain": "행원 사건·코파일럿·후속관리",
    "domainId": "행원-사건-코파일럿-후속관리",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "STAFF",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId",
      "caseId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/demo/sessions/{sessionId}/cases/{caseId}/follow-ups",
    "method": "POST",
    "path": "/api/v1/demo/sessions/{sessionId}/cases/{caseId}/follow-ups",
    "purpose": "재확인 일정만 등록",
    "domain": "행원 사건·코파일럿·후속관리",
    "domainId": "행원-사건-코파일럿-후속관리",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "STAFF",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId",
      "caseId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "PATCH /api/v1/demo/sessions/{sessionId}/staff/follow-ups/{followUpId}",
    "method": "PATCH",
    "path": "/api/v1/demo/sessions/{sessionId}/staff/follow-ups/{followUpId}",
    "purpose": "후속 일정·결과 갱신",
    "domain": "행원 사건·코파일럿·후속관리",
    "domainId": "행원-사건-코파일럿-후속관리",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "STAFF",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId",
      "followUpId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/staff/cases/{caseId}/guidance-plans",
    "method": "POST",
    "path": "/api/v1/staff/cases/{caseId}/guidance-plans",
    "purpose": "안내계획 승인, 실제 조치 아님",
    "domain": "행원 사건·코파일럿·후속관리",
    "domainId": "행원-사건-코파일럿-후속관리",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "STAFF",
    "authorityMode": "BEARER",
    "pathParameters": [
      "caseId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/staff/cases/{caseId}/overrides",
    "method": "POST",
    "path": "/api/v1/staff/cases/{caseId}/overrides",
    "purpose": "정책 결과에 대한 사유 있는 직원 재검토",
    "domain": "행원 사건·코파일럿·후속관리",
    "domainId": "행원-사건-코파일럿-후속관리",
    "priority": "P2",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "STAFF",
    "authorityMode": "BEARER",
    "pathParameters": [
      "caseId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/demo/sessions/{sessionId}/staff/cases",
    "method": "GET",
    "path": "/api/v1/demo/sessions/{sessionId}/staff/cases",
    "purpose": "기존 데모 행원 사건큐",
    "domain": "행원 사건·코파일럿·후속관리",
    "domainId": "행원-사건-코파일럿-후속관리",
    "priority": "P0-A",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "STAFF",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/demo/sessions/{sessionId}/cases/{caseId}",
    "method": "GET",
    "path": "/api/v1/demo/sessions/{sessionId}/cases/{caseId}",
    "purpose": "기존 데모 사건 상세·초안",
    "domain": "행원 사건·코파일럿·후속관리",
    "domainId": "행원-사건-코파일럿-후속관리",
    "priority": "P0-A",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "STAFF",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId",
      "caseId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/demo/sessions/{sessionId}/cases/{caseId}/review",
    "method": "POST",
    "path": "/api/v1/demo/sessions/{sessionId}/cases/{caseId}/review",
    "purpose": "기존 데모 검토 상태전이",
    "domain": "행원 사건·코파일럿·후속관리",
    "domainId": "행원-사건-코파일럿-후속관리",
    "priority": "P0-A",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "STAFF",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId",
      "caseId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/demo/sessions/{sessionId}/cases/{caseId}/guidance-plan",
    "method": "POST",
    "path": "/api/v1/demo/sessions/{sessionId}/cases/{caseId}/guidance-plan",
    "purpose": "기존 데모 안내계획 승인",
    "domain": "행원 사건·코파일럿·후속관리",
    "domainId": "행원-사건-코파일럿-후속관리",
    "priority": "P0-A",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "STAFF",
    "authorityMode": "DEMO_CAPABILITY",
    "pathParameters": [
      "sessionId",
      "caseId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/staff-access-grants",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/staff-access-grants",
    "purpose": "고객 데이터에 접근 가능한 행원 권한 목록",
    "domain": "고객별 행원 접근권한",
    "domainId": "고객별-행원-접근권한",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/customers/{customerId}/staff-access-grants",
    "method": "POST",
    "path": "/api/v1/customers/{customerId}/staff-access-grants",
    "purpose": "목적·범위·만료를 지정한 내부 접근권 생성",
    "domain": "고객별 행원 접근권한",
    "domainId": "고객별-행원-접근권한",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/staff-access-grants/{grantId}",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/staff-access-grants/{grantId}",
    "purpose": "단일 접근권한 상세",
    "domain": "고객별 행원 접근권한",
    "domainId": "고객별-행원-접근권한",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId",
      "grantId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/customers/{customerId}/staff-access-grants/{grantId}/revoke",
    "method": "POST",
    "path": "/api/v1/customers/{customerId}/staff-access-grants/{grantId}/revoke",
    "purpose": "접근권한 철회",
    "domain": "고객별 행원 접근권한",
    "domainId": "고객별-행원-접근권한",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId",
      "grantId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/staff-access-policy/evaluations",
    "method": "POST",
    "path": "/api/v1/staff-access-policy/evaluations",
    "purpose": "행원·고객·목적·범위별 접근 가능성 평가",
    "domain": "고객별 행원 접근권한",
    "domainId": "고객별-행원-접근권한",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "STAFF",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/staff-access-grants/{grantId}/audit",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/staff-access-grants/{grantId}/audit",
    "purpose": "생성·사용·만료·철회 감사이력",
    "domain": "고객별 행원 접근권한",
    "domainId": "고객별-행원-접근권한",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId",
      "grantId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/knowledge/documents",
    "method": "GET",
    "path": "/api/v1/knowledge/documents",
    "purpose": "승인된 공식 문서 목록",
    "domain": "공식 근거·지식 카탈로그",
    "domainId": "공식-근거-지식-카탈로그",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/knowledge/documents/{documentId}",
    "method": "GET",
    "path": "/api/v1/knowledge/documents/{documentId}",
    "purpose": "출처·시행일·체크섬",
    "domain": "공식 근거·지식 카탈로그",
    "domainId": "공식-근거-지식-카탈로그",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "documentId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/knowledge/documents/{documentId}/versions",
    "method": "GET",
    "path": "/api/v1/knowledge/documents/{documentId}/versions",
    "purpose": "문서 버전 이력",
    "domain": "공식 근거·지식 카탈로그",
    "domainId": "공식-근거-지식-카탈로그",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "documentId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/knowledge/search",
    "method": "POST",
    "path": "/api/v1/knowledge/search",
    "purpose": "권한·효력기간을 적용한 검색",
    "domain": "공식 근거·지식 카탈로그",
    "domainId": "공식-근거-지식-카탈로그",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/knowledge/passages/{passageId}",
    "method": "GET",
    "path": "/api/v1/knowledge/passages/{passageId}",
    "purpose": "인용 가능한 조항·페이지",
    "domain": "공식 근거·지식 카탈로그",
    "domainId": "공식-근거-지식-카탈로그",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "passageId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/guidance-candidates",
    "method": "GET",
    "path": "/api/v1/guidance-candidates",
    "purpose": "정책이 고른 보호수단 후보",
    "domain": "공식 근거·지식 카탈로그",
    "domainId": "공식-근거-지식-카탈로그",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/admin/knowledge/documents",
    "method": "POST",
    "path": "/api/v1/admin/knowledge/documents",
    "purpose": "공식 자료 검토등록 (`IMPLEMENTED`)",
    "domain": "공식 근거·지식 카탈로그",
    "domainId": "공식-근거-지식-카탈로그",
    "priority": "P2",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "ADMIN",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/admin/knowledge/documents/{documentId}/publish",
    "method": "POST",
    "path": "/api/v1/admin/knowledge/documents/{documentId}/publish",
    "purpose": "검수 완료 버전 게시 (`IMPLEMENTED`)",
    "domain": "공식 근거·지식 카탈로그",
    "domainId": "공식-근거-지식-카탈로그",
    "priority": "P2",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "ADMIN",
    "authorityMode": "BEARER",
    "pathParameters": [
      "documentId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/admin/knowledge/ingestion-imports",
    "method": "POST",
    "path": "/api/v1/admin/knowledge/ingestion-imports",
    "purpose": "검증된 AI chunk를 Spring 권위 passage로 반영 (`IMPLEMENTED`)",
    "domain": "공식 근거·지식 카탈로그",
    "domainId": "공식-근거-지식-카탈로그",
    "priority": "P2",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "ADMIN",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/inbox",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/inbox",
    "purpose": "서비스 내부 알림함",
    "domain": "인앱 알림·고객지원",
    "domainId": "인앱-알림-고객지원",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/inbox/{messageId}",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/inbox/{messageId}",
    "purpose": "인앱 알림 상세",
    "domain": "인앱 알림·고객지원",
    "domainId": "인앱-알림-고객지원",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId",
      "messageId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/customers/{customerId}/inbox/{messageId}/read",
    "method": "POST",
    "path": "/api/v1/customers/{customerId}/inbox/{messageId}/read",
    "purpose": "읽음 처리",
    "domain": "인앱 알림·고객지원",
    "domainId": "인앱-알림-고객지원",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId",
      "messageId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/notification-preferences",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/notification-preferences",
    "purpose": "채널별 알림 설정",
    "domain": "인앱 알림·고객지원",
    "domainId": "인앱-알림-고객지원",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "PUT /api/v1/customers/{customerId}/notification-preferences",
    "method": "PUT",
    "path": "/api/v1/customers/{customerId}/notification-preferences",
    "purpose": "알림 설정 변경",
    "domain": "인앱 알림·고객지원",
    "domainId": "인앱-알림-고객지원",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/notification-previews",
    "method": "POST",
    "path": "/api/v1/notification-previews",
    "purpose": "외부 발송 없는 문구 미리보기",
    "domain": "인앱 알림·고객지원",
    "domainId": "인앱-알림-고객지원",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/support/faqs",
    "method": "GET",
    "path": "/api/v1/support/faqs",
    "purpose": "자주 묻는 질문 (`IMPLEMENTED`)",
    "domain": "인앱 알림·고객지원",
    "domainId": "인앱-알림-고객지원",
    "priority": "P2",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/support/notices",
    "method": "GET",
    "path": "/api/v1/support/notices",
    "purpose": "금융사 공지 조회 (`IMPLEMENTED`)",
    "domain": "인앱 알림·고객지원",
    "domainId": "인앱-알림-고객지원",
    "priority": "P2",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/support/inquiries",
    "method": "POST",
    "path": "/api/v1/support/inquiries",
    "purpose": "실제 문의 접수 기능 참조",
    "domain": "인앱 알림·고객지원",
    "domainId": "인앱-알림-고객지원",
    "priority": "P2",
    "boundary": "REFERENCE_ONLY",
    "implementation": "REFERENCE_ONLY",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/support/inquiries/{inquiryId}",
    "method": "GET",
    "path": "/api/v1/support/inquiries/{inquiryId}",
    "purpose": "실제 문의 진행상태 참조",
    "domain": "인앱 알림·고객지원",
    "domainId": "인앱-알림-고객지원",
    "priority": "P2",
    "boundary": "REFERENCE_ONLY",
    "implementation": "REFERENCE_ONLY",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "inquiryId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/audit/events",
    "method": "GET",
    "path": "/api/v1/audit/events",
    "purpose": "권한 기반 감사이벤트 검색",
    "domain": "감사·컴플라이언스·정보권리",
    "domainId": "감사-컴플라이언스-정보권리",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "ADMIN",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/audit/events/{eventId}",
    "method": "GET",
    "path": "/api/v1/audit/events/{eventId}",
    "purpose": "불변 감사이벤트 상세",
    "domain": "감사·컴플라이언스·정보권리",
    "domainId": "감사-컴플라이언스-정보권리",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "ADMIN",
    "authorityMode": "BEARER",
    "pathParameters": [
      "eventId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/audit/export-requests",
    "method": "POST",
    "path": "/api/v1/audit/export-requests",
    "purpose": "감사자료 내보내기 작업",
    "domain": "감사·컴플라이언스·정보권리",
    "domainId": "감사-컴플라이언스-정보권리",
    "priority": "P2",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "ADMIN",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/compliance/decision-traces/{decisionId}",
    "method": "GET",
    "path": "/api/v1/compliance/decision-traces/{decisionId}",
    "purpose": "전후 판단·규칙·근거 추적",
    "domain": "감사·컴플라이언스·정보권리",
    "domainId": "감사-컴플라이언스-정보권리",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "ADMIN",
    "authorityMode": "BEARER",
    "pathParameters": [
      "decisionId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/compliance/data-provenance/{resourceType}/{resourceId}",
    "method": "GET",
    "path": "/api/v1/compliance/data-provenance/{resourceType}/{resourceId}",
    "purpose": "데이터 출처·버전 확인",
    "domain": "감사·컴플라이언스·정보권리",
    "domainId": "감사-컴플라이언스-정보권리",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "ADMIN",
    "authorityMode": "BEARER",
    "pathParameters": [
      "resourceType",
      "resourceId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/compliance/retention-policies",
    "method": "GET",
    "path": "/api/v1/compliance/retention-policies",
    "purpose": "보존·파기 정책 조회",
    "domain": "감사·컴플라이언스·정보권리",
    "domainId": "감사-컴플라이언스-정보권리",
    "priority": "P2",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "ADMIN",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/customers/{customerId}/privacy/deletion-requests",
    "method": "POST",
    "path": "/api/v1/customers/{customerId}/privacy/deletion-requests",
    "purpose": "삭제 요청과 법적 예외 기록",
    "domain": "감사·컴플라이언스·정보권리",
    "domainId": "감사-컴플라이언스-정보권리",
    "priority": "P2",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/customers/{customerId}/privacy/correction-requests",
    "method": "POST",
    "path": "/api/v1/customers/{customerId}/privacy/correction-requests",
    "purpose": "데이터 정정 요청",
    "domain": "감사·컴플라이언스·정보권리",
    "domainId": "감사-컴플라이언스-정보권리",
    "priority": "P2",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/admin/rules",
    "method": "GET",
    "path": "/api/v1/admin/rules",
    "purpose": "탐지·상태전이 규칙 목록",
    "domain": "관리자 규칙·정책·모델",
    "domainId": "관리자-규칙-정책-모델",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "ADMIN",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/admin/rules/{ruleId}",
    "method": "GET",
    "path": "/api/v1/admin/rules/{ruleId}",
    "purpose": "규칙 버전·적용기간",
    "domain": "관리자 규칙·정책·모델",
    "domainId": "관리자-규칙-정책-모델",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "ADMIN",
    "authorityMode": "BEARER",
    "pathParameters": [
      "ruleId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/admin/rules",
    "method": "POST",
    "path": "/api/v1/admin/rules",
    "purpose": "초안 규칙 생성",
    "domain": "관리자 규칙·정책·모델",
    "domainId": "관리자-규칙-정책-모델",
    "priority": "P2",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "ADMIN",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "PUT /api/v1/admin/rules/{ruleId}",
    "method": "PUT",
    "path": "/api/v1/admin/rules/{ruleId}",
    "purpose": "초안 규칙 변경",
    "domain": "관리자 규칙·정책·모델",
    "domainId": "관리자-규칙-정책-모델",
    "priority": "P2",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "ADMIN",
    "authorityMode": "BEARER",
    "pathParameters": [
      "ruleId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/admin/rules/{ruleId}/publish",
    "method": "POST",
    "path": "/api/v1/admin/rules/{ruleId}/publish",
    "purpose": "승인된 규칙 게시",
    "domain": "관리자 규칙·정책·모델",
    "domainId": "관리자-규칙-정책-모델",
    "priority": "P2",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "ADMIN",
    "authorityMode": "BEARER",
    "pathParameters": [
      "ruleId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/admin/rules/{ruleId}/rollback",
    "method": "POST",
    "path": "/api/v1/admin/rules/{ruleId}/rollback",
    "purpose": "이전 규칙으로 복귀",
    "domain": "관리자 규칙·정책·모델",
    "domainId": "관리자-규칙-정책-모델",
    "priority": "P2",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "ADMIN",
    "authorityMode": "BEARER",
    "pathParameters": [
      "ruleId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/admin/policies/versions",
    "method": "GET",
    "path": "/api/v1/admin/policies/versions",
    "purpose": "정책엔진 버전",
    "domain": "관리자 규칙·정책·모델",
    "domainId": "관리자-규칙-정책-모델",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "ADMIN",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/admin/algorithms/versions",
    "method": "GET",
    "path": "/api/v1/admin/algorithms/versions",
    "purpose": "탐지 알고리즘 버전",
    "domain": "관리자 규칙·정책·모델",
    "domainId": "관리자-규칙-정책-모델",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "ADMIN",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/admin/feature-flags",
    "method": "GET",
    "path": "/api/v1/admin/feature-flags",
    "purpose": "환경별 기능 플래그",
    "domain": "관리자 규칙·정책·모델",
    "domainId": "관리자-규칙-정책-모델",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "ADMIN",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "PUT /api/v1/admin/feature-flags/{flagKey}",
    "method": "PUT",
    "path": "/api/v1/admin/feature-flags/{flagKey}",
    "purpose": "승인된 기능 플래그 변경",
    "domain": "관리자 규칙·정책·모델",
    "domainId": "관리자-규칙-정책-모델",
    "priority": "P2",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "ADMIN",
    "authorityMode": "BEARER",
    "pathParameters": [
      "flagKey"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/internal/ops/jobs",
    "method": "GET",
    "path": "/api/v1/internal/ops/jobs",
    "purpose": "기준선·탐지·정리 작업 목록",
    "domain": "운영·배치·합성 탐지·연동 상태",
    "domainId": "운영-배치-합성-탐지-연동-상태",
    "priority": "P2",
    "boundary": "OWNED",
    "implementation": "PLANNED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/internal/ops/jobs/{jobId}",
    "method": "GET",
    "path": "/api/v1/internal/ops/jobs/{jobId}",
    "purpose": "작업 실행상태·오류",
    "domain": "운영·배치·합성 탐지·연동 상태",
    "domainId": "운영-배치-합성-탐지-연동-상태",
    "priority": "P2",
    "boundary": "OWNED",
    "implementation": "PLANNED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "jobId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/internal/ops/jobs/{jobId}/retry",
    "method": "POST",
    "path": "/api/v1/internal/ops/jobs/{jobId}/retry",
    "purpose": "실패 작업 안전 재시도",
    "domain": "운영·배치·합성 탐지·연동 상태",
    "domainId": "운영-배치-합성-탐지-연동-상태",
    "priority": "P2",
    "boundary": "OWNED",
    "implementation": "PLANNED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "jobId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/admin/synthetic-datasets",
    "method": "POST",
    "path": "/api/v1/admin/synthetic-datasets",
    "purpose": "합성 특징·근거 데이터셋 초안 등록",
    "domain": "운영·배치·합성 탐지·연동 상태",
    "domainId": "운영-배치-합성-탐지-연동-상태",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "ADMIN",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/admin/synthetic-datasets/{datasetId}",
    "method": "GET",
    "path": "/api/v1/admin/synthetic-datasets/{datasetId}",
    "purpose": "합성 데이터셋·검증상태 조회",
    "domain": "운영·배치·합성 탐지·연동 상태",
    "domainId": "운영-배치-합성-탐지-연동-상태",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "ADMIN",
    "authorityMode": "BEARER",
    "pathParameters": [
      "datasetId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/admin/synthetic-datasets/{datasetId}/validate",
    "method": "POST",
    "path": "/api/v1/admin/synthetic-datasets/{datasetId}/validate",
    "purpose": "합성 데이터셋 의미 검증",
    "domain": "운영·배치·합성 탐지·연동 상태",
    "domainId": "운영-배치-합성-탐지-연동-상태",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "ADMIN",
    "authorityMode": "BEARER",
    "pathParameters": [
      "datasetId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/admin/synthetic-datasets/{datasetId}/ingest",
    "method": "POST",
    "path": "/api/v1/admin/synthetic-datasets/{datasetId}/ingest",
    "purpose": "검증된 합성 데이터셋 불변 적재",
    "domain": "운영·배치·합성 탐지·연동 상태",
    "domainId": "운영-배치-합성-탐지-연동-상태",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "ADMIN",
    "authorityMode": "BEARER",
    "pathParameters": [
      "datasetId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/customers/{customerId}/detection-runs",
    "method": "POST",
    "path": "/api/v1/customers/{customerId}/detection-runs",
    "purpose": "합성 데이터셋 결정론적 탐지 실행",
    "domain": "운영·배치·합성 탐지·연동 상태",
    "domainId": "운영-배치-합성-탐지-연동-상태",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/detection-runs/{detectionRunId}",
    "method": "GET",
    "path": "/api/v1/detection-runs/{detectionRunId}",
    "purpose": "합성 탐지 실행 결과 조회",
    "domain": "운영·배치·합성 탐지·연동 상태",
    "domainId": "운영-배치-합성-탐지-연동-상태",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "ADMIN",
    "authorityMode": "BEARER",
    "pathParameters": [
      "detectionRunId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/detection-runs/{detectionRunId}/promotion",
    "method": "POST",
    "path": "/api/v1/detection-runs/{detectionRunId}/promotion",
    "purpose": "탐지 결과를 운영형 신호·경보로 단일 승격",
    "domain": "운영·배치·합성 탐지·연동 상태",
    "domainId": "운영-배치-합성-탐지-연동-상태",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "ADMIN",
    "authorityMode": "BEARER",
    "pathParameters": [
      "detectionRunId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/detection-runs/{detectionRunId}/promotion",
    "method": "GET",
    "path": "/api/v1/detection-runs/{detectionRunId}/promotion",
    "purpose": "탐지 실행 승격 결과 조회",
    "domain": "운영·배치·합성 탐지·연동 상태",
    "domainId": "운영-배치-합성-탐지-연동-상태",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "ADMIN",
    "authorityMode": "BEARER",
    "pathParameters": [
      "detectionRunId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/internal/ops/audit-integrity",
    "method": "GET",
    "path": "/api/v1/internal/ops/audit-integrity",
    "purpose": "감사 체인·누락 검사",
    "domain": "운영·배치·합성 탐지·연동 상태",
    "domainId": "운영-배치-합성-탐지-연동-상태",
    "priority": "P1",
    "boundary": "OWNED",
    "implementation": "PLANNED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/internal/integrations/providers",
    "method": "GET",
    "path": "/api/v1/internal/integrations/providers",
    "purpose": "외부 공급자 상태 목록",
    "domain": "운영·배치·합성 탐지·연동 상태",
    "domainId": "운영-배치-합성-탐지-연동-상태",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "PLANNED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/internal/integrations/providers/{providerId}/health",
    "method": "GET",
    "path": "/api/v1/internal/integrations/providers/{providerId}/health",
    "purpose": "공급자 연결상태",
    "domain": "운영·배치·합성 탐지·연동 상태",
    "domainId": "운영-배치-합성-탐지-연동-상태",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "PLANNED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "providerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/internal/integrations/providers/{providerId}/sync-runs",
    "method": "POST",
    "path": "/api/v1/internal/integrations/providers/{providerId}/sync-runs",
    "purpose": "운영 동기화 작업 요청",
    "domain": "운영·배치·합성 탐지·연동 상태",
    "domainId": "운영-배치-합성-탐지-연동-상태",
    "priority": "P2",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "PLANNED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "providerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/internal/integrations/sync-runs/{syncRunId}",
    "method": "GET",
    "path": "/api/v1/internal/integrations/sync-runs/{syncRunId}",
    "purpose": "동기화 결과·재시도 여부",
    "domain": "운영·배치·합성 탐지·연동 상태",
    "domainId": "운영-배치-합성-탐지-연동-상태",
    "priority": "P2",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "PLANNED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "syncRunId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/fx/rates",
    "method": "GET",
    "path": "/api/v1/fx/rates",
    "purpose": "금융사 제공 환율표 (`IMPLEMENTED`)",
    "domain": "외환·해외송금",
    "domainId": "외환-해외송금",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/fx/rates/{currency}",
    "method": "GET",
    "path": "/api/v1/fx/rates/{currency}",
    "purpose": "통화별 환율 상세 (`IMPLEMENTED`)",
    "domain": "외환·해외송금",
    "domainId": "외환-해외송금",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "currency"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/foreign-currency-accounts",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/foreign-currency-accounts",
    "purpose": "외화계좌 현황 (`IMPLEMENTED`)",
    "domain": "외환·해외송금",
    "domainId": "외환-해외송금",
    "priority": "P2",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/fx/exchange-simulations",
    "method": "POST",
    "path": "/api/v1/fx/exchange-simulations",
    "purpose": "외화 환전 모의계산 (`IMPLEMENTED`)",
    "domain": "외환·해외송금",
    "domainId": "외환-해외송금",
    "priority": "P2",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/overseas-remittance-history",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/overseas-remittance-history",
    "purpose": "해외송금 이력 조회 (`IMPLEMENTED`)",
    "domain": "외환·해외송금",
    "domainId": "외환-해외송금",
    "priority": "P2",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "IMPLEMENTED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/fx/exchanges",
    "method": "POST",
    "path": "/api/v1/fx/exchanges",
    "purpose": "실제 환전 기능 참조",
    "domain": "외환·해외송금",
    "domainId": "외환-해외송금",
    "priority": "P2",
    "boundary": "REFERENCE_ONLY",
    "implementation": "REFERENCE_ONLY",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/overseas-remittances",
    "method": "POST",
    "path": "/api/v1/overseas-remittances",
    "purpose": "실제 해외송금 접수 참조",
    "domain": "외환·해외송금",
    "domainId": "외환-해외송금",
    "priority": "P2",
    "boundary": "REFERENCE_ONLY",
    "implementation": "REFERENCE_ONLY",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/overseas-remittances/{remittanceId}/confirm",
    "method": "POST",
    "path": "/api/v1/overseas-remittances/{remittanceId}/confirm",
    "purpose": "실제 해외송금 승인 참조",
    "domain": "외환·해외송금",
    "domainId": "외환-해외송금",
    "priority": "P2",
    "boundary": "REFERENCE_ONLY",
    "implementation": "REFERENCE_ONLY",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "remittanceId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/insurance-products",
    "method": "GET",
    "path": "/api/v1/insurance-products",
    "purpose": "보험 상품 목록",
    "domain": "보험·방카슈랑스",
    "domainId": "보험-방카슈랑스",
    "priority": "P2",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "PLANNED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/insurance-products/{productId}",
    "method": "GET",
    "path": "/api/v1/insurance-products/{productId}",
    "purpose": "보장·조건·유의사항",
    "domain": "보험·방카슈랑스",
    "domainId": "보험-방카슈랑스",
    "priority": "P2",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "PLANNED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "productId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/customers/{customerId}/insurance-contracts",
    "method": "GET",
    "path": "/api/v1/customers/{customerId}/insurance-contracts",
    "purpose": "가입 보험 목록",
    "domain": "보험·방카슈랑스",
    "domainId": "보험-방카슈랑스",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "PLANNED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "customerId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/insurance-contracts/{contractId}",
    "method": "GET",
    "path": "/api/v1/insurance-contracts/{contractId}",
    "purpose": "보험 계약 상세",
    "domain": "보험·방카슈랑스",
    "domainId": "보험-방카슈랑스",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "PLANNED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "contractId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/insurance-contracts/{contractId}/coverage",
    "method": "GET",
    "path": "/api/v1/insurance-contracts/{contractId}/coverage",
    "purpose": "보장내용 조회",
    "domain": "보험·방카슈랑스",
    "domainId": "보험-방카슈랑스",
    "priority": "P1",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "PLANNED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "contractId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "GET /api/v1/insurance-contracts/{contractId}/payments",
    "method": "GET",
    "path": "/api/v1/insurance-contracts/{contractId}/payments",
    "purpose": "보험료 납부 이력",
    "domain": "보험·방카슈랑스",
    "domainId": "보험-방카슈랑스",
    "priority": "P2",
    "boundary": "EXTERNAL_INTEGRATION",
    "implementation": "PLANNED",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [
      "contractId"
    ],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/insurance-applications",
    "method": "POST",
    "path": "/api/v1/insurance-applications",
    "purpose": "실제 보험 가입 기능 참조",
    "domain": "보험·방카슈랑스",
    "domainId": "보험-방카슈랑스",
    "priority": "P2",
    "boundary": "REFERENCE_ONLY",
    "implementation": "REFERENCE_ONLY",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/insurance-claims",
    "method": "POST",
    "path": "/api/v1/insurance-claims",
    "purpose": "실제 보험금 청구 기능 참조",
    "domain": "보험·방카슈랑스",
    "domainId": "보험-방카슈랑스",
    "priority": "P2",
    "boundary": "REFERENCE_ONLY",
    "implementation": "REFERENCE_ONLY",
    "audience": "CUSTOMER",
    "authorityMode": "BEARER",
    "pathParameters": [],
    "externalActionAllowed": false
  },
  {
    "key": "POST /api/v1/demo/staff/sessions/{sessionId}/capability",
    "method": "POST",
    "path": "/api/v1/demo/staff/sessions/{sessionId}/capability",
    "purpose": "직원 데모 capability 발급",
    "domain": "시스템·데모",
    "domainId": "시스템-데모",
    "priority": "P0-A",
    "boundary": "OWNED",
    "implementation": "IMPLEMENTED",
    "audience": "STAFF",
    "authorityMode": "STAFF_BOOTSTRAP",
    "pathParameters": [
      "sessionId"
    ],
    "externalActionAllowed": false
  }
] as const satisfies readonly ApiOperationDefinition[];
