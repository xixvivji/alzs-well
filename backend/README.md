# ALZ's well 백엔드

금융생활 변화 조기알림과 행원 보호업무 코파일럿의 공모전용 Spring 백엔드다. `FIN_MGMT_AB_001` 완전 합성데이터만 사용하며 실제 송금·지급정지·한도변경·가족 연락·외부 AI 호출은 실행하지 않는다.

상위 제품 기준은 [`../ALZS_WELL_PROJECT_SSOT.md`](../ALZS_WELL_PROJECT_SSOT.md), 요청·응답 계약은 [`../docs/FINAL_BACKEND_API_SPEC.md`](../docs/FINAL_BACKEND_API_SPEC.md)다.

## 기술 기준

- Java 21, Spring Boot 3.5.16, Gradle 8.14.3 Wrapper
- PostgreSQL 17.11, Spring Data JPA·JDBC, Flyway V14
- Spring MVC·Security·Validation, Actuator
- JUnit 5, Testcontainers

## 로컬 실행

Java 21과 Docker가 필요하다. `.env.example`을 `.env`로 복사한 뒤 PostgreSQL 관리자·migration·runtime 비밀번호와 직원 capability 발급용 계정 비밀번호를 각각 충분히 긴 임의값으로 교체한다. 고객·직원 CORS origin도 실제 HTTPS 주소로 분리해 지정한다.

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

```bash
./scripts/verify-air-gap.sh
```

이 명령은 Spring의 외부 HTTPS 실패, Spring→DB 성공, gateway→DB 실패를 확인한다. 이 구성은 공모전용 망분리 모사이며 금융회사 운영환경의 규정 준수 완료를 뜻하지 않는다.

## 구현 API — 업무 API 39개 + 직원 capability 발급 API 1개

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
```

## 데모 보안 계약

- 세션 생성은 비멱등이며 호출마다 새 세션을 만든다.
- 공개 세션 생성 응답은 `X-Demo-Customer-Capability`만 한 번 반환한다.
- 직원 화면은 별도 origin에서 HTTP Basic으로 보호된 `POST /api/v1/demo/staff/sessions/{sessionId}/capability`를 호출해 `X-Demo-Staff-Capability`를 한 번 발급받는다. staging의 Basic 계정은 직원 프론트 번들에 넣지 않고 신뢰된 운영자만 사용하며, 실제 운영 전에는 기업 IdP·MFA·RBAC로 교체한다.
- token 원문은 URL·JSON·DB·감사로그에 저장하지 않는다. 서버에는 SHA-256 hash만 저장한다.
- 이후 세션 API는 `X-Demo-Capability`, 적재 후 파생 API는 `X-Demo-Run-Id`를 요구한다.
- 고객 token으로 `/staff/**`·`/cases/**`를 호출하면 `403`, 다른 세션 token·만료 token은 존재 여부를 감추기 위해 `404`다.
- URI 인코딩·세미콜론·중복 slash처럼 역할 분류를 모호하게 만드는 세션 경로는 컨트롤러 도달 전에 `404`로 거절하며, 컨트롤러 메서드도 역할 권한을 다시 확인한다.
- 세션 생성을 제외한 변경 API는 8~64자 `Idempotency-Key`가 필요하다. 동일 scope·동일 request는 재생하고, 같은 key에 다른 request는 `409 IDEMPOTENCY_CONFLICT`다.
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
docker compose --env-file .env.example config --quiet
```

P1 첫 API는 `POST /api/v1/demo/sessions/{sessionId}/cases/{caseId}/copilot-drafts`다. 현재는 `CopilotPort` 뒤의 결정론적 템플릿만 사용하며 외부 모델·외부망·실제 실행을 호출하지 않는다.

사건 근거 API는 합성 신호·근거 거래·공식 보호수단 출처를 행원에게 한 묶음으로 제공하며 외부 문서나 금융기관 시스템을 호출하지 않는다.

현재 단위·PostgreSQL Testcontainers 통합시험 52개는 capability/IDOR와 인코딩 경로 우회, 고객·직원 capability 분리 발급, 역할별 메서드 권한, 환경별 CORS 분리, 운영 노출 프로필 fail-closed, run 격리, 3·2·7 신호, A/B 정책, 개인정보형 자유입력 차단, 멱등충돌, 낙관적 잠금, 고객 프로필·환경설정 영속화와 소유권 검증, 미동의 연락 차단, 외부실행 금지, 감사·내부 메모 append-only 제약, 만료 정리, 후속일정 상태 불변식, 사건 타임라인·근거·코파일럿 격리와 기본 공개 프로필 OpenAPI 33개 계약을 검증한다.

## AWS 백엔드 데모 배포

AI 모델이 아직 없어도 현재 P0는 결정론적 규칙·템플릿으로 완주하므로, 백엔드 staging을 먼저 배포해 CORS·HTTPS·프론트 계약·재시작·데이터 정리를 검증하는 편이 좋다. 구체적인 안전 설정과 순서는 [`../docs/AWS_BACKEND_DEPLOYMENT.md`](../docs/AWS_BACKEND_DEPLOYMENT.md)를 따른다. 공개 운영 또는 실제 고객데이터 사용 승인을 뜻하지 않으며, 현재는 완전 합성데이터 데모에만 사용한다.
