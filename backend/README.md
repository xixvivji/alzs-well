# ALZ's well 백엔드

금융생활 변화 조기알림과 행원 보호업무 코파일럿의 공모전용 Spring 백엔드다. `FIN_MGMT_AB_001` 완전 합성데이터만 사용하며 실제 송금·지급정지·한도변경·가족 연락·외부 AI 호출은 실행하지 않는다.

상위 제품 기준은 [`../ALZS_WELL_PROJECT_SSOT.md`](../ALZS_WELL_PROJECT_SSOT.md), 요청·응답 계약은 [`../docs/FINAL_BACKEND_API_SPEC.md`](../docs/FINAL_BACKEND_API_SPEC.md)다.

## 기술 기준

- Java 21, Spring Boot 3.5.16, Gradle 8.14.3 Wrapper
- PostgreSQL 17.11, Spring Data JPA·JDBC, Flyway V36
- Spring MVC·Security·Validation, Actuator
- JUnit 5, Testcontainers

## 로컬 실행

Java 21과 Docker가 필요하다. `.env.example`을 `.env`로 복사한 뒤 PostgreSQL 관리자·migration·runtime 비밀번호와 직원 capability 발급용 계정 비밀번호를 각각 충분히 긴 임의값으로 교체한다. `FRONTEND_PROXY_SHARED_SECRET`도 `openssl rand -hex 32`로 생성해야 하며 placeholder가 남아 있으면 gateway가 시작을 거부한다. 고객·직원 CORS origin도 실제 HTTPS 주소로 분리해 지정한다.

```bash
docker compose --env-file .env up -d --build
curl -i http://localhost:8080/api/v1/system/health
```

OpenAPI 3.1 JSON과 조회 전용 Swagger UI:

```text
http://localhost:8080/v3/api-docs
http://localhost:8080/swagger-ui.html
```

Swagger UI의 요청 실행 기능은 비활성화되어 있다. 프론트 타입 생성은 개발 프로필의 `/v3/api-docs`를 입력으로 사용한다. production 프로필은 OpenAPI·Swagger UI를 기본 비활성화한다.

gateway만 `127.0.0.1`에 공개된다. gateway↔Spring은 `alzs-well-app`, Spring↔PostgreSQL은 `alzs-well-data` 내부망을 사용하며 gateway와 DB는 네트워크를 공유하지 않는다.

내부 AI 하이브리드 검색은 기본적으로 꺼져 있다. `AI_RETRIEVAL_ENABLED=true`와 32자 이상의
`AI_INTERNAL_TOKEN`을 설정하고 `docker compose --profile ai up -d --build`로 실행하면 Spring이
`http://ai-service:8000/internal/v1/search`를 호출한다. Spring은 응답의 문서·버전·chunk ID·원문
및 본문 hash를 권위 지식 카탈로그와 다시 대조하고, ACL·audience·승인·활성·효력기간을 모두
재검사한다. 검증 실패 citation은 제외하며 timeout, 인증 실패, 비정상 응답에는 기존 결정론적
키워드 검색으로 폴백한다. 내부 검색은 `local-hash-ngram-ko-v1` 384차원 임베딩의 pgvector
cosine 유사도와 PostgreSQL 전문검색 점수를 결합하며 외부 모델 다운로드가 없다. 활성화 전
`KNOWLEDGE_ADMIN_WRITE` 권한으로
`POST /api/v1/admin/knowledge/ingestion-imports`에 `ingestion-import.schema.json` bundle을 제출해야
한다. Spring은 승인 governance와 source/text/chunk hash를 다시 계산한 뒤 `chunkId ↔ passageId`
binding을 추가 전용으로 저장한다. Spring은 `ai_knowledge` 스키마를 직접 조회하거나 수정하지 않는다.
일반 요청 본문은 gateway에서 32KiB로 제한하며, 최대 500개 chunk의 검증 bundle을 받는 이 관리자
경로만 4MiB와 더 낮은 연결 제한을 적용한다.

```bash
./scripts/verify-air-gap.sh
```

이 명령은 Spring의 외부 HTTPS 실패, Spring→DB 성공, gateway→DB 실패를 확인한다. 이 구성은 공모전용 망분리 모사이며 금융회사 운영환경의 규정 준수 완료를 뜻하지 않는다.

## 구현 API — 업무 API 49개 + 직원 capability 발급 API 1개

