# ALZ's well 최종 백엔드 API 명세서

> 문서 버전: **1.32.0**
> 상태: **통합 최종안 · API 설계 SSOT**  
> 기준일: **2026-08-31 (Asia/Seoul)**
> 백엔드: **Java 21 · Spring Boot 3.5.16 · PostgreSQL · 모듈형 모놀리스**  
> 프론트 계약: **React 또는 Vue에서 독립적으로 사용하는 JSON REST API**  
> 런타임 네트워크: **AIR_GAPPED_DEMO · Docker internal 네트워크로 외부 egress 차단**
> 상위 제품 기준: `ALZS_WELL_PROJECT_SSOT.md`

이 문서는 통합 SSOT, 현재 Spring 코드와 P0 API 계약, 하나은행·신한은행·카카오뱅크·KB증권의 공식 공개 기능을 하나의 백엔드 API 지도로 통합한다. 통합 전 방향 문서와 구 API 명세는 보관자료일 뿐 현재 계약의 해석 기준이 아니다.

## 문서 요약

| 항목 | 수량 |
|---|---:|
| 전체 API operation | **282개** |
| API 도메인 | **26개** |
| P0-A 기존 핵심 데모·운영 안전성 | **15개** |
| P0-B 공개 데모 핀테크 셸 | **11개** |
| P0 구현 목표 합계 | **26개** |
| P1 제품 핵심 백로그 | **177개** |
| P2 은행·증권 확장 백로그 | **79개** |
| ALZ's well 소유 `OWNED` | **193개** |
| 외부 연동 `EXTERNAL_INTEGRATION` | **67개** |
| 참조 전용 `REFERENCE_ONLY` | **22개** |

API 개수는 `Method + Path` 한 쌍을 operation 하나로 계산한다. 같은 path라도 HTTP method가 다르면 별도 operation이다. 282개에는 실행하지 않을 은행 코어 참조 기능도 포함된다. 현재 실제 구현된 P0 API는 기존 23개에 핵심·AI readiness 2개와 고객 확인 유예 1개를 더한 **26개**다.

| 현재 구현상태 | 수량 |
|---|---:|
| `IMPLEMENTED` | 문서화된 업무 API 237개 + staging 직원 발급 API 1개 |
| 상세 계약 확정, 구현 전 | 0개 |
| 카탈로그·백로그 | 45개 |

문서화된 업무 `IMPLEMENTED`는 고객지원 콘텐츠 조회 2개, 외환 읽기·모의계산 5개, 데모 AI 금융생활 지원 6개와 분리된 readiness·고객 확인 유예를 포함해 237개다. 직원 bootstrap 발급 API 1개는 공개 카탈로그 밖 staging 전용 계약이므로 코드 기준 총 238개다. 실제 OpenAPI 노출 수는 고객 기능·합성 직원 bootstrap 기능 플래그에 따라 달라진다. production 합성 인증은 기본 비활성이며, 성공한 `PUBLIC` fixture의 300명과 Vercel HttpOnly BFF 경계가 함께 준비된 경우만 활성화한다. 운영 직원 인증은 검증된 외부 IdP JWT 어댑터를 요구하지만 실제 금융회사 IdP 테넌트 연동 증적은 아직 없다.

여기서 API 282개라는 수치는 SSOT의 평가용 합성 프로필 240개 목표와 무관하다.

## 구현 결정

1. P0 26개를 먼저 구현하고 테스트한다.
2. P1과 P2는 전체 경로·소유권을 선점하되 일정에 따라 후순위로 미룬다.
3. `REFERENCE_ONLY`는 Spring Controller나 실행 버튼을 생성하지 않는다.
4. 실제 이체·주문·대출·계좌개설·지급정지·한도변경·외부 연락은 공개 데모에서 실행하지 않는다.
5. API 수가 많아도 현재 구조는 MSA가 아니라 도메인 패키지로 분리한 모듈형 모놀리스다.
6. P0 요청·응답 계약은 본 문서에 통합하고 Spring 코드에서 OpenAPI 3.1로 자동 생성한다. `/v3/api-docs`는 프론트 타입 생성용이며 Swagger UI의 요청 실행 기능은 비활성화한다. 구현된 모든 operation은 설명, 권한, 데이터 분류, 런타임 경계, 외부 실행 여부와 표준 오류 예시를 포함해야 하며 계약 테스트가 누락을 차단한다. 보관된 구 명세는 구현 근거로 사용하지 않는다.

OpenAPI 확장 필드는 다음 의미로 고정한다.

| 필드 | 의미 |
|---|---|
| `x-alzs-authority-mode` | `PUBLIC`, `DEMO_CAPABILITY`, `STAFF_BOOTSTRAP`, `BEARER`, `BEARER_AUTHORITY` 중 인증 방식 |
| `x-alzs-required-authorities` | 호출에 필요한 세부 권한 또는 capability 역할 |
| `x-alzs-data-classification` | 현재 구현은 항상 `SYNTHETIC_ONLY` |
| `x-alzs-runtime-boundary` | 내부 소유 구현 또는 합성 외부기관 adapter 경계 |
| `x-alzs-external-action` | 현재 구현은 항상 `NEVER`; 실제 금융 실행·외부 연락 없음 |

## AIR_GAPPED_DEMO 네트워크 격리 결정

공개 데모의 런타임은 실제 금융회사 망분리 준수를 주장하지 않고, **외부 정보반출을 차단한 망분리 모사 환경**으로 운영한다. 금융위원회가 금융권의 생성형 AI·클라우드 활용과 자율보안·결과책임 체계로의 전환 방향을 제시했더라도, 본 공모전은 예외 적용이나 적법성을 전제로 하지 않고 더 보수적인 무외부호출 경계를 사용한다.

### 허용 통신

```text
사용자 브라우저
    → 최소 Nginx gateway
        → Spring Boot REST API
        → PostgreSQL
        → 선택적 내부 FastAPI AI/RAG
        → 결정론적 규칙·템플릿·공식 근거 카탈로그
```

| 발신 | 허용 대상 | 금지 대상 |
|---|---|---|
| 브라우저 | 로컬 정적 자산, 공개 gateway의 Spring API 경로 | Spring·PostgreSQL 직접 접근, 외부 API·CDN·분석/오류수집 SDK |
| Nginx gateway | 내부 Spring API | PostgreSQL·외부 업무 API |
| Spring Boot | PostgreSQL, 내부 FastAPI, 내장 규칙·템플릿·카탈로그 | 외부 LLM, 금융사, 마이데이터, 원격 텔레메트리 |
| PostgreSQL | 응답 없음 | 인터넷·외부 DB |
| 배치·관리 작업 | 승인된 오프라인 반입 디렉터리 | 런타임 웹 다운로드·스크래핑 |

### 강제 규칙

1. 런타임 프로필은 `AIR_GAPPED_DEMO`이며 `externalEgressEnabled=false`, `remoteModelEnabled=false`, `syntheticProviderOnly=true`를 고정한다.
2. Docker Compose는 gateway↔Spring용 `alzs-well-app`과 Spring↔PostgreSQL용 `alzs-well-data`를 각각 `internal: true`로 분리한다. gateway는 `alzs-well-app`만, Spring은 `alzs-well-app`과 `alzs-well-data`, PostgreSQL은 `alzs-well-data`에만 연결한다. gateway와 PostgreSQL은 어떤 네트워크도 공유하지 않는다.
3. Host port는 최소 Nginx gateway만 게시한다. FastAPI는 `ai` 프로필에서만 기동하고 외부 port 없이 Docker 내부망으로만 Spring과 연결한다.
4. Vue/React 번들·폰트·아이콘은 로컬에서 제공하고 Content Security Policy를 최소 `default-src 'self'; connect-src 'self'`로 제한한다. 외부 CDN, Google Fonts, 지도, 분석 SDK, 원격 오류수집 SDK, 제3자 스크립트를 런타임에 사용하지 않는다.
5. Hugging Face 모델, 토크나이저, 임베딩, 공식문서는 빌드 전 통제된 절차로 내려받고 버전·라이선스·SHA-256을 고정한다. 실행 중 자동 다운로드를 금지한다.
6. 공식문서 갱신은 관리자 업로드 → allowlist 확인 → 악성 콘텐츠 검사 → 체크섬 생성 → 승인·게시의 오프라인 절차를 사용한다.
7. `EXTERNAL_INTEGRATION` 카탈로그는 설계 계약만 유지한다. P0에서는 `SYNTHETIC_PROVIDER` 외의 어댑터 bean을 기동하지 않는다.
8. 원격 오류수집, 사용량 분석, prompt tracing, 자동 업데이트 등 outbound telemetry를 비활성화한다.
9. 외부 목적지 연결 시도는 `EGRESS_ATTEMPT_BLOCKED` 감사이벤트를 남기되 URL query, prompt, 계좌·거래 원문은 기록하지 않는다.
   일반 요청 본문은 32KiB이며 관리자 ingestion import 단일 경로만 최대 500개 chunk를 위해 4MiB와 연결 동시성 2를 적용한다.
10. 로컬 AI가 실패하거나 기동되지 않아도 Spring 템플릿 폴백으로 P0 전체 흐름을 완주한다.
11. 이 구조를 실제 금융권 보안성 심사·망분리 규정 준수 완료로 표현하지 않는다. 실도입 전 금융회사 정보보호·준법·신용정보 부서의 검토가 필요하다.

### Docker Compose 기준 예시

```yaml
services:
  gateway:
    networks: [alzs-well-app]
    ports:
      - "127.0.0.1:8080:8080"

  backend:
    networks: [alzs-well-app, alzs-well-data]

  postgres:
    networks: [alzs-well-data]
    expose:
      - "5432"

networks:
  alzs-well-app:
    internal: true
  alzs-well-data:
    internal: true
```

Docker Compose에서 `internal: true`는 외부 연결이 없는 네트워크를 만든다. gateway는 DB 데이터망에, PostgreSQL은 gateway 앱망에 절대 연결하지 않는다. Spring만 app/data 두 내부망을 연결하되 애플리케이션 레벨 접근통제와 최소권한 DB 계정을 적용한다.

### 검증 수용기준

- Spring 컨테이너에서 외부 HTTPS 연결 실패
- Spring → PostgreSQL 내부 통신 성공
- gateway → PostgreSQL 5432 직접 연결 실패, gateway와 PostgreSQL의 공유 network 0개
- Spring·PostgreSQL host 직접 노출 0개, gateway만 loopback에 공개
- 브라우저의 제3자 origin 요청 0건 및 CSP 위반 0건
- 외부 API key 0개로 P0 데모 완주
- 실행 중 모델·문서 다운로드 0건
- 외부 금융사·푸시·문자·전화·LLM 호출 0건
- 외부 AI 서비스 없이 규칙·템플릿 흐름 완주율 100%

