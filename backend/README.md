# ALZ's well 백엔드

금융생활 변화 조기알림과 행원 보호업무 코파일럿의 공모전용 Spring 백엔드다. `FIN_MGMT_AB_001` 완전 합성데이터만 사용하며 실제 송금·지급정지·한도변경·가족 연락·외부 AI 호출은 실행하지 않는다.

상위 제품 기준은 [`../ALZS_WELL_PROJECT_SSOT.md`](../ALZS_WELL_PROJECT_SSOT.md), 요청·응답 계약은 [`../docs/FINAL_BACKEND_API_SPEC.md`](../docs/FINAL_BACKEND_API_SPEC.md)다.

## 기술 기준

- Java 21, Spring Boot 3.5.16, Gradle 8.14.3 Wrapper
- PostgreSQL 17, Spring Data JPA·JDBC, Flyway V7
- Spring MVC·Security·Validation, Actuator
- JUnit 5, Testcontainers

## 로컬 실행

Java 21과 Docker가 필요하다. `.env.example`을 `.env`로 복사한 뒤 `POSTGRES_PASSWORD`를 충분히 긴 임의값으로 교체한다.

```bash
docker compose --env-file .env up -d --build
curl -i http://localhost:8080/api/v1/system/health
```

OpenAPI 3.1 JSON과 조회 전용 Swagger UI:

```text
http://localhost:8080/v3/api-docs
http://localhost:8080/swagger-ui.html
```

Swagger UI의 요청 실행 기능은 비활성화되어 있다. 프론트 타입 생성은 `/v3/api-docs`를 입력으로 사용한다.

gateway만 `127.0.0.1`에 공개된다. gateway↔Spring은 `alzs-well-app`, Spring↔PostgreSQL은 `alzs-well-data` 내부망을 사용하며 gateway와 DB는 네트워크를 공유하지 않는다.

```bash
./scripts/verify-air-gap.sh
```

이 명령은 Spring의 외부 HTTPS 실패, Spring→DB 성공, gateway→DB 실패를 확인한다. 이 구성은 공모전용 망분리 모사이며 금융회사 운영환경의 규정 준수 완료를 뜻하지 않는다.

## 구현 API — P0 23개 + P1 2개

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

# 행원 사건 4
GET  /api/v1/demo/sessions/{sessionId}/staff/cases
GET  /api/v1/demo/sessions/{sessionId}/cases/{caseId}
GET  /api/v1/demo/sessions/{sessionId}/cases/{caseId}/evidence
POST /api/v1/demo/sessions/{sessionId}/cases/{caseId}/review
POST /api/v1/demo/sessions/{sessionId}/cases/{caseId}/guidance-plan
```

## 데모 보안 계약

- 세션 생성은 비멱등이며 호출마다 새 세션을 만든다.
- 생성 응답 헤더 `X-Demo-Customer-Capability`, `X-Demo-Staff-Capability`로 역할별 256-bit 불투명 token을 한 번만 반환한다.
- token 원문은 URL·JSON·DB·감사로그에 저장하지 않는다. 서버에는 SHA-256 hash만 저장한다.
- 이후 세션 API는 `X-Demo-Capability`, 적재 후 파생 API는 `X-Demo-Run-Id`를 요구한다.
- 고객 token으로 `/staff/**`·`/cases/**`를 호출하면 `403`, 다른 세션 token·만료 token은 존재 여부를 감추기 위해 `404`다.
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
- 위험한 guardrail 조합이면 애플리케이션 기동이 실패한다.
- gateway는 요청 본문 32KiB, 연결·요청 rate와 timeout을 제한한다.
- 런타임 외부 egress, remote model, 실제 provider adapter는 비활성화돼 있다.

## 테스트

```bash
./gradlew test --rerun-tasks
docker compose --env-file .env.example config --quiet
```

P1 첫 API는 `POST /api/v1/demo/sessions/{sessionId}/cases/{caseId}/copilot-drafts`다. 현재는 `CopilotPort` 뒤의 결정론적 템플릿만 사용하며 외부 모델·외부망·실제 실행을 호출하지 않는다.

사건 근거 API는 합성 신호·근거 거래·공식 보호수단 출처를 행원에게 한 묶음으로 제공하며 외부 문서나 금융기관 시스템을 호출하지 않는다.

현재 단위·PostgreSQL Testcontainers 통합시험 34개는 capability/IDOR, run 격리, 3·2·7 신호, A/B 정책, 멱등충돌, 낙관적 잠금, 미동의 연락 차단, 외부실행 금지, 감사 hash 체인·append-only 제약, 조회 입력·cursor 검증, 사건 근거·코파일럿 격리와 OpenAPI 25개 계약을 검증한다.