```text
# 시스템 4
GET  /api/v1/system/health
GET  /api/v1/system/readiness
GET  /api/v1/system/public-config
GET  /api/v1/system/versions

# 세션·시나리오 5
POST /api/v1/demo/sessions
GET  /api/v1/demo/sessions/{sessionId}
GET  /api/v1/demo/scenarios
POST /api/v1/demo/sessions/{sessionId}/scenarios/{scenarioId}/ingest
POST /api/v1/demo/sessions/{sessionId}/reset

# 직원 capability 발급 1
POST /api/v1/demo/staff/sessions/{sessionId}/capability

# 합성 금융생활 읽기 6
GET  /api/v1/demo/sessions/{sessionId}/customers/{customerId}/connections/consent-summary
GET  /api/v1/demo/sessions/{sessionId}/customers/{customerId}/accounts
GET  /api/v1/demo/sessions/{sessionId}/accounts/{accountId}/transactions
GET  /api/v1/demo/sessions/{sessionId}/customers/{customerId}/baselines
GET  /api/v1/demo/sessions/{sessionId}/customers/{customerId}/financial-summary
GET  /api/v1/demo/sessions/{sessionId}/protection-actions

# 고객 알림 4
GET  /api/v1/demo/sessions/{sessionId}/customers/{customerId}/alerts
GET  /api/v1/demo/sessions/{sessionId}/alerts/{alertId}
POST /api/v1/demo/sessions/{sessionId}/alerts/{alertId}/context
GET  /api/v1/demo/sessions/{sessionId}/alerts/{alertId}/audit

# 행원 사건 P0 4
GET  /api/v1/demo/sessions/{sessionId}/staff/cases
GET  /api/v1/demo/sessions/{sessionId}/cases/{caseId}
POST /api/v1/demo/sessions/{sessionId}/cases/{caseId}/review
POST /api/v1/demo/sessions/{sessionId}/cases/{caseId}/guidance-plan

# P1 조기구현 9
DELETE /api/v1/demo/sessions/{sessionId}
GET    /api/v1/demo/sessions/{sessionId}/cases/{caseId}/timeline
GET    /api/v1/demo/sessions/{sessionId}/cases/{caseId}/evidence
POST   /api/v1/demo/sessions/{sessionId}/cases/{caseId}/copilot-drafts
POST   /api/v1/demo/sessions/{sessionId}/cases/{caseId}/notes
GET    /api/v1/demo/sessions/{sessionId}/cases/{caseId}/notes
POST   /api/v1/demo/sessions/{sessionId}/cases/{caseId}/follow-ups
GET    /api/v1/demo/sessions/{sessionId}/cases/{caseId}/follow-ups
PATCH  /api/v1/demo/sessions/{sessionId}/staff/follow-ups/{followUpId}

# P1 고객 프로필 7 (기본 비활성화, 사설 인증 경계에서만 활성화)
GET   /api/v1/customers/{customerId}
PATCH /api/v1/customers/{customerId}/display-profile
GET   /api/v1/customers/{customerId}/preferences
PATCH /api/v1/customers/{customerId}/preferences
GET   /api/v1/customers/{customerId}/accessibility-settings
PUT   /api/v1/customers/{customerId}/accessibility-settings
GET   /api/v1/customers/{customerId}/data-summary

# P1 로컬 합성 인증 6 (development 전용, production 강제 비활성화)
POST /api/v1/auth/login
POST /api/v1/auth/token/refresh
POST /api/v1/auth/logout
POST /api/v1/auth/logout-all
GET  /api/v1/auth/me
GET  /api/v1/auth/me/permissions

# P1 합성 금융기관·연결 조회 4
GET /api/v1/financial-institutions
GET /api/v1/financial-institutions/{institutionId}
GET /api/v1/customers/{customerId}/connections
GET /api/v1/customers/{customerId}/connections/{connectionId}
```

합성 금융기관 카탈로그는 `안심은행(SYNTHETIC_BANK)`과 `안심증권(SYNTHETIC_SECURITIES)` 두 곳만 제공한다. 두 기관과 고객 연결 상태는 PostgreSQL의 고정 snapshot이며 실제 금융회사 API·오픈뱅킹·마이데이터망을 호출하지 않는다.

## 데모 보안 계약