공식 참고: [금융위원회 금융분야 망분리 개선 로드맵](https://fsc.go.kr/po010102/82885), [Docker Compose 내부 네트워크](https://docs.docker.com/reference/compose-file/networks/)

---

## 빠른 목차

1. 프로젝트 기준과 도메인 경계
2. 참여 금융사 기능 근거와 반영 범위
3. 26개 도메인·282개 API 마스터 카탈로그
4. 공통 프로토콜·응답·오류 규칙
5. P0-A 15개 상세 계약
6. P0-B 11개 상세 계약
7. 표준 상태·보안·수용기준·변경관리

---

## 1. 프로젝트 기준과 API 도메인 경계

> 문서 역할: 전체 백엔드 API 명세의 최상위 해석 기준  
> 기준일: 2026-08-14  
> 서비스명: **ALZ's well**  
> 구현 기준: **Java 21 · Spring Boot 3.5.16 · PostgreSQL · 모듈형 모놀리스**

ALZ's well은 은행 코어를 새로 만드는 서비스가 아니다. 고객 동의를 전제로 개인의 평소 금융생활과 다른 변화를 설명 가능한 근거로 발견하고, 생활맥락을 재확인한 뒤, 정상 변화는 종결하고 추가 설명이 필요한 사건만 행원의 보호업무로 연결하는 B2B2C 금융안전 코파일럿이다.

전체 API 카탈로그에는 일반 은행·증권 웹서비스 기능도 포함할 수 있지만, **ALZ's well이 직접 소유하는 금융안전 API**, **합성·읽기 전용 지원 API**, **외부 금융회사 시스템이 소유하는 실행 API**를 반드시 구분한다.

---

### 1.1 기준 문서 우선순위

기획, API, DTO, 상태값, 화면, 테스트가 충돌하면 다음 순서로 판단한다.

1. 최신 대회 공식 공지와 제출 양식
2. `ALZS_WELL_PROJECT_SSOT.md`
3. 본 최종 API 명세
4. 실제 구현과 자동 테스트 결과

통합 전 방향 문서와 구 API 명세는 변경 이력 보존용 아카이브다. 명칭, 시나리오, 상태 또는 기술구성이 현재 두 기준문서와 충돌하면 아카이브의 값을 다시 도입하지 않는다.

실제 구현이 문서와 다르더라도 구현을 조용히 새 기준으로 삼지 않는다. 의도된 변경인지 검토한 뒤 코드, 테스트, OpenAPI, 프론트 타입과 문서를 함께 갱신한다.

#### 기술 기준

| 항목 | 최종 기준 |
|---|---|
| 언어 | Java 21 |
| 프레임워크 | Spring Boot 3.5.16 |
| 데이터베이스 | PostgreSQL |
| 데이터 접근 | Spring Data JPA 기본, 필요한 분석 조회만 JDBC·jOOQ 보조 |
| 마이그레이션 | Flyway |
| 구조 | Spring Boot 모듈형 모놀리스 |
| 프론트 계약 | React 또는 Vue에서도 사용할 수 있는 JSON REST API |
| P0 AI | 규칙·통계 탐지 + 정책엔진 + 결정론적 템플릿 |
| 선택형 AI | LLM과 벡터 RAG는 P1 이후 |
| P0 데이터 | 완전 합성데이터만 사용 |

---

### 1.2 API 분류 체계

우선순위와 구현상태는 서로 다른 축이다. 한 필드에 섞어 표현하지 않는다.

#### 우선순위

| 값 | 의미 |
|---|---|
| `P0` | 공모전 핵심 사용자 여정에 필수 |
| `P1` | MVP 완성도와 은행 PoC 준비에 필요 |
| `P2` | 발표 진출, 실증 또는 제품 확장 단계 |
| `REFERENCE` | 전체 은행 웹 기능 지도에는 포함하지만 ALZ's well이 소유하지 않음 |

#### 구현상태

| 값 | 의미 |
|---|---|
| `IMPLEMENTED` | 현재 코드와 자동 테스트로 확인됨 |
| `CONTRACT` | 요청·응답·오류·상태전이 계약이 확정됨 |
| `DRAFT` | 카탈로그와 초안만 존재하며 계약 변경 가능 |
| `EXTERNAL_INTEGRATION` | 금융회사·마이데이터·공공기관 등 외부 시스템이 실제 실행을 소유 |
| `REFERENCE_ONLY` | 비교·화면 설계를 위한 참조 항목이며 호출 가능한 자사 API가 아님 |
| `DEPRECATED` | 신규 호출 금지, 제거 일정과 대체 API가 명시됨 |

예를 들어 `POST /transfers`는 은행 앱에 일반적으로 존재하더라도 ALZ's well에서는 `REFERENCE + EXTERNAL_INTEGRATION`으로 분류한다. 합성데모에 동일한 화면이 필요하면 실제 이체 API와 분리된 `SIMULATED` 명령으로 만들고, 응답에 `externalExecutionCreated=false`를 고정한다.

---

### 1.3 최종 API 도메인 맵

| 도메인 | 책임 | 주요 엔터티 | 기본 우선순위 |
|---|---|---|---|
| `platform` | 헬스, 안전 가드, 기능 플래그, API 메타데이터 | `ServiceHealth`, `SafetyGuard`, `FeatureFlag`, `ApiVersion` | P0 |
| `demo` | 익명 세션, 역할별 capability, 합성 시나리오, run, seed, Reset, 격리 | `DemoSession`, `DemoRun`, `ScenarioFixture`, `SyntheticProfile`, `Snapshot`, `ResetVersion` | P0 |
| `identity` | 인증, 세션, MFA, 기기, 역할과 권한 | `User`, `Staff`, `AuthSession`, `Device`, `MfaChallenge`, `Role`, `Permission` | P1 |
| `customer` | 고객 프로필, 접근성, 연락·알림 선호 | `Customer`, `CustomerProfile`, `AccessibilityPreference`, `CommunicationPreference` | P1 |
| `consent` | 목적별 동의, 철회, 신뢰연락인, 최소정보 정책 | `ConsentSnapshot`, `ConsentGrant`, `ConsentRevocation`, `TrustedContact`, `TrustedContactPolicy` | P0/P1 |
| `institution` | 금융기관·마이데이터 연결과 동기화 | `Institution`, `DataConnection`, `ConnectionConsent`, `SyncJob` | P2/외부연동 |
| `ledger` | 계좌, 잔액, 거래, 수취인, 정기납부 정규화 | `Account`, `BalanceSnapshot`, `Transaction`, `Payee`, `RecurringObligation`, `Subscription` | P0/P1 |
| `detection` | 기준선, 특징, 준비상태, 변화신호와 근거 | `BaselineSnapshot`, `FeatureObservation`, `AnomalySignal`, `EvidenceSnapshot` | P0 |
| `incident` | 고객 알림, 생활맥락, 판단과 상태전이 | `AlertIncident`, `ContextEvent`, `LifeEvent`, `ContextEvidence`, `PolicyDecision` | P0 |
| `case` | 행원 사건큐, 배정, 검토, 재연락, 종결 | `ProtectionCase`, `CaseAssignment`, `StaffReview`, `FollowUp`, `CaseNote` | P0/P1 |
| `guidance` | 공식 보호수단 후보와 상담 안내 계획 | `ActionCatalog`, `ProtectionCandidate`, `EligibilityRule`, `GuidancePlan` | P0/P2 |
| `knowledge` | 승인 문서, 버전, 권한, 검색 근거 | `SourceDocument`, `KnowledgeChunk`, `RetrievalResult`, `Citation` | P0/P1 |
| `explanation` | 쉬운 설명, 중립 질문, 상담기록 초안, 폴백 | `ExplanationFacts`, `CopilotDraft`, `PromptVersion`, `ModelAnswer` | P0/P1 |
| `notification` | 알림 미리보기, 정책 평가, 발송 요청과 결과 | `NotificationPreference`, `NotificationPreview`, `DeliveryRequest`, `DeliveryResult` | P0/P2 |
| `audit` | 결정, 동의, 접근, 상태변경, 직원 override의 불변 이력 | `AuditEvent`, `DecisionAudit`, `AccessLog`, `OverrideEvent`, `VersionSnapshot` | P0 |
| `evaluation` | 골든 시나리오, 모델·정책 평가와 리포트 | `GoldenScenario`, `EvaluationRun`, `MetricResult`, `ModelVersion`, `PolicyVersion` | P1/P2 |
| `operations` | 문서·시나리오 관리, 배치, 장애, 모니터링 | `SourceAdmin`, `ScenarioAdmin`, `JobRun`, `SystemMetric`, `IncidentReport` | P1 |
| `banking-reference` | 일반 은행·카드·대출·증권 웹 기능 카탈로그 | 각 금융기관의 코어 도메인 엔터티 | P1/P2/REFERENCE |

#### 핵심 소유 관계

```text
DemoSession
└─ DemoRun
   └─ SyntheticCustomer
      ├─ Account
      │  └─ Transaction
      ├─ ConsentSnapshot
      │  └─ TrustedContactPolicy
      └─ AlertIncident
         ├─ T0 AlertEvidenceSnapshot
         │  └─ AnomalySignal
         ├─ T1 ContextEvidenceSnapshot
         │  └─ ContextEvent
         └─ ProtectionCase (선택적 0..1)
            ├─ StaffReview
            ├─ FollowUp
            ├─ GuidancePlan
            │  └─ ActionCatalog
            │     └─ SourceDocument
            └─ AuditEvent
```

- 한 `AlertIncident`는 여러 `AnomalySignal`을 하나의 고객 사건으로 묶는다.
- `ProtectionCase`는 행원 검토가 필요할 때만 생성한다.
- 최초 신호, 근거, 판단 snapshot은 덮어쓰지 않고 새 버전·이벤트로 축적한다.
- 합성데모의 모든 자원은 `{DemoSession, DemoRun}`에 귀속되며 다른 세션 capability나 run으로 조회하면 `404` 또는 stale-run `409`로 응답한다.
- 설명·Copilot 도메인은 상태를 직접 변경하지 않고 구조화된 초안만 반환한다.
- `audit`는 모든 중요 명령의 결과를 받지만 다른 업무 도메인의 상태를 변경하지 않는다.

#### 권장 모듈 의존방향

```text
channel/demo
  → incident/case
      → detection
          → ledger

incident/case
  → consent
  → guidance/knowledge
  → explanation

모든 중요 이벤트
  → audit
```

실제 은행·마이데이터·알림 발송 연동은 각 도메인이 외부 SDK를 직접 호출하지 않고 별도 adapter/port 뒤에 둔다.

---

### 1.4 필수 사용자 여정

#### 여정 A — 공개 합성 A/B 데모

1. `GET /api/v1/system/health`로 `syntheticDataOnly=true`, `externalActionsEnabled=false`를 확인한다.
2. 익명 데모 세션을 생성한다.
3. 고정 시나리오 `FIN_MGMT_AB_001`을 적재하고 최초 `demoRunId`를 받는다.
4. 고객 금융생활 요약과 `ALERT_FIN_MGMT_001`의 T0 경보 근거를 조회한다.
5. A 경로에서 `KNOWN_AND_INTENTIONAL` 응답과 서버가 확인한 네 종류의 T1 `ContextType` 근거를 적용한다.
6. `postDecision=CLOSE_AS_NORMAL_CONTEXT`, `state=CLOSED_NORMAL`을 확인한다.
7. 같은 seed와 T0 원시 snapshot으로 세션을 Reset하고 새로운 `demoRunId`를 받는다.
8. B 경로에서 `UNABLE_TO_CONFIRM`을 적용한다.
9. `postDecision=REQUIRE_BANK_REVIEW`, `state=PENDING_BANK_REVIEW`를 확인한다.
10. 신뢰연락인 미동의 정책 평가가 `BLOCKED_BY_CONSENT`로 차단됐음을 확인한다.
11. 행원 큐에서 사건을 열고 검토·재연락 또는 안내 계획을 처리한다.
12. 모든 결정, 동의, 상태, 직원행위와 버전을 감사이력에서 확인한다.

#### A/B 비교 불변조건

Reset 전후 다음 값은 동일해야 한다.

- `scenarioSeed`
- `snapshotHash`
- `alertId`
- 12개월 원시 거래
- 기준선과 특징값
- `reasonCodes`
- `algorithmVersion`
- `policyVersion`
- `preDecision`

T0는 경보 생성 시점의 불변 원시거래·기준선·특징·사유코드·사전판단 snapshot이다. T1은 고객 응답 뒤 서버가 조회한 처리지연·연결장애·취소·환불·결과화면 지연 등 후속 맥락 근거다. API는 `alertEvidenceIds`와 `contextEvidenceIds`를 분리하고, 각 T1 근거에 `effectiveAt`, `observedAt`, `ingestedAt`, `sourceType`, `version`, `integrityHash`를 보존한다. T1을 T0에 소급 병합하거나 탐지 입력으로 사용하지 않는다. Reset 전후 달라질 수 있는 값은 `demoRunId`, 고객 응답과 서버가 선택한 **T1 후속 맥락 패키지**뿐이다.

#### 여정 B — 고객 금융생활 확인

1. 고객 또는 합성 고객의 금융생활 요약을 조회한다.
2. 계좌·거래·정기납부와 개인 기준선을 확인한다.
3. 변화 알림에서 평소값, 현재값, 비교기간과 근거 거래를 확인한다.
4. 고객이 본인 거래·생활변화·확인 불가·나중 확인·은행 문의 중 하나를 선택한다.
5. 강한 신호는 단순 자기확인만으로 해제하지 않는다.
6. 구조적 근거와 일치하는 정상 생활변화만 종결한다.
7. 고객은 동의 철회, 이의제기와 사람의 재검토를 요청할 수 있다.

#### 여정 C — 행원 보호업무

1. 인증된 행원이 검토 우선순위와 상태로 사건큐를 조회한다.
2. 사건에 묶인 신호, 근거, 고객 응답과 미확인 항목을 확인한다.
3. 중립 질문과 공식 보호수단 후보, 상담기록 초안을 검토한다.
4. 검토 시작, 추가 확인, 재검토, 오탐 종결 또는 안내계획 승인을 수행한다.
5. `caseVersion`으로 동시수정을 방지한다.
6. actor는 요청 본문이 아니라 인증 주체에서 결정한다.
7. 모든 직원 결정과 수정이 감사로그에 남는다.

#### 여정 D — 동의와 신뢰연락인

1. 고객이 수신자, 목적, 발동조건, 전달항목, 기간을 별도로 동의한다.
2. 신뢰연락인은 초대 수락, 본인확인과 연락처 처리 동의를 별도로 수행한다.
3. 서버가 연락·제한열람·공동확인·송금·해지·차단 권한을 각각 분리한다.
4. 미동의·철회·만료·범위초과이면 발송 요청을 생성하기 전에 차단한다.
5. 실제 전달이 허용되더라도 최소정보만 제공한다.
6. 모든 동의 변경, 정책 평가와 열람을 감사한다.

`TrustedContactGate` 상세 응답은 다음 필드를 공통으로 사용한다.

| 필드 | 의미 |
|---|---|
| `gateEvaluated` | 이번 명령에서 연락 정책을 평가했는지 |
| `consentSnapshotId` | 평가에 사용한 불변 동의 snapshot |
| `consentStatus` | `GRANTED`, `NOT_GRANTED`, `REVOKED`, `EXPIRED` |
| `recipientAccepted` | 수신자 인증을 거친 수락 여부. 현재 외부 초대 기능 전에는 항상 `false` |
| `acceptanceStatus` | 현재 `PENDING_ACCEPTANCE` 또는 `UNVERIFIED`; 고객 요청만으로 수락 처리 금지 |
| `triggerMatched` | 고객이 동의한 발동조건에 해당하는지 |
| `fieldScopeMatched` | 보내려는 최소정보가 동의 범위 안인지 |
| `validityMatched` | 동의 기간·철회 상태가 유효한지 |
| `deliveryEnabled` | 모든 게이트가 통과하고 해당 환경에서 전달기능이 활성화됐는지 |
| `resultCode` | 허용·차단 결과; 미평가면 `null` |
| `dispatchAttempted` | 정책 게이트 통과 뒤 실제 외부 발송 adapter를 호출했는지 |
| `externalDeliveryRequested` | 외부 adapter 요청 객체를 만들었는지 |
| `externalDeliveryCreated` | 외부 전달 작업이 실제 생성됐는지; P0에서는 항상 `false` |

단일 `trustedContactGranted` boolean만으로 연락 가능 여부를 표현하지 않는다. P0 미동의 fixture에서는 `gateEvaluated=true`, `consentStatus=NOT_GRANTED`, `deliveryEnabled=false`, `dispatchAttempted=false`, `externalDeliveryRequested=false`, `externalDeliveryCreated=false`를 고정한다.

#### 여정 E — 공식 근거와 Copilot

1. 정책엔진이 승인된 카탈로그에서 적용 가능한 보호수단 후보를 고른다.
2. 서버가 발행기관, URL, 시행일, 확인 기준일과 적용조건을 반환한다.
3. 템플릿 또는 선택형 LLM이 후보를 쉬운 말과 상담 초안으로 표현한다.
4. LLM은 상태, 우선순위, 연락권한, `actionCode`를 변경하지 못한다.
5. API 키 없음, timeout, 429, 5xx, malformed JSON 또는 출력검증 실패 시 템플릿으로 폴백한다.

#### 현재 P0 계약의 보완 권고

본 문서의 P0-A 15개 API는 핵심 흐름과 readiness 분리·고객 확인 유예를 지원한다. SSOT가 요구하는 “12개월 금융생활 화면”을 프론트가 고정 fixture에 의존하지 않고 구성하기 위해 다음 읽기 계약을 P0-B로 함께 고정한다.

- 데모 세션 상태 조회
- 고객 금융생활 요약 조회
- 고객 계좌 목록 조회
- 계좌 거래내역 조회
- 고객 기준선·특징 요약 조회
- 공식 보호수단 카탈로그 조회

---

### 1.5 표준 상태와 enum

#### 사건 상태 `IncidentState`

```text
OPEN
→ AWAITING_CONTEXT
  → CONTEXT_DEFERRED → AWAITING_CONTEXT
  → CLOSED_NORMAL
  → PENDING_BANK_REVIEW
    → IN_BANK_REVIEW
      → FOLLOW_UP_REQUIRED → IN_BANK_REVIEW
      → GUIDANCE_PLAN_APPROVED
        → CLOSED_GUIDANCE_DELIVERED
      → CLOSED_FALSE_POSITIVE
```

| 값 | 의미 |
|---|---|
| `OPEN` | 변화신호가 고객 사건으로 생성됨 |
| `AWAITING_CONTEXT` | 고객의 본인거래·생활맥락 확인 대기 |
| `CONTEXT_DEFERRED` | 고객이 나중 확인을 선택함 |
| `CLOSED_NORMAL` | 고객 응답과 구조적 근거가 일치한 정상 변화 종결 |
| `PENDING_BANK_REVIEW` | 설명이 필요해 행원 큐에 등록됨 |
| `IN_BANK_REVIEW` | 행원이 검토 중 |
| `FOLLOW_UP_REQUIRED` | 추가 확인 또는 재연락 필요 |
| `GUIDANCE_PLAN_APPROVED` | 행원이 상담 안내 계획만 승인함; 고객 전달·외부 실행은 아직 없음 |
| `CLOSED_GUIDANCE_DELIVERED` | 승인된 안내가 고객에게 실제 전달됐다는 별도 기록 후 종결 |
| `CLOSED_FALSE_POSITIVE` | 데이터·규칙상 오탐으로 종결 |

`BLOCKED_BY_CONSENT`는 `IncidentState`가 아니다. 연락·정보제공 정책 평가의 거절 결과 코드다.

#### 판단 코드 `DecisionCode`

| 값 | 사용 시점 |
|---|---|
| `NEEDS_CONTEXT` | 생활맥락 확인 전 `preDecision` |
| `CLOSE_AS_NORMAL_CONTEXT` | 검증된 정상 생활변화의 `postDecision` |
| `REQUIRE_BANK_REVIEW` | 추가 설명이 필요한 `postDecision` |

#### 생활맥락 요청 DTO `ContextResponse`

`ContextResponse`는 고객 선택과 서버 fixture 선택자를 분리한다. 고객이 보내는 의미값은 `responseCode`, 선택적 분류는 `contextType`이며 구조적 증거 자체를 요청 본문으로 받지 않는다.

| 값 | 의미 | 기본 처리 |
|---|---|---|
| `KNOWN_AND_INTENTIONAL` | 내가 알고 한 거래 | 적격 구조적 근거가 모든 강한 신호를 설명할 때만 정상종결 후보 |
| `LIFE_CONTEXT_CHANGED` | 생활변화가 있었음 | 유효기간·출처가 있는 구조적 근거 확인 후 정상종결 후보 |
| `UNABLE_TO_CONFIRM` | 본인 거래인지 확인하기 어려움 | 행원 검토 전환 |
| `NOT_MY_TRANSACTION` | 내가 하지 않은 거래 | 행원 검토와 기존 FDS·긴급 은행연락 경로 안내 |
| `DEFERRED` | 나중에 확인 | `CONTEXT_DEFERRED` |
| `REQUEST_BANK_REVIEW` | 은행 문의 선택 | 행원 검토 전환 |

`ContextType`은 서버가 확인한 T1 구조적 근거의 API 타입이며 SSOT의 `ContextEvidenceCode`와 같은 다음 값으로 통일한다. 클라이언트는 이 값을 주장하거나 직접 전송하지 않는다.

| 값 | 의미 |
|---|---|
| `PAYMENT_PROVIDER_DELAY_VERIFIED` | 납부기관 처리지연이 출처·시간·무결성값으로 확인됨 |
| `ACCOUNT_CONNECTION_OUTAGE_VERIFIED` | 연결장애가 정기납부 누락 관찰기간과 일치함 |
| `DUPLICATE_TRANSFER_REFUNDED` | 중복송금 한 건의 취소·환불 완료가 확인됨 |
| `RESULT_SCREEN_DELAY_VERIFIED` | 반복확인 시간대의 결과화면 지연이 확인됨 |

근거 없음은 별도의 긍정적 `ContextType`으로 만들지 않고 빈 `contextTypes`·`contextEvidenceIds`로 표현한다.

#### 사유코드 `ReasonCode`

| 값 | 의미 |
|---|---|
| `DUPLICATE_PAYMENT` | 시간창 내 중복 가능 결제 |
| `DUPLICATE_TRANSFER` | 동일 수취인·동일 금액·시간창 내 중복 가능 송금 |
| `MISSED_RECURRING` | 유예기간 내 예상 정기납부 미발생 |
| `REPEATED_RETRY` | 동일 금융업무의 취소·재시도 반복 |
| `UNFINISHED_TASK` | 시작한 납부·송금·조회 업무의 미완료 증가 |
| `REPEATED_INQUIRY` | 같은 내용의 고객센터·영업점 문의 반복 |
| `POST_EXPLANATION_RECURRENCE` | 행원 설명·상담 후 동일 질문·행동 재발 |
| `REPEATED_CONFIRMATION` | 완료된 거래·납부 결과의 단시간 반복 확인 |

#### 탐지 준비상태 `DetectionReadiness`

- `READY`
- `LOW_CONFIDENCE`
- `COLD_START`

`COLD_START`는 90일 미만 이력으로 강한 결론을 내릴 수 없음을 의미한다.

#### 행원 검토 액션 `CaseReviewAction`

| 값 | 허용 상태 | 결과 상태 |
|---|---|---|
| `START_REVIEW` | `PENDING_BANK_REVIEW` | `IN_BANK_REVIEW` |
| `RESUME_REVIEW` | `FOLLOW_UP_REQUIRED` | `IN_BANK_REVIEW` |
| `REQUIRE_FOLLOW_UP` | `IN_BANK_REVIEW` | `FOLLOW_UP_REQUIRED` |
| `CLOSE_FALSE_POSITIVE` | `IN_BANK_REVIEW`, `FOLLOW_UP_REQUIRED` | `CLOSED_FALSE_POSITIVE` |
| `APPROVE_GUIDANCE_PLAN` | `IN_BANK_REVIEW` | `GUIDANCE_PLAN_APPROVED` |

#### 추가 공통 enum

| enum | 값 또는 원칙 |
|---|---|
| `ReviewPriority` | 행원 업무순서용 `HIGH`, `MEDIUM`, `LOW`; 고객 위험도 아님 |
| `ExecutionType` | P0 보호수단은 항상 `GUIDANCE_ONLY` |
| `EvidencePhase` | 불변 경보근거 `T0_ALERT`, 후속 맥락근거 `T1_CONTEXT` |
| `GuidancePlanStatus` | `NOT_APPROVED`, `APPROVED`, `DELIVERED`; 승인과 전달을 분리 |
| `DataMode` | 공개 데모는 항상 `SYNTHETIC_ONLY` |
| `ActorType` | `SYSTEM`, `CUSTOMER`, `STAFF`, `DEMO_STAFF`, `POLICY_ENGINE` 등 |
| `TransactionStatus` | 최소 `PENDING`, `POSTED`, `CANCELED`, `REFUNDED`를 구분 |

새 enum 값은 하위호환 추가로 취급하되 프론트는 반드시 unknown fallback을 가진다.

---

### 1.6 P0·P1·P2 경계

#### P0 — 공모전 핵심 흐름

- 무로그인 익명 데모 세션과 멱등 Reset
- 12개월 완전 합성 거래와 `FIN_MGMT_AB_001` 고정 fixture
- 합성 고객·계좌·거래·정기납부의 조회용 read model
- 중앙값·MAD 기준선과 cold-start 처리
- 정기납부 반복 누락·중복송금·거래완료 반복확인 탐지
- 동일 `alertId`의 A/B 생활맥락 재평가
- 고객 알림·맥락 확인·행원 사건큐의 세 화면 지원
- 정상종결과 행원검토 상태전이
- 미동의 신뢰연락인 hard block
- 행원 승인 전후 모두 실제 외부 실행 0건
- 구조화 공식 보호수단 카탈로그와 출처·기준일
- 템플릿 설명과 no-key 폴백
- 사건·근거·동의·결정·직원행위 감사로그
- 공개 URL, 헬스체크와 세션 격리

#### P1 — 완성도와 은행 PoC

- 행원 인증, MFA, RBAC와 중요행위 추가인증
- 고객 프로필, 접근성, 알림 설정
- 계좌·거래·수취인·정기납부의 완전한 조회·검색 API
- 동의 부여·철회와 신뢰연락인 정책 전체 생명주기
- 사건 배정, 검색, 필터, 메모, SLA, 재연락 관리
- 선택형 LLM의 쉬운 설명·질문·상담기록 초안
- pgvector 기반 승인 문서 RAG
- 금융 기억노트, 하루 브리핑, 자연어 거래검색
- 평가 대시보드와 리포트 자동 생성
- 문서·정책·시나리오·운영 관리자 API

#### P2 — 실증과 제품 확장

- 금융회사 또는 허가받은 마이데이터 사업자 연결
- 실제 동의 고객의 읽기 전용 데이터 동기화
- 실제 신뢰연락인 초대·최소정보 알림 연동
- 음성검색·TTS 고도화
- 다기관 문서와 권한형 RAG
- 내부·외부 모델 데이터등급 라우팅
- 모델 registry, shadow, canary, kill switch
- 증권계좌·보유자산 등 읽기 전용 투자정보 모델
- 신탁·후견·안심차단 등 공식 상담 채널 handoff

#### 항상 외부 소유로 남길 금융 실행

다음 기능은 150개 이상 전체 카탈로그에 포함할 수 있지만 ALZ's well 내부 실행 API로 구현하거나 구현했다고 주장하지 않는다.

- 실제 이체·예약이체·자동이체 등록 또는 해지
- 지급정지·계좌동결·이체한도 변경
- 계좌개설·예금가입·대출신청·카드발급
- 증권 주문·정정·취소
- 법적 후견·신탁 가입 또는 적격성 최종판정
- 고객 동의 없는 가족·신뢰연락인 연락

이 항목은 `EXTERNAL_INTEGRATION` 또는 `REFERENCE_ONLY`로 분류하고, 금융회사 코어가 최종 실행과 법적 판단을 소유함을 명시한다.

---

### 1.7 다시 발생하면 안 되는 충돌

| 충돌 항목 | 폐기할 값·표현 | 최종 값·원칙 |
|---|---|---|
| 서비스명 | `안심리듬`, `치매머니`의 외부 노출 | `ALZ's well` |
| 부제·고객 라벨 | 인지취약 고객 분류, 치매 위험도 | 금융생활 변화와 확인 필요 사유 |
| P0 데모 소재 | 병원비·이사 고액송금 | 정기납부 반복 누락·중복송금·거래완료 반복확인 `FIN_MGMT_AB_001` |
| A/B 대상 | 서로 다른 두 고객 | 같은 익명 세션을 Reset한 동일 사건 |
| A/B 차이 | B에만 추가 거래·신호 | 맥락 패키지만 변경 |
| 행원 대기상태 | `BANK_REVIEW` | `PENDING_BANK_REVIEW` |
| 동의 차단 | 사건 상태 또는 HTTP 오류 | 정책결과 `BLOCKED_BY_CONSENT` |
| 정기납부 코드 | `MISSED_RECURRING_PAYMENT` | `MISSED_RECURRING` |
| 중복송금 코드 | `REPEATED_TRANSFER` | `DUPLICATE_TRANSFER` |
| 반복확인 코드 | 자유문구 또는 조회횟수 | `REPEATED_CONFIRMATION` |
| 위험평가 엔터티 | 질병·고객 위험을 암시하는 `risk_assessment` | `PolicyDecision` 또는 `ReviewAssessment` |
| 우선순위 필드 | `risk`, `riskScore`, 모호한 `priority` | 업무순서용 `reviewPriority` |
| 정상종결 | 고객의 `괜찮아요`만으로 종결 | 서버 구조적 근거와 응답이 일치할 때만 허용 |
| AI 역할 | LLM이 상태·실행 결정 | 규칙·정책이 결정, LLM은 표현 초안만 생성 |
| P0 AI | 별도 AI 서버·sLLM 필수 | 결정론적 템플릿이 기본, LLM은 P1 |
| P0 RAG | 벡터DB 필수 | 구조화 공식 근거 카탈로그 P0, 벡터 RAG P1 |
| 백엔드 구조 | MSA·Kafka·Kubernetes | Spring Boot 모듈형 모놀리스 |
| Spring 버전 | 원본 문서의 Spring Boot 4.1 | 실제 빌드 기준 Spring Boot 3.5.16 |
| 프론트 결합 | Thymeleaf·HTMX 전용 계약 | React·Vue에서도 사용할 JSON REST 계약 |
| 합성데이터 규모 | 240개 거래 또는 240개 시연 고객 | 평가용 합성 프로필 240개 목표, 시연 페르소나는 소수 |
| 공개 데모 보안 | JWT·RBAC 구현 완료 주장 | 현재 합성데모는 공개, PoC·운영 전에 RBAC 구현 |
| 보호계획 승인 | 실제 지급정지·차단 승인 또는 고객 전달 완료 | 상담 `guidancePlan` 승인만 기록, 전달·외부 실행은 별도 상태 |
| 신뢰연락인 | 가족이면 자동 권한 | 별도 동의와 최소정보, 법적 대리권과 분리 |
| 데이터 연결 | 실제 전 금융사 연결 주장 | P0 합성데이터, 실연동은 적법한 제휴 후 P2 |

---

### 1.8 전역 API 안전 불변식

다음 조건은 개별 엔드포인트보다 우선한다.

1. 거래 데이터로 치매·인지저하·의사결정능력 점수나 라벨을 생성하지 않는다.
2. 나이만으로 `reviewPriority`를 높이지 않는다.
3. 실제 송금, 지급정지, 계좌차단, 한도변경을 ALZ's well이 자동 실행하지 않는다.
4. 고객 동의 없이 가족·신뢰연락인 연락 또는 정보제공 요청을 만들지 않는다.
5. `CLOSED_NORMAL`은 고객 응답과 서버가 확인한 구조적 근거가 일치할 때만 허용한다.
6. 최초 `preDecision`, 근거 snapshot과 계산 버전을 덮어쓰지 않는다.
7. LLM은 상태, 우선순위, 연락권한, 사유코드와 실행코드를 변경하지 못한다.
8. 외부 LLM에는 비식별 구조화 요약만 보내고 prompt·completion 원문 로그는 기본 비활성화한다.
9. 공식 보호수단은 출처, 기준일과 적용조건 없이 반환하지 않는다.
10. 상태 변경 명령 중 본 명세가 `Idempotency-Key`를 표시한 API는 서버 계산 `requestHash`와 함께 중복 실행을 방지하고, 직원 사건 변경은 `caseVersion`으로 동시수정을 막는다. 조회·검색·평가와 인증 세션 명령은 각 API에 명시된 별도 중복 호출 정책을 따르며 데모 세션 생성은 비멱등·rate-limited다.
11. actor는 요청 본문이 아니라 인증·세션 주체에서 결정한다.
12. T0 경보 근거와 T1 맥락 근거는 별도 snapshot으로 저장하며 T1을 T0 탐지 근거로 소급 사용하지 않는다.
13. Reset은 같은 T0 snapshot을 복원하되 새 `demoRunId`를 발급하고 이전 run의 T1·상태·감사이력을 덮어쓰지 않는다.
14. 안내계획 승인과 고객 전달 완료를 같은 상태나 시각으로 기록하지 않는다.
12. 세션 소유관계를 capability로 확인할 수 없는 자원은 정보노출을 막기 위해 `404`로 응답한다.
13. 금액은 10진 문자열과 통화코드로, 장기 seed는 문자열로 직렬화한다.
14. 모든 중요 결정은 `traceId`, actor, 상태 전후값, 알고리즘·정책·모델·프롬프트·문서·스키마 버전과 함께 감사한다.
15. P0의 모든 금융·연락 실행 결과는 `externalExecutionCreated=false`여야 한다.

이 불변식을 위반하는 API는 우선순위나 화면 요구와 관계없이 최종 명세에 포함하지 않는다.

---

## 2. 은행·증권 기능 근거 및 API 경계

> 조사·확인 기준일: **2026-08-14 (Asia/Seoul)**  
> 대상 기관: **하나은행, 신한은행, 카카오뱅크, KB증권**  
> 근거 범위: 각 기관의 공식 홈페이지, 공식 서비스 안내, 약관, 신청서 및 공지사항

이 장의 API 도메인 매핑은 공식적으로 공개된 고객 기능을 ALZ's well의 백엔드 경계로 변환한 **설계 추론**이다. 각 기관의 비공개 내부 API 구조나 제휴 가능 여부를 의미하지 않는다. 또한 기능 동등성만 참고하며, 기관의 화면·문구·브랜드 자산을 복제하지 않는다.

---

### 2.1 공식 출처 레지스터

#### 2.1.1 하나은행

| ID | 공식 근거 | 기준 날짜 | 확인된 기능 |
|---|---|---|---|
| `H1` | [NEW 하나원큐 출시 안내](https://m.kebhana.com/cont/news/news01/1522805_127351.jsp) | 공지 2026-02-13, 출시 2026-02-19 | 맞춤형 홈, 종합자산관리, 입출금·대출이자·신용정보 변동 통합 알림, 영어 홈, 연결된 가족 등의 자산 공동관리 |
| `H2` | [신 하나원큐 앱 이용약관](https://image.kebhana.com/cont/download/documents/provide/0000020260213_20260219.pdf) | 공개 파일 2026-02-19 | 인증서·PIN·공동/금융인증서, FDS 연계 추가 인증, 생체정보 별도 동의, 거래지시와 처리결과 확인 |
| `H3` | [마이데이터서비스 이용약관 개정 안내](https://m.kebhana.com/cont/news/news01/1525746_127351.jsp) | 공지 2026-05-22, 시행 2026-06-25 | 마이데이터 가입·이용계약 및 가입 연령 범위 |
| `H4` | [하나원큐 모바일 안내](https://m.kebhana.com/intro.html) | 확인 2026-08-14 | 맞춤형 홈, 타행 이체, 자산·지출 통합조회, 상품 추천 |
| `H5` | [하나원큐 길라잡이·디지털금융교육](https://kebhana.com/cont/customer/customer04/customer0402/1505652_114300.jsp) | 확인 2026-08-14 | 고령 금융소비자 대상 단계별 학습, 거래내역조회·이체·공과금 납부 연습, 금융사기 예방 교육 |

#### 2.1.2 신한은행

| ID | 공식 근거 | 기준 날짜 | 확인된 기능 |
|---|---|---|---|
| `S1` | [신한 Open API 마켓](https://api.shinhan.com/) | 확인 2026-08-14 | 계좌 거래내역 조회, 이체, 환율조회 등 제휴 API |
| `S2` | [신한 마이데이터 소개](https://openapi.shinhan.com/mydata/info) | 확인 2026-08-14 | 은행 계좌·대출, 카드 소비, 투자종목·투자금액·자산규모, 보험·연금 정보 활용 |
| `S3` | [법인 오픈뱅킹 서비스 이용약관](https://img.shinhan.com/sbank2016/yak/20241217000000360001LC000030.PDF?1735140015785=) | 시행 2024-12-26 | 잔액·거래내역 조회, 인증, FDS 탐지 시 서비스 이용 중단 및 추가 인증 |
| `S4` | [오픈뱅킹 안심차단 서비스 신청서](https://img.shinhan.com/sbank2016/form/20250225000000510002WF00003000000006.PDF?1755098749341=) | 개정 2025-08-14 | 오픈뱅킹 등록·출금이체·잔액조회·거래내역조회 차단, 차단 등록상태 통지 |
| `S5` | [마이데이터 대면 상담서비스 이용·변경·철회 신청서](https://img.shinhan.com/sbank2016/form/20250620000000620003WF00003000000008.PDF?1762788766773=) | 제정 2025-06-27 | 고객이 지정한 직원만 정보 조회, 직원 변경·철회, 상담 종료 또는 철회 후 접근 불가 |
| `S6` | [SOL뱅크 쉬운 홈 따라하기](https://nsol.shinhan.com/contents/guide/step03__set-easyhome/index.html) | 확인 2026-08-14 | 쉬운 홈 설정, 실제 가입·거래가 발생하지 않는 단계별 체험 |

#### 2.1.3 카카오뱅크

| ID | 공식 근거 | 기준 날짜 | 확인된 기능 |
|---|---|---|---|
| `K1` | [카드 청구금액 알림](https://www.kakaobank.com/products/openbankingCard) | 심의 유효 2026-04-01~2027-03-31 | 오픈뱅킹 기반 카드 결제예정금액·결제계좌 잔액 조회 및 알림 |
| `K2` | [카카오톡 알림톡 서비스 확대 공지](https://m.kakaobank.com/Notices/view/15219) | 공지 2024-05-16 | 보안·입출금·카드결제·서비스 알림, 알림톡 미수신 시 SMS/LMS 전환 |
| `K3` | [전자상거래 업체 고객정보 유출 관련 피해 예방수칙](https://m.kakaobank.com/Notices/view/18036) | 공지 2025-12-02 | AI 스미싱 확인, 명의도용방지, 비대면 계좌개설·여신거래·오픈뱅킹 안심차단 |
| `K4` | [카카오뱅크 고객센터](https://www.kakaobank.com/view/customer) | 확인 2026-08-14 | 24시간 사고신고, 전화번호 진위확인, 고령자 전용전화, 챗봇·상담원 연결 |

#### 2.1.4 KB증권

| ID | 공식 근거 | 기준 날짜 | 확인된 기능 |
|---|---|---|---|
| `B1` | [KB증권 마이데이터 서비스 이용약관](https://fdata.kbsec.com/agree/online12.pdf) | 시행 2025-04-14 | 자산·연금·보험·소비 통합조회, 전송요구 범위·주기·종료시점·철회 |
| `B2` | [M-able 서비스 소개](https://m.kbsec.com/go.able?linkcd=s060100010001) | 확인 2026-08-14 | 잔고·자산현황·투자일정·펀드·기업정보·뉴스·주문 기능 |
| `B3` | [마이데이터 2.0 시행 안내](https://m.kbsec.com/go.able?idt=20250619&linkcd=s060300010000&seq=10008609) | 시행 2025-06-19 | 모든 기관 연결 동의, 조회부터 해지, 최대 5년 가입, 총자산·자산달력·노후·절세 콘텐츠 |
| `B4` | [금융사기 피해예방 사례 및 방지 요령](https://www.kbsec.com/go.able?idt=20260625&linkcd=s060901010000&seq=10010010) | 공지 2026-06-25 | 주식매도·담보대출 후 이체 등 복합행동의 FDS 탐지, 거래 차단, 직원 확인 |
| `B5` | [KB증권 금융접근성 향상 안내](https://www.kbsec.com/go.able?linkcd=s500231110000) | 확인 2026-08-14 | 스크린리더·화면확대·음성인식·보이는 ARS·톡/화상상담·고령자 쉬운 말 서비스 |
| `B6` | [지연이체서비스 유의사항](https://www.kbsec.com/go.able?linkcd=s02020201L800) | 확인 2026-08-14 | 지연 실행, 실행 전 취소, 즉시이체 예외계좌, 처리상태 확인 |

---

### 2.2 기능과 API 도메인 매핑

상태의 의미는 다음과 같다.

| 상태 | 의미 |
|---|---|
| `MVP` | ALZ's well이 합성데이터와 자체 정책으로 구현하는 기능 |
| `EXTERNAL` | 내부에는 계약·상태·어댑터·공식 링크만 정의하고 실제 처리는 금융기관 또는 허가된 연동 사업자가 수행하는 기능 |
| `OUT` | 전체 기능 지도에는 남길 수 있지만 ALZ's well 백엔드가 실행 기능을 제공하지 않는 범위 |

하나의 기능이 `MVP + EXTERNAL`로 표시될 수 있다. 이는 정규화된 읽기 모델과 상태 관리는 자체 구현하지만 실제 금융데이터 수집이나 행위 실행은 외부에 위임한다는 의미다.

| 기능 | 권장 API 도메인 | 공식 근거 | 경계 | ALZ's well 적용 내용 |
|---|---|---|---|---|
| 로그인·세션·기기 | `identity`, `auth-session`, `device` | H2, S3, B2 | `MVP + EXTERNAL + OUT` | 데모 고객·행원 역할, 세션, 권한검사는 `MVP`; 실제 인증서·PIN·생체인증은 `EXTERNAL`; 금융 인증정보 발급·보관은 `OUT` |
| 금융기관 연결 | `institution`, `connection` | H4, S1, K1, B1, B3 | `MVP + EXTERNAL` | 합성 연결의 생성·목록·상태·동기화 이력은 `MVP`; 실제 오픈뱅킹·마이데이터 연결은 `EXTERNAL` |
| 동의·전송요구 | `consent`, `data-grant`, `transmission-request` | H3, S2, S5, B1, B3 | `MVP + EXTERNAL` | 목적·범위·기관·주기·만료·철회·감사이력은 `MVP`; 실제 표준 API 전송요구는 `EXTERNAL` |
| 계좌·잔액 | `financial-account`, `balance` | H4, S1, S3, K1, B1 | `MVP + EXTERNAL + OUT` | 은행·카드·증권 계좌의 통합 읽기 모델은 `MVP`; 실시간 원천데이터는 `EXTERNAL`; 계좌 개설·해지는 `OUT` |
| 거래내역 | `transaction` | S1, S3, K2, B1 | `MVP + EXTERNAL + OUT` | 목록·상세·검색·분류·상대방·거래 후 잔액은 `MVP`; 실거래 조회는 `EXTERNAL`; 이체·취소·정정은 `OUT` |
| 카드청구·대출이자·정기지출 | `obligation`, `card-bill`, `recurring-payment` | H1, S2, K1 | `MVP + EXTERNAL + OUT` | 예정일·예정금액·잔액충족 여부·누락/중복 변화는 `MVP`; 원천데이터는 `EXTERNAL`; 결제·대출상환은 `OUT` |
| 통합자산 | `asset-summary`, `asset-snapshot` | H1, H4, S2, B1, B3 | `MVP + EXTERNAL` | 기관·상품군별 자산/부채 요약과 기준 스냅샷은 `MVP`; 실제 수집은 `EXTERNAL` |
| 증권계좌·보유종목 | `investment-account`, `position`, `portfolio` | S2, B1, B2, B3 | `MVP + EXTERNAL` | 종목·수량·평균단가·평가금액·현금잔고·손익의 읽기 모델은 `MVP`; 실시간 시세·원장은 `EXTERNAL` |
| 증권 거래맥락 | `trade`, `order-view`, `investment-cashflow` | B2, B4 | `MVP + EXTERNAL + OUT` | 체결내역·대량매도·신규 담보대출·증권계좌 출금의 읽기 맥락은 `MVP`; 실시간 주문 상태는 `EXTERNAL`; 주문 제출·정정·취소·대출 실행은 `OUT` |
| 시장정보·리서치 | `market-data`, `research`, `investment-calendar` | B2 | `EXTERNAL` | 종목 메타데이터와 일정의 제한적 캐시만 허용하며 실시간 시세·뉴스·리포트는 외부 출처와 이용조건을 따른다 |
| 알림함·수신설정 | `notification`, `notification-preference`, `inbox` | H1, S1, K1, K2, B2 | `MVP + EXTERNAL` | 인앱 알림, 유형별 수신설정, 읽음 상태는 `MVP`; 푸시·알림톡·SMS/LMS 전송은 `EXTERNAL` |
| 변화신호·설명 | `baseline`, `signal`, `alert`, `reason-code` | H2, S3, K3, B4 | `MVP + EXTERNAL` | ALZ 행동변화 탐지, 비교 기준, 설명 가능한 사유코드는 `MVP`; 금융회사의 실제 FDS 판정은 `EXTERNAL` |
| 생활맥락·재평가 | `context-response`, `evidence-package`, `reevaluation` | S5 및 ALZ 핵심 흐름 | `MVP + OUT` | 서버에 등록된 합성 증거, 고객 응답, 재평가, 상태전이는 `MVP`; 고객이 올린 자료를 검증 없이 구조적 증거로 신뢰하는 방식은 `OUT` |
| 보호수단 | `protection-option`, `protection-status`, `guidance` | S4, K3, B6 | `MVP + EXTERNAL + OUT` | 이용 가능한 보호수단·상태·공식 설명·안내 계획은 `MVP`; 안심차단·지연이체 신청/해제는 `EXTERNAL`; 자체 금융조치는 `OUT` |
| 고객지원 | `support-channel`, `incident-guide`, `official-contact` | H5, K4, B5 | `MVP + EXTERNAL` | 공식 연락처·운영시간·사고유형·딥링크는 `MVP`; 전화·채팅·사고신고 처리는 `EXTERNAL` |
| 행원 사건관리 | `case`, `staff-review`, `guidance-plan` | S5, B4 | `MVP + OUT` | 사건큐, 검토우선순위, 문진 초안, 안내계획 승인은 `MVP`; 고객 연락·계좌조치·FDS 해제는 `OUT` |
| 행원 데이터 접근권 | `staff-access-grant`, `access-policy` | S5 | `MVP + EXTERNAL` | 지정 행원·목적·범위·만료·철회와 접근결정은 `MVP`; 실제 은행 IAM 연계는 `EXTERNAL` |
| 쉬운 화면·접근성 | `accessibility-preference`, `guided-demo` | H5, S6, K4, B5 | `MVP + EXTERNAL` | 큰 글씨·고대비·쉬운 말·언어·단순 홈·무거래 따라하기 설정은 `MVP`; 보이는 ARS·화상/전담상담은 `EXTERNAL` |
| 가족·신뢰관계 | `trusted-relationship`, `sharing-grant` | H1 | `MVP + OUT` | 명시적 초대·승인·범위·만료가 있는 읽기 공유는 `MVP`; 묵시적 가족 열람이나 자동 연락은 `OUT` |
| 감사·접근이력 | `audit-event`, `consent-audit`, `access-log` | H2, S5, B1 | `MVP + EXTERNAL` | 동의·조회·판정·상태변경·행원 접근 이력은 `MVP`; 금융기관 원본 감사로그는 `EXTERNAL` |
| 상품·계좌개설·대출 | `product-catalog`, `application-reference` | H1, H4, S1, K3, B2 | `EXTERNAL + OUT` | 검증된 공식 상품·보호수단 링크만 제공할 수 있으며 신청·심사·약정·발급은 `OUT` |

---

### 2.3 기관별 최소 흡수 기능

#### 2.3.1 하나은행에서 흡수할 기능

- 기관·상품군을 넘나드는 통합자산 홈과 자산 스냅샷
- 입출금, 대출이자, 신용정보 변동을 한곳에서 보는 알림함
- 개인별 맞춤 메뉴와 언어·접근성 설정
- 가족 등 신뢰관계 공유를 위한 **명시적 동의·범위·만료 모델**
- 실제 금융행위 없이 연습할 수 있는 고령자용 안내·교육 흐름

#### 2.3.2 신한은행에서 흡수할 기능

- 금융기관 연결, 데이터 범위, 주기, 만료, 철회를 포함하는 동의 생명주기
- 잔액·거래내역 중심의 표준 읽기 모델
- 고객이 지정한 행원만 제한된 목적과 시간 동안 접근하는 권한 모델
- FDS 또는 추가 인증이 필요한 경우를 표현하는 외부 정책결과 상태
- 쉬운 홈과 단계별 무거래 체험

#### 2.3.3 카카오뱅크에서 흡수할 기능

- 카드 결제예정금액과 결제계좌 잔액을 함께 보여주는 의무·잔액 맥락
- 보안·입출금·카드·서비스 알림을 합친 간결한 알림함과 채널 설정
- 공식 전화번호 진위확인, 24시간 사고신고, 고령자 전용전화 등 지원 채널 디렉터리
- 비대면 계좌개설·여신거래·오픈뱅킹 안심차단의 상태 및 공식 안내 카탈로그

#### 2.3.4 KB증권에서 흡수할 기능

- 총자산, 증권계좌, 보유종목, 현금잔고, 손익의 통합 읽기 모델
- 매도, 담보대출, 증권계좌 출금이 이어지는 복합 금융행동 맥락
- 체결내역과 투자일정의 읽기 전용 정보
- 금융사기 탐지 후 행원 확인으로 이어지는 사건 검토 구조
- 스크린리더, 화면확대, 쉬운 말, 보이는 상담 등 접근성 메타데이터

---

### 2.4 MVP·EXTERNAL·OUT 명시적 경계

#### 2.4.1 `MVP`: 자체 구현 범위

- 합성 또는 사전 등록 데이터에 대한 계좌·잔액·거래·의무·투자자산 읽기 모델
- 연결과 동의의 생성, 조회, 범위, 만료, 철회 및 감사이력
- 기준선, 변화신호, 설명 가능한 사유코드, 고객 생활맥락, 재평가
- 인앱 알림함과 알림 설정
- 보호수단의 가용 여부·설명·공식 출처와 `GUIDANCE_ONLY` 안내 계획
- 동의 범위 내 행원 사건큐·검토·안내 승인
- 접근성 설정, 쉬운 말, 무거래 따라하기
- 신뢰관계의 명시적 초대·승인·철회와 읽기 권한

#### 2.4.2 `EXTERNAL`: 외부 연동·참고 범위

- 금융기관 인증서, PIN, 생체인증 및 추가 인증
- 오픈뱅킹·마이데이터 실연결, 전송요구 및 원천데이터 동기화
- 금융기관이 산출한 FDS 결과와 이용 제한 상태
- 실시간 시세·뉴스·리서치·주문 상태
- 푸시·알림톡·SMS/LMS 전송
- 안심차단·지연이체·명의도용방지 서비스 신청/해제
- 은행 IAM, 전화·채팅·영업점·사고신고 처리

`EXTERNAL` 기능은 내부 API에서 다음만 표현한다.

- 외부 제공자와 기능 식별자
- 이용 가능 여부와 현재 알려진 상태
- 마지막 확인 시각과 데이터 신선도
- 필요한 동의·권한
- 검증된 공식 URL 또는 승인된 어댑터 호출 결과
- 외부 장애·점검·미지원 상태

#### 2.4.3 `OUT`: 구현 금지 범위

- 실제 계좌이체, 예약이체 실행, 이체 취소 또는 자금 회수
- 국내·해외주식 주문 제출·정정·취소
- 대출 신청·심사·약정·실행·상환
- 예금·카드·증권계좌 개설, 발급 또는 해지
- ALZ 자체 판단에 의한 거래 차단·지급정지·FDS 해제
- 고객의 명시적 동의 없는 가족·보호자 열람 또는 연락
- 고객이 업로드한 자료를 검증 없이 신뢰 증거로 승격하는 처리
- 직원의 임의 조회, 클라이언트가 지정한 행위자 정보 신뢰, 감사이력 삭제·덮어쓰기

#### 2.4.4 필수 모델링 규칙

1. **ALZ 행동변화 신호와 금융기관 FDS 판정은 분리한다.**  
   ALZ가 생성한 결과는 설명과 확인이 필요한 `BEHAVIOR_CHANGE` 신호이고, 외부 기관이 제공한 경우에만 `EXTERNAL_FDS` 출처를 표시한다.

2. **보호수단은 실행이 아니라 상태와 안내를 제공한다.**

   ```text
   AVAILABLE
   ENROLLED
   NOT_ENROLLED
   UNKNOWN
   EXTERNAL_ONLY
   ```

   기본 실행정책은 `GUIDANCE_ONLY`다.

3. **외부 데이터의 출처와 신선도를 보존한다.**

   ```text
   sourceProvider
   sourceUpdatedAt
   dataFreshness
   connectionId
   consentId
   consentScope
   ```

4. **행원 접근은 고객 식별자만으로 허용하지 않는다.**  
   최소한 `grantId`, `purpose`, `scope`, `expiresAt`, `revokedAt`을 검사하고 접근결정을 감사이력에 남긴다.

5. **실행형 금융 API는 카탈로그와 구현을 구분한다.**  
   전체 API 지도에는 `REFERENCE_ONLY` 또는 `OUT`으로 기록할 수 있지만 Spring Controller와 실행 로직은 제공하지 않는다.

6. **신뢰관계는 곧 연락 동의가 아니다.**  
   자산 읽기 공유, 경보 열람, 연락 허용은 각각 별도 범위로 동의받으며 자동 연락은 기본적으로 비활성화한다.

---

## 3. ALZ's well 전체 백엔드 API 마스터 카탈로그

> 문서 상태: 전체 제품 API 지도  
> 기준일: 2026-08-14  
> API 기준 버전: v1  
> 아키텍처: Java 21 · Spring Boot 3.5.16 · PostgreSQL · 모듈러 모놀리스  
> 상위 기준서: ALZS_WELL_PROJECT_SSOT.md  
> 상세 P0 계약: 본 문서 5~7장에 통합

이 문서는 ALZ's well의 핵심 금융생활 변화 확인 서비스와 은행·카드·증권 웹서비스의 공통 기능을 하나의 백엔드 API 지도에 정리한다. 기능 개수가 제품 범위를 자동으로 넓히는 것은 아니다. 우선 전체 지도를 고정한 뒤 P0를 먼저 구현하고, 일정에 따라 P1과 P2를 뒤로 미룬다.

은행·증권 기능은 특정 회사의 비공개 API를 복제한 것이 아니라 일반적인 기능 범주를 ALZ's well의 공개 REST 계약으로 재구성한 것이다.

---

### 3.1 총괄 집계

| 구분 | 수량 |
|---|---:|
| 전체 | **282** |
| P0-A 기존 핵심 데모·운영 안전성 | **15** |
| P0-B 공개 데모 뱅킹 셸 보강 | **11** |
| P1 제품 핵심 | **177** |
| P2 은행·증권 확장 | **79** |
| OWNED | **193** |
| EXTERNAL_INTEGRATION | **67** |
| REFERENCE_ONLY | **22** |

현재 문서화된 업무 구현은 고객지원 콘텐츠 조회 2개, 외환 읽기·모의계산 5개, 지식 ingestion import 1개, 데모 AI 금융생활 지원 6개와 분리된 readiness·고객 확인 유예를 포함해 총 237개다. 별도 staging 직원 발급 API 1개까지 포함하면 구현 코드는 238개 operation이다. 나머지 문서 operation 45개 중 23개는 계획, 22개는 참조 전용이며 구현 완료로 표현하지 않는다.

#### 우선순위 정의

| 우선순위 | 의미 |
|---|---|
| P0-A | 본 문서 5장에 확정된 공모전 핵심 데모·운영 안전성 15개 |
| P0-B | 공개 데모의 세션 격리를 유지하면서 뱅킹 셸을 완성하는 11개 |
| P1 | 금융회사 PoC와 제품 핵심 기능 |
| P2 | 은행·카드·증권 기능 확장 또는 장기 백로그 |

#### 구현 경계 정의

| 경계 | 의미 |
|---|---|
| OWNED | ALZ's well이 데이터·규칙·상태를 직접 소유하고 구현 |
| EXTERNAL_INTEGRATION | 은행·카드·증권·마이데이터 등 외부 원천의 읽기 전용 연동 포트. 공개 데모에서는 합성 어댑터 사용 |
| REFERENCE_ONLY | 은행 앱 기능 지도를 위한 참조 계약. 공개 데모에서 컨트롤러·외부 호출·실행 버튼을 만들지 않음 |

REFERENCE_ONLY 작업에는 OpenAPI 확장 속성 x-public-demo-enabled: false와 x-generated-controller: false를 붙인다. EXTERNAL_INTEGRATION API도 공개 데모에서는 실제 기관에 접속하지 않고 SYNTHETIC_PROVIDER만 사용한다.

---

### 3.2 안전 경계

- 모든 공개 데모 데이터는 완전 합성데이터다.
- 실제 송금·해외송금·환전·투자주문·보험가입·보험금청구를 실행하지 않는다.
- 실제 계좌개설·대출신청·대출심사·KYC를 수행하지 않는다.
- 실제 카드 정지·해제·재발급, 지급정지, 한도변경을 수행하지 않는다.
- 실제 보호수단 가입·해지 또는 신탁·후견 절차를 실행하지 않는다.
- 고객 동의 없이 가족·신뢰연락인·외부기관에 전화·문자·메일·푸시를 보내지 않는다.
- 신뢰연락인 지정만으로 송금·해지·동결·정보열람 권한을 부여하지 않는다.
- 금융생활 변화 신호를 대출·보험·마케팅·추심·투자권유에 재사용하지 않는다.
- AI는 질병·인지상태·판단능력을 추론하지 않으며, 거래 상태나 보호조치를 결정하지 않는다.
- 안내계획 승인은 상담 계획의 승인일 뿐 실제 금융조치가 아니다.
- 외부 연동 장애가 핵심 데모 흐름을 막지 않도록 템플릿·합성 어댑터 폴백을 유지한다.

---

### 3.3 도메인별 API

#### 3.3.1 시스템·데모 — 18개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P0-A | GET | /api/v1/system/health | 상태와 데모 안전 가드레일 확인 | OWNED |
| P0-A | GET | /api/v1/system/core-readiness | 핵심 업무 의존성 준비상태 | OWNED |
| P0-A | GET | /api/v1/system/ai-readiness | AI 기능 의존성 준비상태 | OWNED |
| P0-B | GET | /api/v1/system/readiness | DB·Flyway·필수 구성 준비상태 | OWNED |
| P0-B | GET | /api/v1/system/public-config | 공개 프론트 설정과 합성데이터 모드 | OWNED |
| P0-B | GET | /api/v1/system/versions | 알고리즘·정책·API 버전 | OWNED |
| P0-A | POST | /api/v1/demo/sessions | 익명 데모 세션 생성 | OWNED |
| P0-B | GET | /api/v1/demo/sessions/{sessionId} | 세션 상태·만료·적재 시나리오 조회 | OWNED |
| P1 | DELETE | /api/v1/demo/sessions/{sessionId} | 익명 데모 세션 조기 폐기 | OWNED |
| P0-A | POST | /api/v1/demo/sessions/{sessionId}/reset | 동일 seed·snapshot 복원 | OWNED |
| P0-B | GET | /api/v1/demo/scenarios | 사용 가능한 합성 시나리오 목록 | OWNED |
| P0-A | POST | /api/v1/demo/sessions/{sessionId}/scenarios/{scenarioId}/ingest | 고정 합성 시나리오 적재 | OWNED |
| P0-B | GET | /api/v1/demo/sessions/{sessionId}/customers/{customerId}/financial-summary | 세션 격리 통합자산·현금흐름 요약 | OWNED |
| P0-B | GET | /api/v1/demo/sessions/{sessionId}/customers/{customerId}/accounts | 세션 격리 합성 계좌 목록 | OWNED |
| P0-B | GET | /api/v1/demo/sessions/{sessionId}/accounts/{accountId}/transactions | 세션 격리 합성 거래내역 | OWNED |
| P0-B | GET | /api/v1/demo/sessions/{sessionId}/customers/{customerId}/baselines | 세션 격리 개인 기준선 | OWNED |
| P0-B | GET | /api/v1/demo/sessions/{sessionId}/protection-actions | 세션 데모용 공식 보호수단 | OWNED |
| P0-B | GET | /api/v1/demo/sessions/{sessionId}/customers/{customerId}/connections/consent-summary | 세션 격리 연결·동의 요약 | OWNED |

P0-B의 session 범위 읽기 API는 반드시 올바른 역할의 `X-Demo-Capability`, sessionId와 만료를 먼저 검증한다. 시나리오 파생 금융생활 읽기는 활성 `demoRunId`도 검증한다. sessionId 자체는 소유권 증명이 아니며, 같은 customerId나 accountId라도 다른 익명 세션·run에서 조회할 수 없어야 한다. 세션 생성 전 목록인 `/api/v1/demo/scenarios`는 session 범위가 아니다.

#### 3.3.2 인증·세션·권한 — 10개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P1 | POST | /api/v1/auth/login | 기업 SSO 또는 인증 공급자 로그인 | EXTERNAL_INTEGRATION |
| P1 | POST | /api/v1/auth/token/refresh | 애플리케이션 토큰 갱신 | OWNED |
| P1 | POST | /api/v1/auth/logout | 현재 세션 종료 | OWNED |
| P1 | POST | /api/v1/auth/logout-all | 현재 사용자의 모든 인증 세션 종료 | OWNED |
| P1 | GET | /api/v1/auth/me | 현재 사용자·직원 정보 | OWNED |
| P1 | GET | /api/v1/auth/me/permissions | 역할·세부 권한 조회 | OWNED |
| P2 | GET | /api/v1/auth/sessions | 로그인 세션 목록 (`IMPLEMENTED`) | OWNED |
| P2 | DELETE | /api/v1/auth/sessions/{authSessionId} | 선택한 로그인 세션 폐기 (`IMPLEMENTED`) | OWNED |
| P2 | POST | /api/v1/auth/step-up/challenges | 중요화면 추가인증 시작 | EXTERNAL_INTEGRATION |
| P2 | POST | /api/v1/auth/step-up/challenges/{challengeId}/verify | 추가인증 검증 | EXTERNAL_INTEGRATION |

KYC·실명확인 API는 포함하지 않는다. 해당 절차는 금융회사 기존 체계의 책임이다.

앞의 P1 6개는 Flyway V15의 인증 주체·역할·권한·세션 테이블과 V17의 refresh token 계열 이력·절대 만료·로그인 감사 테이블로 구현했다. Access/Refresh token은 256-bit 불투명 난수이며 원문은 응답에서 한 번만 제공하고 DB에는 SHA-256 hash만 저장한다. Refresh는 두 token을 모두 회전하며 이전 token 재사용 시 계열 전체를 폐기한다. 세션은 절대 만료와 사용자별 활성 상한을 적용하고, logout과 logout-all로 현재 또는 전체 세션을 폐기한다. 존재하지 않는 계정도 BCrypt dummy hash를 검증하며 반복 실패는 DB 기반으로 제한한다. V40부터 로그인 제한은 짧은 트랜잭션에서 `PENDING` 시도 슬롯을 원자적으로 예약한 뒤 DB 연결을 놓고 자격증명을 검증하며, `RATE_LIMITED` 감사이벤트 자체는 다음 제한 계산에 포함하지 않는다. 로컬 합성 인증은 development 전용이고 공개 production에서는 기동 가드와 기능 플래그로 노출을 거부한다. 실제 기업 SSO/IdP 연동은 `IdentityProviderPort`의 후속 어댑터 작업이다.

V57의 P2 세션 관리 2개는 현재 Bearer token의 principal과 session ID를 서버에서 복원한다. 목록은 본인의 세션을 활성 상태 우선, 그다음 `createdAt DESC, sessionId DESC`로 최대 50개 반환하고 `ACTIVE/EXPIRED/REVOKED`, 현재 세션 여부, token 만료시각만 제공한다. 최대 5개인 활성 세션은 종료 이력 수와 관계없이 목록에 모두 포함된다. token hash·family ID·IP·User-Agent는 반환하지 않는다. 선택 폐기는 URL의 session ID와 인증 principal 소유권을 함께 잠근 뒤 session과 미사용 refresh token을 원자적으로 폐기한다. 동일한 본인 세션의 재호출은 `alreadyEnded=true`로 성공하며 다른 principal 또는 존재하지 않는 ID는 모두 `404 AUTH_SESSION_NOT_FOUND`다. 결과는 `auth_session_event`에 추가 전용으로 감사한다.

#### 3.3.3 고객 프로필·접근성 — 8개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P1 | GET | /api/v1/customers/{customerId} | 비식별 고객 요약 | OWNED |
| P1 | PATCH | /api/v1/customers/{customerId}/display-profile | 별칭 등 표시 전용 정보 변경 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/preferences | 서비스 환경설정 조회 | OWNED |
| P1 | PATCH | /api/v1/customers/{customerId}/preferences | 서비스 환경설정 변경 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/accessibility-settings | 쉬운 금융 모드 설정 조회 | OWNED |
| P1 | PUT | /api/v1/customers/{customerId}/accessibility-settings | 글자·대비·읽기 흐름 설정 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/data-summary | 서비스가 보유한 데이터 범위 확인 | OWNED |
| P2 | POST | /api/v1/customers/{customerId}/data-export-requests | 고객 데이터 사본 요청 | OWNED |

P1의 앞 7개 경로는 Flyway V14 기반 PostgreSQL 영속화, 요청별 `expectedVersion` 낙관적 잠금, V15 Bearer 인증 주체와 customerId 소유권·읽기/쓰기 권한 검증 및 계약 테스트까지 구현했다. 기본값은 비활성이다. 공개 합성데모에서는 성공한 V75 `PUBLIC` fixture 300명, `demo[0-9]{3}` 계정 제한, 합성 전용 가드레일, Vercel HttpOnly BFF를 모두 적용한 경우에만 `SYNTHETIC_MEMBER_AUTH_ENABLED=true`로 함께 활성화한다. P2 데이터 사본 요청은 구현 전이다.

#### 3.3.3-A 금융생활 준비·의향 — 7개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P1 | GET | /api/v1/customers/{customerId}/continuity-preparation | 준비상태와 최신 승인 의향 조회 | OWNED |
| P1 | POST | /api/v1/customers/{customerId}/financial-intents/drafts | 고객 확인 전 구조화 초안 생성 | OWNED |
| P1 | PUT | /api/v1/customers/{customerId}/financial-intents/{intentId}/draft | 승인 전 초안 수정 | OWNED |
| P1 | POST | /api/v1/customers/{customerId}/financial-intents/{intentId}/approve | 법적 효력 제한 확인 후 고객 승인 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/financial-intents/versions | 불변 버전 이력 조회 | OWNED |
| P1 | POST | /api/v1/customers/{customerId}/financial-intents/{intentId}/revoke | 최신 승인 의향 철회 | OWNED |
| P1 | GET | /api/v1/staff/customers/{customerId}/financial-intent-summary | 동의한 항목만 행원 요약 조회 | OWNED |

Flyway V39의 현재상태, 불변 revision·event, 멱등 command 테이블로 구현한다. 초안은 고객 승인 전 효력이 없고,
승인은 법적 후견·유언·대리권을 만들지 않는다는 고정 면책 확인이 필수다. 행원 응답은 `shareScopes`에
포함된 항목만 반환하며 철회된 의향은 조회하지 않는다. 모든 응답은 `legallyBinding=false`,
`healthInferenceUsed=false`이며 의향은 건강상태·위험도·사건 우선순위 산정에 사용하지 않는다.

#### 3.3.3-B 데모 AI 금융생활 지원 — 6개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P1 | POST | /api/v1/demo/sessions/{sessionId}/customers/{customerId}/ai-financial-assistance/intent-suggestions | 고객 발화를 확인 가능한 의향 초안으로 구조화 | OWNED |
| P1 | PUT | /api/v1/demo/sessions/{sessionId}/customers/{customerId}/ai-financial-assistance/intent | 고객이 수정한 데모 의향 초안 저장 | OWNED |
| P1 | POST | /api/v1/demo/sessions/{sessionId}/customers/{customerId}/ai-financial-assistance/intent/approve | 법적 효력 제한 확인 후 데모 의향 승인 | OWNED |
| P1 | GET | /api/v1/demo/sessions/{sessionId}/customers/{customerId}/ai-financial-assistance/intent | 현재 데모 의향 조회 | OWNED |
| P1 | POST | /api/v1/demo/sessions/{sessionId}/customers/{customerId}/ai-financial-assistance/change-analysis | 30·60·90일 설명 가능한 장기 변화 분석 실행 및 감사이력 기록 | OWNED |
| P1 | POST | /api/v1/demo/sessions/{sessionId}/customers/{customerId}/ai-financial-assistance/plain-language | 고객 선호에 맞는 쉬운말·음성용 문장 생성 | OWNED |

이 6개는 합성 데모 capability와 활성 `demoRunId`를 모두 검증한다. 내부 FastAPI가 구조화 임베딩 보조, EWMA·CUSUM 분석, 제한된 문장 생성을 수행하되 Spring이 응답 enum·금지 표현·크기·버전·승인을 다시 검증한다. FastAPI 장애나 자격정보 미설정 시 Spring의 결정론적 폴백으로 전환하며 건강상태 추론, 금융거래 실행, 외부 연락은 항상 금지한다. V73의 의향 행은 세션과 run에만 귀속되고 세션 폐기 시 cascade 삭제된다.

#### 3.3.4 금융기관·데이터 연결 — 8개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P1 | GET | /api/v1/financial-institutions | 연결 가능한 금융기관 목록 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/financial-institutions/{institutionId} | 기관·지원 데이터 범위 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/customers/{customerId}/connections | 고객 데이터 연결 목록 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/customers/{customerId}/connections/{connectionId} | 연결 상태·동의 범위 | EXTERNAL_INTEGRATION |
| P2 | POST | /api/v1/customers/{customerId}/connections | 마이데이터 연결 절차 시작 | EXTERNAL_INTEGRATION |
| P2 | POST | /api/v1/connections/{connectionId}/sync | 데이터 동기화 요청 | EXTERNAL_INTEGRATION |
| P2 | GET | /api/v1/connections/{connectionId}/sync-runs | 연결별 동기화 이력 | EXTERNAL_INTEGRATION |
| P2 | DELETE | /api/v1/customers/{customerId}/connections/{connectionId} | 연결·수집 동의 해제 | EXTERNAL_INTEGRATION |

공개 데모에서는 실제 마이데이터 연결 없이 동일 계약의 SYNTHETIC_PROVIDER만 사용한다.

앞의 P1 조회 4개는 Flyway V16의 금융기관·지원 범위·고객 연결·동의 범위 테이블과 함께 구현했다. 실행 데이터는 가상 기관인 `안심은행(SYNTHETIC_BANK)`과 `안심증권(SYNTHETIC_SECURITIES)`의 기준일 고정 합성 snapshot이며 모든 범위는 읽기 전용이다. 고객 연결 경로는 Bearer 인증의 customerId 소유권과 `FINANCIAL_CONNECTION_READ` 권한을 검증한다. P2 연결 생성·동기화·해제는 구현 전이며 실제 기관 API를 호출하지 않는다.

구현된 합성 기관 계약은 다음과 같다.

| institutionId | 표시명 | 유형 | 지원 scope | providerMode |
|---|---|---|---|---|
| `SYNTHETIC_BANK` | 안심은행 | `BANK` | `ACCOUNTS`, `TRANSACTIONS` | `SYNTHETIC_PROVIDER` |
| `SYNTHETIC_SECURITIES` | 안심증권 | `SECURITIES` | `INVESTMENT_ACCOUNTS`, `POSITIONS` | `SYNTHETIC_PROVIDER` |

- 기관 목록과 상세도 인증이 필요하다.
- 고객 연결 목록·상세는 Bearer 주체의 ID와 `{customerId}`가 같고 `FINANCIAL_CONNECTION_READ` 권한이 있거나, 직원 주체에 `FINANCIAL_CONNECTION_READ_ALL` 권한이 있어야 한다.
- `{institutionId}`는 대문자로 시작하는 3~40자의 영문 대문자·숫자·밑줄만 허용한다.
- `{connectionId}`는 UUID이며 현재 고정 fixture는 안심은행 `92000000-0000-0000-0000-000000000001`, 안심증권 `92000000-0000-0000-0000-000000000002`를 사용한다.
- 목록 응답은 현재 두 기관·두 연결을 반환하며 페이지네이션은 적용하지 않는다.
- 존재하지 않는 기관은 `CONNECTION_INSTITUTION_NOT_FOUND`, 고객 소유가 아니거나 존재하지 않는 연결은 `CONNECTION_NOT_FOUND`로 응답한다. 인증 없음은 `401`, 소유권·권한 위반은 `403`, 경로 형식 오류는 `400`이다.

기관 목록의 `data.items[]`는 `institutionId`, `displayName`, `institutionType`, `providerMode`, `connectionAvailable`, `dataAsOf`를 반환한다. 기관 상세는 같은 구조의 `institution`과 `supportedScopes[]`를 추가한다. 고객 연결 목록의 `data.items[]`는 `connectionId`, `customerId`, 중첩 `institution`, `connectionStatus`, `consentedAt`, `consentExpiresAt`, `lastSyncedAt`, `providerMode`, `version`을 반환하며, 연결 상세는 여기에 `consentScopes[]`를 추가한다. 기관 상세의 scope는 `consentStatus=null`, 연결 상세의 scope는 현재 `CONSENTED`다.

#### 3.3.5 계좌 — 11개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P1 | GET | /api/v1/customers/{customerId}/accounts | 계좌 목록 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/accounts/{accountId} | 마스킹된 계좌 상세 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/accounts/{accountId}/balance | 현재·가용 잔액 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/accounts/{accountId}/balance-history | 기간별 잔액 추세 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/accounts/{accountId}/restrictions | 계좌 상태·제약 읽기 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/accounts/{accountId}/interest-summary | 이자 요약 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/accounts/{accountId}/statements | 거래명세서 목록 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/accounts/{accountId}/statements/{statementId} | 거래명세서 상세 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/accounts/{accountId}/recurring-counterparties | 반복 거래 상대 분석 | OWNED |
| P1 | PATCH | /api/v1/accounts/{accountId}/display-settings | `Idempotency-Key` 기반 계좌 별칭·노출 순서 변경 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/account-groups | 고객 지정 계좌 그룹 | OWNED |

P1 11개 전체가 구현됐다. 앞의 조회 8개는 Flyway V42의 `customer_account_snapshot`, `customer_account_balance_snapshot`, `customer_account_restriction_snapshot`, `customer_account_statement_snapshot`으로 `IMPLEMENTED-SYNTHETIC-READ-ONLY`다. V43은 반복 상대·계좌 그룹을 불변 합성 snapshot으로 추가하고, 별칭·노출 순서·숨김 여부만 `account_display_setting`에서 변경하도록 원천 계좌 데이터와 분리했다. `ACCOUNT_READ|WRITE` 권한과 고객 소유권을 Controller·서비스 양쪽에서 확인하며 `{accountId}` 단독 경로와 `{statementId}` 상세도 다른 고객의 식별자를 사용하면 404를 반환한다. 계좌번호는 마스킹 형식만 DB 제약으로 허용하고 원문 번호·거래 원문은 저장하지 않는다.

잔액 추세의 `from`, `to`는 `YYYY-MM-DD`이며 생략 시 기준일을 끝으로 최근 약 3개월을 반환하고 최대 366일로 제한한다. 잘못된 기간은 `400 ACCOUNT_BALANCE_DATE_RANGE_INVALID`, 계좌 없음·소유권 불일치는 `404 ACCOUNT_NOT_FOUND`, 명세서 없음은 `404 ACCOUNT_STATEMENT_NOT_FOUND`다. 표시 설정 PATCH는 `alias`, `displayOrder`, `hidden` 중 하나 이상과 `expectedVersion`을 받고 변경 이력을 추가 전용으로 기록한다. 변경 이벤트에는 고객 ID뿐 아니라 실제 Bearer `principalId`, `sessionId`, `actorType` snapshot을 보존하며 V70 이전 이력은 `LEGACY`로 구분한다. 버전 충돌은 `409 ACCOUNT_DISPLAY_VERSION_CONFLICT`, 표시 순서 충돌은 `409 ACCOUNT_DISPLAY_ORDER_CONFLICT`다. 계좌 목록·상세의 요약 객체는 `providerMode=SYNTHETIC_PROVIDER`, `syntheticData=true`, `externalExecutionAvailable=false`를 유지한다. 상세의 `transferAvailable=false`, `closureAvailable=false`, 명세의 `fileAvailable=false`, `externalDownloadAvailable=false`를 고정하며 실제 은행 API·이체·출금·계좌해지·명세 다운로드를 실행하지 않는다.

#### 3.3.6 통합자산·현금흐름 — 8개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P1 | GET | /api/v1/customers/{customerId}/financial-summary | 전 금융기관 자산·부채·현금흐름 요약 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/asset-breakdown | 기관·상품·자산군별 구성 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/asset-trends | 기간별 총자산·순자산 추세 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/liabilities | 대출·카드대금 등 부채 통합 요약 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/cashflow-summary | 기간별 수입·지출·순현금흐름 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/expense-summary | 범주·기관·기간별 지출 분석 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/asset-calendar | 급여·이자·납부·만기 통합 일정 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/data-freshness | 기관별 최종 동기화·완전성·지연 상태 | OWNED |

정확한 원천 잔액과 거래는 외부 연결이 제공하되, 통합·정규화·현금흐름 계산 결과는 ALZ's well이 소유한다. 데이터가 오래됐거나 일부 기관 연결이 끊긴 경우 반드시 data-freshness를 함께 표시한다.

8개 모두 Flyway V45와 기존 V41·V42·V44 합성 read model을 결합해 구현됐다. `FINANCIAL_OVERVIEW_READ`와 고객 본인 소유권을 요구하며 자산·현금흐름 기간은 최대 366일, 통합 일정은 최대 93일로 제한한다. 자산·거래 집계는 불변 계좌·거래 snapshot을 사용하고, 부채와 급여·이자·만기 일정은 `customer_liability_snapshot`, `customer_asset_calendar_snapshot`에 추가 전용으로 저장한다.

부채 참조값은 마스킹하며 `repaymentAvailable=false`, 일정은 `externalActionAvailable=false`, 전체 요약은 `syntheticData=true`, `externalExecutionAvailable=false`를 반환한다. 최신성 API는 기관별 `lastSyncedAt`, `dataAsOf`, 계좌·거래 건수, 연결 상태와 `FRESH|STALE|INCOMPLETE` 판정을 명시한다. 실제 대출·카드 결제·상환·외부 동기화는 수행하지 않는다.

#### 3.3.7 거래내역·검색 — 10개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P1 | GET | /api/v1/accounts/{accountId}/transactions | 계좌 거래내역 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/transactions/{transactionId} | 마스킹된 거래 상세 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/customers/{customerId}/transactions/search | 전 계좌 자연어·조건 검색 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/transactions/summary | 기간·범주별 거래 요약 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/counterparties | 거래 상대 목록·신규성 | OWNED |
| P1 | GET | /api/v1/counterparties/{counterpartyId}/transaction-history | 상대별 거래 추세 | OWNED |
| P1 | GET | /api/v1/transactions/{transactionId}/enrichment | 범주·정규화·분석 부가정보 | OWNED |
| P1 | PUT | /api/v1/transactions/{transactionId}/category | `Idempotency-Key` 기반 고객 지정 범주 보정 | OWNED |
| P1 | PUT | /api/v1/transactions/{transactionId}/note | `Idempotency-Key` 기반 금융 기억노트 작성 | OWNED |
| P2 | POST | /api/v1/customers/{customerId}/transaction-export-requests | 거래내역 파일 생성 요청 | OWNED |

앞의 P1 9개는 Flyway V44의 `financial_transaction_snapshot`, `transaction_enrichment_snapshot`, `customer_transaction_preference`, `customer_transaction_preference_event`로 구현됐다. 원천 거래와 enrichment는 추가 전용 불변 snapshot이며 `TRANSACTION_READ`와 고객 소유권을 서비스 계층에서도 확인한다. 목록·검색은 `occurredAt DESC, transactionId DESC` 복합 정렬과 UUID cursor를 사용하고 기간은 최대 366일, limit은 최대 100으로 제한한다. 검색어는 최대 80자이며 민감정보 정책을 통과한 마스킹 설명·상대방 이름만 검색한다. `q` 원문이 URI access log에 남지 않도록 거래 검색 경로의 Nginx access log는 비활성화한다.

범주·노트 PUT은 `TRANSACTION_WRITE`, `Idempotency-Key`, `expectedVersion`을 요구한다. 고객 보정 범주는 고정 enum만 허용하고, 기억노트는 최대 120자이며 식별정보·계좌번호·연락처·질병 표현을 거부한다. 변경은 원천 거래를 수정하지 않고 별도 preference만 갱신하며 추가 전용 이벤트에 실제 Bearer `principalId`, `sessionId`, 고객 ID와 `actorType`을 함께 남긴다. 버전 충돌은 `409 TRANSACTION_PREFERENCE_VERSION_CONFLICT`, 잘못된 cursor는 `400 TRANSACTION_CURSOR_INVALID`, 기간·금액 범위 오류는 각각 `400 TRANSACTION_DATE_RANGE_INVALID`, `400 TRANSACTION_AMOUNT_RANGE_INVALID`다. 응답은 `syntheticData=true`, `externalActionAvailable=false`, `externalActionExecuted=false`를 유지하며 실제 취소·정정·이체·export를 실행하지 않는다.

#### 3.3.8 정기납부·구독·청구 — 8개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P1 | GET | /api/v1/customers/{customerId}/recurring-payments | 정기납부·구독 목록 | OWNED |
| P1 | GET | /api/v1/recurring-payments/{recurringPaymentId} | 추정 주기·금액·상태 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/recurring-payments/calendar | 예상 납부 일정 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/recurring-payments/missed | 미발생 정기납부 후보 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/recurring-payments/duplicates | 중복 구독·납부 후보 | OWNED |
| P1 | GET | /api/v1/recurring-payments/{recurringPaymentId}/occurrences | 과거·예상 발생 내역 | OWNED |
| P1 | PUT | /api/v1/recurring-payments/{recurringPaymentId}/reminder-settings | `Idempotency-Key` 기반 납부 확인 알림 설정 | OWNED |
| P2 | POST | /api/v1/recurring-payments/{recurringPaymentId}/cancellation-guidance | 해지 방법 안내만 생성 | REFERENCE_ONLY |

앞의 P1 7개는 Flyway V41의 `recurring_payment`, 추가 전용 `recurring_payment_occurrence`, `recurring_payment_reminder_event`와 함께 `IMPLEMENTED-SYNTHETIC-READ-MODEL`이다. 고객 본인의 Bearer 주체와 `RECURRING_PAYMENT_READ|WRITE` 권한을 함께 검사하며 `{recurringPaymentId}` 단독 경로도 서비스 계층에서 소유권을 다시 확인해 교차 고객 IDOR에는 404를 반환한다. 목록·상세·달력·미발생·중복 후보·발생 이력은 기준일 `2026-08-14`의 `SYNTHETIC_PROVIDER` snapshot만 읽는다. 달력 조회는 `from`, `to`를 `YYYY-MM-DD`로 받고 생략하면 기준일이 속한 달부터 두 달 범위를 사용하며 최대 93일로 제한한다.

알림 설정 PUT 본문은 `enabled`, `leadDays(0..30)`, `expectedVersion`을 요구한다. 갱신은 `row_version` 낙관적 잠금으로 수행하고 충돌 시 `409 RECURRING_PAYMENT_VERSION_CONFLICT`, 잘못된 기간은 `400 RECURRING_PAYMENT_DATE_RANGE_INVALID`, 존재하지 않거나 다른 고객 소유인 자원은 `404 RECURRING_PAYMENT_NOT_FOUND`를 반환한다. 알림 채널은 `IN_APP`만 제공하며 `externalDeliveryEnabled=false`, `externalExecutionAvailable=false`, `cancellationAvailable=false`, `externalActionExecuted=false`를 고정한다. 실제 자동이체·결제·해지·SMS·푸시 외부 전송은 생성하지 않는다.

#### 3.3.9 이체·지급 — 10개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P1 | GET | /api/v1/customers/{customerId}/beneficiaries | 마스킹된 수취인 목록 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/customers/{customerId}/transfer-limits | 금융회사 이체한도 조회 | EXTERNAL_INTEGRATION |
| P1 | POST | /api/v1/transfer-simulations | 합성 이체 결과·수수료 모의계산 | OWNED |
| P1 | POST | /api/v1/transfer-validations | 형식·정책 사전검사, 실행 없음 | OWNED |
| P2 | GET | /api/v1/customers/{customerId}/transfer-templates | 고객 저장 이체 양식 (`IMPLEMENTED`) | OWNED |
| P2 | POST | /api/v1/customers/{customerId}/transfer-templates | 이체 양식 저장 (`IMPLEMENTED`) | OWNED |
| P2 | DELETE | /api/v1/customers/{customerId}/transfer-templates/{templateId} | 이체 양식 삭제 (`IMPLEMENTED`) | OWNED |
| P2 | POST | /api/v1/transfers | 실제 이체 접수 기능 참조 | REFERENCE_ONLY |
| P2 | POST | /api/v1/transfers/{transferId}/confirm | 실제 이체 승인 기능 참조 | REFERENCE_ONLY |
| P2 | POST | /api/v1/transfers/{transferId}/cancel | 이체 취소 기능 참조 | REFERENCE_ONLY |

마지막 3개는 공개 데모와 ALZ 핵심 백엔드에서 구현하지 않는다.

앞의 P1 4개는 Flyway V46의 마스킹된 `customer_beneficiary_snapshot`, 추가 전용 `customer_transfer_limit_snapshot`과 기존 V42 계좌 snapshot을 결합한 `IMPLEMENTED-SYNTHETIC-PREVIEW`다. 수취인·한도 조회는 `TRANSFER_PREVIEW_READ`, 모의계산·사전검증은 `TRANSFER_PREVIEW_EVALUATE`와 정확한 고객 소유권을 요구한다. 요청은 서버가 보유한 합성 `sourceAccountId`, `beneficiaryId`만 받으며 원문 계좌번호 입력은 허용하지 않는다. 금액은 1원 이상 1억원 이하 KRW로 제한하고 가용잔액·건별한도·일일 잔여한도·수취인 활성상태를 결정론적으로 확인한다.

모의계산과 사전검증은 상태를 쓰지 않는 읽기 전용 평가다. 응답은 `externalProviderCalled=false`, `transferCreated=false`, `authorizationCreated=false`를 고정하며 실제 이체 접수·OTP/MFA 승인·한도 변경·외부 금융사 호출을 생성하지 않는다. 실제 이체 관련 마지막 3개 API는 계속 `REFERENCE_ONLY`다.

V59의 저장 이체 양식 3개는 `TRANSFER_TEMPLATE_READ|WRITE`와 고객 본인 소유권을 요구한다. 생성은 `Idempotency-Key`를 필수로 받고 활성 합성 출금계좌와 같은 고객의 활성 마스킹 수취인만 참조한다. 양식명은 50자 이하이며 민감정보 정책을 통과해야 하고, 금액은 생략하거나 1원 이상 1억원 이하 KRW로 제한한다. 목적은 고정 코드만 허용하고 고객당 활성 양식은 트랜잭션 advisory lock 아래 최대 20개로 제한한다.

목록은 `templateName, createdAt, templateId` 순서로 활성 양식만 반환한다. 삭제는 물리 삭제가 아니라 `ACTIVE → DELETED` 단방향 전이이며 동일 멱등키는 최초 응답을 그대로 재생하고 이미 삭제된 본인 양식에 새 키로 재요청하면 `alreadyDeleted=true`를 반환한다. 다른 고객 또는 존재하지 않는 ID는 `404 TRANSFER_TEMPLATE_NOT_FOUND`로 숨긴다. 핵심 필드는 DB trigger로 불변이며 생성·삭제 snapshot은 `customer_transfer_template_event`에 추가 전용으로 보존하고 통합 감사 API에도 노출한다. 모든 응답은 `externalActionAvailable=false`, `externalActionExecuted=false`이며 실제 이체·승인·외부 금융사 호출을 생성하지 않는다.

#### 3.3.10 카드 — 10개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P1 | GET | /api/v1/customers/{customerId}/cards | 마스킹된 보유 카드 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/cards/{cardId} | 카드 상태·결제일·브랜드 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/cards/{cardId}/transactions | 카드 이용내역 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/cards/{cardId}/statements | 카드 청구서 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/cards/{cardId}/payment-due | 결제예정 금액 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/cards/{cardId}/limits | 이용한도 조회 | EXTERNAL_INTEGRATION |
| P2 | GET | /api/v1/cards/{cardId}/benefits | 혜택·실적 정보 | EXTERNAL_INTEGRATION |
| P2 | POST | /api/v1/cards/{cardId}/lock | 카드 사용정지 기능 참조 | REFERENCE_ONLY |
| P2 | POST | /api/v1/cards/{cardId}/unlock | 카드 정지해제 기능 참조 | REFERENCE_ONLY |
| P2 | POST | /api/v1/cards/{cardId}/replacement-requests | 재발급 기능 참조 | REFERENCE_ONLY |

앞의 P1 6개는 Flyway V47의 `customer_card_snapshot`, `card_transaction_snapshot`, `card_statement_snapshot`을 사용하는 `IMPLEMENTED-SYNTHETIC-READ-MODEL`이다. 고객 Bearer 주체와 `CARD_READ`를 요구하고 `{cardId}` 단독 경로도 서비스 계층에서 고객 소유권을 다시 확인해 교차 고객 자원은 `404 CARD_NOT_FOUND`로 숨긴다. 카드번호는 마지막 네 자리 외 전부 마스킹하고 가맹점명은 허용된 합성 이름만 저장한다. 이용내역은 최대 366일, 최대 100건, `(occurredAt, cardTransactionId)` 복합 정렬을 보존하는 UUID cursor로 조회한다.

카드 상세·청구·결제예정·한도 응답은 모두 조회만 제공한다. 카드 잠금·해제·재발급, 결제·출금, 한도 변경, 청구서 파일 생성, 외부 금융사 호출은 실행하지 않으며 `externalActionExecuted=false`와 각 실행 가능 플래그 `false`를 반환한다. 마지막 P2 3개는 계속 `REFERENCE_ONLY`다.

#### 3.3.11 예금·적금 — 8개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P2 | GET | /api/v1/deposit-products | 예금·적금 상품 목록 | EXTERNAL_INTEGRATION |
| P2 | GET | /api/v1/deposit-products/{productId} | 상품 조건·유의사항 | EXTERNAL_INTEGRATION |
| P2 | GET | /api/v1/deposit-products/{productId}/rates | 적용 금리표 | EXTERNAL_INTEGRATION |
| P2 | POST | /api/v1/deposit-products/{productId}/interest-simulations | 비개인화 이자 계산 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/deposit-holdings | 보유 예금·적금 목록 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/deposit-holdings/{holdingId} | 보유상품 잔액·만기 | EXTERNAL_INTEGRATION |
| P2 | GET | /api/v1/deposit-holdings/{holdingId}/maturity-options | 만기 처리 선택지 조회 | EXTERNAL_INTEGRATION |
| P2 | POST | /api/v1/deposit-applications | 계좌개설 기능 참조 | REFERENCE_ONLY |

앞의 상품 목록·상세·금리표·이자 모의계산과 만기 선택지 5개는 Flyway V51의 추가 전용 합성 snapshot으로 구현한다. `FINANCIAL_PRODUCT_READ|SIMULATE` 권한을 분리하고, 만기 선택지는 Bearer 고객과 보유 예금의 소유권을 다시 확인한다. 정기예금은 일시예치 단리, 적금은 월 적립금별 잔여월 단리로 계산하며 세금은 합성 추정치다. 가입·만기선택·해지·외부 금융사 호출은 실행하지 않는다.

#### 3.3.12 대출·신용 — 8개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P2 | GET | /api/v1/loan-products | 대출 상품 목록 | EXTERNAL_INTEGRATION |
| P2 | GET | /api/v1/loan-products/{productId} | 금리·조건·유의사항 | EXTERNAL_INTEGRATION |
| P2 | POST | /api/v1/loan-products/{productId}/repayment-simulations | 비개인화 상환 계산 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/loan-holdings | 보유 대출 목록 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/loan-holdings/{loanId} | 대출 잔액·조건 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/loan-holdings/{loanId}/repayment-schedule | 원리금 상환 일정 | EXTERNAL_INTEGRATION |
| P2 | POST | /api/v1/loan-applications | 대출 신청 기능 참조 | REFERENCE_ONLY |
| P2 | POST | /api/v1/loan-applications/{applicationId}/submit | 대출 심사 제출 기능 참조 | REFERENCE_ONLY |

앞의 대출상품 목록·상세·상환 모의계산 3개도 V51 합성 snapshot으로 구현한다. 모의계산은 상품 허용 금액·기간·금리 범위를 검증하고 `EQUAL_PRINCIPAL_ESTIMATE_V1` 원금균등 계산만 수행한다. 고객의 신호·경보·건강·보호업무 데이터는 입력이나 가격결정에 사용하지 않으며 신용조회·심사·신청·외부 호출은 모두 실행하지 않는다.

신호·경보 데이터는 대출 심사나 가격결정에 절대 재사용하지 않는다.

#### 3.3.13 투자·증권 — 10개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P1 | GET | /api/v1/customers/{customerId}/investment-accounts | 증권계좌 목록 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/investment-accounts/{accountId}/portfolio | 자산배분·평가액 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/investment-accounts/{accountId}/positions | 종목별 보유내역 | EXTERNAL_INTEGRATION |
| P2 | GET | /api/v1/investment-accounts/{accountId}/orders | 주문·체결 이력 | EXTERNAL_INTEGRATION |
| P2 | GET | /api/v1/market-instruments/{instrumentId}/quote | 종목 시세 | EXTERNAL_INTEGRATION |
| P2 | GET | /api/v1/market-instruments/{instrumentId}/chart | 종목 차트 데이터 | EXTERNAL_INTEGRATION |
| P2 | GET | /api/v1/customers/{customerId}/watchlist | 관심종목 | OWNED |
| P2 | PUT | /api/v1/customers/{customerId}/watchlist | 관심종목 변경 | OWNED |
| P2 | POST | /api/v1/investment-orders | 실제 매매 주문 기능 참조 | REFERENCE_ONLY |
| P2 | DELETE | /api/v1/investment-orders/{orderId} | 주문 취소 기능 참조 | REFERENCE_ONLY |

P1 예금·대출·투자 보유 조회 8개는 Flyway V50과 `FinancialHoldingController`로 구현한다. 기존 V42 계좌와 V45 부채 snapshot을 원장으로 재사용하며, 상품 상세·상환일정·투자 포지션 projection은 고객 ID와 원장 ID의 복합 외래키로 소유권을 강제한다. 권한은 본인의 `FINANCIAL_OVERVIEW_READ`로 제한하고 모든 신규 snapshot은 append-only 및 runtime DML 회수를 적용한다. 기관은 `안심은행`, `안심증권` 합성 기관만 사용하고 응답의 외부 호출·만기처리·상환·주문 실행 가능 여부는 항상 `false`다.

앞의 P2 주문이력·시세·차트·관심종목 5개는 Flyway V52와 `InvestmentMarketController`로 구현한다. 주문이력은 합성 snapshot을 최대 100건까지 반환하고, 시세는 지연된 고정 snapshot만 제공하며, 차트 조회기간은 최대 366일이다. 관심종목은 최대 20개를 전체 교체하고 `Idempotency-Key`와 `expectedVersion`으로 중복 요청과 충돌을 제어한다. V70부터 변경 이벤트는 요청을 수행한 실제 Bearer principal·session·고객·actor type을 append-only snapshot으로 남기며 `WATCHLIST` source로 통합 감사 목록·상세·내보내기에 포함한다. 신규 이벤트의 `ACTOR_SNAPSHOT_V2` 무결성 해시는 event ID·고객·버전·종목 순서·발생시각과 전체 actor snapshot을 함께 결속하며, V70 이전 이벤트는 `LEGACY_V1`로 명시한다. 실제 시세망 호출·투자 추천·주문·취소는 실행하지 않으며 주문 생성·취소 2개 API는 계속 `REFERENCE_ONLY`다.

ALZ's well은 투자 추천·적합성 판단·주문 실행을 하지 않는다.

#### 3.3.14 연금·신탁·보호수단 — 10개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P1 | GET | /api/v1/customers/{customerId}/pension-holdings | 연금 보유현황 | EXTERNAL_INTEGRATION |
| P2 | GET | /api/v1/pension-holdings/{holdingId}/projection | 금융사 제공 연금 전망 | EXTERNAL_INTEGRATION |
| P2 | GET | /api/v1/customers/{customerId}/trust-holdings | 신탁 보유현황 | EXTERNAL_INTEGRATION |
| P2 | GET | /api/v1/trust-holdings/{trustId} | 신탁 계약 상세 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/protection-actions | 공식 보호수단 카탈로그 | OWNED |
| P1 | GET | /api/v1/protection-actions/{actionCode} | 출처·시행일·적용조건 | OWNED |
| P1 | POST | /api/v1/protection-actions/{actionCode}/eligibility-evaluations | 규칙 기반 안내 가능성 평가 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/protection-enrollments | 금융사 보호수단 가입상태 읽기 | EXTERNAL_INTEGRATION |
| P2 | POST | /api/v1/protection-enrollments | 실제 보호수단 신청 참조 | REFERENCE_ONLY |
| P2 | DELETE | /api/v1/protection-enrollments/{enrollmentId} | 실제 해지 기능 참조 | REFERENCE_ONLY |

앞의 P1 4개는 Flyway V29와 함께 구현했다. 카탈로그와 상세는 공식 출처·확인일을 반환하고, 근거 citation은 `actionCode → documentId` 정책 매핑에서 현재 `APPROVED/ACTIVE` governance·역할 ACL·효력일·verified binding·`AI_DB_SNAPSHOT_V1` proof를 통과한 첫 passage를 동적으로 선택한다. 고정 V28 passage UUID를 반환하지 않으며 검증된 현재 근거가 없으면 citation 목록을 비워 fail-closed한다. 새 문서 버전 import 뒤에는 동일 document의 새 stable passage ID를 반환한다. citation resolution은 `PROTECTION_ACTION_CITATION` 접근감사에 호출 permission, action code, 기준일, 반환 passage ID, `ALLOWED/NOT_FOUND`를 남긴다. 안내 가능성 평가는 고정 정책 버전과 reason code만 사용하는 결정론적 결과다. 가입상태는 `안심은행` 합성 snapshot만 읽으며 `externalProviderCalled=false`를 명시한다. 모든 응답에서 신청 endpoint와 외부 실행은 제공하지 않고, 실제 신청·해지 API는 계속 `REFERENCE_ONLY`다.

연금 보유목록과 연금 전망, 신탁 보유목록·상세 4개는 Flyway V53과 `FinancialHoldingController`로 구현한다. 모든 원본은 `안심은행` 합성 snapshot이며 고객 소유권과 `FINANCIAL_OVERVIEW_READ`를 함께 검사한다. 연금 전망은 보장·추천이 아닌 두 개의 고정 가정 시나리오로만 제공하고, 신탁은 수익자 수만 반환하며 수익자 식별정보를 저장하거나 노출하지 않는다. 가입·변경·해지·지급 등 외부 실행 기능은 모두 `false`이며 snapshot은 append-only다.

#### 3.3.15 동의·신뢰연락인·정보제공 — 12개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P1 | GET | /api/v1/customers/{customerId}/consents | 유효한 동의 목록 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/consents/{consentId} | 범위·기간·철회조건 | OWNED |
| P1 | POST | /api/v1/customers/{customerId}/consents | 세분화된 동의 등록 | OWNED |
| P1 | POST | /api/v1/customers/{customerId}/consents/{consentId}/withdraw | 동의 철회 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/consents/{consentId}/history | 동의 변경 불변 이력 | OWNED |
| P1 | POST | /api/v1/customers/{customerId}/disclosure-evaluations | 정보제공 가능 여부 정책 평가 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/trusted-contacts | 신뢰연락인과 권한 없는 상태 표시 | OWNED |
| P1 | POST | /api/v1/customers/{customerId}/trusted-contacts | 신뢰연락인 지정 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/trusted-contacts/{contactId} | 동의 범위·유효기간 조회 | OWNED |
| P1 | PATCH | /api/v1/customers/{customerId}/trusted-contacts/{contactId} | 최소정보 범위 수정 | OWNED |
| P1 | POST | /api/v1/customers/{customerId}/trusted-contacts/{contactId}/revoke | JSON 본문으로 지정 철회 | OWNED |
| P2 | POST | /api/v1/customers/{customerId}/trusted-contacts/{contactId}/contact-attempts | 실제 외부 연락 기능 참조 | REFERENCE_ONLY |

앞의 동의·정보제공 평가 P1 6개는 Flyway V30의 목적별 동의와 scope로 구현했고, V32에서 목적별 허용 scope 매트릭스와 조회·평가 감사이력, 생성 멱등성을 보강했다. 신뢰연락인 P1 5개는 Flyway V31의 지정·최소 scope·변경이력에 V32 보안 강화를 적용했다. V33은 동의 목적의 불변성, 연락처 마스킹 정규화, 신뢰연락인 조회 감사와 기존 운영 이벤트의 인증 principal 식별을 추가로 강제한다. 동의와 신뢰연락인 생성은 원문 키가 아닌 hash를 저장하고 `INSERT ... ON CONFLICT DO NOTHING`으로 동시 동일키 요청도 한 행만 만든다. 생성과 변경 시 잠근 유효 동의를 확인하며 동의 철회 시 관련 지정을 `REVOKED_BY_CONSENT`로 함께 비활성화한다. 연락처는 서버가 허용된 형식으로 정규화한 마스킹 값만 저장하고, 별도 수신자 인증 API가 없으므로 `recipientAccepted=false`, `acceptanceStatus=PENDING_ACCEPTANCE`, `authorizedToAct=false`, `externalContactEnabled=false`, `externalContactExecuted=false`를 유지한다. 생성 API는 `Idempotency-Key`가 필수이며 철회 사유는 URL이 아닌 JSON 본문으로 전달한다. 실제 연락 시도 API는 계속 `REFERENCE_ONLY`다.

마지막 API는 공개 데모에서 호출하지 않는다. 데모에서는 정책 평가 결과 BLOCKED_BY_CONSENT만 감사로그에 남긴다.

#### 3.3.16 기준선·신호·경보·생활맥락 — 19개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P1 | GET | /api/v1/customers/{customerId}/baselines | 고객 개인 기준선 목록 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/baselines/{baselineId} | 기준기간·준비상태·버전 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/baselines/{baselineId}/features | 기준선 특징값 | OWNED |
| P1 | POST | /api/v1/customers/{customerId}/baseline-calculations | 기준선 계산 작업 생성 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/signals | 변화신호 목록 | OWNED |
| P1 | GET | /api/v1/signals/{signalId} | 평소값·현재값·사유코드 | OWNED |
| P1 | GET | /api/v1/signals/{signalId}/evidence | 불변 근거 거래 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/alerts | 운영 고객 경보 목록 | OWNED |
| P1 | GET | /api/v1/alerts/{alertId} | 운영 경보 상세 | OWNED |
| P1 | GET | /api/v1/alerts/{alertId}/context-options | 허용 고객 응답·질문 | OWNED |
| P1 | POST | /api/v1/alerts/{alertId}/context-responses | 운영 생활맥락 제출·재평가 | OWNED |
| P1 | POST | /api/v1/alerts/{alertId}/defer | 확인 연기 | OWNED |
| P2 | POST | /api/v1/alerts/{alertId}/appeals | 고객 이의·재검토 요청 | OWNED |
| P1 | GET | /api/v1/alerts/{alertId}/audit | 운영 경보 판단이력 | OWNED |
| P0-A | GET | /api/v1/demo/sessions/{sessionId}/customers/{customerId}/alerts | 기존 데모 고객 경보 목록 | OWNED |
| P0-A | GET | /api/v1/demo/sessions/{sessionId}/alerts/{alertId} | 기존 데모 경보 상세 | OWNED |
| P0-A | POST | /api/v1/demo/sessions/{sessionId}/alerts/{alertId}/context | 기존 데모 맥락 응답·재평가 | OWNED |
| P0-A | POST | /api/v1/demo/sessions/{sessionId}/alerts/{alertId}/defer | 고객 확인 유예·감사이력 추가 | OWNED |
| P0-A | GET | /api/v1/demo/sessions/{sessionId}/alerts/{alertId}/audit | 기존 데모 판단·동의 감사이력 | OWNED |

경보 이의신청은 Flyway V54부터 구현한다. 고객 본인의 경보와 `ALERT_APPEAL` 권한, `expectedVersion`, `Idempotency-Key`를 모두 검사하고 민감정보 검사를 통과한 사유만 추가 전용으로 저장한다. 허용 상태의 경보를 `BANK_REVIEW`로 전환하고 사람의 검토 사건을 한 건 생성하지만 금융 차단·이체·외부 알림은 실행하지 않는다. 동일 경보의 이의신청은 한 건으로 제한하고 통합 감사 API에 사유코드와 사건 ID를 남긴다.

#### 3.3.17 행원 사건·코파일럿·후속관리 — 25개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P1 | GET | /api/v1/staff/cases | 운영 행원 사건큐 | OWNED |
| P1 | GET | /api/v1/staff/cases/{caseId} | 운영 사건 상세 | OWNED |
| P1 | PUT | /api/v1/staff/cases/{caseId}/assignment | 담당자·팀 배정 | OWNED |
| P1 | GET | /api/v1/staff/cases/{caseId}/timeline | 운영 사건·경보·검토 통합 타임라인 | OWNED |
| P1 | GET | /api/v1/staff/cases/{caseId}/evidence | 운영 사건의 불변 합성 근거 묶음 | OWNED |
| P1 | GET | /api/v1/staff/cases/{caseId}/notes | 운영 사건 추가 전용 내부 메모 목록 | OWNED |
| P1 | POST | /api/v1/staff/cases/{caseId}/notes | 운영 사건 내부 메모 등록 | OWNED |
| P1 | GET | /api/v1/staff/cases/{caseId}/follow-ups | 운영 사건 후속 일정 목록 | OWNED |
| P1 | POST | /api/v1/staff/cases/{caseId}/follow-ups | 외부 연락 없는 운영 후속 일정 등록 | OWNED |
| P1 | PATCH | /api/v1/staff/follow-ups/{followUpId} | 운영 후속 일정·결과 상태 변경 | OWNED |
| P1 | GET | /api/v1/demo/sessions/{sessionId}/cases/{caseId}/timeline | 사건·신호·맥락·감사 타임라인 | OWNED |
| P1 | GET | /api/v1/demo/sessions/{sessionId}/cases/{caseId}/evidence | 합성 근거 거래·신호·공식 출처 묶음 | OWNED |
| P1 | GET | /api/v1/demo/sessions/{sessionId}/cases/{caseId}/notes | 행원 내부 메모 목록 | OWNED |
| P1 | POST | /api/v1/staff/cases/{caseId}/reviews | 검토 상태전이 | OWNED |
| P1 | POST | /api/v1/demo/sessions/{sessionId}/cases/{caseId}/notes | 행원 내부 메모 등록 | OWNED |
| P1 | POST | /api/v1/demo/sessions/{sessionId}/cases/{caseId}/copilot-drafts | 결정론적 질문·상담기록 초안 생성 | OWNED |
| P1 | GET | /api/v1/demo/sessions/{sessionId}/cases/{caseId}/follow-ups | 내부 재확인 일정 목록 | OWNED |
| P1 | POST | /api/v1/demo/sessions/{sessionId}/cases/{caseId}/follow-ups | 재확인 일정만 등록 | OWNED |
| P1 | PATCH | /api/v1/demo/sessions/{sessionId}/staff/follow-ups/{followUpId} | 후속 일정·결과 갱신 | OWNED |
| P1 | POST | /api/v1/staff/cases/{caseId}/guidance-plans | 안내계획 승인, 실제 조치 아님 | OWNED |
| P2 | POST | /api/v1/staff/cases/{caseId}/overrides | 정책 결과에 대한 사유 있는 직원 재검토 | OWNED |
| P0-A | GET | /api/v1/demo/sessions/{sessionId}/staff/cases | 기존 데모 행원 사건큐 | OWNED |
| P0-A | GET | /api/v1/demo/sessions/{sessionId}/cases/{caseId} | 기존 데모 사건 상세·초안 | OWNED |
| P0-A | POST | /api/v1/demo/sessions/{sessionId}/cases/{caseId}/review | 기존 데모 검토 상태전이 | OWNED |
| P0-A | POST | /api/v1/demo/sessions/{sessionId}/cases/{caseId}/guidance-plan | 기존 데모 안내계획 승인 | OWNED |

follow-ups는 일정과 업무상태만 관리한다. 전화·문자·푸시 발송 기능이 아니다.

직원 정책 재검토는 Flyway V54부터 구현한다. `PROTECTION_STAFF`의 `STAFF_CASE_OVERRIDE` 권한, 고객별 `PROTECTION_CASE_MANAGEMENT/CASE_OVERRIDE` grant, 사건 담당자 일치를 모두 요구한다. 구조화된 사유와 민감정보 검사를 통과한 근거를 불변 이벤트로 남기고 `GUIDANCE_APPROVED` 또는 `COMPLETED` 사건만 `IN_REVIEW`로 되돌린다. 기존 안내계획을 변조하거나 새 정책 결과·금융조치·고객 연락을 자동 실행하지 않는다.

#### 3.3.18 고객별 행원 접근권한 — 6개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P1 | GET | /api/v1/customers/{customerId}/staff-access-grants | 고객 데이터에 접근 가능한 행원 권한 목록 | OWNED |
| P1 | POST | /api/v1/customers/{customerId}/staff-access-grants | 목적·범위·만료를 지정한 내부 접근권 생성 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/staff-access-grants/{grantId} | 단일 접근권한 상세 | OWNED |
| P1 | POST | /api/v1/customers/{customerId}/staff-access-grants/{grantId}/revoke | 접근권한 철회 | OWNED |
| P1 | POST | /api/v1/staff-access-policy/evaluations | 행원·고객·목적·범위별 접근 가능성 평가 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/staff-access-grants/{grantId}/audit | 생성·사용·만료·철회 감사이력 | OWNED |

V48부터 직원 접근권 판정은 `staffPrincipalId + customerId + purposeCode + scope + expiresAt`을 모두 결합한다. 목적별 scope 매트릭스는 동의, 신뢰연락인, 금융의향, 사건, 개인정보 요청, 경보, 보호가입 조회를 서로 분리하며 `*_ALL` 권한도 고객별 grant를 우회하지 않는다. 만료된 grant는 `EXPIRED`로 원자 전환하고 재발급을 허용하며, 허용·거부 판정 모두 `staff_access_decision_audit_event`에 추가 전용으로 보존한다. 자유입력 철회 사유와 탐지 근거 설명은 저장 전에 공통 민감정보 정책을 통과해야 한다.

V48의 `customer_mutation_command`는 계좌 표시, 거래 범주·노트, 정기납부 알림 변경의 원문 멱등키 대신 SHA-256만 저장한다. 동일 scope·동일 키·동일 요청은 최초 업무 결과를 재사용하고, 다른 요청에 같은 키를 사용하면 각 도메인의 `*_IDEMPOTENCY_CONFLICT`를 반환한다.

V49는 고객 표시·환경·접근성 설정, 경보 연기, 사건 배정·안내승인, 동의·신뢰연락인·직원 접근권 변경까지 같은 저장소를 확장한다. 재생 대상은 상태코드·업무 코드·메시지·data로 구성된 업무 결과이며, `traceId`와 응답 `timestamp`는 현재 HTTP 재요청을 추적하기 위해 새로 발급한다. 완료된 `result_payload`는 DB trigger로 다시 쓰거나 NULL로 되돌릴 수 없다.

V49부터 경보 접근권은 `DETECTION_ADMIN`, 그 밖의 보호업무 접근권은 `PROTECTION_STAFF`에게만 발급할 수 있다. 거부 감사는 업무 트랜잭션이 연결을 반환한 뒤 별도 단일 트랜잭션으로 기록해 풀 크기 1에서도 403을 유지한다. grant의 고객·직원·목적·scope·기간·hash는 생성 후 불변이며 사건 배정 감사는 이벤트 당시 상태 snapshot만 반환한다.

모든 grant에는 grantId, customerId, staffSubjectId, purpose, scopes, grantedAt, expiresAt, revokedAt을 저장한다. purpose와 scopes가 요청 자원에 맞지 않거나 expiresAt이 지났거나 revokedAt이 존재하면 접근을 거절한다. 현재는 내부 `auth_principal`의 활성 `PROTECTION_STAFF`만 주체로 허용하고 외부 은행 IAM을 호출하지 않는다. 실제 도입 시 기업 IdP/IAM adapter가 주체를 검증하더라도 권한 목적·범위·만료·감사 상태는 ALZ's well이 보존한다.

이 절의 6개 operation은 `IMPLEMENTED-PRIVATE`다. Flyway V40의 `staff_access_grant`와 추가 전용 event 이력으로 고객·직원 principal·목적·scope·만료를 결합하며, 동의·신뢰연락인·금융의향·사건·개인정보 대행 API가 기존 `*_ALL` 권한만으로 고객 경계를 넘지 못하도록 서비스 계층에서 다시 검사한다. 접근권 생성·평가·사용·철회는 모두 감사이력에 남고 실제 은행 IAM이나 외부 시스템은 호출하지 않는다.

V40 보안 강화에서는 사건 배정 대상을 활성 `PROTECTION_STAFF` UUID로 고정하고, 검토·안내승인·메모·후속처리 주체가 배정 principal과 일치하는지 확인한다. 사건 메모·검토사유·후속 목적·결과는 식별정보·계좌·연락처·질병 표현 검사를 통과해야 한다. 금융의향 command는 고객 ID가 포함된 scope와 SHA-256 멱등키만 저장하고, 기존 승인 의향과 새 승인이 충돌하면 명시적 `409`를 반환한다. 통합 감사와 인앱 알림 cursor는 PostgreSQL 마이크로초를 보존하는 v2 형식이며 기존 밀리초 cursor는 읽기 호환만 유지한다. 감사 무결성 해시에 포함되는 시각은 UTC·마이크로초로 먼저 정규화하고 같은 값을 `timestamptz`에 저장해 DB 재조회 후 동일 해시를 재계산할 수 있게 한다.

#### 3.3.19 공식 근거·지식 카탈로그 — 9개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P1 | GET | /api/v1/knowledge/documents | 승인된 공식 문서 목록 | OWNED |
| P1 | GET | /api/v1/knowledge/documents/{documentId} | 출처·시행일·체크섬 | OWNED |
| P1 | GET | /api/v1/knowledge/documents/{documentId}/versions | 문서 버전 이력 | OWNED |
| P1 | POST | /api/v1/knowledge/search | 권한·효력기간을 적용한 검색 | OWNED |
| P1 | GET | /api/v1/knowledge/passages/{passageId} | 인용 가능한 조항·페이지 | OWNED |
| P1 | GET | /api/v1/guidance-candidates | 정책이 고른 보호수단 후보 | OWNED |
| P2 | POST | /api/v1/admin/knowledge/documents | 공식 자료 검토등록 (`IMPLEMENTED`) | OWNED |
| P2 | POST | /api/v1/admin/knowledge/documents/{documentId}/publish | 검수 완료 버전 게시 (`IMPLEMENTED`) | OWNED |
| P2 | POST | /api/v1/admin/knowledge/ingestion-imports | 검증된 AI chunk를 Spring 권위 passage로 반영 (`IMPLEMENTED`) | OWNED |

앞의 P1 6개는 Flyway V28의 문서·불변 버전·인용 passage 구조를 기반으로 구현하되, V28 seed 자체는 검증된 AI import가 없는 legacy 자료이므로 조회·검색에 노출하지 않는다. `asOf`와 audience로 승인·효력기간을 제한하며, AI 기능 플래그가 꺼졌거나 내부 서비스가 실패하면 검증된 현재 passage의 결정론적 키워드 일치만 사용한다. V65부터 내부 FastAPI가 활성화된 경우 외부 다운로드가 없는 384차원 로컬 임베딩과 PostgreSQL 전문검색 점수를 pgvector에서 결합한다. AI 적재 snapshot은 import 전 `PENDING_ACTIVATION`이므로 내부 FastAPI는 `APPROVED`이면서 `PENDING_ACTIVATION|ACTIVE`인 chunk를 후보로 반환할 수 있지만, Spring이 current `APPROVED/ACTIVE` governance·proof·ACL·효력을 최종 검증하기 전에는 외부 응답에 포함하지 않는다. keyword/vector 가중치는 각각 `0.35/0.65`이며 vector 후보 임계값 `0.15`, 최종 결합 점수 임계값 `0.35` 미만은 무응답 처리한다. 이 설정은 합성 평가 데이터셋의 Recall@3·Recall@5·MRR·무응답 오탐률과 정책 위반 수를 CI 품질 게이트로 검증한다. 안내 후보는 기존 `protection_action_catalog`와 정책 허용 reason code를 결합하되, 현재 로그인 역할로 직접 조회 가능한 검증 passage가 있는 후보만 반환하며 `externalExecutionCreated=false`를 강제한다. 각 resolution은 `GUIDANCE_CITATION` 접근감사에 호출 permission, action code, 기준일, 반환 passage ID와 결과를 남긴다. 실제 은행 내부문서, 외부 검색 API, 외부 모델과 LLM 호출은 포함하지 않는다.

V55·V64·V69의 관리자 3개 API는 공용 manifest/import 계약과 동일한 문서 ID·버전·체크섬·ACL·효력 메타데이터만 저장한다. 등록 상태는 `IN_REVIEW/PENDING_ACTIVATION`이며 `KNOWLEDGE_ADMIN_WRITE`, `Idempotency-Key`, 명시적 게시 승인과 낙관적 버전을 통과하면 `APPROVED/PENDING_ACTIVATION`이 된다. 게시 응답은 `ingestionReady=true`, `searchable=false`이고 기존 `ACTIVE` head는 계속 검색된다. import는 같은 PostgreSQL의 정확한 `SUCCEEDED` ingestion run과 전체 chunk를 statement snapshot으로 대조하며, DB trigger가 proof 값을 직접 생성하고 커밋 시 binding·passage가 모든 AI chunk와 1:1인지 다시 강제한다. 실행이나 chunk가 없거나 본문·순서·페이지·해시·버전이 다르면 fail-closed 처리한다. 검증을 통과한 트랜잭션에서만 target governance `ACTIVE`, 이전 governance `SUPERSEDED`, 새 version·passage, `currentVersion` 전환, 이전 version `supersededAt`을 함께 반영하므로 어느 단계든 실패하면 기존 `ACTIVE` head가 유지된다. V28 legacy head도 새 governance의 supersedes가 현재 document/version과 정확히 일치할 때만 이 경로로 대체한다. import 전 오승인·영구 실패 pending은 같은 catalog head를 기준으로 등록한 후속 버전을 명시적으로 publish할 때 `RETIRED` 감사 이벤트와 제한된 replacement reference를 남기고 교체한다. 별도 자유입력 사유는 받지 않으며 verified import가 생긴 후보에는 이 경로를 허용하지 않는다. AI 계정은 Spring 권위 테이블을 직접 수정하지 않는다. 모든 등록·게시·활성화·대체·import는 불변 감사이력에 보존된다.

AI 검색 장애 시뿐 아니라 내부 AI가 반환한 citation을 Spring이 전부 거부한 경우에도 결정론적 폴백을 사용하고 거부 건수를 감사에 보존한다. 폴백은 Spring catalog 조건만 신뢰하지 않는다. 현재 version과 동일한 `APPROVED/ACTIVE` governance, 역할 ACL, audience, 효력일, source hash가 일치하는 AI binding과 `AI_DB_SNAPSHOT_V1` import proof를 모두 요구한다. V71의 제목·본문 `pg_catalog.simple` stored `tsvector` GIN과 별도 keyword GIN에서 사용자 입력을 `plainto_tsquery`·배열 포함 조건의 바인딩 값으로만 조회하고, 요청 limit에 비례하되 DB 후보를 최대 200개로 제한해 DB에서 일치 개수와 순서를 결정한다. 따라서 SQL 문법을 사용자 문자열로 조립하지 않고, superseded·legacy·미검증 자료를 재노출하지 않으며 전체 corpus DB/메모리 scan도 수행하지 않는다.

V56·V71부터 지식 목록·상세·버전·passage·검색은 permission만으로 허용하지 않는다. 실제 로그인 역할과 current version governance의 `allowedRoles` 교집합, 역할에서 계산한 requester audience, 문서 audience, catalog와 governance 양쪽의 `APPROVED/ACTIVE`, 효력일, source hash가 일치하는 binding, `AI_DB_SNAPSHOT_V1` import proof를 모두 만족해야 한다. 클라이언트 audience는 권한을 넓히지 않고 허용 범위 안에서만 좁히며, `asOf`가 없으면 Spring이 `Asia/Seoul` 현재 날짜를 고정한다. 직접 ID 조회도 같은 필터를 적용해 접근 불가능하거나 legacy·미검증인 문서를 `404`로 숨긴다. 모든 직접 조회·검색과 안내/보호수단의 citation 우회조회는 추가 전용 `knowledge_access_audit_event`에 호출 permission, 역할, audience, action code 또는 필터, 반환 ID와 결과를 기록하되 검색 원문은 저장하지 않고 SHA-256만 보존한다. 검색은 `KnowledgeRetrievalPort` 뒤에서 V65 내부 FastAPI 하이브리드 어댑터와 결정론적 폴백을 선택하고, Spring이 반환 citation을 동일한 proof와 권위 DB에 다시 대조한다.

#### 3.3.20 인앱 알림·고객지원 — 10개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P1 | GET | /api/v1/customers/{customerId}/inbox | 서비스 내부 알림함 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/inbox/{messageId} | 인앱 알림 상세 | OWNED |
| P1 | POST | /api/v1/customers/{customerId}/inbox/{messageId}/read | 읽음 처리 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/notification-preferences | 채널별 알림 설정 | OWNED |
| P1 | PUT | /api/v1/customers/{customerId}/notification-preferences | 알림 설정 변경 | OWNED |
| P1 | POST | /api/v1/notification-previews | 외부 발송 없는 문구 미리보기 | OWNED |
| P2 | GET | /api/v1/support/faqs | 자주 묻는 질문 (`IMPLEMENTED`) | OWNED |
| P2 | GET | /api/v1/support/notices | 금융사 공지 조회 (`IMPLEMENTED`) | EXTERNAL_INTEGRATION |
| P2 | POST | /api/v1/support/inquiries | 실제 문의 접수 기능 참조 | REFERENCE_ONLY |
| P2 | GET | /api/v1/support/inquiries/{inquiryId} | 실제 문의 진행상태 참조 | REFERENCE_ONLY |

앞의 P1 6개는 Flyway V27의 `customer_inbox_message`, `customer_notification_preference`와 함께 구현했다. 목록은 `(createdAt, messageId)` 복합 커서를 사용하고 읽음 처리와 설정 변경은 `expectedVersion` 낙관적 잠금을 적용한다. 고객은 자신의 알림만 조회·변경할 수 있으며 미리보기는 `NOTIFICATION_PREVIEW` 권한과 승인 템플릿 코드만 허용한다. 모든 응답은 `externalDeliveryExecuted=false` 또는 `externalDeliveryEnabled=false`를 명시하며 문자·푸시·전화·외부 예약을 실행하지 않는다.

V60의 고객지원 조회 2개는 Bearer 인증과 `SUPPORT_CONTENT_READ`를 요구한다. FAQ는 `GENERAL|SECURITY|ALERTS|PRIVACY|ACCESSIBILITY` 범주와 최대 100건 제한을, 공지는 `SERVICE|SECURITY|MAINTENANCE|PRODUCT` 범주·게시일 범위·최대 100건 제한을 적용한다. 양쪽 원본은 추가 전용 snapshot이며 공지는 중요 여부, 게시시각, ID의 고정 정렬로 반환한다. FAQ는 내부 합성 콘텐츠이고 공지는 안심은행 `SYNTHETIC_PROVIDER` 자료이므로 `syntheticData=true`, `externalProviderCalled=false`, `externalActionExecuted=false`를 고정한다. 문의 접수·상태 API는 계속 `REFERENCE_ONLY`이며 외부 고객센터를 호출하지 않는다.

#### 3.3.21 감사·컴플라이언스·정보권리 — 8개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P1 | GET | /api/v1/audit/events | 권한 기반 감사이벤트 검색 | OWNED |
| P1 | GET | /api/v1/audit/events/{eventId} | 불변 감사이벤트 상세 | OWNED |
| P2 | POST | /api/v1/audit/export-requests | 감사자료 내보내기 작업 | OWNED |
| P1 | GET | /api/v1/compliance/decision-traces/{decisionId} | 전후 판단·규칙·근거 추적 | OWNED |
| P1 | GET | /api/v1/compliance/data-provenance/{resourceType}/{resourceId} | 데이터 출처·버전 확인 | OWNED |
| P2 | GET | /api/v1/compliance/retention-policies | 보존·파기 정책 조회 | OWNED |
| P2 | POST | /api/v1/customers/{customerId}/privacy/deletion-requests | 삭제 요청과 법적 예외 기록 | OWNED |
| P2 | POST | /api/v1/customers/{customerId}/privacy/correction-requests | 데이터 정정 요청 | OWNED |

앞의 P1 감사·컴플라이언스 조회 4개는 Flyway V36의 전용 최소권한과 함께 구현한다. 감사 검색은
`decision_audit`, 경보·사건·동의·신뢰연락인·정책·기능 플래그의 append-only 이력을 통합하되
원본을 수정하지 않는다. `(occurredAt,eventId)` 불투명 cursor와 source/event/customer/기간 필터를
사용한다. 판단 추적은 정책·알고리즘·상태·무결성 hash를 반환하고, 데이터 출처 조회는
`DETECTION_RUN`, `SIGNAL`, `ALERT`, `CASE`, `POLICY`의 합성 lineage만 제공한다. 응답은 항상
`externalProviderCalled=false`, `externalActionExecuted=false`다. `AUDIT_READ_ALL`과
`COMPLIANCE_TRACE_READ`는 기존 역할에 자동 부여하지 않고 별도 승인된 주체에만 할당한다.

P2 보존정책 조회와 개인정보 삭제·정정 요청 3개는 Flyway V37의
`compliance_retention_policy`, `customer_privacy_request`, 추가 전용
`customer_privacy_request_event`와 함께 구현했다. 삭제 요청은 데이터를 즉시 파기하지 않고
`LEGAL_HOLD_REVIEW` 상태와 `RETENTION_POLICY_REVIEW_REQUIRED` 예외를 기록한다. 정정 요청도
원본을 직접 덮어쓰지 않고 검토 요청만 생성한다. 두 생성 API는 고객·요청유형·`Idempotency-Key`를
범위로 원자적 멱등성을 보장하며 다른 payload 재사용은 `409 IDEMPOTENCY_CONFLICT`로 거절한다.
응답은 항상 `deletionExecuted=false`, `externalActionExecuted=false`이고 외부 기관이나 모델을 호출하지 않는다.

감사자료 내보내기 요청은 Flyway V38의 `audit_export_request`와 추가 전용
`audit_export_request_event`로 구현했다. 이 API는 승인 대기 작업만 만들며 파일, 다운로드 URL,
외부 전송을 생성하지 않는다. `AUDIT_EXPORT_REQUEST`는 기존 역할에 자동 부여하지 않는다.
요청 기간은 과거의 정상 범위여야 하고, 주체·`Idempotency-Key` 범위의 원자적 멱등 처리를 적용한다.
응답은 `artifactCreated=false`, `downloadEnabled=false`, `externalTransferExecuted=false`를 강제한다.

#### 3.3.22 관리자 규칙·정책·모델 — 10개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P1 | GET | /api/v1/admin/rules | 탐지·상태전이 규칙 목록 | OWNED |
| P1 | GET | /api/v1/admin/rules/{ruleId} | 규칙 버전·적용기간 | OWNED |
| P2 | POST | /api/v1/admin/rules | 초안 규칙 생성 | OWNED |
| P2 | PUT | /api/v1/admin/rules/{ruleId} | 초안 규칙 변경 | OWNED |
| P2 | POST | /api/v1/admin/rules/{ruleId}/publish | 승인된 규칙 게시 | OWNED |
| P2 | POST | /api/v1/admin/rules/{ruleId}/rollback | 이전 규칙으로 복귀 | OWNED |
| P1 | GET | /api/v1/admin/policies/versions | 정책엔진 버전 | OWNED |
| P1 | GET | /api/v1/admin/algorithms/versions | 탐지 알고리즘 버전 | OWNED |
| P1 | GET | /api/v1/admin/ai-quality/summary | 최근 AI 검색·폴백·인용 거부 운영 품질 | OWNED |
| P1 | GET | /api/v1/admin/feature-flags | 환경별 기능 플래그 | OWNED |
| P2 | PUT | /api/v1/admin/feature-flags/{flagKey} | 승인된 기능 플래그 변경 | OWNED |

앞의 관리자 규칙·정책 버전 P1/P2 8개는 Flyway V34의 `detection_policy_version`과 append-only
`detection_policy_event`로 구현한다. 규칙 묶음은 `DRAFT → ACTIVE → RETIRED`로 전이하며 활성 버전은
항상 하나만 허용한다. 수정은 `expectedVersion` 낙관적 잠금을 사용하고, rollback은 과거 행을 다시
활성화하지 않고 동일 규칙의 새 ACTIVE 버전을 생성한다. 탐지 실행은 당시의 `policyVersion`과
`policySnapshotHash`를 `synthetic_detection_run`에 고정해 이후 정책 변경과 무관하게 재현할 수 있다.
알고리즘 버전 조회는 `advisoryAiUsed=false`, `externalProviderCalled=false`를 명시한다.

AI 운영 품질 요약은 `DETECTION_POLICY_READ` 권한에 한해 최근 1~720시간의 추가 전용
감사 이벤트를 집계한다. 검색 요청·하이브리드 근거 연결·결정론적 폴백·빈 결과·Spring
citation 거부와 금융생활 AI 도움의 생성·폴백 건수 및 비율만 반환하며 질의 원문이나
고객 식별자를 반환하지 않는다. 표본이 없으면 `NO_DATA`, 폴백률 10% 초과 또는 citation
거부가 있으면 `ATTENTION`, 나머지는 `HEALTHY`다. 이 상태는 운영 점검 신호이며 모델의
정확도나 고객 위험도를 판정하지 않는다.
기능 플래그 2개는 Flyway V35의 승인 희망값과 append-only 변경이력으로 구현한다. API는 Spring
런타임 설정을 동적으로 바꾸지 않으며 `desiredEnabled`, `runtimeEnabled`, `appliedToRuntime`,
`restartRequired`를 분리해 반환한다. 외부 실행·외부 송신·외부 모델 가드레일은 API 변경 불가이고,
사설 기능 활성화도 공개 배포에서는 거부한다. 실제 적용은 승인된 배포 환경변수와 재기동을 거쳐야 한다.

#### 3.3.23 운영·배치·합성 탐지·연동 상태 — 16개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P2 | GET | /api/v1/internal/ops/jobs | 기준선·탐지·정리 작업 목록 | OWNED |
| P2 | GET | /api/v1/internal/ops/jobs/{jobId} | 작업 실행상태·오류 | OWNED |
| P2 | POST | /api/v1/internal/ops/jobs/{jobId}/retry | 실패 작업 안전 재시도 | OWNED |
| P1 | POST | /api/v1/admin/synthetic-datasets | 합성 특징·근거 데이터셋 초안 등록 | OWNED |
| P1 | GET | /api/v1/admin/synthetic-datasets/{datasetId} | 합성 데이터셋·검증상태 조회 | OWNED |
| P1 | POST | /api/v1/admin/synthetic-datasets/{datasetId}/validate | 합성 데이터셋 의미 검증 | OWNED |
| P1 | POST | /api/v1/admin/synthetic-datasets/{datasetId}/ingest | 검증된 합성 데이터셋 불변 적재 | OWNED |
| P1 | POST | /api/v1/customers/{customerId}/detection-runs | 합성 데이터셋 결정론적 탐지 실행 | OWNED |
| P1 | GET | /api/v1/detection-runs/{detectionRunId} | 합성 탐지 실행 결과 조회 | OWNED |
| P1 | POST | /api/v1/detection-runs/{detectionRunId}/promotion | 탐지 결과를 운영형 신호·경보로 단일 승격 | OWNED |
| P1 | GET | /api/v1/detection-runs/{detectionRunId}/promotion | 탐지 실행 승격 결과 조회 | OWNED |
| P1 | GET | /api/v1/internal/ops/audit-integrity | 감사 체인·누락 검사 | OWNED |
| P1 | GET | /api/v1/internal/integrations/providers | 외부 공급자 상태 목록 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/internal/integrations/providers/{providerId}/health | 공급자 연결상태 | EXTERNAL_INTEGRATION |
| P2 | POST | /api/v1/internal/integrations/providers/{providerId}/sync-runs | 운영 동기화 작업 요청 | EXTERNAL_INTEGRATION |
| P2 | GET | /api/v1/internal/integrations/sync-runs/{syncRunId} | 동기화 결과·재시도 여부 | EXTERNAL_INTEGRATION |

#### 3.3.24 외환·해외송금 — 8개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P1 | GET | /api/v1/fx/rates | 금융사 제공 환율표 (`IMPLEMENTED`) | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/fx/rates/{currency} | 통화별 환율 상세 (`IMPLEMENTED`) | EXTERNAL_INTEGRATION |
| P2 | GET | /api/v1/customers/{customerId}/foreign-currency-accounts | 외화계좌 현황 (`IMPLEMENTED`) | EXTERNAL_INTEGRATION |
| P2 | POST | /api/v1/fx/exchange-simulations | 외화 환전 모의계산 (`IMPLEMENTED`) | OWNED |
| P2 | GET | /api/v1/customers/{customerId}/overseas-remittance-history | 해외송금 이력 조회 (`IMPLEMENTED`) | EXTERNAL_INTEGRATION |
| P2 | POST | /api/v1/fx/exchanges | 실제 환전 기능 참조 | REFERENCE_ONLY |
| P2 | POST | /api/v1/overseas-remittances | 실제 해외송금 접수 참조 | REFERENCE_ONLY |
| P2 | POST | /api/v1/overseas-remittances/{remittanceId}/confirm | 실제 해외송금 승인 참조 | REFERENCE_ONLY |

V62의 앞 5개 API는 `USD|JPY|EUR`와 기준통화 `KRW`만 지원한다. 환율·외화계좌·합성 해외송금 이력은 `FX_READ`, 실행 없는 환전 계산은 `FX_SIMULATE`를 요구하며 고객 경로는 본인 소유권을 검사한다. 계좌번호는 마스킹 값만, 해외 수취인은 합성 별칭만 저장하고 모든 snapshot은 추가 전용이다. 실제 환전·송금·승인·외부 호출은 생성하지 않으며 마지막 실행 3개 API는 계속 `REFERENCE_ONLY`다.

#### 3.3.25 보험·방카슈랑스 — 8개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P2 | GET | /api/v1/insurance-products | 보험 상품 목록 | EXTERNAL_INTEGRATION |
| P2 | GET | /api/v1/insurance-products/{productId} | 보장·조건·유의사항 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/customers/{customerId}/insurance-contracts | 가입 보험 목록 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/insurance-contracts/{contractId} | 보험 계약 상세 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/insurance-contracts/{contractId}/coverage | 보장내용 조회 | EXTERNAL_INTEGRATION |
| P2 | GET | /api/v1/insurance-contracts/{contractId}/payments | 보험료 납부 이력 | EXTERNAL_INTEGRATION |
| P2 | POST | /api/v1/insurance-applications | 실제 보험 가입 기능 참조 | REFERENCE_ONLY |
| P2 | POST | /api/v1/insurance-claims | 실제 보험금 청구 기능 참조 | REFERENCE_ONLY |

---

### 3.4 구현 순서

1. 본 문서 5장의 P0-A 15개 계약을 구현한다.
2. P0-B 11개는 공개 데모 안전설정 5개와 sessionId가 포함된 뱅킹 셸 읽기 API 6개로 한정한다.
3. P0 총 26개를 공모전 MVP의 구현 경계로 삼는다.
4. P0-B 금융 데이터는 실제 금융사 대신 세션별 SYNTHETIC_PROVIDER에서만 제공한다.
5. P1은 고객 화면, 행원 업무, 공식 근거, 감사 추적 순으로 확장한다.
6. P2는 시간이 부족하면 전부 문서 상태로 유지한다.
7. REFERENCE_ONLY 22개는 구현 완료 수치에 포함하지 않으며, 공개 프론트 라우트와 서버 컨트롤러를 생성하지 않는다.

#### 권장 구현 웨이브

| 웨이브 | 범위 | 누적 API |
|---|---|---:|
| Wave 1 | P0-A 핵심 A/B 데모·운영 안전성 | 15 |
| Wave 2 | P0-B 세션 격리 뱅킹 셸 | 26 |
| Wave 3 | P1 행원·감사·접근성·읽기 전용 금융기능 | 176 |
| Wave 4 | P2 제품 확장 및 외부 연동 계약 | 261 |

발표에서는 “282개 API 카탈로그를 설계했고 238개 코드 operation을 구현했다”고 표현한다. 282개 전체가 구현됐다고 주장하지 않는다.

---

## 4. 공통 프로토콜 계약

### 4.1 Base URL

```text
로컬: http://localhost:8080/api/v1
운영: https://{host}/api/v1
```

### 4.2 요청 헤더

| 헤더 | 필수 | 설명 |
|---|---:|---|
| `Content-Type: application/json` | JSON 본문 사용 시 | 문자 인코딩은 UTF-8 |
| `X-Trace-Id` | 선택 | 8~64자의 영문·숫자·`.`·`_`·`-`; 없거나 형식이 틀리면 서버가 생성 |
| `Idempotency-Key` | 명세에 멱등 명령으로 표시된 상태 변경 API에서 필수 | 8~100자의 고유 키; 동일 소유범위·경로·`requestHash` 재요청만 최초 업무 결과를 재사용 |
| `X-Demo-Capability` | 모든 세션 범위 API에서 필수 | 고객 세션 생성 또는 인증된 직원 발급 경로에서 받은 256-bit 이상의 불투명 소유권 토큰; URL·본문·로그에 기록 금지 |
| `X-Demo-Run-Id` | 시나리오 적재 후 run 파생 API에서 필수 | Reset 전후 실행을 구분하는 서버 발급 ID; 오래된 탭의 상태 혼합 방지 |
| `Authorization: Bearer {token}` | PoC/운영 행원 API | 공개 합성데모에서는 생략; 운영에서는 RBAC 적용 |
| `Authorization: Bearer {bootstrap-token}` | staging 직원 capability 발급 API | 신뢰된 운영자 전용 opaque 임시 토큰; 브라우저 번들에 넣지 않으며 실제 운영 전 기업 IdP·MFA로 교체 |

모든 응답은 `X-Trace-Id` 헤더를 반환하며 본문의 `traceId`와 같아야 한다.

현재 구현의 CORS 허용 헤더에는 `Authorization`, `Content-Type`, `X-Trace-Id`, `Idempotency-Key`, `X-Demo-Capability`, `X-Demo-Run-Id`가 포함돼 있다. 공개 세션 생성 경로는 고객 origin에만 `X-Demo-Customer-Capability`를 노출하고, 직원 발급 경로는 직원 origin에만 `X-Demo-Staff-Capability`를 노출한다. 세션 하위 `/staff/**`·`/cases/**`는 직원 origin, 나머지 세션·시나리오 경로는 고객 origin만 허용하며 응답에는 trace·run 헤더만 노출한다. header 기반 무쿠키 데모는 `allowCredentials=false`로 고정한다. 개발 프로필은 고객·직원 localhost origin을 분리하고, 운영 프로필은 환경변수로 주입한 서로 겹치지 않는 HTTPS origin만 허용한다. 빈 목록·wildcard·경로·query 또는 두 목록의 중복 origin은 기동 시 거절한다. CORS가 권한을 대신하지 않으며 각 화면은 서로 다른 capability와 서버 권한을 사용한다.

현재 Spring Security의 URL 규칙은 `/api/v1/demo/**`를 합성데모 진입점으로 허용하지만, 모든 세션 하위 경로는 별도 filter가 세션별 hash와 `CUSTOMER_DEMO`·`DEMO_STAFF` 역할을 검증하고 인증 주체를 SecurityContext에 설치한다. 컨트롤러 메서드가 같은 역할을 다시 검사하며, `%` 인코딩·세미콜론·역슬래시·중복 slash·제어문자가 포함된 모호한 세션 URI는 역할 분류 전에 거절한다. 공개 데모에는 JWT가 없으며, PoC/운영 전환 시 기업 인증과 `STAFF`, `CONSUMER_PROTECTION` 권한을 추가한다.

#### 4.2.1 익명 세션 capability 소유권

`sessionId`는 조회 식별자일 뿐 인증정보가 아니다. 공개 세션 생성은 `CUSTOMER_DEMO` capability만 응답 헤더로 한 번 반환한다. `DEMO_STAFF` capability는 직원 origin에서 별도 인증된 staging 발급 API를 호출할 때 생성·회전되며 한 번만 반환된다. 서버에는 역할과 함께 단방향 hash만 저장한다. 이후 모든 `/api/v1/demo/sessions/{sessionId}/**` 요청은 `X-Demo-Capability`의 hash와 역할을 상수시간 비교로 검증한다. 누락·불일치·다른 세션 토큰이면 자원 존재 여부를 감추기 위해 모두 `404 DEMO_SESSION_NOT_FOUND`를 반환한다. capability는 query string, 응답 본문, 감사 payload, access log, `Referer`에 포함하지 않는다.

`CUSTOMER_DEMO` capability는 세션 조회·적재·Reset, 금융생활 읽기, 알림 조회와 맥락 제출에만 사용할 수 있다. `DEMO_STAFF` capability는 행원 사건큐·상세·검토·안내계획과 필요한 감사조회에만 사용할 수 있다. 역할 범위를 벗어난 유효 토큰은 `403 DEMO_CAPABILITY_SCOPE_FORBIDDEN`을 반환한다. 두 토큰을 하나로 합치거나 고객 화면에 staff 토큰을 전달하지 않는다.

현재 P0의 `POST /api/v1/demo/sessions`는 고객 token만 발급한다. 직원 token은 opaque bootstrap Bearer 토큰으로 보호된 `POST /api/v1/demo/staff/sessions/{sessionId}/capability`에서 별도로 발급한다. 공모전 공개 시연의 Vercel BFF는 브라우저가 보낸 현재 고객 capability로 같은 합성 세션을 먼저 조회·검증한 뒤에만 서버 전용 bootstrap 토큰을 사용하며, 이 토큰은 프론트 번들에 넣지 않는다. 두 역할 capability는 분리하고 고객 capability로 사건 API를 호출할 수 없다. 이 임시 발급 절차는 기업 직원 신원인증을 대체하지 않으므로 AWS 배포는 합성데이터 staging으로 한정한다. 실제 직원 화면 운영 전에는 기업 IdP·MFA·RBAC를 붙인 인증·발급 경로로 교체한다.

시나리오가 적재되면 서버는 `demoRunId`를 발급한다. alert·context·audit·case와 시나리오 파생 금융생활 조회는 `{sessionId, demoRunId}` 복합범위에 귀속된다. Reset 뒤 이전 run ID로 변경 요청을 보내면 `409 DEMO_RUN_STALE`을 반환하고 이전 run의 감사이력은 읽기 전용으로만 보존한다.

#### 4.2.2 `requestHash` 기반 멱등성

`POST /api/v1/demo/sessions`는 멱등 API가 아니다. 이 API에서 클라이언트 제공 `Idempotency-Key`를 받거나 전역 namespace로 재사용하지 않는다. Gateway 초기 제한값은 세션 생성 IP당 분당 10회, 전체 조회 IP·capability 각각 분당 120회, 상태변경 IP·capability 각각 분당 30회, 요청 본문 32KiB다. 신뢰 프록시는 환경별 CIDR allowlist로 한정하며 초과 시 `429`와 `Retry-After`를 반환한다. 애플리케이션의 동시 활성 세션 quota 초과는 `429 DEMO_SESSION_RATE_LIMITED`를 반환한다.

멱등 명령으로 표시된 API에서 서버는 `HTTP method + 정규화 path + 정규화 query + content-type + canonical JSON body`의 SHA-256을 `requestHash`로 계산한다. 데모 멱등 저장키는 `{sessionId, capabilityHash, capabilityRole, demoRunId, method, path, idempotencyKey}`다. Reset처럼 요청 시점의 run을 닫는 명령도 저장키에는 요청 헤더의 이전 `demoRunId`를 사용한다.

- 동일 저장키와 동일 `requestHash`: 최초 HTTP status·응답 code·resource ID를 그대로 재사용한다.
- 동일 저장키와 다른 `requestHash`: `409 IDEMPOTENCY_CONFLICT`를 반환하며 상태를 변경하지 않는다.
- 같은 키라도 소유범위나 run이 다르면 서로 다른 요청이다.
- 멱등 기록에는 원문 capability·자유입력·개인정보를 저장하지 않는다.
- 프로세스 로컬 `synchronized`가 아니라 DB unique constraint와 transaction으로 원자성을 보장한다.

세션 생성을 제외한 변경 응답은 `data.command.requestHash`와 `data.command.idempotencyReplayed`를 반환한다. 감사이력에는 `requestHash`와 `idempotencyKeyHash`만 남기고 원문 키와 capability는 남기지 않는다.

### 4.3 자료형

| 항목 | 규칙 | 예시 |
|---|---|---|
| ID | UUID 또는 고정 합성 ID 문자열 | `4e85...`, `ALERT_FIN_MGMT_001` |
| 시간 | ISO-8601, UTC 또는 명시적 offset | `2026-08-14T01:00:00Z` |
| 금액 | 정밀도 손실을 막기 위한 10진 문자열과 통화코드 | `"450000"`, `currency=KRW` |
| 장기 seed | JavaScript 정밀도 손실 방지를 위해 문자열 | `"842039285123456789"` |
| 비율 | 소수, 0~1 또는 별도 단위 명시 | `0.72` |
| enum | 대문자 `UPPER_SNAKE_CASE` | `PENDING_BANK_REVIEW` |
| nullable | 명세에 `nullable`로 표시된 필드만 `null` 허용 | `postDecision: null` |

클라이언트가 보낸 시각보다 서버의 수신·계산 시각을 권위값으로 사용한다.

### 4.4 페이지네이션

목록이 커질 수 있는 API는 cursor 방식을 사용한다.

```json
{
  "items": [],
  "nextCursor": null,
  "hasMore": false
}
```

- `limit` 기본값 `20`, 최댓값 `100`
- `cursor`는 서버가 발급한 불투명 문자열이며 클라이언트가 해석하지 않는다.
- `limit` 범위를 벗어나거나 cursor가 Base64URL·내부 자료형 규칙을 만족하지 않으면 `400 COMMON_INVALID_INPUT`을 반환한다.
- 거래 `direction`은 `IN|OUT`, 사건 `reviewPriority`는 `HIGH|MEDIUM|LOW`만 허용하며 알 수 없는 필터 값은 빈 목록으로 묵인하지 않는다.

---

### 4.5 공통 응답 envelope

#### 4.5.1 성공 응답

```json
{
  "success": true,
  "status": 200,
  "code": "SYSTEM_HEALTHY",
  "message": "서비스가 정상 동작 중입니다.",
  "data": {},
  "errors": [],
  "timestamp": "2026-08-14T01:00:00Z",
  "traceId": "frontend-trace-0001"
}
```

#### 4.5.2 실패 응답

```json
{
  "success": false,
  "status": 400,
  "code": "COMMON_INVALID_INPUT",
  "message": "입력값을 확인해 주세요.",
  "data": null,
  "errors": [
    {
      "field": "responseCode",
      "reason": "지원하지 않는 고객 응답 코드입니다."
    }
  ],
  "timestamp": "2026-08-14T01:00:00Z",
  "traceId": "frontend-trace-0001"
}
```

#### 4.5.3 공통 필드

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `success` | boolean | Y | 성공 여부 |
| `status` | integer | Y | HTTP status와 동일 |
| `code` | string | Y | 프론트 분기용 안정적인 응답 코드 |
| `message` | string | Y | 사용자 또는 개발자용 한글 메시지 |
| `data` | object/array/null | Y | 성공 데이터, 실패 시 `null` |
| `errors` | array | Y | 필드 검증 오류; 없으면 빈 배열 |
| `timestamp` | string | Y | 서버 응답 생성 시각 |
| `traceId` | string | Y | 요청·응답·감사로그 연계 ID |

`/actuator/**`는 Spring Actuator 고유 응답 형식을 사용하며 `/api/v1/**` 공통 envelope 적용 대상이 아니다.

---

## 5. P0-A 핵심 데모 상세 계약

아래 P0-A 15개는 모두 `IMPLEMENTED`다. `FIN_MGMT_AB_001`, 역할별 capability, `demoRunId`, T0/T1 분리, `requestHash`, 사건 상태전이와 외부실행 금지, 핵심·AI readiness 분리와 고객 확인 유예를 코드·Flyway·계약시험에 함께 반영했다.

### 5.1 시스템 API

#### 5.1.1 헬스체크

`IMPLEMENTED`

```http
GET /api/v1/system/health
```

##### 성공 응답 `200 OK`

```json
{
  "success": true,
  "status": 200,
  "code": "SYSTEM_HEALTHY",
  "message": "서비스가 정상 동작 중입니다.",
  "data": {
    "status": "UP",
    "service": "alzs-well-backend",
    "syntheticDataOnly": true,
    "externalActionsEnabled": false
  },
  "errors": [],
  "timestamp": "2026-08-14T01:00:00Z",
  "traceId": "frontend-trace-0001"
}
```

`syntheticDataOnly=false` 또는 `externalActionsEnabled=true`이면 공개 데모 프론트는 실행을 중단하고 설정 오류를 표시한다.

#### 5.1.2 핵심 업무 readiness

`IMPLEMENTED`

```http
GET /api/v1/system/core-readiness
```

DB 연결, Flyway V76, 합성 fixture, 보호업무 정책, 활성 탐지정책과 공개 데모 안전 가드레일만 검사한다. AI 검색 서비스가 중단되어도 이 응답이 `READY`이면 규칙·템플릿 폴백으로 고객 확인과 행원 검토 흐름을 계속할 수 있다. 준비되면 `SYSTEM_CORE_READY`, 하나라도 실패하면 `503 SYSTEM_NOT_READY`를 반환한다.

```json
{
  "success": true,
  "status": 200,
  "code": "SYSTEM_CORE_READY",
  "data": {
    "ready": true,
    "status": "READY",
    "checks": {
      "database": "UP",
      "flyway": "UP",
      "syntheticFixtures": "UP",
      "policyCatalog": "UP",
      "detectionPolicy": "UP",
      "safeGuardrails": "UP"
    }
  }
}
```

#### 5.1.3 AI 기능 readiness

`IMPLEMENTED`

```http
GET /api/v1/system/ai-readiness
```

Spring이 내부 FastAPI의 `/readiness`를 확인해 승인된 모델·index·DB 최소권한·검색 probe·assistance 계약이 모두 유효한지 검증한다. 준비되면 `SYSTEM_AI_READY`, 불일치·무응답이면 `503 SYSTEM_NOT_READY`와 `aiRetrieval` 세부 상태를 반환한다. 이 실패를 핵심 서비스 장애로 오인하지 않고 화면에서는 “AI 보조 기능 일시 중단, 검증된 템플릿 사용 중”으로 표시한다.

```json
{
  "success": true,
  "status": 200,
  "code": "SYSTEM_AI_READY",
  "data": {
    "ready": true,
    "status": "READY",
    "checks": { "aiRetrieval": "UP" }
  }
}
```

---

### 5.2 익명 데모 세션 API

#### 5.2.1 데모 세션 생성

`IMPLEMENTED`

```http
POST /api/v1/demo/sessions
```

요청 본문과 `Idempotency-Key`는 없다. 호출할 때마다 새 세션을 만들며 rate limit과 활성 세션 quota를 적용한다.

##### 성공 응답 `201 Created`

```http
X-Demo-Customer-Capability: {opaque-customer-capability-returned-once}
Access-Control-Expose-Headers: X-Trace-Id, X-Demo-Customer-Capability
```

```json
{
  "success": true,
  "status": 201,
  "code": "DEMO_SESSION_CREATED",
  "message": "익명 데모 세션을 생성했습니다.",
  "data": {
    "sessionId": "4e85d88f-16d3-4aa7-a0a7-d309d7d223d3",
    "scenarioSeed": "842039285123456789",
    "demoRunId": null,
    "expiresAt": "2026-08-14T03:00:00Z",
    "resetVersion": 0,
    "dataMode": "SYNTHETIC_ONLY"
  },
  "errors": [],
  "timestamp": "2026-08-14T01:00:00Z",
  "traceId": "frontend-trace-0001"
}
```

고객 capability 원문은 JSON 본문에 넣지 않으며 이 응답 이후 다시 조회할 수 없다. Vercel 공개 배포에서는 BFF가 생성 응답의 capability 헤더를 제거하고 `Secure`·`HttpOnly`·`SameSite=Strict` host cookie로 전환한다. 자바스크립트와 URL, `localStorage`, `sessionStorage`에는 원문을 저장하지 않고, BFF가 AWS 요청 시에만 `X-Demo-Capability` 헤더로 변환한다. 로컬에서 백엔드를 직접 호출하는 개발 모드는 capability를 모듈 메모리에만 보관한다. 서버는 SHA-256 hash만 저장하며 역할·세션·만료를 검증하고, gateway rate limit과 애플리케이션 활성 세션 quota를 함께 적용한다.

#### 5.2.1-A 직원 capability 발급

`IMPLEMENTED-STAGING-SECURITY`

```http
POST /api/v1/demo/staff/sessions/{sessionId}/capability
Authorization: Bearer {staging-bootstrap-token}
```

직원 origin과 인증된 staging 운영자에게만 허용한다. 발급할 때마다 기존 staff capability는 즉시 회전되어 이전 원문은 더 이상 사용할 수 없다. 자격증명을 직원 SPA 번들에 포함하면 안 되며 실제 운영 전에는 기업 IdP·MFA·RBAC로 교체한다.

##### 성공 응답 `200 OK`

```http
X-Demo-Staff-Capability: {opaque-demo-staff-capability-returned-once}
Access-Control-Expose-Headers: X-Trace-Id, X-Demo-Staff-Capability
```

응답 본문에는 `sessionId`와 `expiresAt`만 포함하고 capability 원문은 넣지 않는다. 서버는 hash만 저장하고 `DEMO_STAFF_CAPABILITY_ISSUED` 감사 이벤트를 남긴다.

#### 5.2.2 데모 세션 조기 폐기

`IMPLEMENTED-P1`

```http
DELETE /api/v1/demo/sessions/{sessionId}
X-Demo-Capability: {opaque-customer-capability}
```

소유한 익명 세션을 즉시 폐기한다. session·run·합성 snapshot·capability hash·명령 기록은 삭제하고, 불변 `decision_audit` 체인은 세션 ID 기준으로 보존한다. 실제 금융회사·가족·외부 서비스에는 아무 요청도 만들지 않는다.

##### 성공 응답 `200 OK`

```json
{
  "success": true,
  "status": 200,
  "code": "DEMO_SESSION_DISCARDED",
  "message": "익명 데모 세션과 합성 실행 데이터를 폐기했습니다.",
  "data": {
    "sessionId": "4e85d88f-16d3-4aa7-a0a7-d309d7d223d3",
    "demoRunId": "RUN_FIN_MGMT_A_001",
    "syntheticDataDeleted": true,
    "externalActionCreated": false
  }
}
```

폐기 완료 후 같은 capability와 sessionId로 하는 모든 session API는 `DEMO_SESSION_NOT_FOUND`를 반환한다.

TTL이 지난 세션은 다중 인스턴스 중복 처리를 피하는 PostgreSQL transaction advisory lock을 획득한 뒤 제한된 batch로 자동 정리한다. 정리 직전에 `DEMO_SESSION_EXPIRED_PURGED` 감사 이벤트를 추가하고 session·run·합성 snapshot·명령 기록을 삭제하되, 세션 FK에 cascade되지 않는 `decision_audit` hash chain은 보존한다.

#### 5.2.3 데모 Reset

`IMPLEMENTED`

```http
POST /api/v1/demo/sessions/{sessionId}/reset
Idempotency-Key: reset-a-to-b-0001
X-Demo-Capability: {opaque-customer-capability}
X-Demo-Run-Id: RUN_FIN_MGMT_A_001
```

같은 `scenarioSeed`, T0 원시 거래 snapshot, `alertId`, 알고리즘·정책 버전을 복원하되 새로운 `demoRunId`를 발급한다. 이전 run의 T1 맥락·상태·감사이력은 덮어쓰지 않는다. 새로운 멱등키의 Reset만 `resetVersion`을 정확히 1 증가시키고, 같은 `Idempotency-Key`와 같은 `requestHash` 재요청은 최초 `demoRunId`와 응답을 재사용해 감사이벤트와 `resetVersion`을 중복 증가시키지 않는다.

##### 성공 응답 `200 OK`

```json
{
  "success": true,
  "status": 200,
  "code": "DEMO_SESSION_RESET",
  "message": "동일한 seed와 원시 snapshot으로 초기화했습니다.",
  "data": {
    "sessionId": "4e85d88f-16d3-4aa7-a0a7-d309d7d223d3",
    "previousDemoRunId": "RUN_FIN_MGMT_A_001",
    "demoRunId": "RUN_FIN_MGMT_B_001",
    "scenarioSeed": "842039285123456789",
    "scenarioId": "FIN_MGMT_AB_001",
    "snapshotHash": "sha256:07d4c6...",
    "alertId": "ALERT_FIN_MGMT_001",
    "resetVersion": 1,
    "restoredAt": "2026-08-14T01:05:00Z",
    "command": {
      "requestHash": "sha256:reset-request-001...",
      "idempotencyReplayed": false
    }
  },
  "errors": [],
  "timestamp": "2026-08-14T01:05:00Z",
  "traceId": "frontend-trace-0002"
}
```

시나리오 적재 전에 Reset하면 `previousDemoRunId`, `demoRunId`, `scenarioId`, `snapshotHash`, `alertId`는 `null`이며 세션 메타데이터만 초기화한다. Reset 뒤 모든 run 파생 요청은 새 `X-Demo-Run-Id`를 사용한다.

#### 5.2.4 합성 시나리오 적재

`IMPLEMENTED`

```http
POST /api/v1/demo/sessions/{sessionId}/scenarios/{scenarioId}/ingest
Idempotency-Key: ingest-fin-mgmt-0001
X-Demo-Capability: {opaque-customer-capability}
```

P0에서 허용하는 `scenarioId`는 `FIN_MGMT_AB_001` 하나다. 요청 본문은 없다. 최초 적재 성공 시 서버가 `demoRunId`를 발급하고 응답 헤더 `X-Demo-Run-Id`와 본문에 같은 값을 반환한다.

##### 성공 응답 `201 Created`

```json
{
  "success": true,
  "status": 201,
  "code": "DEMO_SCENARIO_INGESTED",
  "message": "고정 합성 시나리오를 적재했습니다.",
  "data": {
    "scenarioId": "FIN_MGMT_AB_001",
    "demoRunId": "RUN_FIN_MGMT_A_001",
    "customerId": "SYN_CUSTOMER_FIN_MGMT_001",
    "alertId": "ALERT_FIN_MGMT_001",
    "caseId": null,
    "scenarioSeed": "842039285123456789",
    "snapshotHash": "sha256:07d4c6...",
    "baselinePeriod": {
      "from": "2025-08-01",
      "to": "2026-04-30"
    },
    "observationPeriod": {
      "from": "2026-05-01",
      "to": "2026-07-31"
    },
    "reasonCodes": [
      "MISSED_RECURRING",
      "DUPLICATE_TRANSFER",
      "REPEATED_CONFIRMATION"
    ],
    "t0Evidence": {
      "phase": "T0_ALERT",
      "alertSnapshotAt": "2026-08-01T00:00:00Z",
      "alertEvidenceIds": ["SIG_MISSED_RECURRING_001", "SIG_DUPLICATE_TRANSFER_001", "SIG_REPEATED_CONFIRMATION_001"],
      "missedRecurringCount60d": 3,
      "duplicateTransferCount10m": 2,
      "repeatedConfirmationCount1h": 7,
      "immutable": true
    },
    "preDecision": "NEEDS_CONTEXT",
    "state": "AWAITING_CONTEXT",
    "algorithmVersion": "baseline-rules-v2.0.0",
    "policyVersion": "context-policy-v1.0.0",
    "command": {
      "requestHash": "sha256:ingest-request-001...",
      "idempotencyReplayed": false
    }
  },
  "errors": [],
  "timestamp": "2026-08-14T01:01:00Z",
  "traceId": "frontend-trace-0003"
}
```

---

### 5.3 고객 변화 알림 API

#### 5.3.1 고객 알림 목록

`IMPLEMENTED`

```http
GET /api/v1/demo/sessions/{sessionId}/customers/{customerId}/alerts
```

##### 성공 응답 `200 OK`

```json
{
  "success": true,
  "status": 200,
  "code": "ALERT_LIST_RETRIEVED",
  "message": "금융생활 변화 알림을 조회했습니다.",
  "data": {
    "demoRunId": "RUN_FIN_MGMT_A_001",
    "customerId": "SYN_CUSTOMER_FIN_MGMT_001",
    "syntheticData": true,
    "items": [
      {
        "alertId": "ALERT_FIN_MGMT_001",
        "state": "AWAITING_CONTEXT",
        "title": "정기납부·중복송금·거래확인 변화가 있어요",
        "summary": "최근 60일 정기납부 누락 3건, 10분 내 중복송금 2회, 완료 후 1시간 내 반복확인 7회를 확인해 주세요.",
        "reasonCodes": ["MISSED_RECURRING", "DUPLICATE_TRANSFER", "REPEATED_CONFIRMATION"],
        "evidencePhase": "T0_ALERT",
        "observedAt": "2026-07-31T23:59:59Z",
        "algorithmVersion": "baseline-rules-v2.0.0"
      }
    ]
  },
  "errors": [],
  "timestamp": "2026-08-14T01:02:00Z",
  "traceId": "frontend-trace-0004"
}
```

#### 5.3.2 알림 상세

`IMPLEMENTED`

```http
GET /api/v1/demo/sessions/{sessionId}/alerts/{alertId}
```

##### 성공 응답 `200 OK`

```json
{
  "success": true,
  "status": 200,
  "code": "ALERT_DETAIL_RETRIEVED",
  "message": "변화 알림 상세를 조회했습니다.",
  "data": {
    "demoRunId": "RUN_FIN_MGMT_A_001",
    "alertId": "ALERT_FIN_MGMT_001",
    "customerId": "SYN_CUSTOMER_FIN_MGMT_001",
    "syntheticData": true,
    "state": "AWAITING_CONTEXT",
    "preDecision": "NEEDS_CONTEXT",
    "postDecision": null,
    "reasonCodes": ["MISSED_RECURRING", "DUPLICATE_TRANSFER", "REPEATED_CONFIRMATION"],
    "t0AlertEvidence": {
      "phase": "T0_ALERT",
      "snapshotHash": "sha256:07d4c6...",
      "alertSnapshotAt": "2026-08-01T00:00:00Z",
      "alertEvidenceIds": ["SIG_MISSED_RECURRING_001", "SIG_DUPLICATE_TRANSFER_001", "SIG_REPEATED_CONFIRMATION_001"],
      "immutable": true,
      "signals": [
        {
          "signalId": "SIG_MISSED_RECURRING_001",
          "reasonCode": "MISSED_RECURRING",
          "readiness": "READY",
          "baselineValue": 0,
          "currentValue": 3,
          "unit": "COUNT_60D",
          "evidenceIds": ["OBLIGATION_MGMT_001", "OBLIGATION_TELCO_001"]
        },
        {
          "signalId": "SIG_DUPLICATE_TRANSFER_001",
          "reasonCode": "DUPLICATE_TRANSFER",
          "readiness": "READY",
          "baselineValue": 0,
          "currentValue": 2,
          "unit": "COUNT_10M",
          "evidenceIds": ["TX_FIN_MGMT_DUP_001", "TX_FIN_MGMT_DUP_002"]
        },
        {
          "signalId": "SIG_REPEATED_CONFIRMATION_001",
          "reasonCode": "REPEATED_CONFIRMATION",
          "readiness": "READY",
          "baselineValue": 1,
          "currentValue": 7,
          "unit": "COUNT_1H",
          "evidenceIds": ["CONFIRM_EVENT_001", "CONFIRM_EVENT_007"]
        }
      ],
      "evidenceTransactions": [
        {
          "transactionId": "TX_FIN_MGMT_DUP_001",
          "institutionCode": "SYN_BANK_001",
          "accountType": "DEMAND_DEPOSIT",
          "transactionType": "TRANSFER_OUT",
          "occurredAt": "2026-07-10T01:20:00Z",
          "postedAt": "2026-07-10T01:20:03Z",
          "amount": "450000",
          "currency": "KRW",
          "counterpartyDisplayName": "합성수취인 A",
          "channel": "MOBILE_BANKING",
          "status": "POSTED"
        }
      ]
    },
    "t1ContextEvidence": null,
    "trustedContactGate": {
      "gateEvaluated": false,
      "consentSnapshotId": "CONSENT_TRUSTED_CONTACT_001",
      "consentStatus": "NOT_GRANTED",
      "recipientAccepted": false,
      "triggerMatched": false,
      "fieldScopeMatched": false,
      "validityMatched": false,
      "deliveryEnabled": false,
      "resultCode": null,
      "dispatchAttempted": false,
      "externalDeliveryRequested": false,
      "externalDeliveryCreated": false,
      "minimumInformationPreview": "확인이 필요한 금융활동이 있습니다. 고객에게 연락하거나 은행 상담을 도와주세요."
    },
    "algorithmVersion": "baseline-rules-v2.0.0",
    "policyVersion": "context-policy-v1.0.0"
  },
  "errors": [],
  "timestamp": "2026-08-14T01:03:00Z",
  "traceId": "frontend-trace-0005"
}
```

`t0AlertEvidence.signals`에는 점수만 보내지 않고 평소값, 현재값, 비교기간, 사실설명, 불변 근거 ID를 함께 제공한다. 맥락 제출 전 `t1ContextEvidence`는 반드시 `null`이며, T1 처리지연·연결장애·취소·환불 근거를 T0 배열에 미리 노출하지 않는다.

#### 5.3.3 생활맥락 응답

`IMPLEMENTED`

```http
POST /api/v1/demo/sessions/{sessionId}/alerts/{alertId}/context
Idempotency-Key: context-a-0001
X-Demo-Capability: {opaque-customer-capability}
X-Demo-Run-Id: RUN_FIN_MGMT_A_001
Content-Type: application/json
```

##### A 경로 요청

```json
{
  "responseCode": "KNOWN_AND_INTENTIONAL",
  "demoBranchCode": "FIN_MGMT_A_NORMAL_CONTEXT"
}
```

##### B 경로 요청

```http
POST /api/v1/demo/sessions/{sessionId}/alerts/{alertId}/context
Idempotency-Key: context-b-0001
X-Demo-Capability: {opaque-customer-capability}
X-Demo-Run-Id: RUN_FIN_MGMT_B_001
Content-Type: application/json
```

```json
{
  "responseCode": "UNABLE_TO_CONFIRM",
  "demoBranchCode": "FIN_MGMT_B_NO_CONTEXT"
}
```

이 요청 DTO가 `ContextResponse`다. `demoBranchCode`는 합성데모 전용 분기 선택자이며 클라이언트가 T1 근거나 `ContextType`을 직접 보내는 필드가 아니다. 서버는 allowlist된 분기에 대응하는 T1 근거만 조회해 정합성을 판단한다. 운영 API에서는 이 필드를 제거하고 승인된 내부 데이터 조회 결과만 사용한다. `demoBranchCode`와 `responseCode`의 허용 조합이 아니면 `400 COMMON_INVALID_INPUT`을 반환한다.

##### A 경로 성공 응답 `200 OK`

```json
{
  "success": true,
  "status": 200,
  "code": "ALERT_CONTEXT_APPLIED",
  "message": "생활맥락을 반영해 변화를 다시 확인했습니다.",
  "data": {
    "demoRunId": "RUN_FIN_MGMT_A_001",
    "contextEventId": "CTX_FIN_MGMT_A_001",
    "alertId": "ALERT_FIN_MGMT_001",
    "contextResponse": {
      "responseCode": "KNOWN_AND_INTENTIONAL",
      "demoBranchCode": "FIN_MGMT_A_NORMAL_CONTEXT"
    },
    "t1ContextEvidence": {
      "phase": "T1_CONTEXT",
      "structuralEvidenceMatched": true,
      "contextTypes": ["PAYMENT_PROVIDER_DELAY_VERIFIED", "ACCOUNT_CONNECTION_OUTAGE_VERIFIED", "DUPLICATE_TRANSFER_REFUNDED", "RESULT_SCREEN_DELAY_VERIFIED"],
      "contextEvidenceIds": ["PAYMENT_DELAY_SYN_001", "CONNECTION_OUTAGE_SYN_001", "TRANSFER_REFUND_SYN_001", "RESULT_DISPLAY_DELAY_SYN_001"],
      "contextEvidenceRefs": [
        {
          "contextEvidenceId": "PAYMENT_DELAY_SYN_001",
          "contextType": "PAYMENT_PROVIDER_DELAY_VERIFIED",
          "effectiveAt": "2026-06-01T00:00:00Z",
          "observedAt": "2026-07-31T23:59:59Z",
          "ingestedAt": "2026-08-14T01:04:00Z",
          "sourceType": "PAYMENT_PROVIDER_EVENT",
          "version": "1",
          "integrityHash": "sha256:payment-delay-001..."
        },
        {
          "contextEvidenceId": "CONNECTION_OUTAGE_SYN_001",
          "contextType": "ACCOUNT_CONNECTION_OUTAGE_VERIFIED",
          "effectiveAt": "2026-06-01T00:00:00Z",
          "observedAt": "2026-07-31T23:59:59Z",
          "ingestedAt": "2026-08-14T01:04:00Z",
          "sourceType": "SYSTEM_EVENT",
          "version": "1",
          "integrityHash": "sha256:connection-outage-001..."
        },
        {
          "contextEvidenceId": "TRANSFER_REFUND_SYN_001",
          "contextType": "DUPLICATE_TRANSFER_REFUNDED",
          "effectiveAt": "2026-07-10T01:28:00Z",
          "observedAt": "2026-07-10T01:28:03Z",
          "ingestedAt": "2026-08-14T01:04:00Z",
          "sourceType": "SYSTEM_EVENT",
          "version": "1",
          "integrityHash": "sha256:transfer-refund-001..."
        },
        {
          "contextEvidenceId": "RESULT_DISPLAY_DELAY_SYN_001",
          "contextType": "RESULT_SCREEN_DELAY_VERIFIED",
          "effectiveAt": "2026-07-10T01:20:00Z",
          "observedAt": "2026-07-10T02:20:00Z",
          "ingestedAt": "2026-08-14T01:04:00Z",
          "sourceType": "SYSTEM_EVENT",
          "version": "1",
          "integrityHash": "sha256:result-delay-001..."
        }
      ],
      "observedAt": "2026-08-14T01:04:00Z"
    },
    "preDecision": "NEEDS_CONTEXT",
    "postDecision": "CLOSE_AS_NORMAL_CONTEXT",
    "previousState": "AWAITING_CONTEXT",
    "currentState": "CLOSED_NORMAL",
    "trustedContactGate": {
      "gateEvaluated": false,
      "consentSnapshotId": "CONSENT_TRUSTED_CONTACT_001",
      "consentStatus": "NOT_GRANTED",
      "recipientAccepted": false,
      "triggerMatched": false,
      "fieldScopeMatched": false,
      "validityMatched": false,
      "deliveryEnabled": false,
      "resultCode": null,
      "dispatchAttempted": false,
      "externalDeliveryRequested": false,
      "externalDeliveryCreated": false
    },
    "nextAction": {
      "type": "SHOW_CHECKLIST",
      "actionCode": "RECHECK_RECURRING_PAYMENT"
    },
    "policyVersion": "context-policy-v1.0.0",
    "command": {
      "requestHash": "sha256:context-a-request-001...",
      "idempotencyReplayed": false
    }
  },
  "errors": [],
  "timestamp": "2026-08-14T01:04:00Z",
  "traceId": "frontend-trace-0006"
}
```

##### B 경로 성공 응답 `200 OK`

```json
{
  "success": true,
  "status": 200,
  "code": "ALERT_ESCALATED_TO_BANK_REVIEW",
  "message": "추가 설명이 필요해 은행 검토로 연결했습니다.",
  "data": {
    "demoRunId": "RUN_FIN_MGMT_B_001",
    "contextEventId": "CTX_FIN_MGMT_B_001",
    "alertId": "ALERT_FIN_MGMT_001",
    "caseId": "CASE_FIN_MGMT_001",
    "contextResponse": {
      "responseCode": "UNABLE_TO_CONFIRM",
      "demoBranchCode": "FIN_MGMT_B_NO_CONTEXT"
    },
    "t1ContextEvidence": {
      "phase": "T1_CONTEXT",
      "structuralEvidenceMatched": false,
      "contextTypes": [],
      "contextEvidenceIds": [],
      "contextEvidenceRefs": [],
      "observedAt": "2026-08-14T01:04:30Z"
    },
    "preDecision": "NEEDS_CONTEXT",
    "postDecision": "REQUIRE_BANK_REVIEW",
    "previousState": "AWAITING_CONTEXT",
    "currentState": "PENDING_BANK_REVIEW",
    "trustedContactGate": {
      "gateEvaluated": true,
      "consentSnapshotId": "CONSENT_TRUSTED_CONTACT_001",
      "consentStatus": "NOT_GRANTED",
      "recipientAccepted": false,
      "triggerMatched": true,
      "fieldScopeMatched": false,
      "validityMatched": false,
      "deliveryEnabled": false,
      "resultCode": "BLOCKED_BY_CONSENT",
      "dispatchAttempted": false,
      "externalDeliveryRequested": false,
      "externalDeliveryCreated": false
    },
    "nextAction": {
      "type": "OPEN_BANK_REVIEW",
      "actionCode": "REVIEW_CASE"
    },
    "policyVersion": "context-policy-v1.0.0",
    "command": {
      "requestHash": "sha256:context-b-request-001...",
      "idempotencyReplayed": false
    }
  },
  "errors": [],
  "timestamp": "2026-08-14T01:04:30Z",
  "traceId": "frontend-trace-0007"
}
```

`trustedContactGate.gateEvaluated=true`는 연락 정책 게이트를 평가했다는 뜻이지 외부 연락을 시도했다는 뜻이 아니다. 미동의 상태에서는 `dispatchAttempted`, `externalDeliveryRequested`, `externalDeliveryCreated`가 모두 `false`여야 한다.

고객의 `KNOWN_AND_INTENTIONAL` 응답만으로 강한 신호를 자동 해제하지 않는다. 구조적 근거가 없거나 불일치하면 `PENDING_BANK_REVIEW`로 전환한다.

#### 5.3.4 고객 확인 유예

`IMPLEMENTED`

```http
POST /api/v1/demo/sessions/{sessionId}/alerts/{alertId}/defer
Idempotency-Key: defer-0001
X-Demo-Capability: {opaque-customer-capability}
X-Demo-Run-Id: RUN_FIN_MGMT_A_001
Content-Type: application/json
```

```json
{
  "expectedVersion": 1,
  "deferredUntil": "2026-09-01T09:00:00+09:00"
}
```

`deferredUntil`은 서버 현재 시각보다 미래이고 7일 이내여야 한다. 서버는 세션·run·고객 capability·멱등키·현재 `AWAITING_CONTEXT` 상태와 낙관적 버전을 모두 확인한 뒤 별도 추가 전용 유예 이벤트와 감사이력을 기록한다. 생체인증, 이체 제한, 외부 연락은 실행하지 않는다.

```json
{
  "success": true,
  "status": 200,
  "code": "ALERT_CONFIRMATION_DEFERRED",
  "data": {
    "alertId": "ALERT_FIN_MGMT_001",
    "incidentVersion": 2,
    "previousState": "AWAITING_CONTEXT",
    "currentState": "DEFERRED",
    "deferredUntil": "2026-09-01T09:00:00+09:00",
    "nextAction": {
      "type": "RECHECK_LATER",
      "actionCode": "CONFIRM_CONTEXT"
    }
  }
}
```

동일 멱등키와 동일 요청은 최초 응답을 재생하고, 다른 요청 본문은 충돌로 거절한다. 이미 생활맥락이 제출됐거나 버전이 달라진 경우에도 덮어쓰지 않고 `409`를 반환한다.

#### 5.3.5 알림 감사이력

`IMPLEMENTED`

```http
GET /api/v1/demo/sessions/{sessionId}/alerts/{alertId}/audit?cursor={cursor}&limit=20
```

##### 성공 응답 `200 OK`

```json
{
  "success": true,
  "status": 200,
  "code": "ALERT_AUDIT_RETRIEVED",
  "message": "감사이력을 조회했습니다.",
  "data": {
    "items": [
      {
        "auditId": "92d1af1e-8f17-40d4-9d60-09c09e73fa60",
        "demoRunId": "RUN_FIN_MGMT_B_001",
        "eventType": "CONSENT_ACTION_BLOCKED",
        "actorType": "SYSTEM",
        "fromState": "AWAITING_CONTEXT",
        "toState": "PENDING_BANK_REVIEW",
        "resultCode": "BLOCKED_BY_CONSENT",
        "evidenceIds": ["CONSENT_SNAPSHOT_001"],
        "algorithmVersion": "baseline-rules-v2.0.0",
        "policyVersion": "context-policy-v1.0.0",
        "schemaVersion": "76",
        "requestHash": "sha256:context-b-request-001...",
        "idempotencyKeyHash": "sha256:context-b-key-001...",
        "traceId": "frontend-trace-0007",
        "occurredAt": "2026-08-14T01:04:30Z"
      }
    ],
    "nextCursor": null,
    "hasMore": false
  },
  "errors": [],
  "timestamp": "2026-08-14T01:05:00Z",
  "traceId": "frontend-trace-0008"
}
```

감사 API는 실제 거래 원문, prompt·completion 원문, 개인식별정보를 반환하지 않는다.

---

### 5.4 행원 사건 API

#### 5.4.1 행원 사건큐

`IMPLEMENTED`

```http
GET /api/v1/demo/sessions/{sessionId}/staff/cases?state=PENDING_BANK_REVIEW&reviewPriority=HIGH&cursor={cursor}&limit=20
```

##### 성공 응답 `200 OK`

```json
{
  "success": true,
  "status": 200,
  "code": "CASE_QUEUE_RETRIEVED",
  "message": "행원 사건큐를 조회했습니다.",
  "data": {
    "items": [
      {
        "demoRunId": "RUN_FIN_MGMT_B_001",
        "caseId": "CASE_FIN_MGMT_001",
        "alertId": "ALERT_FIN_MGMT_001",
        "customerId": "SYN_CUSTOMER_FIN_MGMT_001",
        "state": "PENDING_BANK_REVIEW",
        "reviewPriority": "HIGH",
        "reasonCodes": ["MISSED_RECURRING", "DUPLICATE_TRANSFER", "REPEATED_CONFIRMATION"],
        "customerResponseCode": "UNABLE_TO_CONFIRM",
        "summary": "정기납부 누락·중복송금·반복확인을 본인이 확인하기 어렵고 정상 구조적 근거가 없습니다.",
        "trustedContactGate": {
          "gateEvaluated": true,
          "consentSnapshotId": "CONSENT_TRUSTED_CONTACT_001",
          "consentStatus": "NOT_GRANTED",
          "recipientAccepted": false,
          "triggerMatched": true,
          "fieldScopeMatched": false,
          "validityMatched": false,
          "deliveryEnabled": false,
          "resultCode": "BLOCKED_BY_CONSENT",
          "dispatchAttempted": false,
          "externalDeliveryRequested": false,
          "externalDeliveryCreated": false
        },
        "createdAt": "2026-08-14T01:04:30Z",
        "caseVersion": 1,
        "sessionResetVersion": 1
      }
    ],
    "nextCursor": null,
    "hasMore": false
  },
  "errors": [],
  "timestamp": "2026-08-14T01:06:00Z",
  "traceId": "staff-trace-0001"
}
```

정렬 기본값은 `reviewPriority DESC, createdAt ASC`다. `reviewPriority`는 행원의 검토 순서를 위한 업무 우선순위이며 고객 위험도나 사기 확률을 뜻하지 않는다.

#### 5.4.2 사건 상세와 코파일럿 초안

`IMPLEMENTED`

```http
GET /api/v1/demo/sessions/{sessionId}/cases/{caseId}
```

##### 성공 응답 `200 OK`

```json
{
  "success": true,
  "status": 200,
  "code": "CASE_DETAIL_RETRIEVED",
  "message": "보호업무 사건 상세를 조회했습니다.",
  "data": {
    "demoRunId": "RUN_FIN_MGMT_B_001",
    "caseId": "CASE_FIN_MGMT_001",
    "caseVersion": 1,
    "sessionResetVersion": 1,
    "state": "PENDING_BANK_REVIEW",
    "reviewPriority": "HIGH",
    "alert": {
      "alertId": "ALERT_FIN_MGMT_001",
      "preDecision": "NEEDS_CONTEXT",
      "postDecision": "REQUIRE_BANK_REVIEW",
      "reasonCodes": ["MISSED_RECURRING", "DUPLICATE_TRANSFER", "REPEATED_CONFIRMATION"],
      "algorithmVersion": "baseline-rules-v2.0.0",
      "policyVersion": "context-policy-v1.0.0"
    },
    "customerContext": {
      "responseCode": "UNABLE_TO_CONFIRM",
      "contextTypes": [],
      "confirmedItems": [],
      "unconfirmedItems": ["최근 60일 정기납부 누락 3건", "10분 내 중복송금 2회", "완료 후 1시간 내 반복확인 7회"]
    },
    "timeline": [
      {
        "phase": "T0_ALERT",
        "type": "ALERT_CREATED",
        "title": "변화 알림 생성",
        "occurredAt": "2026-08-01T00:00:00Z",
        "evidenceIds": ["SIG_MISSED_RECURRING_001", "SIG_DUPLICATE_TRANSFER_001", "SIG_REPEATED_CONFIRMATION_001"]
      },
      {
        "phase": "T1_CONTEXT",
        "type": "CONTEXT_EVALUATED",
        "title": "검증된 정상 구조적 근거 없음",
        "occurredAt": "2026-08-14T01:04:30Z",
        "evidenceIds": []
      }
    ],
    "suggestedQuestions": [
      {
        "questionId": "Q_FIN_MGMT_001",
        "text": "최근 누락된 정기납부와 두 차례 송금, 완료내역을 여러 번 확인한 이유를 함께 살펴봐도 될까요?",
        "basisReasonCodes": ["MISSED_RECURRING", "DUPLICATE_TRANSFER", "REPEATED_CONFIRMATION"]
      }
    ],
    "protectionCandidates": [
      {
        "actionCode": "SAFE_BLOCK_INFO",
        "title": "금융거래 안심차단 안내",
        "eligibilitySummary": "신청 가능 여부와 적용 범위는 은행 확인이 필요합니다.",
        "source": {
          "issuer": "금융위원회",
          "url": "https://www.fsc.go.kr/no010101/85644",
          "effectiveFrom": null,
          "checkedAt": "2026-08-14"
        },
        "executionType": "GUIDANCE_ONLY"
      }
    ],
    "consultationDraft": {
      "summary": "고객이 일부 거래를 확인하기 어려워 추가 사실확인이 필요합니다.",
      "checklist": ["정기납부 처리상태 확인", "중복송금 취소·환불 확인", "거래 결과화면 지연 여부 확인"],
      "generatedBy": "TEMPLATE",
      "fallbackUsed": true
    },
    "trustedContactGate": {
      "gateEvaluated": true,
      "consentSnapshotId": "CONSENT_TRUSTED_CONTACT_001",
      "consentStatus": "NOT_GRANTED",
      "recipientAccepted": false,
      "triggerMatched": true,
      "fieldScopeMatched": false,
      "validityMatched": false,
      "deliveryEnabled": false,
      "resultCode": "BLOCKED_BY_CONSENT",
      "dispatchAttempted": false,
      "externalDeliveryRequested": false,
      "externalDeliveryCreated": false
    },
    "guidancePlan": {
      "status": "NOT_APPROVED",
      "approvedAt": null,
      "delivered": false,
      "deliveredAt": null
    },
    "capabilities": {
      "externalMessage": false,
      "transactionHold": false,
      "limitChange": false,
      "accountBlock": false
    },
    "allowedActions": [
      {
        "action": "START_REVIEW",
        "enabled": true,
        "disabledReasonCode": null
      },
      {
        "action": "APPROVE_GUIDANCE_PLAN",
        "enabled": false,
        "disabledReasonCode": "REVIEW_NOT_STARTED"
      },
      {
        "action": "CLOSE_FALSE_POSITIVE",
        "enabled": false,
        "disabledReasonCode": "REVIEW_NOT_STARTED"
      }
    ]
  },
  "errors": [],
  "timestamp": "2026-08-14T01:07:00Z",
  "traceId": "staff-trace-0002"
}
```

`protectionCandidates`는 공식 조건과 상담 경로만 제공한다. `executionType`은 P0에서 항상 `GUIDANCE_ONLY`다.
`allowedActions`는 화면이 서버 상태머신을 추측하지 않도록 `START_REVIEW`, `APPROVE_GUIDANCE_PLAN`, `CLOSE_FALSE_POSITIVE`의 현재 허용 여부를 함께 반환한다. 오탐 종결은 반드시 행원이 검토를 시작한 뒤에만 활성화한다.

#### 5.4.2.0 행원 내부 메모 조회

`IMPLEMENTED-P1`

```http
GET /api/v1/demo/sessions/{sessionId}/cases/{caseId}/notes
X-Demo-Capability: {opaque-demo-staff-capability}
X-Demo-Run-Id: {current-demo-run-id}
```

내부 메모 목록을 읽기 전용으로 조회한다. `customerVisible=false`, `externalDeliveryCreated=false`가 고정이다.

#### 5.4.2.1 후속일정 조회

`IMPLEMENTED-P1`

```http
GET /api/v1/demo/sessions/{sessionId}/cases/{caseId}/follow-ups
X-Demo-Capability: {opaque-demo-staff-capability}
X-Demo-Run-Id: {current-demo-run-id}
```

행원 내부의 후속일정 목록을 조회한다. 각 항목은 `status`, `reason`, `scheduledAt`, `completedAt`, `resultNote`, `externalDeliveryCreated=false`를 포함한다.

#### 5.4.2.2 사건 타임라인

`IMPLEMENTED-P1`

```http
GET /api/v1/demo/sessions/{sessionId}/cases/{caseId}/timeline
X-Demo-Capability: {opaque-demo-staff-capability}
X-Demo-Run-Id: {current-demo-run-id}
```

T0 알림과 T1 맥락확인 단계를 시간 근거와 함께 반환하고, 같은 사건의 hash-chain 감사 이벤트를 `auditTrail`로 제공한다. 행원 capability만 접근할 수 있고 응답은 상태를 변경하거나 외부 실행을 만들지 않는다.

#### 5.4.2.3 사건 근거 묶음

`IMPLEMENTED-P1`

```http
GET /api/v1/demo/sessions/{sessionId}/cases/{caseId}/evidence
X-Demo-Capability: {opaque-demo-staff-capability}
X-Demo-Run-Id: {current-demo-run-id}
```

현재 run에 고정된 합성 신호, 근거 거래, 생활맥락 근거 ID와 공식 보호수단 출처를 반환한다. 응답은 `syntheticData=true`, `externalFetchPerformed=false`를 명시하며 고객 capability로는 접근할 수 없다. 코파일럿과 행원은 이 묶음의 `reasonCode`, `evidenceIds`, `snapshotHash`, 공식 출처만 근거로 사용한다.

#### 5.4.2.4 결정론적 코파일럿 초안

`IMPLEMENTED-P1`

```http
POST /api/v1/demo/sessions/{sessionId}/cases/{caseId}/copilot-drafts
X-Demo-Capability: {opaque-demo-staff-capability}
X-Demo-Run-Id: {current-demo-run-id}
Content-Type: application/json

{"draftType":"CONSULTATION_NOTE"}
```

응답은 `summary`, `suggestedQuestions`, `checklist`, `basisReasonCodes`, `retrievalMode`, `citations`와 안전 메타데이터를 반환한다. `COPILOT_RAG_ENABLED=true`이고 승인·활성·ACL·효력 검증을 통과한 내부 근거가 있을 때만 `RAG_GROUNDED_TEMPLATE`을 반환한다. 근거가 없거나 내부 검색이 실패하면 `DETERMINISTIC_TEMPLATE`로 폴백한다. 두 경로 모두 `modelInvoked=false`, `externalEgressAttempted=false`, `humanReviewRequired=true`를 강제한다. 직접식별자와 미확인 항목 원문은 검색 질의로 전달하지 않으며 실제 연락·거래조치·상태전이를 만들지 않는다. 공식 원문은 검토 완료 전 임의로 `APPROVED/ACTIVE` 처리하지 않는다.

CI의 `scripts/copilot_rag_e2e.py`는 합성 승인 문서만 사용해 `PostgreSQL ingestion → Spring import → INTERNAL_RAG_HYBRID → RAG_GROUNDED_TEMPLATE + citations`를 검증하고, FastAPI 중단 후 같은 사건이 `DETERMINISTIC_TEMPLATE`로 폴백하는지 확인한다. 실행 증적에는 capability·access token·원문 검색 질의를 저장하지 않는다.

#### 5.4.2.5 행원 내부 메모 등록

`IMPLEMENTED-P1`

```http
POST /api/v1/demo/sessions/{sessionId}/cases/{caseId}/notes
X-Demo-Capability: {opaque-demo-staff-capability}
X-Demo-Run-Id: {current-demo-run-id}
Idempotency-Key: case-note-0001
Content-Type: application/json

{"caseVersion":1,"note":"고객 응답과 합성 근거를 추가 확인합니다."}
```

메모는 행원 전용이며 고객에게 노출하거나 외부로 전달하지 않는다. 서버는 민감정보·질병 추정 표현을 거부하고, `caseVersion` 낙관적 잠금과 멱등키를 적용한다. Flyway V8의 `case_note`는 append-only로 저장되어 UPDATE·DELETE가 거부된다.

#### 5.4.2.6 후속관리 일정 등록

`IMPLEMENTED-P1`

```http
POST /api/v1/demo/sessions/{sessionId}/cases/{caseId}/follow-ups
X-Demo-Capability: {opaque-demo-staff-capability}
X-Demo-Run-Id: {current-demo-run-id}
Idempotency-Key: follow-up-0001
Content-Type: application/json

{"caseVersion":2,"scheduledAt":"2026-08-20T10:00:00+09:00","reason":"처리 상태를 다시 확인합니다."}
```

현재 이후 365일 이내의 내부 재확인 일정만 등록한다. 사건은 `FOLLOW_UP_REQUIRED`로 전이하지만 전화·문자·푸시를 발송하지 않으며 `deliveryAttempted=false`, `externalDeliveryCreated=false`를 강제한다. Flyway V12는 같은 세션·run·사건에 `SCHEDULED` 일정이 하나만 존재하도록 부분 unique index를 적용하고, 상태·완료시각·결과메모 조합을 check constraint로 고정한다.

#### 5.4.2.7 후속관리 일정 상태 변경

`IMPLEMENTED-P1`

```http
PATCH /api/v1/demo/sessions/{sessionId}/staff/follow-ups/{followUpId}
X-Demo-Capability: {opaque-demo-staff-capability}
X-Demo-Run-Id: {current-demo-run-id}
Idempotency-Key: follow-up-update-0001
Content-Type: application/json
```

요청

```json
{
  "expectedCaseVersion":3,
  "status": "COMPLETED",
  "resultNote": "내부 재확인을 마쳤고 상태가 정리되었습니다."
}
```

`status`는 `COMPLETED` 또는 `CANCELLED`만 허용하고 `resultNote`는 필수다. `SCHEDULED` 상태인 일정만 한 번 종결할 수 있으며 이미 완료·취소된 일정의 재변경은 거절한다. `completedAt`은 클라이언트에서 받지 않고 서버 시각으로 저장하며, 결과는 외부 전달을 생성하지 않는다. PATCH 멱등 scope도 staff capability hash·`DEMO_STAFF` 역할·HTTP method를 포함한다.

#### 5.4.3 행원 검토 상태전이

`IMPLEMENTED`

```http
POST /api/v1/demo/sessions/{sessionId}/cases/{caseId}/review
Idempotency-Key: case-review-0001
X-Demo-Capability: {opaque-demo-staff-capability}
X-Demo-Run-Id: RUN_FIN_MGMT_B_001
Content-Type: application/json
```

##### 요청

```json
{
  "action": "START_REVIEW",
  "caseVersion": 1,
  "note": "고객 응답과 근거 거래를 확인합니다.",
  "followUpAt": null
}
```

| `action` | 허용 상태 | 결과 상태 |
|---|---|---|
| `START_REVIEW` | `PENDING_BANK_REVIEW` | `IN_BANK_REVIEW` |
| `RESUME_REVIEW` | `FOLLOW_UP_REQUIRED` | `IN_BANK_REVIEW` |
| `REQUIRE_FOLLOW_UP` | `IN_BANK_REVIEW` | `FOLLOW_UP_REQUIRED` |
| `CLOSE_FALSE_POSITIVE` | `IN_BANK_REVIEW`, `FOLLOW_UP_REQUIRED` | `CLOSED_FALSE_POSITIVE` |

`note`에는 실제 고객 식별정보·계좌번호·질병 추정 표현을 입력하지 않는다. `followUpAt`은 `REQUIRE_FOLLOW_UP`일 때 필수다. `caseVersion`은 낙관적 잠금에 사용하며, actor는 요청에서 받지 않고 인증 주체에서 정한다. 공개 데모 actor는 서버가 `DEMO_STAFF`로 고정한다.

`REQUIRE_FOLLOW_UP`은 사건 상태전이와 같은 transaction에서 `SCHEDULED` 일정을 생성한다. `RESUME_REVIEW`는 해당 일정을 `COMPLETED`로 종결한 뒤 검토를 재개하며, 후속상태에서 `CLOSE_FALSE_POSITIVE`를 선택하면 남은 일정을 `CANCELLED`로 종결한다. 상태전이와 일정 상태가 갈라지면 전체 transaction을 rollback한다.

##### 성공 응답 `200 OK`

```json
{
  "success": true,
  "status": 200,
  "code": "CASE_REVIEW_UPDATED",
  "message": "행원 검토 상태를 변경했습니다.",
  "data": {
    "demoRunId": "RUN_FIN_MGMT_B_001",
    "caseId": "CASE_FIN_MGMT_001",
    "previousState": "PENDING_BANK_REVIEW",
    "currentState": "IN_BANK_REVIEW",
    "caseVersion": 2,
    "reviewedBy": "DEMO_STAFF",
    "followUpAt": null,
    "externalExecutionCreated": false,
    "updatedAt": "2026-08-14T01:08:00Z",
    "command": {
      "requestHash": "sha256:case-review-request-001...",
      "idempotencyReplayed": false
    }
  },
  "errors": [],
  "timestamp": "2026-08-14T01:08:00Z",
  "traceId": "staff-trace-0003"
}
```

#### 5.4.4 안내 계획 승인

`IMPLEMENTED`

```http
POST /api/v1/demo/sessions/{sessionId}/cases/{caseId}/guidance-plan
Idempotency-Key: guidance-plan-0001
X-Demo-Capability: {opaque-demo-staff-capability}
X-Demo-Run-Id: RUN_FIN_MGMT_B_001
Content-Type: application/json
```

##### 요청

```json
{
  "caseVersion": 2,
  "decision": "APPROVE_GUIDANCE_PLAN",
  "selectedActionCodes": [
    "SAFE_BLOCK_INFO",
    "BANK_CONSULTATION"
  ],
  "staffNote": "공식 적용조건을 확인한 뒤 고객에게 안내할 계획입니다."
}
```

##### 성공 응답 `200 OK`

```json
{
  "success": true,
  "status": 200,
  "code": "GUIDANCE_PLAN_APPROVED",
  "message": "상담 안내 계획을 승인했습니다. 실제 계좌 조치는 실행되지 않았습니다.",
  "data": {
    "demoRunId": "RUN_FIN_MGMT_B_001",
    "caseId": "CASE_FIN_MGMT_001",
    "previousState": "IN_BANK_REVIEW",
    "currentState": "GUIDANCE_PLAN_APPROVED",
    "caseVersion": 3,
    "guidancePlanStatus": "APPROVED",
    "approvedActionCodes": ["SAFE_BLOCK_INFO", "BANK_CONSULTATION"],
    "externalExecutionCreated": false,
    "guidanceDelivered": false,
    "approvedAt": "2026-08-14T01:09:00Z",
    "deliveredAt": null,
    "command": {
      "requestHash": "sha256:guidance-plan-request-001...",
      "idempotencyReplayed": false
    }
  },
  "errors": [],
  "timestamp": "2026-08-14T01:09:00Z",
  "traceId": "staff-trace-0004"
}
```

이 API의 `APPROVE`는 상담 계획 승인만 뜻한다. 지급정지·이체차단·한도변경·외부 연락 승인이나 고객 전달 완료로 해석하지 않는다. `CLOSED_GUIDANCE_DELIVERED`는 별도 전달 확인 이벤트가 기록된 뒤에만 사용할 수 있으며, 해당 전달 확인 API는 현재 P0 26개에 포함하지 않는다.

---

## 6. P0-B 최소 핀테크 셸 상세 계약

P0-B는 은행 앱 전체를 만드는 단계가 아니다. 공개 합성데모의 첫 화면에서 참여기관 연결 상태, 통합 금융생활, 계좌·거래, 기준선, 보호수단을 읽기 전용으로 보여주기 위한 최소 계약이다. 모든 경로는 세션으로 격리되고 실제 금융회사 호출이나 외부 실행을 만들지 않는다.

### P0-B 엔드포인트 11개

| Method | Path | 응답 코드 | 핵심 DTO |
|---|---|---|---|
| GET | `/api/v1/system/readiness` | `SYSTEM_READY` | `SystemReadinessResponse` |
| GET | `/api/v1/system/public-config` | `PUBLIC_CONFIG_RETRIEVED` | `PublicConfigResponse` |
| GET | `/api/v1/system/versions` | `SYSTEM_VERSIONS_RETRIEVED` | `SystemVersionsResponse` |
| GET | `/api/v1/demo/sessions/{sessionId}` | `DEMO_SESSION_RETRIEVED` | `DemoSessionResponse` |
| GET | `/api/v1/demo/scenarios` | `DEMO_SCENARIO_LIST_RETRIEVED` | `DemoScenarioListResponse` |
| GET | `/api/v1/demo/sessions/{sessionId}/customers/{customerId}/financial-summary` | `FINANCIAL_SUMMARY_RETRIEVED` | `FinancialSummaryResponse` |
| GET | `/api/v1/demo/sessions/{sessionId}/customers/{customerId}/accounts` | `ACCOUNT_LIST_RETRIEVED` | `AccountListResponse` |
| GET | `/api/v1/demo/sessions/{sessionId}/accounts/{accountId}/transactions` | `TRANSACTION_LIST_RETRIEVED` | `TransactionListResponse` |
| GET | `/api/v1/demo/sessions/{sessionId}/customers/{customerId}/baselines` | `BASELINE_LIST_RETRIEVED` | `BaselineListResponse` |
| GET | `/api/v1/demo/sessions/{sessionId}/protection-actions` | `PROTECTION_ACTION_LIST_RETRIEVED` | `ProtectionActionListResponse` |
| GET | `/api/v1/demo/sessions/{sessionId}/customers/{customerId}/connections/consent-summary` | `DEMO_CONNECTION_LIST_RETRIEVED` | `ConnectionConsentSummaryResponse` |

P0-B 11개 operation은 모두 `IMPLEMENTED`다. 금융생활 읽기 API 6개는 세션·`demoRunId`별 FIN_MGMT 합성 fixture, Flyway V16까지의 스키마·fixture/정책 카탈로그, 거래 필터·불투명 cursor 페이지네이션, capability 역할·만료·교차세션 격리 검증을 포함한다. CORS에는 capability·run·멱등 헤더와 경로별 일회성 응답 헤더 노출 목록이 반영돼 있다.

---

### 공통 합성데이터 출처 필드

금융생활 읽기 응답은 최상위 `data.provenance`에 다음 출처 필드를 공통으로 가진다. 계좌·연결 항목은 여기에 `institutionId`, `connectionId`를 추가로 제공한다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `syntheticData` | boolean | P0에서는 항상 `true` |
| `sourceProvider` | string | P0에서는 항상 `SYNTHETIC_PROVIDER` |
| `sourceUpdatedAt` | ISO-8601 | 합성 snapshot 기준시각 |
| `dataFreshness` | enum | P0에서는 `FIXED_SNAPSHOT` |
| `consentId` | string | 분석 목적의 합성 동의 snapshot ID |
| `consentScope` | string[] | 세션 연결 전체의 허용된 읽기 범위 |
| `snapshotHash` | string | Reset 전후 동일성을 확인하는 합성 snapshot SHA-256 |

P0에서 허용하는 `dataFreshness` 값은 `FIXED_SNAPSHOT`, `FRESH`, `STALE`, `UNAVAILABLE`이며 실제 시연 fixture는 `FIXED_SNAPSHOT`을 사용한다.

금액은 항상 다음 구조 또는 동일 의미의 `amount` 문자열과 `currency` 조합으로 반환한다.

```json
{
  "amount": "450000",
  "currency": "KRW"
}
```

---

### 시스템 준비상태·공개 설정·버전

#### 준비상태

```http
GET /api/v1/system/readiness
```

```json
{
  "success": true,
  "status": 200,
  "code": "SYSTEM_READY",
  "message": "공개 데모 실행 준비가 완료되었습니다.",
  "data": {
    "ready": true,
    "status": "READY",
    "checks": {
      "database": "UP",
      "flyway": "UP",
      "syntheticFixtures": "UP",
      "policyCatalog": "UP",
      "detectionPolicy": "UP",
      "safeGuardrails": "UP",
      "aiRetrieval": "DISABLED"
    }
  },
  "errors": [],
  "timestamp": "2026-08-14T01:00:00Z",
  "traceId": "frontend-trace-ready-0001"
}
```

데이터베이스 또는 필수 fixture가 준비되지 않으면 `503 Service Unavailable`과 `SYSTEM_NOT_READY`를 반환한다. Flyway 준비상태는 최신 성공 migration이 서비스의 필수 스키마 버전 V76과 정확히 일치하고 실패 migration이 없을 때만 `UP`이다. 활성 탐지정책이 정확히 하나가 아니어도 readiness는 `DOWN`이다. `/system/core-readiness`는 AI와 무관한 핵심 업무 의존성만, `/system/ai-readiness`는 AI 기능만 검사한다. 기존 `/system/readiness`는 두 결과를 합치되 `AI_REQUIRE_FOR_CORE_READINESS=false`이면 AI 중단을 핵심 데모 중단으로 승격하지 않는다. AWS AI 통합 staging은 strict 모드를 사용하며 Spring이 FastAPI `/readiness`의 `status=READY`, 모델 승인상태·revision·artifact/골든셋 SHA-256·index version·배포환경을 검증한다. 불일치하면 `MISMATCH`, 무응답이면 `DOWN`으로 AI readiness를 실패시킨다. FastAPI `/health`는 프로세스 liveness 전용이므로 모델·DB 준비상태 판정에 사용하지 않는다.

#### 공개 설정

```http
GET /api/v1/system/public-config
```

```json
{
  "success": true,
  "status": 200,
  "code": "PUBLIC_CONFIG_RETRIEVED",
  "message": "공개 데모 설정을 조회했습니다.",
  "data": {
    "apiVersion": "v1",
    "dataMode": "SYNTHETIC_ONLY",
    "syntheticDataOnly": true,
    "externalActionsEnabled": false,
    "networkMode": "AIR_GAPPED_DEMO",
    "externalEgressEnabled": false,
    "remoteModelEnabled": false,
    "syntheticProviderOnly": true,
    "supportedScenarioIds": ["FIN_MGMT_AB_001"],
    "defaultLocale": "ko-KR",
    "demoSessionTtlSeconds": 7200,
    "featureFlags": {
      "optionalLlmEnabled": false,
      "templateFallbackEnabled": true,
      "trustedContactDeliveryEnabled": false
    }
  },
  "errors": [],
  "timestamp": "2026-08-14T01:00:00Z",
  "traceId": "frontend-trace-config-0001"
}
```

#### 버전

```http
GET /api/v1/system/versions
```

```json
{
  "success": true,
  "status": 200,
  "code": "SYSTEM_VERSIONS_RETRIEVED",
  "message": "서비스 버전을 조회했습니다.",
  "data": {
    "applicationVersion": "0.0.1-SNAPSHOT",
    "apiVersion": "v1",
    "schemaVersion": "76",
    "fixtureVersion": "fin-mgmt-ab-v2.0.0",
    "algorithmVersion": "baseline-rules-v2.0.0",
    "policyVersion": "context-policy-v1.0.0",
    "sourceCatalogCheckedAt": "2026-08-14"
  },
  "errors": [],
  "timestamp": "2026-08-14T01:00:00Z",
  "traceId": "frontend-trace-version-0001"
}
```

---

### 데모 세션·시나리오 조회

#### 세션 상태

```http
GET /api/v1/demo/sessions/{sessionId}
```

```json
{
  "success": true,
  "status": 200,
  "code": "DEMO_SESSION_RETRIEVED",
  "message": "데모 세션 상태를 조회했습니다.",
  "data": {
    "sessionId": "4e85d88f-16d3-4aa7-a0a7-d309d7d223d3",
    "scenarioSeed": "842039285123456789",
    "scenarioId": "FIN_MGMT_AB_001",
    "demoRunId": "RUN_FIN_MGMT_B_001",
    "status": "ACTIVE",
    "resetVersion": 1,
    "snapshotHash": "sha256:07d4c6...",
    "createdAt": "2026-08-14T01:00:00Z",
    "expiresAt": "2026-08-14T03:00:00Z",
    "dataMode": "SYNTHETIC_ONLY"
  },
  "errors": [],
  "timestamp": "2026-08-14T01:01:00Z",
  "traceId": "frontend-trace-session-0001"
}
```

#### 시나리오 목록

```http
GET /api/v1/demo/scenarios
```

```json
{
  "success": true,
  "status": 200,
  "code": "DEMO_SCENARIO_LIST_RETRIEVED",
  "message": "사용 가능한 합성 시나리오를 조회했습니다.",
  "data": {
    "items": [
      {
        "scenarioId": "FIN_MGMT_AB_001",
        "title": "정기납부·중복송금·거래완료 반복확인 A/B 비교",
        "baselineMonths": 9,
        "observationMonths": 3,
        "supportedBranchCodes": ["FIN_MGMT_A_NORMAL_CONTEXT", "FIN_MGMT_B_NO_CONTEXT"],
        "syntheticData": true
      }
    ]
  },
  "errors": [],
  "timestamp": "2026-08-14T01:01:00Z",
  "traceId": "frontend-trace-scenarios-0001"
}
```

---

### 연결기관·동의 요약

```http
GET /api/v1/demo/sessions/{sessionId}/customers/{customerId}/connections/consent-summary
```

```json
{
  "success": true,
  "status": 200,
  "code": "DEMO_CONNECTION_LIST_RETRIEVED",
  "message": "합성 연결기관과 동의 범위를 조회했습니다.",
  "data": {
    "demoRunId": "RUN_FIN_MGMT_B_001",
    "items": [
      {
        "connectionId": "CONN_SYN_BANK_001",
        "institutionId": "SYNTHETIC_BANK",
        "institutionName": "안심은행",
        "institutionType": "BANK",
        "status": "CONNECTED_SYNTHETIC",
        "sourceProvider": "SYNTHETIC_PROVIDER",
        "sourceUpdatedAt": "2026-07-31T23:59:59Z",
        "dataFreshness": "FIXED_SNAPSHOT",
        "consentId": "CONSENT_SYN_001",
        "consentScope": ["ACCOUNT", "BALANCE", "TRANSACTION", "RECURRING_PAYMENT"]
      },
      {
        "connectionId": "CONN_SYN_SECURITIES_001",
        "institutionId": "SYNTHETIC_SECURITIES",
        "institutionName": "안심증권",
        "institutionType": "SECURITIES",
        "status": "CONNECTED_SYNTHETIC",
        "sourceProvider": "SYNTHETIC_PROVIDER",
        "sourceUpdatedAt": "2026-07-31T23:59:59Z",
        "dataFreshness": "FIXED_SNAPSHOT",
        "consentId": "CONSENT_SYN_001",
        "consentScope": ["INVESTMENT_ACCOUNT", "POSITION", "TRADE_HISTORY"]
      }
    ],
    "consentSummary": {
      "purpose": "FINANCIAL_LIFE_CHANGE_ANALYSIS",
      "granted": true,
      "grantedAt": "2026-08-14T01:00:00Z",
      "expiresAt": "2026-08-14T03:00:00Z",
      "revocable": true
    },
    "trustedContactGate": {
      "gateEvaluated": false,
      "consentSnapshotId": "CONSENT_TRUSTED_CONTACT_001",
      "consentStatus": "NOT_GRANTED",
      "recipientAccepted": false,
      "triggerMatched": false,
      "fieldScopeMatched": false,
      "validityMatched": false,
      "deliveryEnabled": false,
      "resultCode": null,
      "dispatchAttempted": false,
      "externalDeliveryRequested": false,
      "externalDeliveryCreated": false
    },
    "provenance": {
      "syntheticData": true,
      "sourceProvider": "SYNTHETIC_PROVIDER",
      "sourceUpdatedAt": "2026-07-31T23:59:59Z",
      "dataFreshness": "FIXED_SNAPSHOT",
      "consentId": "CONSENT_SYN_001",
      "consentScope": ["ACCOUNT", "BALANCE", "TRANSACTION"],
      "snapshotHash": "sha256:07d4c6..."
    }
  },
  "errors": [],
  "timestamp": "2026-08-14T01:02:00Z",
  "traceId": "frontend-trace-connections-0001"
}
```

화면에는 안심은행·안심증권 두 합성기관 배지를 사용할 수 있지만 연결 데이터가 합성이라는 표시를 고정한다. 실제 참여 기업의 브랜드 UI를 복제하거나 실제 연결 완료로 표현하지 않는다.

---

### 통합 금융생활 요약

```http
GET /api/v1/demo/sessions/{sessionId}/customers/{customerId}/financial-summary
```

```json
{
  "success": true,
  "status": 200,
  "code": "FINANCIAL_SUMMARY_RETRIEVED",
  "message": "합성 금융생활 요약을 조회했습니다.",
  "data": {
    "demoRunId": "RUN_FIN_MGMT_B_001",
    "customerId": "SYN_CUSTOMER_FIN_MGMT_001",
    "asOf": "2026-07-31",
    "period": {
      "from": "2025-08-01",
      "to": "2026-07-31"
    },
    "assets": {
      "total": {"amount": "48250000", "currency": "KRW"},
      "bankDeposits": {"amount": "28750000", "currency": "KRW"},
      "investments": {"amount": "19500000", "currency": "KRW"},
      "liabilities": {"amount": "0", "currency": "KRW"}
    },
    "cashFlow": {
      "monthlyIncome": {"amount": "3200000", "currency": "KRW"},
      "monthlyExpense": {"amount": "2140000", "currency": "KRW"},
      "upcomingObligations": {"amount": "430000", "currency": "KRW"}
    },
    "changeSummary": {
      "openAlertCount": 1,
      "reasonCodes": ["MISSED_RECURRING", "DUPLICATE_TRANSFER", "REPEATED_CONFIRMATION"],
      "summary": "정기납부 누락 3건, 10분 내 중복송금 2회, 완료 후 1시간 내 반복확인 7회를 확인해 주세요."
    },
    "twelveMonthTrend": [
      {
        "month": "2026-06",
        "totalAssets": {"amount": "67180000", "currency": "KRW"}
      },
      {
        "month": "2026-07",
        "totalAssets": {"amount": "48250000", "currency": "KRW"}
      }
    ],
    "syntheticData": true,
    "provenance": {
      "syntheticData": true,
      "sourceProvider": "SYNTHETIC_PROVIDER",
      "sourceUpdatedAt": "2026-07-31T23:59:59Z",
      "dataFreshness": "FIXED_SNAPSHOT",
      "consentId": "CONSENT_SYN_001",
      "consentScope": ["ACCOUNT", "BALANCE", "TRANSACTION"],
      "snapshotHash": "sha256:07d4c6..."
    }
  },
  "errors": [],
  "timestamp": "2026-08-14T01:02:00Z",
  "traceId": "frontend-trace-summary-0001"
}
```

`assets.total`은 조회된 자산의 합계이고 순자산으로 오인하지 않도록 부채를 별도 반환한다. 투자 평가금액은 합성 snapshot의 값이며 실시간 시세가 아니다.

---

### 계좌·거래 조회

#### 계좌 목록

```http
GET /api/v1/demo/sessions/{sessionId}/customers/{customerId}/accounts
```

```json
{
  "success": true,
  "status": 200,
  "code": "ACCOUNT_LIST_RETRIEVED",
  "message": "합성 계좌 목록을 조회했습니다.",
  "data": {
    "demoRunId": "RUN_FIN_MGMT_B_001",
    "customerId": "SYN_CUSTOMER_FIN_MGMT_001",
    "items": [
      {
        "accountId": "SYN_ACCOUNT_BANK_001",
        "institutionId": "SYNTHETIC_BANK",
        "accountType": "DEMAND_DEPOSIT",
        "displayName": "생활비 통장",
        "maskedAccountNumber": "***-***-1234",
        "currentBalance": {"amount": "9250000", "currency": "KRW"},
        "availableBalance": {"amount": "9250000", "currency": "KRW"},
        "connectionId": "CONN_SYN_BANK_001",
        "consentId": "CONSENT_SYN_001",
        "sourceProvider": "SYNTHETIC_PROVIDER",
        "sourceUpdatedAt": "2026-07-31T23:59:59Z",
        "dataFreshness": "FIXED_SNAPSHOT"
      }
    ],
    "nextCursor": null,
    "hasMore": false,
    "provenance": {
      "syntheticData": true,
      "sourceProvider": "SYNTHETIC_PROVIDER",
      "sourceUpdatedAt": "2026-07-31T23:59:59Z",
      "dataFreshness": "FIXED_SNAPSHOT",
      "consentId": "CONSENT_SYN_001",
      "consentScope": ["ACCOUNT", "BALANCE", "TRANSACTION"],
      "snapshotHash": "sha256:07d4c6..."
    }
  },
  "errors": [],
  "timestamp": "2026-08-14T01:03:00Z",
  "traceId": "frontend-trace-accounts-0001"
}
```

#### 거래 목록

```http
GET /api/v1/demo/sessions/{sessionId}/accounts/{accountId}/transactions?from=2026-05-01&to=2026-07-31&cursor={cursor}&limit=20
```

지원 query:

| 이름 | 필수 | 설명 |
|---|---:|---|
| `from` | N | 발생일 시작, ISO date |
| `to` | N | 발생일 종료, ISO date |
| `direction` | N | `IN`, `OUT` |
| `category` | N | 정규화된 거래 범주 |
| `cursor` | N | 서버 발급 불투명 cursor |
| `limit` | N | 기본 20, 최대 100 |

```json
{
  "success": true,
  "status": 200,
  "code": "TRANSACTION_LIST_RETRIEVED",
  "message": "합성 거래내역을 조회했습니다.",
  "data": {
    "demoRunId": "RUN_FIN_MGMT_B_001",
    "accountId": "SYN_ACCOUNT_BANK_001",
    "items": [
      {
        "transactionId": "TX_FIN_MGMT_DUP_001",
        "occurredAt": "2026-07-10T01:20:00Z",
        "postedAt": "2026-07-10T01:20:03Z",
        "direction": "OUT",
        "transactionType": "TRANSFER_OUT",
        "amount": "450000",
        "currency": "KRW",
        "balanceAfter": "18250000",
        "counterpartyDisplayName": "합성수취인 A",
        "category": "FAMILY_SUPPORT",
        "status": "POSTED",
        "sourceProvider": "SYNTHETIC_PROVIDER",
        "dataFreshness": "FIXED_SNAPSHOT"
      }
    ],
    "nextCursor": null,
    "hasMore": false,
    "provenance": {
      "syntheticData": true,
      "sourceProvider": "SYNTHETIC_PROVIDER",
      "sourceUpdatedAt": "2026-07-31T23:59:59Z",
      "dataFreshness": "FIXED_SNAPSHOT",
      "consentId": "CONSENT_SYN_001",
      "consentScope": ["ACCOUNT", "BALANCE", "TRANSACTION"],
      "snapshotHash": "sha256:07d4c6..."
    }
  },
  "errors": [],
  "timestamp": "2026-08-14T01:03:00Z",
  "traceId": "frontend-trace-transactions-0001"
}
```

정렬 기본값은 `occurredAt DESC, transactionId DESC`다. 취소·환불·대기 거래를 `POSTED`와 합산하지 않는다.

---

### 기준선 조회

```http
GET /api/v1/demo/sessions/{sessionId}/customers/{customerId}/baselines
```

```json
{
  "success": true,
  "status": 200,
  "code": "BASELINE_LIST_RETRIEVED",
  "message": "개인 금융생활 기준선을 조회했습니다.",
  "data": {
    "demoRunId": "RUN_FIN_MGMT_B_001",
    "customerId": "SYN_CUSTOMER_FIN_MGMT_001",
    "baselinePeriod": {
      "from": "2025-08-01",
      "to": "2026-04-30"
    },
    "observationPeriod": {
      "from": "2026-05-01",
      "to": "2026-07-31"
    },
    "items": [
      {
        "baselineId": "BASELINE_MISSED_RECURRING_001",
        "featureCode": "MISSED_RECURRING_COUNT_60D",
        "baselineValue": "0",
        "currentValue": "3",
        "unit": "COUNT",
        "readiness": "READY",
        "comparisonText": "최근 60일간 예상 정기납부 3건이 유예기간 안에 확인되지 않았습니다.",
        "reasonCodes": ["MISSED_RECURRING"],
        "algorithmVersion": "baseline-rules-v2.0.0",
        "calculatedAt": "2026-08-01T00:00:00Z"
      }
    ],
    "provenance": {
      "syntheticData": true,
      "sourceProvider": "SYNTHETIC_PROVIDER",
      "sourceUpdatedAt": "2026-07-31T23:59:59Z",
      "dataFreshness": "FIXED_SNAPSHOT",
      "consentId": "CONSENT_SYN_001",
      "consentScope": ["ACCOUNT", "BALANCE", "TRANSACTION"],
      "snapshotHash": "sha256:07d4c6..."
    }
  },
  "errors": [],
  "timestamp": "2026-08-14T01:04:00Z",
  "traceId": "frontend-trace-baseline-0001"
}
```

고객에게 질병·사기 확률을 반환하지 않는다. `reviewPriority` 역시 이 응답에 포함하지 않는다.

---

### 공식 보호수단 카탈로그

```http
GET /api/v1/demo/sessions/{sessionId}/protection-actions
```

```json
{
  "success": true,
  "status": 200,
  "code": "PROTECTION_ACTION_LIST_RETRIEVED",
  "message": "공식 보호수단 안내를 조회했습니다.",
  "data": {
    "demoRunId": "RUN_FIN_MGMT_B_001",
    "items": [
      {
        "actionCode": "SAFE_BLOCK_INFO",
        "title": "금융거래 안심차단 안내",
        "status": "EXTERNAL_ONLY",
        "executionType": "GUIDANCE_ONLY",
        "eligibilitySummary": "신청 가능 여부와 세부 범위는 금융회사 확인이 필요합니다.",
        "source": {
          "issuer": "금융위원회",
          "url": "https://www.fsc.go.kr/no010101/85644",
          "effectiveFrom": null,
          "checkedAt": "2026-08-14"
        }
      },
      {
        "actionCode": "BANK_CONSULTATION",
        "title": "은행 상담 연결 안내",
        "status": "AVAILABLE",
        "executionType": "GUIDANCE_ONLY",
        "eligibilitySummary": "공식 고객센터 또는 영업점 상담 경로를 확인합니다.",
        "source": {
          "issuer": "참여 금융회사 공식 고객지원",
          "url": null,
          "effectiveFrom": null,
          "checkedAt": "2026-08-14"
        }
      }
    ],
    "syntheticData": true,
    "dataMode": "SYNTHETIC_ONLY"
  },
  "errors": [],
  "timestamp": "2026-08-14T01:04:00Z",
  "traceId": "frontend-trace-protection-0001"
}
```

보호수단 상태는 `AVAILABLE`, `ENROLLED`, `NOT_ENROLLED`, `UNKNOWN`, `EXTERNAL_ONLY` 중 하나다. P0에서는 실제 가입상태를 조회하지 않으므로 기본값은 `EXTERNAL_ONLY` 또는 `UNKNOWN`이다.

---

### P0-B 오류와 수용기준

추가 오류코드:

| HTTP | 코드 | 조건 |
|---:|---|---|
| 404 | `DEMO_SESSION_NOT_FOUND` | 세션이 없거나 capability가 누락·불일치·만료됐거나 다른 세션의 자원 접근 |
| 404 | `SYNTHETIC_ACCOUNT_NOT_FOUND` | 세션 내 합성 계좌 없음 |
| 422 | `SYNTHETIC_FIXTURE_NOT_READY` | 시나리오 적재 전 금융생활 조회 |
| 503 | `SYSTEM_NOT_READY` | DB·마이그레이션·필수 fixture 미준비 |

수용기준:

- 모든 금융생활 읽기 응답은 최상위 `provenance.syntheticData=true` 또는 `dataMode=SYNTHETIC_ONLY`를 제공하고 프론트는 이를 항상 표시한다.
- 안심은행·안심증권 배지는 합성 연결이며 실제 제휴·실연동으로 표현하지 않는다.
- 같은 Reset 뒤 계좌, 거래, 기준선, 연결, 자산 요약의 snapshot hash가 동일하다.
- 모든 session 범위 읽기는 올바른 역할의 `X-Demo-Capability`를 검증하고, 시나리오 파생 읽기는 활성 `X-Demo-Run-Id`도 검증한다.
- 금액은 10진 문자열로 직렬화한다.
- 각 읽기 데이터에 원천기관·기준시각·신선도·동의 범위를 추적할 수 있다.
- 외부 금융회사 API, 외부 LLM, 원격 모델 저장소, 실제 푸시, 문자, 전화, 이체, 주문, 차단 호출은 0건이다.
- P0 Spring 컨테이너의 외부 HTTPS 연결은 실패하고 Docker internal 네트워크의 PostgreSQL 통신만 성공한다. FastAPI는 P1 이후 별도 내부 서비스로 도입할 때 같은 정책을 적용한다.
- `protection-actions`의 모든 P0 항목은 `GUIDANCE_ONLY`다.
- 계좌번호·카드번호는 마스킹하며 합성값이라도 실제 번호 형식을 그대로 노출하지 않는다.

---

## 6.1 P1 고객 프로필·접근성 상세 계약

이 절의 7개 operation은 `IMPLEMENTED`지만 `CUSTOMER_PROFILE_API_ENABLED=false`가 기본값이다. 사설 검증 환경에서만 기능 플래그를 켜며, 모든 경로는 Bearer 인증과 customerId 소유권을 검증한다.

### 공통 인증과 path

```http
Authorization: Bearer {accessToken}
```

- `customerId`: `^[A-Za-z0-9][A-Za-z0-9_:-]{2,79}$`
- 본인 조회: 인증 주체의 `authentication.name == customerId`
- 조회 authority: `CUSTOMER_PROFILE_READ` 또는 `CUSTOMER_PROFILE_READ_ALL`
- 변경 authority: `CUSTOMER_PROFILE_WRITE` 또는 `CUSTOMER_PROFILE_WRITE_ALL`
- 다른 고객의 ID를 사용하면 `403 COMMON_FORBIDDEN`
- 존재하지 않는 고객은 `404 CUSTOMER_NOT_FOUND`
- 변경의 `expectedVersion`이 현재 `version`과 다르면 `409 CUSTOMER_VERSION_CONFLICT`

### 6.1.1 고객 요약 조회

```http
GET /api/v1/customers/{customerId}
```

성공은 `200 CUSTOMER_SUMMARY_RETRIEVED`이며 `data`는 다음 필드를 가진다.

| 필드 | 타입 | nullable | 설명 |
|---|---|---:|---|
| `customerId` | string | N | 비식별 고객 ID |
| `displayName` | string | N | 화면 표시명 |
| `organization` | string | N | 합성 소속 표시 |
| `region` | string | N | 지역 코드 |
| `status` | enum | N | `ACTIVE`, `SUSPENDED`, `CLOSED` |
| `version` | integer(int64) | N | 낙관적 잠금 버전 |
| `createdAt` | ISO-8601 offset datetime | N | 생성시각 |
| `updatedAt` | ISO-8601 offset datetime | N | 최종 변경시각 |

### 6.1.2 표시 프로필 변경

```http
PATCH /api/v1/customers/{customerId}/display-profile
Content-Type: application/json
Idempotency-Key: {8~100자의 안전한 키}
```

```json
{
  "expectedVersion": 0,
  "displayName": "이용자 001"
}
```

- `expectedVersion`: 필수, 0 이상
- `displayName`: 필수, trim 후 빈 문자열 금지, 최대 80자
- 성공: `200 CUSTOMER_DISPLAY_PROFILE_UPDATED`
- 응답 `data`: `customerId`, `displayName`, 증가된 `version`, `updatedAt`
- 같은 고객·키·요청의 재전송은 최초 업무 결과를 재사용하고, 같은 키에 다른 요청은 `409 CUSTOMER_IDEMPOTENCY_CONFLICT`다.

### 6.1.3 환경설정 조회·부분변경

```http
GET   /api/v1/customers/{customerId}/preferences
PATCH /api/v1/customers/{customerId}/preferences
```

PATCH는 `Idempotency-Key: {8~100자의 안전한 키}`를 필수로 요구한다.

PATCH 요청:

```json
{
  "expectedVersion": 0,
  "smsNotificationEnabled": false,
  "pushNotificationEnabled": false,
  "inAppNotificationEnabled": true
}
```

- `expectedVersion`은 필수다.
- 세 boolean은 nullable이며 하나 이상을 반드시 보내야 한다.
- `null` 또는 생략된 설정은 현재 값을 유지한다.
- 실제 SMS·push 발송을 실행하지 않고 서비스 설정만 저장한다.
- 조회 성공: `200 CUSTOMER_PREFERENCES_RETRIEVED`
- 변경 성공: `200 CUSTOMER_PREFERENCES_UPDATED`
- 응답 필드: `customerId`, 세 boolean, `version`, `updatedAt`

### 6.1.4 접근성 설정 조회·전체변경

```http
GET /api/v1/customers/{customerId}/accessibility-settings
PUT /api/v1/customers/{customerId}/accessibility-settings
```

PUT은 `Idempotency-Key: {8~100자의 안전한 키}`를 필수로 요구한다.

PUT 요청:

```json
{
  "expectedVersion": 0,
  "largeFont": true,
  "highContrast": false,
  "speechGuidance": false,
  "oneHandMode": true
}
```

PUT은 전체 교체다. `expectedVersion`과 네 boolean은 모두 필수다.

- 조회 성공: `200 CUSTOMER_ACCESSIBILITY_SETTINGS_RETRIEVED`
- 변경 성공: `200 CUSTOMER_ACCESSIBILITY_SETTINGS_UPDATED`
- 응답 필드: `customerId`, 네 boolean, `version`, `updatedAt`

### 6.1.5 보유 데이터 요약 조회

```http
GET /api/v1/customers/{customerId}/data-summary
```

성공은 `200 CUSTOMER_DATA_SUMMARY_RETRIEVED`다.

```json
{
  "customerId": "SYN_CUSTOMER_FIN_MGMT_001",
  "institutions": 2,
  "accounts": 4,
  "transactionsSynced": 42,
  "lastSyncAt": null,
  "dataFreshness": {
    "accounts": "FIXED_SNAPSHOT",
    "transactions": "FIXED_SNAPSHOT",
    "baseline": "CURRENT"
  },
  "updatedAt": "2026-08-14T00:00:00Z"
}
```

개수는 0 이상이며 `lastSyncAt`만 nullable이다. 실제 금융회사 동기화를 시작하지 않는다.

---

## 6.2 P1 합성 회원 인증 상세 계약

이 절의 operation은 기본적으로 `LOCAL_AUTH_API_ENABLED=true`인 로컬 검증용이다. 공개 합성데모에서는 `SYNTHETIC_MEMBER_AUTH_ENABLED=true`일 때 성공한 `PUBLIC` fixture의 `demo001`~`demo300`만 허용한다. Vercel은 원문 token 응답을 브라우저에 전달하지 않고 Secure·HttpOnly·SameSite=Strict 쿠키로 변환한다. 실제 서비스의 기업 IdP·MFA 계약은 아니다.

### 공통 token 계약

- access·refresh token은 256-bit 불투명 난수이며 JWT가 아니다.
- token 원문은 응답에서 한 번만 반환하고 DB에는 SHA-256 hash만 저장한다.
- access 기본 TTL은 15분, refresh sliding TTL은 8시간, 세션 절대 TTL은 24시간이다.
- refresh할 때 access·refresh token을 모두 회전한다.
- 이미 사용한 refresh token이 다시 들어오면 탈취 신호로 보고 token family 전체를 폐기한다.
- 사용자별 활성 세션 기본 상한은 5개이며 초과 시 가장 오래된 세션부터 폐기한다.
- Authorization header, token, 비밀번호를 URL·로그·감사 payload에 기록하지 않는다.

### 6.2.1 로그인

```http
POST /api/v1/auth/login
Content-Type: application/json
```

```json
{
  "loginId": "demo001",
  "password": "{12~200자의 로컬 합성 계정 비밀번호}"
}
```

- 인증 불필요
- `loginId`: 필수, 최대 80자
- `password`: 필수, 12~200자
- 성공: `200 AUTH_LOGIN_SUCCEEDED`
- 실패: `401 AUTH_INVALID_CREDENTIALS`; 계정 존재 여부를 구분하지 않는다.
- 같은 loginId hash의 반복 실패: `429 AUTH_LOGIN_RATE_LIMITED`

성공 `data`:

```json
{
  "tokenType": "Bearer",
  "accessToken": "{opaque-token}",
  "accessExpiresAt": "2026-08-18T01:15:00Z",
  "refreshToken": "{opaque-refresh-token}",
  "refreshExpiresAt": "2026-08-18T09:00:00Z"
}
```

### 6.2.2 token 갱신

```http
POST /api/v1/auth/token/refresh
Content-Type: application/json
```

```json
{
  "refreshToken": "{40~300자의 opaque refresh token}"
}
```

- Bearer 인증은 요구하지 않고 refresh token 자체를 검증한다.
- 성공: `200 AUTH_TOKEN_REFRESHED`, 새로운 `TokenPair` 반환
- token 없음·만료·폐기·절대 만료: `401 AUTH_INVALID_TOKEN`
- 이전 token 재사용: `401 AUTH_INVALID_TOKEN`과 해당 family 전체 폐기
- 새 `refreshExpiresAt`은 절대 만료를 넘지 않는다.

### 6.2.3 현재 세션 로그아웃

```http
POST /api/v1/auth/logout
Authorization: Bearer {accessToken}
```

- 성공: `200 AUTH_LOGOUT_SUCCEEDED`, `data=null`
- 이미 폐기되었거나 유효하지 않은 세션: `401 AUTH_INVALID_TOKEN` 또는 `AUTH_SESSION_REVOKED`
- 현재 세션과 연결된 모든 refresh token을 폐기한다.

### 6.2.4 모든 세션 로그아웃

```http
POST /api/v1/auth/logout-all
Authorization: Bearer {accessToken}
```

- 성공: `200 AUTH_LOGOUT_ALL_SUCCEEDED`, `data=null`
- 인증 주체의 현재·다른 기기 세션과 refresh token을 모두 폐기한다.

### 6.2.5 현재 사용자 조회

```http
GET /api/v1/auth/me
Authorization: Bearer {accessToken}
```

성공은 `200 AUTH_CURRENT_USER_RETRIEVED`다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `principalId` | UUID | 인증 주체 ID |
| `loginId` | string | 로컬 합성 로그인 ID |
| `customerId` | string | 연결된 비식별 고객 ID |
| `displayName` | string | 표시명 |
| `roles` | string[] | 정렬된 역할 목록 |

### 6.2.6 권한 조회

```http
GET /api/v1/auth/me/permissions
Authorization: Bearer {accessToken}
```

성공은 `200 AUTH_PERMISSIONS_RETRIEVED`이며 `data.permissions`는 중복 없이 정렬된 authority 문자열 배열이다.

---

## 6.3 P1 합성 금융기관·연결 조회 상세 계약

이 절의 4개 operation은 `IMPLEMENTED-SYNTHETIC-READ-ONLY`다. 모든 데이터는 PostgreSQL 고정 snapshot이며 실제 금융기관·마이데이터 API를 호출하지 않는다.

### 공통 인증

- 네 endpoint 모두 Bearer 인증이 필요하다.
- 기관 목록·상세는 유효한 인증 주체면 조회할 수 있다.
- 고객 연결 목록·상세는 본인 `customerId + FINANCIAL_CONNECTION_READ` 또는 `FINANCIAL_CONNECTION_READ_ALL`이 필요하다.
- `customerId`: `^[A-Za-z0-9][A-Za-z0-9_:-]{2,79}$`
- `institutionId`: `^[A-Z][A-Z0-9_]{2,39}$`
- `connectionId`: UUID

### 공통 DTO

`InstitutionSummary`:

| 필드 | 타입 | 값·설명 |
|---|---|---|
| `institutionId` | string | `SYNTHETIC_BANK`, `SYNTHETIC_SECURITIES` |
| `displayName` | string | 안심은행, 안심증권 |
| `institutionType` | enum | `BANK`, `SECURITIES` |
| `providerMode` | enum | 항상 `SYNTHETIC_PROVIDER` |
| `connectionAvailable` | boolean | 합성 연결 가능 여부 |
| `dataAsOf` | date | 고정 snapshot 기준일 |

`Scope`:

| 필드 | 타입 | nullable | 설명 |
|---|---|---:|---|
| `scopeCode` | string | N | `ACCOUNTS`, `TRANSACTIONS`, `INVESTMENT_ACCOUNTS`, `POSITIONS` |
| `displayName` | string | N | 화면 표시명 |
| `readOnly` | boolean | N | 현재 항상 true |
| `consentStatus` | enum | Y | 기관 지원범위에서는 null, 고객 연결에서는 `CONSENTED` 또는 `WITHDRAWN` |

### 6.3.1 금융기관 목록

```http
GET /api/v1/financial-institutions
Authorization: Bearer {accessToken}
```

- 성공: `200 FINANCIAL_INSTITUTIONS_RETRIEVED`
- 응답: `{ "items": InstitutionSummary[], "total": integer }`
- 정렬: `displayName`, `institutionId` 오름차순
- 현재 fixture의 `total`은 2다.

### 6.3.2 금융기관 상세

```http
GET /api/v1/financial-institutions/{institutionId}
Authorization: Bearer {accessToken}
```

- 성공: `200 FINANCIAL_INSTITUTION_RETRIEVED`
- 응답: `{ "institution": InstitutionSummary, "supportedScopes": Scope[] }`
- 없음: `404 CONNECTION_INSTITUTION_NOT_FOUND`
- `supportedScopes`는 `scopeCode` 오름차순이며 `consentStatus=null`이다.

### 6.3.3 고객 연결 목록

```http
GET /api/v1/customers/{customerId}/connections
Authorization: Bearer {accessToken}
```

- 성공: `200 CUSTOMER_CONNECTIONS_RETRIEVED`
- 응답: `{ "items": ConnectionSummary[], "total": integer }`
- 정렬: 기관 `displayName`, `connectionId` 오름차순

`ConnectionSummary` 필드:

| 필드 | 타입 | nullable | 설명 |
|---|---|---:|---|
| `connectionId` | UUID | N | 연결 ID |
| `customerId` | string | N | 소유 고객 |
| `institution` | InstitutionSummary | N | 합성 기관 |
| `connectionStatus` | enum | N | `ACTIVE`, `DEGRADED`, `EXPIRED` |
| `consentedAt` | datetime | N | 합성 동의 시작 |
| `consentExpiresAt` | datetime | N | 합성 동의 만료 |
| `lastSyncedAt` | datetime | Y | 마지막 snapshot 동기화 |
| `providerMode` | enum | N | 항상 `SYNTHETIC_PROVIDER` |
| `version` | integer(int64) | N | row version |

### 6.3.4 고객 연결 상세

```http
GET /api/v1/customers/{customerId}/connections/{connectionId}
Authorization: Bearer {accessToken}
```

- 성공: `200 CUSTOMER_CONNECTION_RETRIEVED`
- 응답: `{ "connection": ConnectionSummary, "consentScopes": Scope[] }`
- 같은 고객에게 해당 연결이 없음: `404 CONNECTION_NOT_FOUND`
- 다른 고객 ID로 조회하면 소유권 단계에서 `403 COMMON_FORBIDDEN`
- `consentScopes`는 `scopeCode` 오름차순이다.

---

## 6.4 P1 고객 기준선·변화신호 상세 계약

이 절의 7개 operation은 `IMPLEMENTED-SYNTHETIC-SNAPSHOT`이다. 운영형 고객 API는 익명 데모의 `{sessionId, demoRunId}` 테이블을 직접 노출하지 않고 V18의 고객 소유 snapshot을 사용한다. 현재 계산은 외부 데이터 수집이나 외부 모델 실행 없이 고정 합성 snapshot을 결정론적으로 검증한다.

### 공통 접근·데이터 경계

- 고객 경로 조회: 본인 `customerId + DETECTION_READ` 또는 `DETECTION_READ_ALL`
- 계산 생성: 본인 `customerId + DETECTION_CALCULATE` 또는 `DETECTION_CALCULATE_ALL`
- `/signals/{signalId}` 경로는 `DETECTION_READ` 또는 `DETECTION_READ_ALL`이 필요하다.
- 다른 고객의 customerId 경로는 `403 COMMON_FORBIDDEN`이다.
- 다른 고객의 signalId는 소유관계를 감추기 위해 `404 DETECTION_SIGNAL_NOT_FOUND`다.
- 금액은 10진 문자열로 반환하고 신호는 `BEHAVIOR_CHANGE`이며 금융기관 FDS 판정으로 표현하지 않는다.

### 6.4.1 기준선 목록·상세·특징

```http
GET /api/v1/customers/{customerId}/baselines
GET /api/v1/customers/{customerId}/baselines/{baselineId}
GET /api/v1/customers/{customerId}/baselines/{baselineId}/features
```

- 목록 성공: `200 CUSTOMER_BASELINES_RETRIEVED`
- 상세 성공: `200 CUSTOMER_BASELINE_RETRIEVED`
- 특징 성공: `200 CUSTOMER_BASELINE_FEATURES_RETRIEVED`
- 기준선 없음: `404 DETECTION_BASELINE_NOT_FOUND`
- 목록 정렬: `featureCode`, `baselineId` 오름차순
- 특징 정렬: `featureCode`, `featureId` 오름차순
- `BaselineSummary`: `baselineId`, `customerId`, `featureCode`, `baselineValue`, `currentValue`, `unit`, `readiness`, `comparisonText`, `algorithmVersion`, `calculatedAt`, `version`
- 상세 추가 필드: `baselinePeriod`, `observationPeriod`, `snapshotHash`
- 특징 필드: `featureId`, `featureCode`, `value`, `unit`, `observedPeriod`, `sampleCount`, `snapshotHash`

### 6.4.2 기준선 계산 작업 생성

```http
POST /api/v1/customers/{customerId}/baseline-calculations
Idempotency-Key: {8~100자의 안전한 키}
```

현재 구현은 이미 적재된 합성 기준선·신호 snapshot을 해시로 검증하고 계산 작업 이력을 남긴다. 원천 금융데이터 재수집, 외부 API, 외부 LLM, 실제 금융 실행은 만들지 않는다.

- 성공: `202 BASELINE_CALCULATION_COMPLETED`
- snapshot 미준비: `422 DETECTION_SNAPSHOT_NOT_READY`
- 응답: `calculationId`, `customerId`, `status=COMPLETED`, `algorithmVersion`, `baselinesEvaluated`, `signalsEvaluated`, `reusedCurrentSnapshot=true`, `requestedAt`, `completedAt`, `resultSnapshotHash`, `requestHash`, `idempotencyReplayed`, `externalExecutionCreated=false`
- `Idempotency-Key`는 필수이며 같은 고객·키의 재요청은 기존 결과와 `idempotencyReplayed=true`를 반환한다.
- 작업별 `idempotencyKeyHash`, `requestHash`, `inputSnapshotHash`, `resultSnapshotHash`를 저장하고 원문 멱등키는 저장하지 않는다.

### 6.4.3 변화신호 목록·상세·근거

```http
GET /api/v1/customers/{customerId}/signals?severity=HIGH&status=OPEN
GET /api/v1/signals/{signalId}
GET /api/v1/signals/{signalId}/evidence
```

- 목록 성공: `200 CUSTOMER_SIGNALS_RETRIEVED`
- 상세 성공: `200 SIGNAL_RETRIEVED`
- 근거 성공: `200 SIGNAL_EVIDENCE_RETRIEVED`
- `severity`: 선택, `LOW|MEDIUM|HIGH`
- `status`: 선택, `OPEN|ACKNOWLEDGED|CLOSED`
- 목록 정렬: `detectedAt`, `signalId` 내림차순
- `SignalSummary`: `signalId`, `customerId`, `baselineId`, `signalType`, `severity`, `baselineValue`, `currentValue`, `unit`, `reasonCode`, `status`, `algorithmVersion`, `detectedAt`
- 근거 필드: `evidenceId`, `evidenceType`, `sourceReference`, `occurredAt`, nullable `amount`·`currency`, `description`, `integrityHash`
- 근거는 신호 생성시점의 불변 snapshot이며 이후 고객 맥락을 소급 병합하지 않는다.

---

## 6.5 P1 합성 데이터셋·탐지 실행 상세 계약

이 절의 6개 operation은 `IMPLEMENTED-PRIVATE-SYNTHETIC-ONLY`다. 실제 금융거래 원문이나 파일 업로드를 받지 않고 허용된 세 가지 특징과 최소 합성 근거만 JSON으로 등록한다. 모든 경로는 사설 검증 관리자 권한이 필요하다.

Flyway V63은 위 공개 operation 수를 늘리지 않고 배포용 `synthetic-v3` 생성 Job을 추가한다. 생성 Job은 `SMOKE(고객 10명·거래 600건)`, `DEMO(고객 50명·거래 12,000건)`, `LOAD(고객 250명·거래 75,000건)`, `DEV(고객 1,000명·거래 1,000,000건)` 중 하나를 선택한다. `fixtureVersion + profile + seed`가 같으면 동일 ID와 manifest hash를 재생하며, 데이터는 1~100명 단위 DB batch로 적재한다. 실행이력은 `synthetic_fixture_generation_run`, 고객별 시나리오와 기대 신호 수는 `synthetic_fixture_customer`에 보존한다.

Flyway V67은 `LOAD` profile과 추가 전용 `synthetic_fixture_quality_report`를 추가한다. `SYNTHETIC_SEED_VERIFY_DETECTION=true`인 Job은 생성된 모든 고객을 현재 활성 정책과 고정 알고리즘으로 평가한다. 정책이 실행 도중 바뀌지 않고 기대·실제 신호 수가 일치하며 정상 고객 오탐과 이상 고객 미탐이 모두 0일 때만 `PASSED`다. 리포트에는 정책·알고리즘 버전, precision·recall, 64자리 report hash를 남기며 외부 실행과 advisory AI는 허용하지 않는다.

Flyway V68은 AI 파생 `chunk_embedding` 테이블을 추가해 384차원과 1024차원 벡터를
모델별로 구분한다. 각 행은 chunk, 모델 ID, 고정 모델 버전, 차원을 함께 식별하며
Hash v1, 고정 E5-small revision, 고정 Arctic-ko revision마다 별도 HNSW 부분 인덱스를
사용한다. 기존 `chunk.embedding`은 배포 호환성을 위해 유지하고 Hash 벡터를 신규
테이블로 이관한다. 운영 ingestion은 Spring publish/import와 같은 문서별 advisory lock 안에서 기존 chunk와
종속 embedding을 삭제한 뒤 현재 provider의 완전한 파생 snapshot을 INSERT-only로
교체하며, 실행 완료와 교체를 같은 트랜잭션으로 커밋한다. 이 교체는 검증 import 전까지만
허용되며 `AI_DB_SNAPSHOT_V1` proof 생성 후 내용 변경은 새 `versionLabel`을 사용한다. 모델 비교는 격리된 임시 DB에서
각각 ingestion해 운영 snapshot에 서로 다른 모델 결과를 섞지 않는다.

Flyway V72는 `alzswell_ai_ingestor`의 `ai_knowledge.chunk`와
`ai_knowledge.chunk_embedding` UPDATE 권한을 테이블·컬럼 수준에서 모두 회수한다.
ingestor는 chunk에 SELECT·INSERT·DELETE, chunk_embedding에 SELECT·INSERT만 가지며 임베딩
삭제는 부모 chunk cascade로만 수행한다. Spring과 공유하는 문서 advisory lock 안에서 검증
import 전까지만 삭제 후 INSERT-only로 교체한다. proof가 생성된 문서·버전의 chunk·manifest
snapshot과 proof가 참조하는 terminal ingestion run은 SECURITY DEFINER trigger가 이후 변경을
거부하고, UPDATE는 OLD·NEW 양쪽 키를 검사한다. 이후 내용 변경은 새 버전으로 적재한다. 미검증·신규
버전의 권위 DB snapshot 동기화에 필요한 `ai_knowledge.document_snapshot` UPDATE와 검색 runtime의
파생 테이블 SELECT는 유지한다.

AI ingestion은 문서당 최대 500개 청크를 허용하고 지연 분할 중 501번째 청크가 생기는
즉시 실패한다. 운영 Compose는 `PRODUCTION`, `hash`, hash fallback 비활성화를 명시한다.
운영에서 non-hash backend에 `ALZS_EMBEDDING_ALLOW_HASH_FALLBACK=true`를 함께 설정하면
대체 모델로 조용히 강등하지 않고 `EMBEDDING_CONFIGURATION_INVALID`로 기동을 거부한다.
fallback은 `SYNTHETIC_TEST`에서만 선택적으로 사용할 수 있다.

이 Job은 HTTP Controller를 제공하지 않는다. `alzswell_migrator` 역할과 `synthetic-tools` Compose profile에서만 실행하며 `SYNTHETIC_DATA_ONLY=true`, `SYNTHETIC_PROVIDER_ONLY=true`, `EXTERNAL_ACTIONS_ENABLED=false`가 아니면 시작 전에 실패한다. 생성된 모든 계좌·거래는 `SYNTHETIC_PROVIDER`이고 실제 금융기관 호출·송금·알림·외부 AI 실행을 만들지 않는다.

### 6.5.1 권한과 상태전이

- 데이터셋 4개 API: `SYNTHETIC_DATASET_ADMIN`
- 탐지 실행 생성: `DETECTION_RUN_CREATE`
- 탐지 실행 조회: `DETECTION_RUN_READ`
- 상태전이: `DRAFT → VALIDATED → INGESTED`, 검증 실패는 `DRAFT → INVALID`
- `INVALID`는 재검증할 수 있지만 적재할 수 없다.
- payload는 JSONB와 `payloadHash`로 보존하고 INGESTED 이후 변경 API를 제공하지 않는다.

### 6.5.2 데이터셋 등록·조회·검증·적재

```http
POST /api/v1/admin/synthetic-datasets
GET  /api/v1/admin/synthetic-datasets/{datasetId}
POST /api/v1/admin/synthetic-datasets/{datasetId}/validate
POST /api/v1/admin/synthetic-datasets/{datasetId}/ingest
```

등록 요청은 `datasetName`, `customerId`, 1~50개의 `observations`를 받는다. observation은 다음으로 제한한다.

- `featureCode`: `MISSED_RECURRING_PAYMENT`, `DUPLICATE_TRANSFER`, `REPEATED_CONFIRMATION`
- `baselineValue`, `currentValue`: 0 이상의 10진수
- `unit`: `COUNT`
- observation별 evidence 1~20개
- evidence: `TRANSACTION|INTERACTION`, 안전한 `sourceReference`, `occurredAt`, 선택적 amount·currency 쌍, 최대 300자 설명

응답 코드:

- `201 SYNTHETIC_DATASET_CREATED`
- `200 SYNTHETIC_DATASET_RETRIEVED`
- `200 SYNTHETIC_DATASET_VALIDATED`
- `200 SYNTHETIC_DATASET_INGESTED`
- `404 SYNTHETIC_DATASET_NOT_FOUND`
- `409 SYNTHETIC_DATASET_STATE_CONFLICT`

### 6.5.3 탐지 실행·결과 조회

```http
POST /api/v1/customers/{customerId}/detection-runs
Idempotency-Key: {8~100자의 안전한 키}
Content-Type: application/json

{"datasetId":"{INGESTED 상태의 dataset UUID}"}

GET /api/v1/detection-runs/{detectionRunId}
```

- 생성 성공: `202 DETECTION_RUN_COMPLETED`
- 조회 성공: `200 DETECTION_RUN_RETRIEVED`
- 없음: `404 DETECTION_RUN_NOT_FOUND`
- 데이터셋 고객 불일치 또는 미적재: `409 SYNTHETIC_DATASET_STATE_CONFLICT`
- 같은 고객·멱등키는 최초 run을 재생하며 원문 멱등키는 저장하지 않는다.
- 같은 멱등키를 다른 datasetId에 재사용하면 `409 DETECTION_IDEMPOTENCY_CONFLICT`다.
- 결과에는 `signals`, `signalCount`, 버전·입력/결과/request hash, `idempotencyReplayed`, `advisoryAiUsed=false`, `externalExecutionCreated=false`를 포함한다.
- 현재 권위 경로는 Java 규칙이며 변경된 SSOT의 Isolation Forest 보조점수는 아직 실행하지 않는다.

### 6.5.4 탐지 결과의 운영형 신호·경보 승격

```http
POST /api/v1/detection-runs/{detectionRunId}/promotion
GET  /api/v1/detection-runs/{detectionRunId}/promotion
```

- 생성 권한은 `DETECTION_PROMOTE`, 조회 권한은 `DETECTION_PROMOTION_READ`다.
- 완료된 run 하나에는 승격 결과가 정확히 하나만 존재한다. POST 재호출은 같은 `promotionId`를 반환하고 `idempotencyReplayed=true`로 표시한다.
- 서버는 run 행을 잠근 뒤 하나의 DB 트랜잭션에서 `customer_detection_signal`, 불변 evidence snapshot, `operational_alert`, 최초 `ALERT_CREATED` 감사를 함께 기록한다.
- 신호는 `(sourceDetectionRunId, reasonCode)`로 중복을 막고 경보는 신호당 하나만 허용한다.
- 저장된 고객 기준선의 고객·특징·기준값·단위가 합성 observation과 정확히 일치해야 한다. 기준선을 임의로 덮어쓰거나 새 기준선을 자동 생성하지 않는다.
- 기준선 불일치는 `422 DETECTION_PROMOTION_BASELINE_MISMATCH`, 원본·결과 특징 불일치는 `422 DETECTION_PROMOTION_SOURCE_INVALID`다.
- 생성 성공은 `201 DETECTION_RUN_PROMOTED`, 조회 성공은 `200 DETECTION_RUN_PROMOTION_RETRIEVED`, 미승격 조회는 `404 DETECTION_PROMOTION_NOT_FOUND`다.
- 결과에는 생성한 `signalIds`, `alertIds`, 입력·승격 결과 hash를 포함하며 `financialActionExecuted=false`, `externalNotificationSent=false`를 명시한다.

---

## 6.6 P1 운영형 경보·생활맥락 상세 계약

이 절의 6개 operation은 `IMPLEMENTED-PRIVATE-SYNTHETIC-ONLY`다. 기존 합성 변화신호를 고객이 이해할 수 있는 경보로 보여주고 생활맥락을 확인하지만, 금융거래 차단·지급정지·외부 알림·가족 연락은 실행하지 않는다.

```http
GET  /api/v1/customers/{customerId}/alerts?state=AWAITING_CONTEXT&severity=HIGH
GET  /api/v1/alerts/{alertId}
GET  /api/v1/alerts/{alertId}/context-options
POST /api/v1/alerts/{alertId}/context-responses
POST /api/v1/alerts/{alertId}/defer
GET  /api/v1/alerts/{alertId}/audit
```

### 6.6.1 권한·소유권

- 자신의 목록·상세·선택지·감사이력: `ALERT_READ`
- 자신의 생활맥락 응답·확인 연기: `ALERT_RESPOND`
- 사설 검증 관리자 전체 조회·응답: `ALERT_READ_ALL`, `ALERT_RESPOND_ALL`
- 다른 고객의 경보는 존재 여부를 노출하지 않고 `404 ALERT_NOT_FOUND`로 응답한다.

### 6.6.2 상태전이와 동시성

- 최초 상태는 `AWAITING_CONTEXT`다.
- `AWAITING_CONTEXT|DEFERRED → CLOSED_NORMAL`: 고객이 `EXPECTED_CHANGE`로 확인한 경우다.
- `AWAITING_CONTEXT|DEFERRED → BANK_REVIEW`: `UNRECOGNIZED|NOT_SURE` 응답인 경우다.
- 확인 연기는 `AWAITING_CONTEXT|DEFERRED → DEFERRED`이며 서버 현재시각보다 미래이고 최대 7일 이내여야 한다.
- 변경 요청은 `expectedVersion`을 사용한다. 버전 또는 상태가 오래되면 `409 ALERT_STATE_CONFLICT`다.
- 생활맥락 제출과 확인 연기는 `Idempotency-Key`가 필수다. 같은 요청은 최초 결과를 재사용하고 다른 요청에 키를 재사용하면 `409 ALERT_IDEMPOTENCY_CONFLICT`다. 원문 키는 저장하지 않는다.

### 6.6.3 안전 응답과 감사

- 모든 변경 응답은 `financialActionExecuted=false`, `externalNotificationSent=false`를 명시한다.
- 맥락 선택지는 `EXPECTED_CHANGE`, `UNRECOGNIZED`, `NOT_SURE` 세 개로 제한한다.
- 감사이력은 `ALERT_CREATED`, `ALERT_DEFERRED`, `CONTEXT_RESPONDED` 이벤트의 이전·결과 상태, 최소 상세, 무결성 해시를 시간순으로 제공한다.
- `CLOSED_NORMAL`은 워크플로 종결일 뿐 금융상 불이익이나 계좌조치가 아니다.
- `BANK_REVIEW`는 검토 필요 상태이며 실제 사건 배정·금융조치가 실행됐다는 뜻이 아니다.

응답 코드:

- `200 CUSTOMER_ALERTS_RETRIEVED`
- `200 ALERT_RETRIEVED`
- `200 ALERT_CONTEXT_OPTIONS_RETRIEVED`
- `200 ALERT_CONTEXT_APPLIED`
- `200 ALERT_DEFERRED`
- `200 ALERT_AUDIT_RETRIEVED`
- `404 ALERT_NOT_FOUND`
- `409 ALERT_STATE_CONFLICT`
- `409 ALERT_IDEMPOTENCY_CONFLICT`

---

## 6.7 P1 운영형 행원 사건 상세 계약

이 절의 5개 operation은 `IMPLEMENTED-PRIVATE-SYNTHETIC-ONLY`다. 고객의 `UNRECOGNIZED|NOT_SURE` 응답으로 경보가 `BANK_REVIEW`가 되는 트랜잭션 안에서 운영형 사건 하나를 생성한다. 기존 `demo_session_id` 범위의 `protection_case`와는 별도 테이블을 사용한다.

```http
GET  /api/v1/staff/cases?status=PENDING&priority=HIGH&cursor={caseId}&limit=20
GET  /api/v1/staff/cases/{caseId}
PUT  /api/v1/staff/cases/{caseId}/assignment
POST /api/v1/staff/cases/{caseId}/reviews
POST /api/v1/staff/cases/{caseId}/guidance-plans
GET  /api/v1/staff/cases/{caseId}/evidence
GET  /api/v1/staff/cases/{caseId}/timeline
GET  /api/v1/staff/cases/{caseId}/notes
POST /api/v1/staff/cases/{caseId}/notes
GET  /api/v1/staff/cases/{caseId}/follow-ups
POST /api/v1/staff/cases/{caseId}/follow-ups
PATCH /api/v1/staff/follow-ups/{followUpId}
```

### 6.7.1 권한과 사건 생성

- 큐·상세: `STAFF_CASE_READ`
- 담당자 배정: `STAFF_CASE_ASSIGN`
- 검토 상태전이: `STAFF_CASE_REVIEW`
- 안내계획 승인: `STAFF_GUIDANCE_APPROVE`
- `PROTECTION_STAFF` 역할은 위 권한을 가진 사설 검증용 행원 역할이다.
- 경보당 사건은 하나만 존재하며 `operational_protection_case.alert_id` 고유 제약으로 중복을 차단한다.
- 우선순위는 경보 severity를 그대로 사용하고 최초 업무상태는 `PENDING`이다.

### 6.7.2 큐·배정·검토 상태

- 큐 정렬은 `HIGH → MEDIUM → LOW`, 생성시각, `caseId` 순서다. 다음 cursor는 마지막 `caseId`이며 동일 복합 정렬 기준으로 이어진다.
- 배정은 `assignedTeam`, `assignedTo`, `expectedVersion`을 함께 요구하며 완료 사건은 변경할 수 없다.
- 검토 상태전이는 `PENDING → IN_REVIEW → COMPLETED`이며 안내계획 승인 후에는 `GUIDANCE_APPROVED → COMPLETED`가 가능하다.
- 완료 사건은 `REOPEN_REVIEW`로 `IN_REVIEW`에 되돌릴 수 있다.
- `START_REVIEW`는 담당자가 배정된 사건에만 허용한다.
- 모든 변경은 `expectedVersion`을 사용하고 오래된 요청은 `409 STAFF_CASE_STATE_CONFLICT`다.
- 담당자 배정은 `Idempotency-Key`가 필수이며 같은 키의 다른 요청은 `409 STAFF_CASE_ASSIGNMENT_IDEMPOTENCY_CONFLICT`다.
- 검토 요청은 `Idempotency-Key`가 필수이며 원문 키를 저장하지 않는다. 같은 키의 다른 요청은 `409 STAFF_CASE_REVIEW_IDEMPOTENCY_CONFLICT`다.

### 6.7.3 안내계획 안전경계

- 허용 action은 `FDS_REVIEW`, `DELAYED_TRANSFER_GUIDANCE`, `SECURITY_SETTINGS_GUIDANCE`, `BRANCH_CONSULTATION`이다.
- 안내계획은 배정된 `IN_REVIEW` 사건에 한 번만 승인할 수 있다.
- 안내계획 승인은 `Idempotency-Key`가 필수이며 같은 키의 다른 요청은 `409 STAFF_GUIDANCE_IDEMPOTENCY_CONFLICT`다.
- 응답은 `delivered=false`, `externalExecutionCreated=false`다. 실제 FDS 실행, 지연이체 신청, 설정 변경, 영업점 예약을 수행하지 않는다.
- 사건 응답도 `financialActionExecuted=false`, `externalNotificationSent=false`를 명시한다.

### 6.7.4 근거·타임라인·내부 메모

- 사건 근거는 연결된 `customer_detection_signal`과 생성 당시의 `customer_signal_evidence_snapshot`만 읽으며 `syntheticData=true`를 명시한다.
- 타임라인은 사건 생성, 경보 상태변경, 담당자 배정, 검토 상태전이, 안내계획 승인, 내부 메모 등록을 시간순으로 반환한다.
- 내부 메모 조회는 `STAFF_CASE_READ|STAFF_CASE_NOTE`, 등록은 `STAFF_CASE_NOTE` 권한이 필요하다.
- 메모는 수정·삭제 API가 없고 DB trigger도 update/delete를 거절하는 추가 전용 기록이다.
- 메모 등록은 `Idempotency-Key`가 필수이며 원문 키는 저장하지 않는다. 같은 키의 다른 내용은 `409 STAFF_CASE_NOTE_IDEMPOTENCY_CONFLICT`다.
- 타임라인에는 내부 메모의 존재와 작성자만 표시하고 메모 본문은 notes API에서만 반환한다.

### 6.7.5 내부 후속 일정

- 후속 일정 조회·등록·변경은 `STAFF_FOLLOW_UP` 권한이 필요하며 조회는 `STAFF_CASE_READ`로도 가능하다.
- 유형은 `CUSTOMER_RECHECK`, `BRANCH_CONSULTATION`, `INTERNAL_REVIEW`이며 최대 90일 이내 미래 시각만 등록한다.
- 등록은 담당자가 배정된 `IN_REVIEW|GUIDANCE_APPROVED|COMPLETED` 사건에만 가능하고 `expectedCaseVersion`으로 사건 변경과 경쟁하지 않게 한다.
- 최초 상태는 `SCHEDULED`다. `RESCHEDULE`은 미래 시각을 요구하고, `COMPLETE|CANCEL`은 결과 사유를 요구한다.
- 등록은 `Idempotency-Key`가 필수이며 원문 키를 저장하지 않는다. 같은 키의 다른 요청은 `409 STAFF_FOLLOW_UP_IDEMPOTENCY_CONFLICT`다.
- 상태 변경은 `expectedVersion`을 사용하며 완료·취소된 일정은 다시 변경하지 않는다.
- 모든 변경은 추가 전용 `operational_case_follow_up_event`에 기록되고 사건 타임라인에 합쳐진다.
- 응답은 `externalContactExecuted=false`다. 전화·문자·푸시·영업점 예약을 실행하지 않는다.

---

## 6.8 P1 이체 안전 미리보기 상세 계약

### 6.8.1 접근·입력

- `GET /customers/{customerId}/beneficiaries`, `GET /customers/{customerId}/transfer-limits`는 본인 Bearer 주체와 `TRANSFER_PREVIEW_READ`가 모두 필요하다.
- `POST /transfer-simulations`, `POST /transfer-validations`는 본인 `customerId`와 `TRANSFER_PREVIEW_EVALUATE`가 모두 필요하다.
- POST 본문은 `customerId`, 합성 `sourceAccountId`, 합성 `beneficiaryId`, 정수 원화 `amount(1..100000000)`, `currency=KRW`를 받는다. 원문 계좌번호·주민번호·연락처·자유입력 수취인 정보는 받지 않는다.
- validation은 고정 `purposeCode`와 `recipientConfirmed`를 추가로 요구한다. purpose는 설명·감사용 분류이며 상품추천·탐지점수·건강상태 판단에 사용하지 않는다.

### 6.8.2 출력·판정

- 수취인 이름과 계좌 참조는 서버 저장 단계부터 마스킹하고 `SYNTHETIC_PROVIDER` 기준일을 함께 반환한다.
- 모의계산은 수수료·예상 차감 후 잔액과 `SIMULATION_ALLOWED|SIMULATION_BLOCKED`만 반환한다.
- 사전검증은 계좌·수취인 활성상태, 통화, 가용잔액, 건별한도, 일일 잔여한도, 고객 수취인 확인을 각각 구조화된 check로 반환한다.
- 조건 불충족은 금융 실행 실패가 아니라 `200`의 `PREVIEW_BLOCKED` 결과다. 소유 자원이 없거나 다른 고객 소유이면 `404 TRANSFER_PREVIEW_RESOURCE_NOT_FOUND`, 기준 한도가 없으면 `409 TRANSFER_PREVIEW_LIMIT_NOT_AVAILABLE`다.

### 6.8.3 데이터·불변경계

- V46의 두 snapshot은 update/delete trigger로 추가 전용이며 runtime 역할의 INSERT·UPDATE·DELETE를 회수한다.
- POST 두 API도 DB 쓰기, 이체 예약, OTP/MFA 세션, 승인 token, 외부 호출을 만들지 않는다.
- 모든 응답은 `syntheticData=true`, `externalProviderCalled=false`, `transferCreated=false`, `authorizationCreated=false`를 명시한다.
- 실제 `/transfers`, confirm, cancel은 계속 `REFERENCE_ONLY`이며 Controller와 프런트 실행 버튼을 만들지 않는다.

---

## 6.9 P1 카드 읽기 전용 상세 계약

### 6.9.1 접근·소유권·목록

- 6개 API 모두 `CARD_READ`가 필요하다. 고객 목록 경로는 path의 `customerId`와 인증 주체가 일치해야 한다.
- `{cardId}` 단독 경로는 인증 주체의 고객 ID를 서비스에 전달해 카드 소유권을 다시 조회한다. 다른 고객 소유 또는 존재하지 않는 카드는 모두 `404 CARD_NOT_FOUND`다.
- 카드번호는 `안심카드 ****-****-****-마지막4자리` 형식만 DB가 허용하고 합성 가맹점명도 고정 패턴만 저장한다.

### 6.9.2 이용내역·청구·한도

- 이용내역 기본 범위는 고정 기준일 이전 30일이며 최대 366일, `limit`은 1~100이다.
- 정렬은 `occurredAt DESC, cardTransactionId DESC`이고 cursor UUID가 가리키는 시각을 서버가 동일 카드 소유 범위에서 다시 확인한다. 잘못된 cursor는 `400 CARD_TRANSACTION_CURSOR_INVALID`다.
- 청구서는 최근 24개 불변 요약만 반환하며 파일을 만들지 않는다. 결제예정액은 최신 청구 snapshot ID와 카드 기준일 금액·예정일을 함께 반환한다.
- 한도는 합성 총한도·사용액·가용한도만 반환하며 변경 기능은 없다.

### 6.9.3 실행 금지·불변성

- V47의 카드·이용·청구 snapshot은 update/delete trigger가 변경을 거절하고 runtime 역할의 INSERT·UPDATE·DELETE를 회수한다. 카드의 `customerId`·기관·연결계좌는 복합 외래키로 같은 소유 snapshot임을 DB에서도 강제한다.
- 응답은 `syntheticData=true` 또는 합성 provider 정보를 표시하고, 외부 호출·잠금·해제·재발급·결제·출금·한도 변경을 실행하지 않는다.
- P2 잠금·해제·재발급 API는 계속 `REFERENCE_ONLY`이며 Controller나 실행 버튼을 만들지 않는다.

---

## 6.10 데모 AI 금융생활 지원 상세 계약

이 절의 6개 operation은 `IMPLEMENTED-SYNTHETIC-AI-ASSISTED`다. 공통 경로는
`/api/v1/demo/sessions/{sessionId}/customers/{customerId}/ai-financial-assistance`이며
`CUSTOMER_DEMO` capability와 현재 `X-Demo-Run-Id`가 모두 필요하다.

| Method·하위 path | 요청 핵심 | 성공 code | 안전 경계 |
|---|---|---|---|
| `POST /intent-suggestions` | `utterance` 4~500자 | `DEMO_AI_INTENT_SUGGESTED` | AI는 고정 enum 초안·근거·확인질문만 반환 |
| `PUT /intent` | `expectedVersion`, 납부·설명·도움 enum, 공유범위 0~4개 | `DEMO_AI_INTENT_DRAFT_SAVED` | 고객 수정값만 DRAFT로 저장, 낙관적 버전 검사 |
| `POST /intent/approve` | `expectedVersion`, `disclaimerAccepted=true` | `DEMO_AI_INTENT_APPROVED` | 고객 확인 전 승인 불가, 법적 효력과 자동 실행 없음 |
| `GET /intent` | 없음 | `DEMO_AI_INTENT_RETRIEVED` | 현재 세션·run의 의향만 조회 |
| `POST /change-analysis` | 없음 | `DEMO_AI_CHANGE_ANALYZED` | 총 90일 합성 기준선, 이전 60일과 최근 30일 비교·감사이력 기록 |
| `POST /plain-language` | 허용된 `featureCode` | `DEMO_AI_PLAIN_LANGUAGE_GENERATED` | 300자 이하 제한 문장과 브라우저 음성용 동일 사실만 반환 |

장기 변화 결과는 정기납부 누락, 중복송금, 거래결과 재확인, 새 수취인, 평소와 다른 시간대, 평소와 다른 금액의 6개 특징에 대해 EWMA·CUSUM 보조 점수와 기준·최근 횟수, 지속 여부, 자연어 근거를 함께 반환한다. 점수만으로 위험·질병·사기를 판정하지 않으며 화면도 “위험도” 대신 관찰된 횟수 변화로 설명한다. 음성 읽기는 서버가 음성 파일을 만들거나 외부 TTS로 전송하지 않고, 고객 브라우저의 `speechSynthesis`가 반환 문장을 `ko-KR`로 천천히 읽는다.

FastAPI 내부 계약은 `POST /internal/v1/intent-structure`, `POST /internal/v1/change-analysis`, `POST /internal/v1/plain-language`다. 32자 이상 내부 서비스 토큰, 짧은 timeout, 최대 128KiB 응답 제한을 적용하고 Spring이 request ID와 모든 안전 플래그를 재검증한다. 실패하면 의향은 보수적 기본값, 변화는 기존 기준선 규칙, 쉬운말은 고정 템플릿으로 폴백한다.

## 6.11 미구현 API를 CONTRACT로 승격하는 규칙

현재 미구현 카탈로그·백로그 45개는 이름만 보고 구현하지 않는다. 개발할 endpoint는 먼저 아래 표를 채우고 리뷰에서 `DRAFT → CONTRACT` 승인을 받은 뒤 코드를 작성한다.

| 필수 항목 | 기록 내용 |
|---|---|
| 식별 | 우선순위, 상태, 경계, Method, path |
| 접근 | 호출 주체, authority, 소유권, step-up 필요 여부 |
| 입력 | header, path, query, request DTO, validation |
| 출력 | HTTP status, 안정적 response code, typed response DTO |
| 실패 | endpoint별 오류 code와 발생 조건 |
| 일관성 | 멱등키, request hash, 낙관적 잠금, 정렬·cursor |
| 데이터 | 소유 테이블, 읽기·쓰기 set, Flyway 버전 |
| 외부경계 | port, synthetic adapter, timeout·fallback, 외부 실행 여부 |
| 검증 | 정상, 검증실패, 권한, 소유권, 동시성, 감사 테스트 |

구현 완료 후 `IMPLEMENTED`로 바꾸고 Controller·DTO·migration·통합 테스트 위치와 OpenAPI operation을 같은 PR에서 연결한다. 세부 작업 순서는 `docs/BACKEND_DEVELOPER_HANDOFF.md`를 따른다.

---

## 7. 표준 상태·오류·보안·변경관리

### 7.1 사건 상태

| 값 | 의미 |
|---|---|
| `OPEN` | 변화신호로 사건 생성 |
| `AWAITING_CONTEXT` | 고객 생활맥락 확인 대기 |
| `CONTEXT_DEFERRED` | 고객이 나중 확인 선택 |
| `CLOSED_NORMAL` | 구조적 근거와 일치한 정상 변화 종결 |
| `PENDING_BANK_REVIEW` | 행원 큐 등록 |
| `IN_BANK_REVIEW` | 행원 검토 중 |
| `FOLLOW_UP_REQUIRED` | 재연락·추가확인 필요 |
| `GUIDANCE_PLAN_APPROVED` | 안내 계획 승인 완료, 고객 전달·외부 실행은 아직 없음 |
| `CLOSED_GUIDANCE_DELIVERED` | 승인된 안내의 고객 전달을 별도 확인한 뒤 종결 |
| `CLOSED_FALSE_POSITIVE` | 데이터·규칙상 오탐 종결 |

`BLOCKED_BY_CONSENT`는 사건 상태가 아니라 연락·정보제공 시도의 결과 코드다.

### 7.2 판단 코드

| 값 | 의미 |
|---|---|
| `NEEDS_CONTEXT` | 생활맥락 확인 필요 |
| `CLOSE_AS_NORMAL_CONTEXT` | 검증된 정상 생활변화 |
| `REQUIRE_BANK_REVIEW` | 추가 설명이 필요해 행원 검토 전환 |

### 7.3 고객 응답 코드

| 값 | 화면 문구 | 기본 처리 |
|---|---|---|
| `KNOWN_AND_INTENTIONAL` | 내가 알고 한 거래예요 | 모든 강한 신호에 적격 구조적 근거가 있을 때만 정상종결 후보 |
| `LIFE_CONTEXT_CHANGED` | 생활변화가 있었어요 | 유효기간·출처가 있는 구조적 근거 확인 |
| `UNABLE_TO_CONFIRM` | 본인 거래인지 확인하기 어려워요 | 행원 검토 전환 |
| `NOT_MY_TRANSACTION` | 내가 하지 않은 거래예요 | 행원 검토와 기존 FDS·긴급 은행연락 경로 안내 |
| `DEFERRED` | 나중에 확인할게요 | `CONTEXT_DEFERRED` |
| `REQUEST_BANK_REVIEW` | 은행에 문의할게요 | 행원 검토 전환 |

`ContextResponse` 요청 DTO는 위 `responseCode`와 합성데모 전용 `demoBranchCode`만 담는다. `ContextType`은 서버가 검증한 T1 근거코드 `PAYMENT_PROVIDER_DELAY_VERIFIED`, `ACCOUNT_CONNECTION_OUTAGE_VERIFIED`, `DUPLICATE_TRANSFER_REFUNDED`, `RESULT_SCREEN_DELAY_VERIFIED`이며 응답의 `contextTypes`에만 나타난다. 근거가 없으면 빈 배열이다.

### 7.4 사유코드

| 값 | 의미 |
|---|---|
| `DUPLICATE_PAYMENT` | 시간창 내 중복 가능 결제 |
| `DUPLICATE_TRANSFER` | 동일 수취인·동일 금액·시간창 내 중복 가능 송금 |
| `MISSED_RECURRING` | 유예기간 내 예상 정기납부 미발생 |
| `REPEATED_RETRY` | 동일 금융업무의 취소·재시도 반복 |
| `UNFINISHED_TASK` | 시작한 금융업무의 미완료 증가 |
| `REPEATED_INQUIRY` | 같은 내용의 고객센터·영업점 문의 반복 |
| `POST_EXPLANATION_RECURRENCE` | 행원 설명 후 같은 질문·행동 재발 |
| `REPEATED_CONFIRMATION` | 완료된 거래·납부 결과의 단시간 반복 확인 |

과거 코드 `MISSED_RECURRING_PAYMENT`는 사용하지 않는다.

### 7.5 탐지 준비상태

| 값 | 의미 |
|---|---|
| `READY` | 기준선이 준비됨 |
| `LOW_CONFIDENCE` | 이력이 제한적이거나 보조 규칙 사용 |
| `COLD_START` | 90일 미만 이력으로 강한 결론 금지 |

---

### 7.6 오류코드

#### 7.6.1 공통 오류

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| 400 | `COMMON_INVALID_INPUT` | 필드 검증, UUID·enum 형식 오류 |
| 400 | `COMMON_MALFORMED_REQUEST` | JSON 파싱 실패 |
| 401 | `COMMON_UNAUTHORIZED` | 인증 누락·실패 |
| 403 | `COMMON_FORBIDDEN` | 역할·권한 부족 |
| 404 | `COMMON_NOT_FOUND` | 매핑되지 않은 자원 |
| 405 | `COMMON_METHOD_NOT_ALLOWED` | 지원하지 않는 HTTP method |
| 409 | `COMMON_CONFLICT` | 동시수정 또는 현재 상태 충돌 |
| 500 | `COMMON_INTERNAL_ERROR` | 처리하지 못한 서버 오류 |

#### 7.6.2 도메인 오류

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| 400 | `COMMON_INVALID_INPUT` | 멱등 명령으로 표시된 API에서 `Idempotency-Key` 누락; `errors.field=Idempotency-Key` |
| 409 | `IDEMPOTENCY_CONFLICT` | 같은 scope·키를 다른 `requestHash`에 재사용 |
| 429 | `DEMO_SESSION_RATE_LIMITED` | 비멱등 세션 생성 rate limit 또는 활성 세션 quota 초과 |
| 429 | `AUTH_LOGIN_RATE_LIMITED` | 같은 로그인 ID hash의 반복 인증 실패 한도 초과 |
| 404 | `DEMO_SESSION_NOT_FOUND` | 세션 없음 또는 capability 누락·불일치·만료 |
| 403 | `DEMO_CAPABILITY_SCOPE_FORBIDDEN` | 유효 capability의 고객/데모행원 역할범위 위반 |
| 409 | `DEMO_RUN_STALE` | Reset 이전 `demoRunId`로 상태 변경 요청 |
| 400 | `DEMO_SCENARIO_NOT_SUPPORTED` | `FIN_MGMT_AB_001` 외 시나리오 요청 |
| 404 | `SYNTHETIC_CUSTOMER_NOT_FOUND` | 세션 내 합성 고객 없음 |
| 404 | `ALERT_NOT_FOUND` | 세션 내 경보 없음 |
| 409 | `ALERT_CONTEXT_ALREADY_SUBMITTED` | 다른 멱등키로 맥락 중복 제출 |
| 409 | `INVALID_STATE_TRANSITION` | 허용되지 않은 사건 상태전이 |
| 404 | `CASE_NOT_FOUND` | 세션 내 사건 없음 |
| 409 | `CASE_VERSION_CONFLICT` | 오래된 `caseVersion`으로 수정 요청 |

세션 소유관계를 확인하지 못한 자원도 정보노출을 막기 위해 `404`로 응답한다.

`BLOCKED_BY_CONSENT`와 구조적 근거 불일치는 HTTP 실패가 아니라 정책 평가의 성공 결과다. 서버는 `200 OK`와 함께 안전한 후속 상태·결과 코드를 반환하고 감사이력을 남긴다.

---

### 7.7 상태전이 규칙

```text
OPEN
  → AWAITING_CONTEXT
      → CONTEXT_DEFERRED → AWAITING_CONTEXT
      → CLOSED_NORMAL
      → PENDING_BANK_REVIEW
          → IN_BANK_REVIEW
              → FOLLOW_UP_REQUIRED → IN_BANK_REVIEW
              → GUIDANCE_PLAN_APPROVED
                  → CLOSED_GUIDANCE_DELIVERED
              → CLOSED_FALSE_POSITIVE
```

규칙:

1. 최초 `preDecision`과 T0 경보근거 snapshot을 덮어쓰지 않는다.
2. `CLOSED_NORMAL`은 고객 응답과 서버가 확인한 구조적 근거가 일치할 때만 가능하다.
3. 강한 신호가 있는데 증거가 없거나 불일치하면 `PENDING_BANK_REVIEW`로 전환한다.
4. 신뢰연락인 미동의 상태의 연락 시도는 상태를 바꾸지 않고 `BLOCKED_BY_CONSENT` 감사이벤트만 남긴다.
5. 모든 변경 API는 상태 전후값, actor, traceId, 정책·알고리즘·스키마 버전을 감사로그에 기록한다.
6. 행원 승인 전과 승인 후 모두 P0에서는 외부 실행 이벤트를 생성하지 않는다.
7. T1 맥락 근거는 `demoRunId`에 귀속하고 T0 경보근거와 별도 필드·이벤트로 보존한다.
8. `APPROVE_GUIDANCE_PLAN`은 `GUIDANCE_PLAN_APPROVED`까지만 전이하며 전달 확인 없이 `CLOSED_GUIDANCE_DELIVERED`로 건너뛰지 않는다.

---

### 7.8 보안·개인정보·감사 규칙

- 공개 데모 API는 익명 세션·`demoRunId` 단위로 격리하고 `sessionId`만으로 조회를 허용하지 않는다.
- 세션 생성은 비멱등·rate-limited이며 고객용 capability와 데모행원용 capability를 분리한다.
- 모든 고객·계좌·거래·문서 데이터는 합성 fixture만 사용한다.
- 자유입력에는 이름, 계좌번호, 카드번호, 주민번호 형식의 값이 들어오지 않도록 프론트와 서버 양쪽에서 검사한다.
- 운영 행원 API는 `STAFF`, `CONSUMER_PROTECTION` 역할을 분리하고 중요 행위에 추가인증을 적용한다.
- 프론트가 보낸 구조적 증거를 신뢰하지 않는다. 서버 내부 조회와 결정론적 정책만 상태를 변경한다.
- 외부 LLM에는 비식별 구조화 사건요약만 전달하며 prompt·completion 원문 로그는 기본 비활성화한다.
- 공식 보호수단은 출처, 기준일, 적용조건을 함께 반환하고 실제 신청·실행 버튼을 제공하지 않는다.

---

### 7.9 프론트 연동 수용기준

- 모든 응답이 공통 envelope를 사용한다.
- 응답 헤더와 본문의 `traceId`가 일치한다.
- React에서는 `scenarioSeed`를 `string`으로 처리한다.
- 동일 Reset 후 `scenarioSeed`, `snapshotHash`, `alertId`, 버전, T0 원시 거래가 동일하고 `demoRunId`만 새로 발급된다.
- A/B 경로는 T1 맥락 fixture 외의 T0 원시 데이터가 동일하다.
- `KNOWN_AND_INTENTIONAL`만으로 강한 신호가 `CLOSED_NORMAL`이 되지 않는다.
- 미동의 연락 시도는 `BLOCKED_BY_CONSENT`이며 실제 외부 호출은 0건이다.
- `guidance-plan` 성공은 `GUIDANCE_PLAN_APPROVED`, `guidanceDelivered=false`, `externalExecutionCreated=false`이며 전달 완료로 표시하지 않는다.
- API 키나 LLM 장애가 있어도 템플릿 응답으로 전체 데모가 끝까지 동작한다.

---

### 7.10 변경관리

1. API path 또는 필드의 제거·의미 변경은 `/api/v2` 또는 명시적 마이그레이션 기간을 둔다.
2. enum 추가는 하위호환 변경이지만 프론트의 unknown fallback을 필수로 한다.
3. 이 문서, Spring DTO, `/v3/api-docs` OpenAPI 계약과 프론트 TypeScript 타입의 명칭을 함께 변경한다. Swagger UI는 조회 전용이며 `Try it out` 실행을 허용하지 않는다.
4. 구현 완료 시 엔드포인트 상태를 `CONTRACT`에서 `IMPLEMENTED`로 변경하고 테스트 링크를 기록한다.
5. 최종 제출 전 공식 보호수단 URL과 기준일을 다시 확인한다.