- 세션 생성은 비멱등이며 호출마다 새 세션을 만든다.
- 공개 세션 생성 응답은 `X-Demo-Customer-Capability`만 한 번 반환한다.
- Vercel BFF는 현재 고객 capability로 합성 세션을 먼저 검증한 뒤 `Authorization: Bearer {bootstrap-token}`으로 보호된 `POST /api/v1/demo/staff/sessions/{sessionId}/capability`를 호출해 `X-Demo-Staff-Capability`를 한 번 발급받는다. bootstrap 토큰은 Vercel 서버 환경변수에만 두고 프론트 번들에 넣지 않는다. 이 공개 시연 경로는 실제 운영 전 기업 IdP·MFA·RBAC로 교체한다.
- token 원문은 URL·JSON·DB·감사로그에 저장하지 않는다. 서버에는 SHA-256 hash만 저장한다.
- 이후 세션 API는 `X-Demo-Capability`, 적재 후 파생 API는 `X-Demo-Run-Id`를 요구한다.
- 고객 token으로 `/staff/**`·`/cases/**`를 호출하면 `403`, 다른 세션 token·만료 token은 존재 여부를 감추기 위해 `404`다.
- URI 인코딩·세미콜론·중복 slash처럼 역할 분류를 모호하게 만드는 세션 경로는 컨트롤러 도달 전에 `404`로 거절하며, 컨트롤러 메서드도 역할 권한을 다시 확인한다.
- 상태 변경 명령 중 API 명세에 `Idempotency-Key`가 표시된 엔드포인트는 8~100자의 키가 필요하다. 동일 scope·동일 request의 업무 결과는 재생하고, 같은 key에 다른 request는 도메인별 `409 *_IDEMPOTENCY_CONFLICT`다. 조회·검색·정책평가처럼 호출 자체가 기록인 API와 로그인·로그아웃은 별도 중복 호출 정책을 사용한다.
- Reset은 이전 run을 덮어쓰지 않고 새 `demoRunId`를 만든다. 이전 run의 거래·판단·감사자료는 보존된다.

## 고정 시나리오

`FIN_MGMT_AB_001`은 원시 합성 거래 42건과 상호작용 8건에서 다음 신호를 Java 규칙으로 직접 계산한다.

- 최근 60일 정기납부 누락 3건
- 같은 수취인·금액의 10분 내 중복송금 2건
- 동일 거래 결과의 1시간 내 반복확인 7회

A 경로는 네 종류의 검증된 T1 구조적 근거가 모두 있을 때만 `CLOSED_NORMAL`로 종결한다. B 경로는 `PENDING_BANK_REVIEW` 사건을 만들고, 신뢰연락인 미동의 상태를 `BLOCKED_BY_CONSENT`로 기록한다. 안내계획 승인은 `GUIDANCE_PLAN_APPROVED`에서 멈추며 고객 전달이나 외부 금융 실행을 만들지 않는다.

## 감사와 안전장치

- 감사행은 이전 event hash를 포함한 SHA-256 체인으로 연결된다.
- DB trigger가 `decision_audit`의 UPDATE·DELETE를 거부한다.
- 세션 정리와 감사로그 삭제를 cascade하지 않는다.
- 만료 세션은 배치 단위로 자동 정리하며 `DEMO_SESSION_EXPIRED_PURGED` 감사 이벤트와 기존 hash chain은 보존한다.
- 위험한 guardrail 조합이면 애플리케이션 기동이 실패한다.
- gateway는 요청 본문 32KiB, IP·capability별 연결·요청 rate와 timeout을 제한하고 `429`에 `Retry-After`를 반환한다.
- 개발 CORS는 고객·직원 localhost allowlist를 분리하고, 운영 CORS는 서로 겹치지 않는 고객·직원 HTTPS allowlist를 사용한다. 고객 세션 경로와 직원 `/staff/**`·`/cases/**` 경로도 origin별로 분리하며 빈 목록·wildcard·경로가 붙은 origin이나 두 목록의 중복 origin은 기동 시 거절한다.
- Flyway migration은 관리자 계정과 분리한 `alzswell_migrator`로 실행하고 애플리케이션은 제한된 `alzswell_app` runtime 역할로 접속한다. runtime 역할은 감사·메모 append-only 테이블의 UPDATE·DELETE 권한이 없다.
- 런타임 외부 egress, remote model, 실제 provider adapter는 비활성화돼 있다.

## 테스트

```bash
./gradlew test --rerun-tasks
./gradlew test --tests 'com.alzswell.e2e.BackendCoreFlowE2ETest'
docker compose --env-file .env.example config --quiet
```

P1 첫 API는 `POST /api/v1/demo/sessions/{sessionId}/cases/{caseId}/copilot-drafts`다. 현재는 `CopilotPort` 뒤의 결정론적 템플릿만 사용하며 외부 모델·외부망·실제 실행을 호출하지 않는다.

사건 근거 API는 합성 신호·근거 거래·공식 보호수단 출처를 행원에게 한 묶음으로 제공하며 외부 문서나 금융기관 시스템을 호출하지 않는다.

현재 단위·PostgreSQL Testcontainers 통합시험은 capability/IDOR와 인코딩 경로 우회, 고객·직원 capability 분리 발급, 역할별 메서드 권한, 환경별 CORS 분리, 명시적 실행 프로필과 운영 노출 fail-closed, refresh token 재사용 탐지·절대 만료·세션 상한·전체 로그아웃, 합성 금융기관·연결 조회와 고객 소유권, run 격리, 3·2·7 신호, A/B 정책, 개인정보형 자유입력 차단, 동시 멱등 요청, 낙관적 잠금, 고객 프로필·환경설정 영속화와 소유권 검증, 목적별 동의·신뢰연락인 철회 연쇄와 열람 감사, 외부실행 금지, 감사·내부 메모 append-only 제약, 만료 정리, 후속일정 상태 불변식, 사건 타임라인·근거·코파일럿 격리, 탐지 정책·기능 플래그 변경관리, 통합 감사·출처 조회와 development OpenAPI 계약을 검증한다. `BackendCoreFlowE2ETest`는 기준선 계산→합성 데이터 검증·적재→결정론적 탐지→운영 경보 승격→고객 맥락응답→행원 사건 배정·검토→안내계획 승인과 감사이력을 하나의 HTTP 폐루프로 검증한다. 같은 테스트에서 네트워크 재시도형 멱등 replay, 같은 키의 다른 요청 충돌, 오래된 버전, 타 고객 접근, 실패 전이의 원자적 롤백과 `X-Trace-Id` 응답 추적도 검증한다.

## 합성 운영 데이터 생성

스키마 migration과 백엔드 readiness가 완료된 뒤 `synthetic-tools` profile의 일회성 Job으로 실행한다. 대량 데이터는 Flyway에 포함하지 않는다.

```bash
docker compose --env-file .env --profile synthetic-tools run --rm synthetic-seed
```

기본값은 `SMOKE`다. 배포 검증용 데이터 규모는 `.env`에서 선택한다.

```dotenv
SYNTHETIC_SEED_PROFILE=DEMO
SYNTHETIC_SEED_FIXTURE_VERSION=synthetic-v3.0.0
SYNTHETIC_SEED_VALUE=20260825
SYNTHETIC_SEED_BATCH_SIZE=10
SYNTHETIC_SEED_RESUME=false
SYNTHETIC_SEED_VERIFY_DETECTION=false
```

동일 버전·profile·seed의 완료 실행은 중복 적재하지 않고 기존 manifest를 재생한다. 중단된 `RUNNING` 실행만 운영자가 원인을 확인한 후 `SYNTHETIC_SEED_RESUME=true`로 재개할 수 있다. 상세 절차는 [`../docs/runbooks/SYNTHETIC_DATASET_V3.md`](../docs/runbooks/SYNTHETIC_DATASET_V3.md)를 따른다.
`LOAD`는 고객 250명·거래 75,000건의 중간 규모이며 `SYNTHETIC_SEED_VERIFY_DETECTION=true`로
활성 정책의 전체 고객 오탐·미탐 검증까지 실행할 수 있다.

## AWS 백엔드 데모 배포

AI 없는 최소 staging은 업무 EC2 한 대로 실행할 수 있다. AI 통합 공모전 staging의 최종 기준은 `업무 EC2(Nginx+Spring) + AI EC2(FastAPI+Arctic-ko) + Private RDS PostgreSQL/pgvector`다. `compose.aws-app.yaml`과 `compose.aws-ai.yaml`을 각 인스턴스에서 분리 실행한다.

AWS 업무 환경은 `AI_RETRIEVAL_STRICT_READINESS=true`로 FastAPI health의 `STAGED_APPROVED`, revision, 모델·골든셋 SHA-256, index version, `AWS_STAGING`을 재검증한다. 불일치하면 target 등록을 거부한다. 로컬 기본값은 strict가 아니어서 AI 장애 시 결정론적 템플릿 폴백을 유지한다. 구체적인 보안그룹, RDS TLS, SSM ingestion과 장애 복구는 [`../docs/AWS_BACKEND_DEPLOYMENT.md`](../docs/AWS_BACKEND_DEPLOYMENT.md)를 따른다. 현재 배포는 완전 합성데이터 데모 전용이다.
