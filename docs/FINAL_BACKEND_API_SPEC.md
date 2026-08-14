# ALZ's well 최종 백엔드 API 명세서

> 문서 버전: **1.0.0**  
> 상태: **통합 최종안 · API 설계 SSOT**  
> 기준일: **2026-08-14 (Asia/Seoul)**  
> 백엔드: **Java 21 · Spring Boot 3.5.16 · PostgreSQL · 모듈형 모놀리스**  
> 프론트 계약: **React 또는 Vue에서 독립적으로 사용하는 JSON REST API**  
> 런타임 네트워크: **AIR_GAPPED_DEMO · 외부 egress 차단**  
> 상위 제품 기준: `ALZS_WELL_PROJECT_SSOT.md`

이 문서는 처음 작성한 네 방향 문서, 통합 SSOT, 현재 Spring 코드와 P0 API 계약, 하나은행·신한은행·카카오뱅크·KB증권의 공식 공개 기능을 하나의 백엔드 API 지도로 통합한다.

## 문서 요약

| 항목 | 수량 |
|---|---:|
| 전체 API operation | **246개** |
| API 도메인 | **25개** |
| P0-A 기존 핵심 데모 | **12개** |
| P0-B 공개 데모 핀테크 셸 | **11개** |
| P0 구현 목표 합계 | **23개** |
| P1 제품 핵심 백로그 | **145개** |
| P2 은행·증권 확장 백로그 | **78개** |
| ALZ's well 소유 `OWNED` | **156개** |
| 외부 연동 `EXTERNAL_INTEGRATION` | **68개** |
| 참조 전용 `REFERENCE_ONLY` | **22개** |

API 개수는 `Method + Path` 한 쌍을 operation 하나로 계산한다. 같은 path라도 HTTP method가 다르면 별도 operation이다. 246개에는 실행하지 않을 은행 코어 참조 기능도 포함되며, 현재 실제 구현된 API는 `GET /api/v1/system/health` **1개뿐**이다.

| 현재 구현상태 | 수량 |
|---|---:|
| `IMPLEMENTED` | 1개 |
| 상세 계약 확정, 구현 전 | 22개 |
| 카탈로그·백로그 | 223개 |

여기서 API 246개라는 수치는 SSOT의 평가용 합성 프로필 240개 목표와 무관하다.

## 구현 결정

1. P0 23개를 먼저 구현하고 테스트한다.
2. P1과 P2는 전체 경로·소유권을 선점하되 일정에 따라 후순위로 미룬다.
3. `REFERENCE_ONLY`는 Spring Controller나 실행 버튼을 생성하지 않는다.
4. 실제 이체·주문·대출·계좌개설·지급정지·한도변경·외부 연락은 공개 데모에서 실행하지 않는다.
5. API 수가 많아도 현재 구조는 MSA가 아니라 도메인 패키지로 분리한 모듈형 모놀리스다.
6. 본 문서가 `docs/API_SPEC.md`의 P0 계약을 포함해 확장한 최종본이며, OpenAPI 3.1 파일은 이 계약에서 후속 생성한다.

## AIR_GAPPED_DEMO 네트워크 격리 결정

공개 데모의 런타임은 실제 금융회사 망분리 준수를 주장하지 않고, **외부 정보반출을 차단한 망분리 모사 환경**으로 운영한다. 금융위원회가 금융권의 생성형 AI·클라우드 활용과 자율보안·결과책임 체계로의 전환 방향을 제시했더라도, 본 공모전은 예외 적용이나 적법성을 전제로 하지 않고 더 보수적인 무외부호출 경계를 사용한다.

### 허용 통신

```text
사용자 브라우저
    → Spring Boot REST API
        → PostgreSQL
        → 내부 FastAPI AI 서비스
            → 로컬 모델·로컬 임베딩·로컬 공식문서 인덱스
```

| 발신 | 허용 대상 | 금지 대상 |
|---|---|---|
| 브라우저 | 로컬 정적 자산, 공개 Spring API | FastAPI·PostgreSQL 직접 접근, 외부 API·CDN·분석/오류수집 SDK |
| Spring Boot | PostgreSQL, 내부 FastAPI | 외부 LLM, 금융사, 마이데이터, 원격 텔레메트리 |
| FastAPI | 이미지·볼륨에 포함된 로컬 모델과 지식 인덱스 | OpenAI·Hugging Face Hub·외부 검색·원격 모델 |
| PostgreSQL | 응답 없음 | 인터넷·외부 DB |
| 배치·관리 작업 | 승인된 오프라인 반입 디렉터리 | 런타임 웹 다운로드·스크래핑 |

### 강제 규칙

1. 런타임 프로필은 `AIR_GAPPED_DEMO`이며 `externalEgressEnabled=false`, `remoteModelEnabled=false`, `syntheticProviderOnly=true`를 고정한다.
2. Docker Compose의 업무망은 `internal: true`로 만들고 Spring, FastAPI, PostgreSQL을 같은 내부망에 둔다. 런타임 서비스는 인터넷이 가능한 두 번째 네트워크에 동시에 연결하지 않는다.
3. FastAPI는 외부 공개 port를 열지 않고 Spring만 서비스명으로 호출한다. 프론트는 FastAPI를 직접 호출하지 않는다.
4. Vue/React 번들·폰트·아이콘은 로컬에서 제공하고 Content Security Policy를 최소 `default-src 'self'; connect-src 'self'`로 제한한다. 외부 CDN, Google Fonts, 지도, 분석 SDK, 원격 오류수집 SDK, 제3자 스크립트를 런타임에 사용하지 않는다.
5. Hugging Face 모델, 토크나이저, 임베딩, 공식문서는 빌드 전 통제된 절차로 내려받고 버전·라이선스·SHA-256을 고정한다. 실행 중 자동 다운로드를 금지한다.
6. 공식문서 갱신은 관리자 업로드 → allowlist 확인 → 악성 콘텐츠 검사 → 체크섬 생성 → 승인·게시의 오프라인 절차를 사용한다.
7. `EXTERNAL_INTEGRATION` 카탈로그는 설계 계약만 유지한다. P0에서는 `SYNTHETIC_PROVIDER` 외의 어댑터 bean을 기동하지 않는다.
8. 원격 오류수집, 사용량 분석, prompt tracing, 자동 업데이트 등 outbound telemetry를 비활성화한다.
9. 외부 목적지 연결 시도는 `EGRESS_ATTEMPT_BLOCKED` 감사이벤트를 남기되 URL query, prompt, 계좌·거래 원문은 기록하지 않는다.
10. 로컬 AI가 실패하거나 기동되지 않아도 Spring 템플릿 폴백으로 P0 전체 흐름을 완주한다.
11. 이 구조를 실제 금융권 보안성 심사·망분리 규정 준수 완료로 표현하지 않는다. 실도입 전 금융회사 정보보호·준법·신용정보 부서의 검토가 필요하다.

### Docker Compose 기준 예시

```yaml
services:
  backend:
    networks: [alz_internal]
    ports:
      - "127.0.0.1:8080:8080"

  ai:
    networks: [alz_internal]
    expose:
      - "8000"

  postgres:
    networks: [alz_internal]
    expose:
      - "5432"

networks:
  alz_internal:
    internal: true
```

Docker Compose에서 `internal: true`는 외부 연결이 없는 네트워크를 만든다. 서비스가 다른 외부 네트워크에도 함께 연결되면 그 경로로 인터넷에 접근할 수 있으므로 dual-homing을 금지한다.

### 검증 수용기준

- Spring 컨테이너에서 외부 DNS·HTTPS 요청 실패
- FastAPI 컨테이너에서 외부 DNS·HTTPS 요청 실패
- Spring → FastAPI, Spring → PostgreSQL 내부 통신 성공
- FastAPI·PostgreSQL host 직접 노출 0개
- 브라우저의 제3자 origin 요청 0건 및 CSP 위반 0건
- 외부 API key 0개로 P0 데모 완주
- 실행 중 모델·문서 다운로드 0건
- 외부 금융사·푸시·문자·전화·LLM 호출 0건
- AI 서비스 강제 종료 후 템플릿 폴백 성공률 100%
- 이미지에 포함된 모델·문서의 버전과 SHA-256 감사 가능

공식 참고: [금융위원회 금융분야 망분리 개선 로드맵](https://fsc.go.kr/po010102/82885), [Docker Compose 내부 네트워크](https://docs.docker.com/reference/compose-file/networks/)

---

## 빠른 목차

1. 프로젝트 기준과 도메인 경계
2. 참여 금융사 기능 근거와 반영 범위
3. 25개 도메인·246개 API 마스터 카탈로그
4. 공통 프로토콜·응답·오류 규칙
5. P0-A 12개 상세 계약
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
4. `docs/API_SPEC.md`의 이미 확정된 P0 계약
5. 실제 구현과 자동 테스트 결과
6. 아래 네 원본 방향 문서
   - `안심리듬_프로젝트_최종방향.md`
   - `Ansim_Rhythm_Project_Final_Direction.md`
   - `Ansim_Rhythm_Project_Final_Summary.md`
   - `PROJECT_DIRECTION.md`

네 원본 문서는 기능 아이디어와 의사결정 이력을 보존하는 참고자료다. 명칭, 시나리오, 상태 또는 기술구성이 SSOT와 충돌하면 원본 문서의 값을 다시 도입하지 않는다.

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
| `demo` | 익명 세션, 합성 시나리오, seed, Reset, 격리 | `DemoSession`, `ScenarioFixture`, `SyntheticProfile`, `Snapshot`, `ResetVersion` | P0 |
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
└─ SyntheticCustomer
   ├─ Account
   │  └─ Transaction
   ├─ ConsentSnapshot
   │  └─ TrustedContactPolicy
   └─ AlertIncident
      ├─ AnomalySignal
      │  └─ EvidenceSnapshot
      ├─ ContextEvent
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
- 합성데모의 모든 자원은 `DemoSession`에 귀속되며 다른 세션에서 조회하면 `404`로 응답한다.
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
3. 고정 시나리오 `MOVE_AB_001`을 적재한다.
4. 고객 금융생활 요약과 `ALERT_MOVE_001`의 변화 근거를 조회한다.
5. A 경로에서 `LIFE_CHANGE + MOVING_HOME`과 서버 보유 구조적 근거를 적용한다.
6. `postDecision=CLOSE_AS_NORMAL_CONTEXT`, `state=CLOSED_NORMAL`을 확인한다.
7. 같은 seed와 원시 snapshot으로 세션을 Reset한다.
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

달라지는 값은 고객 응답과 서버가 선택한 **후속 맥락 패키지**뿐이다.

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

#### 여정 E — 공식 근거와 Copilot

1. 정책엔진이 승인된 카탈로그에서 적용 가능한 보호수단 후보를 고른다.
2. 서버가 발행기관, URL, 시행일, 확인 기준일과 적용조건을 반환한다.
3. 템플릿 또는 선택형 LLM이 후보를 쉬운 말과 상담 초안으로 표현한다.
4. LLM은 상태, 우선순위, 연락권한, `actionCode`를 변경하지 못한다.
5. API 키 없음, timeout, 429, 5xx, malformed JSON 또는 출력검증 실패 시 템플릿으로 폴백한다.

#### 현재 P0 계약의 보완 권고

현재 `docs/API_SPEC.md`의 12개 API는 P0-A 핵심 흐름을 지원한다. 다만 SSOT가 요구하는 “12개월 금융생활 화면”을 프론트가 고정 fixture에 의존하지 않고 구성하려면 다음 읽기 계약을 P0-B로 추가하는 것이 안전하다.

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
      → CLOSED_GUIDANCE_PROVIDED
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
| `CLOSED_GUIDANCE_PROVIDED` | 상담 안내 계획 제공 후 종결 |
| `CLOSED_FALSE_POSITIVE` | 데이터·규칙상 오탐으로 종결 |

`BLOCKED_BY_CONSENT`는 `IncidentState`가 아니다. 연락·정보제공 정책 평가의 거절 결과 코드다.

#### 판단 코드 `DecisionCode`

| 값 | 사용 시점 |
|---|---|
| `NEEDS_CONTEXT` | 생활맥락 확인 전 `preDecision` |
| `CLOSE_AS_NORMAL_CONTEXT` | 검증된 정상 생활변화의 `postDecision` |
| `REQUIRE_BANK_REVIEW` | 추가 설명이 필요한 `postDecision` |

#### 고객 응답 `ContextResponseCode`

| 값 | 의미 | 기본 처리 |
|---|---|---|
| `KNOWN_TRANSACTION` | 내가 알고 한 거래 | 강한 신호는 구조적 근거 추가 확인 |
| `LIFE_CHANGE` | 생활변화가 있었음 | 구조적 근거 정합성 확인 |
| `UNABLE_TO_CONFIRM` | 본인 거래인지 확인하기 어려움 | 행원 검토 전환 |
| `DEFER` | 나중에 확인 | `CONTEXT_DEFERRED` |
| `CONTACT_BANK` | 은행 문의 선택 | 행원 검토 전환 |

#### 사유코드 `ReasonCode`

| 값 | 의미 |
|---|---|
| `NEW_PAYEE` | 기준선에 없던 신규 수취인 |
| `REPEATED_TRANSFER` | 동일·유사 수취인·금액의 반복송금 |
| `DUPLICATE_PAYMENT` | 시간창 내 중복 가능 결제 |
| `MISSED_RECURRING` | 유예기간 내 예상 정기납부 미발생 |
| `CASH_WITHDRAWAL_TREND` | 현금인출 금액·빈도의 지속 증가 |
| `UNUSUAL_AMOUNT` | 개인 기준선 대비 금액 변화 |
| `UNUSUAL_TIME` | 개인 기준선 대비 이용시간 변화 |
| `TREND_SHIFT` | 일정 기간 지속된 수준·추세 변화 |

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
| `APPROVE_GUIDANCE_PLAN` | `IN_BANK_REVIEW` | `CLOSED_GUIDANCE_PROVIDED` |

#### 추가 공통 enum

| enum | 값 또는 원칙 |
|---|---|
| `ReviewPriority` | 행원 업무순서용 `HIGH`, `MEDIUM`, `LOW`; 고객 위험도 아님 |
| `ExecutionType` | P0 보호수단은 항상 `GUIDANCE_ONLY` |
| `DataMode` | 공개 데모는 항상 `SYNTHETIC_ONLY` |
| `ActorType` | `SYSTEM`, `CUSTOMER`, `STAFF`, `DEMO_STAFF`, `POLICY_ENGINE` 등 |
| `TransactionStatus` | 최소 `PENDING`, `POSTED`, `CANCELED`, `REFUNDED`를 구분 |

새 enum 값은 하위호환 추가로 취급하되 프론트는 반드시 unknown fallback을 가진다.

---

### 1.6 P0·P1·P2 경계

#### P0 — 공모전 핵심 흐름

- 무로그인 익명 데모 세션과 멱등 Reset
- 12개월 완전 합성 거래와 `MOVE_AB_001` 고정 fixture
- 합성 고객·계좌·거래·정기납부의 조회용 read model
- 중앙값·MAD 기준선과 cold-start 처리
- 신규 수취인·반복송금·정기납부 누락 탐지
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
| P0 데모 소재 | 병원비 180만 원 | 이사·부동산 `MOVE_AB_001` |
| A/B 대상 | 서로 다른 두 고객 | 같은 익명 세션을 Reset한 동일 사건 |
| A/B 차이 | B에만 추가 거래·신호 | 맥락 패키지만 변경 |
| 행원 대기상태 | `BANK_REVIEW` | `PENDING_BANK_REVIEW` |
| 동의 차단 | 사건 상태 또는 HTTP 오류 | 정책결과 `BLOCKED_BY_CONSENT` |
| 정기납부 코드 | `MISSED_RECURRING_PAYMENT` | `MISSED_RECURRING` |
| 현금인출 코드 | `ATM_WITHDRAWAL_TREND` | `CASH_WITHDRAWAL_TREND` |
| 금액변화 코드 | `AMOUNT_DEVIATION` | `UNUSUAL_AMOUNT` |
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
| 보호계획 승인 | 실제 지급정지·차단 승인 | 상담 `guidancePlan` 승인, 외부 실행 없음 |
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
10. 모든 변경 명령은 `Idempotency-Key`를 지원하고 직원 사건 변경은 `caseVersion`으로 동시수정을 방지한다.
11. actor는 요청 본문이 아니라 인증·세션 주체에서 결정한다.
12. 세션 소유관계를 확인할 수 없는 자원은 정보노출을 막기 위해 `404`로 응답한다.
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
> 상세 P0 계약: 본 문서 5~7장에 통합 (기존 docs/API_SPEC.md 확장)

이 문서는 ALZ's well의 핵심 금융생활 변화 확인 서비스와 은행·카드·증권 웹서비스의 공통 기능을 하나의 백엔드 API 지도에 정리한다. 기능 개수가 제품 범위를 자동으로 넓히는 것은 아니다. 우선 전체 지도를 고정한 뒤 P0를 먼저 구현하고, 일정에 따라 P1과 P2를 뒤로 미룬다.

은행·증권 기능은 특정 회사의 비공개 API를 복제한 것이 아니라 일반적인 기능 범주를 ALZ's well의 공개 REST 계약으로 재구성한 것이다.

---

### 3.1 총괄 집계

| 구분 | 수량 |
|---|---:|
| 전체 | **246** |
| P0-A 기존 핵심 데모 | **12** |
| P0-B 공개 데모 뱅킹 셸 보강 | **11** |
| P1 제품 핵심 | **145** |
| P2 은행·증권 확장 | **78** |
| OWNED | **156** |
| EXTERNAL_INTEGRATION | **68** |
| REFERENCE_ONLY | **22** |

현재 실제 구현된 API는 GET /api/v1/system/health 하나다. 나머지는 계약 또는 백로그이며, 카탈로그 등록과 구현 완료를 혼동하지 않는다.

#### 우선순위 정의

| 우선순위 | 의미 |
|---|---|
| P0-A | 기존 API_SPEC.md에 확정된 공모전 핵심 데모 12개 |
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

#### 3.3.1 시스템·데모 — 16개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P0-A | GET | /api/v1/system/health | 상태와 데모 안전 가드레일 확인 | OWNED |
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

P0-B 읽기 API는 반드시 sessionId 소유권과 만료를 먼저 검증한다. 같은 customerId나 accountId라도 다른 익명 세션에서 조회할 수 없어야 한다.

#### 3.3.2 인증·세션·권한 — 9개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P1 | POST | /api/v1/auth/login | 기업 SSO 또는 인증 공급자 로그인 | EXTERNAL_INTEGRATION |
| P1 | POST | /api/v1/auth/token/refresh | 애플리케이션 토큰 갱신 | OWNED |
| P1 | POST | /api/v1/auth/logout | 현재 세션 종료 | OWNED |
| P1 | GET | /api/v1/auth/me | 현재 사용자·직원 정보 | OWNED |
| P1 | GET | /api/v1/auth/me/permissions | 역할·세부 권한 조회 | OWNED |
| P2 | GET | /api/v1/auth/sessions | 로그인 세션 목록 | OWNED |
| P2 | DELETE | /api/v1/auth/sessions/{authSessionId} | 선택한 로그인 세션 폐기 | OWNED |
| P2 | POST | /api/v1/auth/step-up/challenges | 중요화면 추가인증 시작 | EXTERNAL_INTEGRATION |
| P2 | POST | /api/v1/auth/step-up/challenges/{challengeId}/verify | 추가인증 검증 | EXTERNAL_INTEGRATION |

KYC·실명확인 API는 포함하지 않는다. 해당 절차는 금융회사 기존 체계의 책임이다.

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
| P1 | PATCH | /api/v1/accounts/{accountId}/display-settings | 계좌 별칭·노출 순서 변경 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/account-groups | 고객 지정 계좌 그룹 | OWNED |

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
| P1 | PUT | /api/v1/transactions/{transactionId}/category | 고객 지정 범주 보정 | OWNED |
| P1 | PUT | /api/v1/transactions/{transactionId}/note | 금융 기억노트 작성 | OWNED |
| P2 | POST | /api/v1/customers/{customerId}/transaction-export-requests | 거래내역 파일 생성 요청 | OWNED |

#### 3.3.8 정기납부·구독·청구 — 8개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P1 | GET | /api/v1/customers/{customerId}/recurring-payments | 정기납부·구독 목록 | OWNED |
| P1 | GET | /api/v1/recurring-payments/{recurringPaymentId} | 추정 주기·금액·상태 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/recurring-payments/calendar | 예상 납부 일정 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/recurring-payments/missed | 미발생 정기납부 후보 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/recurring-payments/duplicates | 중복 구독·납부 후보 | OWNED |
| P1 | GET | /api/v1/recurring-payments/{recurringPaymentId}/occurrences | 과거·예상 발생 내역 | OWNED |
| P1 | PUT | /api/v1/recurring-payments/{recurringPaymentId}/reminder-settings | 납부 확인 알림 설정 | OWNED |
| P2 | POST | /api/v1/recurring-payments/{recurringPaymentId}/cancellation-guidance | 해지 방법 안내만 생성 | REFERENCE_ONLY |

#### 3.3.9 이체·지급 — 10개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P1 | GET | /api/v1/customers/{customerId}/beneficiaries | 마스킹된 수취인 목록 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/customers/{customerId}/transfer-limits | 금융회사 이체한도 조회 | EXTERNAL_INTEGRATION |
| P1 | POST | /api/v1/transfer-simulations | 합성 이체 결과·수수료 모의계산 | OWNED |
| P1 | POST | /api/v1/transfer-validations | 형식·정책 사전검사, 실행 없음 | OWNED |
| P2 | GET | /api/v1/customers/{customerId}/transfer-templates | 고객 저장 이체 양식 | OWNED |
| P2 | POST | /api/v1/customers/{customerId}/transfer-templates | 이체 양식 저장 | OWNED |
| P2 | DELETE | /api/v1/customers/{customerId}/transfer-templates/{templateId} | 이체 양식 삭제 | OWNED |
| P2 | POST | /api/v1/transfers | 실제 이체 접수 기능 참조 | REFERENCE_ONLY |
| P2 | POST | /api/v1/transfers/{transferId}/confirm | 실제 이체 승인 기능 참조 | REFERENCE_ONLY |
| P2 | POST | /api/v1/transfers/{transferId}/cancel | 이체 취소 기능 참조 | REFERENCE_ONLY |

마지막 3개는 공개 데모와 ALZ 핵심 백엔드에서 구현하지 않는다.

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
| P1 | DELETE | /api/v1/customers/{customerId}/trusted-contacts/{contactId} | 지정 철회 | OWNED |
| P2 | POST | /api/v1/customers/{customerId}/trusted-contacts/{contactId}/contact-attempts | 실제 외부 연락 기능 참조 | REFERENCE_ONLY |

마지막 API는 공개 데모에서 호출하지 않는다. 데모에서는 정책 평가 결과 BLOCKED_BY_CONSENT만 감사로그에 남긴다.

#### 3.3.16 기준선·신호·경보·생활맥락 — 18개

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
| P0-A | GET | /api/v1/demo/sessions/{sessionId}/alerts/{alertId}/audit | 기존 데모 판단·동의 감사이력 | OWNED |

#### 3.3.17 행원 사건·코파일럿·후속관리 — 16개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P1 | GET | /api/v1/staff/cases | 운영 행원 사건큐 | OWNED |
| P1 | GET | /api/v1/staff/cases/{caseId} | 운영 사건 상세 | OWNED |
| P1 | PUT | /api/v1/staff/cases/{caseId}/assignment | 담당자·팀 배정 | OWNED |
| P1 | GET | /api/v1/staff/cases/{caseId}/timeline | 사건·신호·맥락 타임라인 | OWNED |
| P1 | GET | /api/v1/staff/cases/{caseId}/evidence | 근거 거래·문서 묶음 | OWNED |
| P1 | POST | /api/v1/staff/cases/{caseId}/reviews | 검토 상태전이 | OWNED |
| P1 | POST | /api/v1/staff/cases/{caseId}/notes | 행원 내부 메모 | OWNED |
| P1 | POST | /api/v1/staff/cases/{caseId}/copilot-drafts | 질문·상담기록 초안 생성 | OWNED |
| P1 | POST | /api/v1/staff/cases/{caseId}/follow-ups | 재확인 일정만 등록 | OWNED |
| P1 | PATCH | /api/v1/staff/follow-ups/{followUpId} | 후속 일정·결과 갱신 | OWNED |
| P1 | POST | /api/v1/staff/cases/{caseId}/guidance-plans | 안내계획 승인, 실제 조치 아님 | OWNED |
| P2 | POST | /api/v1/staff/cases/{caseId}/overrides | 정책 결과에 대한 사유 있는 직원 재검토 | OWNED |
| P0-A | GET | /api/v1/demo/sessions/{sessionId}/staff/cases | 기존 데모 행원 사건큐 | OWNED |
| P0-A | GET | /api/v1/demo/sessions/{sessionId}/cases/{caseId} | 기존 데모 사건 상세·초안 | OWNED |
| P0-A | POST | /api/v1/demo/sessions/{sessionId}/cases/{caseId}/review | 기존 데모 검토 상태전이 | OWNED |
| P0-A | POST | /api/v1/demo/sessions/{sessionId}/cases/{caseId}/guidance-plan | 기존 데모 안내계획 승인 | OWNED |

follow-ups는 일정과 업무상태만 관리한다. 전화·문자·푸시 발송 기능이 아니다.

#### 3.3.18 고객별 행원 접근권한 — 6개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P1 | GET | /api/v1/customers/{customerId}/staff-access-grants | 고객 데이터에 접근 가능한 행원 권한 목록 | OWNED |
| P1 | POST | /api/v1/customers/{customerId}/staff-access-grants | 목적·범위·만료를 지정한 권한 생성과 은행 IAM 연결 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/customers/{customerId}/staff-access-grants/{grantId} | 단일 접근권한 상세 | OWNED |
| P1 | POST | /api/v1/customers/{customerId}/staff-access-grants/{grantId}/revoke | 접근권한 철회 | OWNED |
| P1 | POST | /api/v1/staff-access-policy/evaluations | 행원·고객·목적·범위별 접근 가능성 평가 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/staff-access-grants/{grantId}/audit | 생성·사용·만료·철회 감사이력 | OWNED |

모든 grant에는 grantId, customerId, staffSubjectId, purpose, scopes, grantedAt, expiresAt, revokedAt을 저장한다. purpose와 scopes가 요청 자원에 맞지 않거나 expiresAt이 지났거나 revokedAt이 존재하면 접근을 거절한다. POST 생성의 은행 IAM 주체 확인은 외부 연동이지만 권한 목적·범위·만료·감사 상태는 ALZ's well이 보존한다.

#### 3.3.19 공식 근거·지식 카탈로그 — 8개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P1 | GET | /api/v1/knowledge/documents | 승인된 공식 문서 목록 | OWNED |
| P1 | GET | /api/v1/knowledge/documents/{documentId} | 출처·시행일·체크섬 | OWNED |
| P1 | GET | /api/v1/knowledge/documents/{documentId}/versions | 문서 버전 이력 | OWNED |
| P1 | POST | /api/v1/knowledge/search | 권한·효력기간을 적용한 검색 | OWNED |
| P1 | GET | /api/v1/knowledge/passages/{passageId} | 인용 가능한 조항·페이지 | OWNED |
| P1 | GET | /api/v1/guidance-candidates | 정책이 고른 보호수단 후보 | OWNED |
| P2 | POST | /api/v1/admin/knowledge/documents | 공식 자료 등록 | OWNED |
| P2 | POST | /api/v1/admin/knowledge/documents/{documentId}/publish | 검수 완료 버전 게시 | OWNED |

#### 3.3.20 인앱 알림·고객지원 — 10개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P1 | GET | /api/v1/customers/{customerId}/inbox | 서비스 내부 알림함 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/inbox/{messageId} | 인앱 알림 상세 | OWNED |
| P1 | POST | /api/v1/customers/{customerId}/inbox/{messageId}/read | 읽음 처리 | OWNED |
| P1 | GET | /api/v1/customers/{customerId}/notification-preferences | 채널별 알림 설정 | OWNED |
| P1 | PUT | /api/v1/customers/{customerId}/notification-preferences | 알림 설정 변경 | OWNED |
| P1 | POST | /api/v1/notification-previews | 외부 발송 없는 문구 미리보기 | OWNED |
| P2 | GET | /api/v1/support/faqs | 자주 묻는 질문 | OWNED |
| P2 | GET | /api/v1/support/notices | 금융사 공지 조회 | EXTERNAL_INTEGRATION |
| P2 | POST | /api/v1/support/inquiries | 실제 문의 접수 기능 참조 | REFERENCE_ONLY |
| P2 | GET | /api/v1/support/inquiries/{inquiryId} | 실제 문의 진행상태 참조 | REFERENCE_ONLY |

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
| P1 | GET | /api/v1/admin/feature-flags | 환경별 기능 플래그 | OWNED |
| P2 | PUT | /api/v1/admin/feature-flags/{flagKey} | 승인된 기능 플래그 변경 | OWNED |

#### 3.3.23 운영·배치·연동 상태 — 8개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P2 | GET | /api/v1/internal/ops/jobs | 기준선·탐지·정리 작업 목록 | OWNED |
| P2 | GET | /api/v1/internal/ops/jobs/{jobId} | 작업 실행상태·오류 | OWNED |
| P2 | POST | /api/v1/internal/ops/jobs/{jobId}/retry | 실패 작업 안전 재시도 | OWNED |
| P1 | GET | /api/v1/internal/ops/audit-integrity | 감사 체인·누락 검사 | OWNED |
| P1 | GET | /api/v1/internal/integrations/providers | 외부 공급자 상태 목록 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/internal/integrations/providers/{providerId}/health | 공급자 연결상태 | EXTERNAL_INTEGRATION |
| P2 | POST | /api/v1/internal/integrations/providers/{providerId}/sync-runs | 운영 동기화 작업 요청 | EXTERNAL_INTEGRATION |
| P2 | GET | /api/v1/internal/integrations/sync-runs/{syncRunId} | 동기화 결과·재시도 여부 | EXTERNAL_INTEGRATION |

#### 3.3.24 외환·해외송금 — 8개

| 우선순위 | Method | Path | 용도 | 경계 |
|---|---|---|---|---|
| P1 | GET | /api/v1/fx/rates | 금융사 제공 환율표 | EXTERNAL_INTEGRATION |
| P1 | GET | /api/v1/fx/rates/{currency} | 통화별 환율 상세 | EXTERNAL_INTEGRATION |
| P2 | GET | /api/v1/customers/{customerId}/foreign-currency-accounts | 외화계좌 현황 | EXTERNAL_INTEGRATION |
| P2 | POST | /api/v1/fx/exchange-simulations | 외화 환전 모의계산 | OWNED |
| P2 | GET | /api/v1/customers/{customerId}/overseas-remittance-history | 해외송금 이력 조회 | EXTERNAL_INTEGRATION |
| P2 | POST | /api/v1/fx/exchanges | 실제 환전 기능 참조 | REFERENCE_ONLY |
| P2 | POST | /api/v1/overseas-remittances | 실제 해외송금 접수 참조 | REFERENCE_ONLY |
| P2 | POST | /api/v1/overseas-remittances/{remittanceId}/confirm | 실제 해외송금 승인 참조 | REFERENCE_ONLY |

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

1. 기존 P0-A 12개를 API_SPEC.md 계약 그대로 구현한다.
2. P0-B 11개는 공개 데모 안전설정 5개와 sessionId가 포함된 뱅킹 셸 읽기 API 6개로 한정한다.
3. P0 총 23개를 공모전 MVP의 구현 경계로 삼는다.
4. P0-B 금융 데이터는 실제 금융사 대신 세션별 SYNTHETIC_PROVIDER에서만 제공한다.
5. P1은 고객 화면, 행원 업무, 공식 근거, 감사 추적 순으로 확장한다.
6. P2는 시간이 부족하면 전부 문서 상태로 유지한다.
7. REFERENCE_ONLY 22개는 구현 완료 수치에 포함하지 않으며, 공개 프론트 라우트와 서버 컨트롤러를 생성하지 않는다.

#### 권장 구현 웨이브

| 웨이브 | 범위 | 누적 API |
|---|---|---:|
| Wave 1 | P0-A 핵심 A/B 데모 | 12 |
| Wave 2 | P0-B 세션 격리 뱅킹 셸 | 23 |
| Wave 3 | P1 행원·감사·접근성·읽기 전용 금융기능 | 168 |
| Wave 4 | P2 제품 확장 및 외부 연동 계약 | 246 |

발표에서는 “246개 API 카탈로그를 설계했고 공모전 구현 목표는 안전한 P0 23개”라고 표현한다. 246개 전체가 구현됐다고 주장하지 않는다.

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
| `Idempotency-Key` | `P0-CONTRACT` 변경 API에서 필수 | 8~64자의 고유 키; 동일 세션·경로·키 재요청은 최초 결과를 재사용 |
| `Authorization: Bearer {token}` | PoC/운영 행원 API | 공개 합성데모에서는 생략; 운영에서는 RBAC 적용 |

모든 응답은 `X-Trace-Id` 헤더를 반환하며 본문의 `traceId`와 같아야 한다.

현재 구현의 CORS 허용 헤더에는 `Idempotency-Key`가 아직 포함되지 않았다. P0 변경 API 구현과 동시에 CORS 설정에도 이 헤더를 추가한다.

현재 Spring Security에서 `/api/v1/demo/**`는 합성데모를 위해 공개되어 있으며 그 하위 행원 API도 예외가 아니다. JWT와 역할 기반 접근제어는 아직 구현되지 않았고, PoC/운영 전환 시 `STAFF`, `CONSUMER_PROTECTION` 권한으로 보호한다.

### 4.3 자료형

| 항목 | 규칙 | 예시 |
|---|---|---|
| ID | UUID 또는 고정 합성 ID 문자열 | `4e85...`, `ALERT_MOVE_001` |
| 시간 | ISO-8601, UTC 또는 명시적 offset | `2026-08-14T01:00:00Z` |
| 금액 | 정밀도 손실을 막기 위한 10진 문자열과 통화코드 | `"18500000"`, `currency=KRW` |
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

---

### 5.2 익명 데모 세션 API

#### 5.2.1 데모 세션 생성

`P0-CONTRACT`

```http
POST /api/v1/demo/sessions
Idempotency-Key: demo-start-0001
```

요청 본문은 없다.

##### 성공 응답 `201 Created`

```json
{
  "success": true,
  "status": 201,
  "code": "DEMO_SESSION_CREATED",
  "message": "익명 데모 세션을 생성했습니다.",
  "data": {
    "sessionId": "4e85d88f-16d3-4aa7-a0a7-d309d7d223d3",
    "scenarioSeed": "842039285123456789",
    "expiresAt": "2026-08-14T03:00:00Z",
    "resetVersion": 0,
    "dataMode": "SYNTHETIC_ONLY"
  },
  "errors": [],
  "timestamp": "2026-08-14T01:00:00Z",
  "traceId": "frontend-trace-0001"
}
```

#### 5.2.2 데모 Reset

`P0-CONTRACT`

```http
POST /api/v1/demo/sessions/{sessionId}/reset
Idempotency-Key: reset-a-to-b-0001
```

같은 `scenarioSeed`, 원시 거래 snapshot, `alertId`, 알고리즘·정책 버전을 복원한다. 같은 `Idempotency-Key`의 재요청은 감사이벤트와 `resetVersion`을 중복 증가시키지 않는다.

##### 성공 응답 `200 OK`

```json
{
  "success": true,
  "status": 200,
  "code": "DEMO_SESSION_RESET",
  "message": "동일한 seed와 원시 snapshot으로 초기화했습니다.",
  "data": {
    "sessionId": "4e85d88f-16d3-4aa7-a0a7-d309d7d223d3",
    "scenarioSeed": "842039285123456789",
    "scenarioId": "MOVE_AB_001",
    "snapshotHash": "sha256:07d4c6...",
    "alertId": "ALERT_MOVE_001",
    "resetVersion": 1,
    "restoredAt": "2026-08-14T01:05:00Z"
  },
  "errors": [],
  "timestamp": "2026-08-14T01:05:00Z",
  "traceId": "frontend-trace-0002"
}
```

시나리오 적재 전에 Reset하면 `scenarioId`, `snapshotHash`, `alertId`는 `null`이며 세션 메타데이터만 초기화한다.

#### 5.2.3 합성 시나리오 적재

`P0-CONTRACT`

```http
POST /api/v1/demo/sessions/{sessionId}/scenarios/{scenarioId}/ingest
Idempotency-Key: ingest-move-0001
```

P0에서 허용하는 `scenarioId`는 `MOVE_AB_001` 하나다. 요청 본문은 없다.

##### 성공 응답 `201 Created`

```json
{
  "success": true,
  "status": 201,
  "code": "DEMO_SCENARIO_INGESTED",
  "message": "고정 합성 시나리오를 적재했습니다.",
  "data": {
    "scenarioId": "MOVE_AB_001",
    "customerId": "SYN_CUSTOMER_MOVE_001",
    "alertId": "ALERT_MOVE_001",
    "caseId": "CASE_MOVE_001",
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
      "NEW_PAYEE",
      "REPEATED_TRANSFER",
      "MISSED_RECURRING"
    ],
    "preDecision": "NEEDS_CONTEXT",
    "state": "AWAITING_CONTEXT",
    "algorithmVersion": "baseline-mad-v1.0.0",
    "policyVersion": "context-policy-v1.0.0"
  },
  "errors": [],
  "timestamp": "2026-08-14T01:01:00Z",
  "traceId": "frontend-trace-0003"
}
```

---

### 5.3 고객 변화 알림 API

#### 5.3.1 고객 알림 목록

`P0-CONTRACT`

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
    "customerId": "SYN_CUSTOMER_MOVE_001",
    "syntheticData": true,
    "items": [
      {
        "alertId": "ALERT_MOVE_001",
        "state": "AWAITING_CONTEXT",
        "title": "평소와 다른 송금과 정기납부 변화가 있어요",
        "summary": "신규 수취인 1명, 반복송금 2회, 정기납부 누락 1건을 확인해 주세요.",
        "reasonCodes": ["NEW_PAYEE", "REPEATED_TRANSFER", "MISSED_RECURRING"],
        "observedAt": "2026-07-31T23:59:59Z",
        "algorithmVersion": "baseline-mad-v1.0.0"
      }
    ]
  },
  "errors": [],
  "timestamp": "2026-08-14T01:02:00Z",
  "traceId": "frontend-trace-0004"
}
```

#### 5.3.2 알림 상세

`P0-CONTRACT`

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
    "alertId": "ALERT_MOVE_001",
    "customerId": "SYN_CUSTOMER_MOVE_001",
    "syntheticData": true,
    "state": "AWAITING_CONTEXT",
    "preDecision": "NEEDS_CONTEXT",
    "postDecision": null,
    "reasonCodes": ["NEW_PAYEE", "REPEATED_TRANSFER", "MISSED_RECURRING"],
    "signals": [
      {
        "signalId": "SIG_NEW_PAYEE_001",
        "reasonCode": "NEW_PAYEE",
        "readiness": "READY",
        "baseline": {
          "value": 0,
          "unit": "COUNT",
          "window": "9M"
        },
        "current": {
          "value": 1,
          "unit": "COUNT",
          "window": "3M"
        },
        "factExplanation": "최근 관찰기간에 처음 등장한 수취인입니다.",
        "evidenceTransactionIds": ["TX_MOVE_101", "TX_MOVE_102"],
        "calculatedAt": "2026-08-01T00:00:00Z"
      }
    ],
    "evidenceTransactions": [
      {
        "transactionId": "TX_MOVE_101",
        "institutionCode": "SYN_BANK_001",
        "accountType": "DEMAND_DEPOSIT",
        "transactionType": "TRANSFER_OUT",
        "occurredAt": "2026-07-10T01:20:00Z",
        "postedAt": "2026-07-10T01:20:03Z",
        "amount": "10000000",
        "currency": "KRW",
        "counterpartyDisplayName": "합성수취인 A",
        "channel": "MOBILE_BANKING",
        "status": "POSTED"
      }
    ],
    "consent": {
      "trustedContactGranted": false,
      "minimumInformationPreview": "확인이 필요한 금융활동이 있습니다. 고객에게 연락하거나 은행 상담을 도와주세요."
    },
    "algorithmVersion": "baseline-mad-v1.0.0",
    "policyVersion": "context-policy-v1.0.0"
  },
  "errors": [],
  "timestamp": "2026-08-14T01:03:00Z",
  "traceId": "frontend-trace-0005"
}
```

`signals`에는 점수만 보내지 않고 평소값, 현재값, 비교기간, 사실설명, 불변 근거 ID를 함께 제공한다.

#### 5.3.3 생활맥락 응답

`P0-CONTRACT`

```http
POST /api/v1/demo/sessions/{sessionId}/alerts/{alertId}/context
Idempotency-Key: context-a-0001
Content-Type: application/json
```

##### A 경로 요청

```json
{
  "responseCode": "LIFE_CHANGE",
  "contextCode": "MOVING_HOME",
  "demoEvidenceFixture": "MOVE_A_VERIFIED"
}
```

##### B 경로 요청

```json
{
  "responseCode": "UNABLE_TO_CONFIRM",
  "contextCode": null,
  "demoEvidenceFixture": "MOVE_B_UNVERIFIED"
}
```

`demoEvidenceFixture`는 합성데모 전용 선택자다. 클라이언트가 계약서나 주소변경 증거를 직접 보내는 필드가 아니며, 서버는 이 코드에 대응하는 고정 fixture를 조회해 정합성을 판단한다. 운영 API에서는 이 필드를 제거하고 승인된 내부 데이터 조회 결과만 사용한다.

##### A 경로 성공 응답 `200 OK`

```json
{
  "success": true,
  "status": 200,
  "code": "ALERT_CONTEXT_APPLIED",
  "message": "생활맥락을 반영해 변화를 다시 확인했습니다.",
  "data": {
    "contextEventId": "CTX_MOVE_A_001",
    "alertId": "ALERT_MOVE_001",
    "responseCode": "LIFE_CHANGE",
    "contextCode": "MOVING_HOME",
    "structuralEvidenceMatched": true,
    "matchedEvidenceIds": ["LEASE_SYN_001", "ADDRESS_CHANGE_SYN_001"],
    "preDecision": "NEEDS_CONTEXT",
    "postDecision": "CLOSE_AS_NORMAL_CONTEXT",
    "previousState": "AWAITING_CONTEXT",
    "currentState": "CLOSED_NORMAL",
    "trustedContactPolicy": {
      "granted": false,
      "attempted": false,
      "resultCode": null
    },
    "nextAction": {
      "type": "SHOW_CHECKLIST",
      "actionCode": "RECHECK_RECURRING_PAYMENT"
    },
    "policyVersion": "context-policy-v1.0.0"
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
    "contextEventId": "CTX_MOVE_B_001",
    "alertId": "ALERT_MOVE_001",
    "caseId": "CASE_MOVE_001",
    "responseCode": "UNABLE_TO_CONFIRM",
    "contextCode": null,
    "structuralEvidenceMatched": false,
    "matchedEvidenceIds": [],
    "preDecision": "NEEDS_CONTEXT",
    "postDecision": "REQUIRE_BANK_REVIEW",
    "previousState": "AWAITING_CONTEXT",
    "currentState": "PENDING_BANK_REVIEW",
    "trustedContactPolicy": {
      "granted": false,
      "attempted": true,
      "resultCode": "BLOCKED_BY_CONSENT"
    },
    "nextAction": {
      "type": "OPEN_BANK_REVIEW",
      "actionCode": "REVIEW_CASE"
    },
    "policyVersion": "context-policy-v1.0.0"
  },
  "errors": [],
  "timestamp": "2026-08-14T01:04:30Z",
  "traceId": "frontend-trace-0007"
}
```

`trustedContactPolicy.attempted=true`는 외부 연락을 시도했다는 뜻이 아니라 연락 정책 게이트를 평가했다는 뜻이다. 미동의 상태에서는 외부 메시지·전화 요청을 만들지 않는다.

고객의 `KNOWN_TRANSACTION` 응답만으로 강한 신호를 자동 해제하지 않는다. 구조적 근거가 없거나 불일치하면 `PENDING_BANK_REVIEW`로 전환한다.

#### 5.3.4 알림 감사이력

`P0-CONTRACT`

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
        "eventType": "CONSENT_ACTION_BLOCKED",
        "actorType": "SYSTEM",
        "fromState": "AWAITING_CONTEXT",
        "toState": "PENDING_BANK_REVIEW",
        "resultCode": "BLOCKED_BY_CONSENT",
        "evidenceIds": ["CONSENT_SNAPSHOT_001"],
        "algorithmVersion": "baseline-mad-v1.0.0",
        "policyVersion": "context-policy-v1.0.0",
        "schemaVersion": "1.0",
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

`P0-CONTRACT`

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
        "caseId": "CASE_MOVE_001",
        "alertId": "ALERT_MOVE_001",
        "customerId": "SYN_CUSTOMER_MOVE_001",
        "state": "PENDING_BANK_REVIEW",
        "reviewPriority": "HIGH",
        "reasonCodes": ["NEW_PAYEE", "REPEATED_TRANSFER", "MISSED_RECURRING"],
        "customerResponseCode": "UNABLE_TO_CONFIRM",
        "summary": "본인 거래 확인이 어렵고 정상 생활맥락의 구조적 근거가 없습니다.",
        "trustedContactAllowed": false,
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

`P0-CONTRACT`

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
    "caseId": "CASE_MOVE_001",
    "caseVersion": 1,
    "sessionResetVersion": 1,
    "state": "PENDING_BANK_REVIEW",
    "reviewPriority": "HIGH",
    "alert": {
      "alertId": "ALERT_MOVE_001",
      "preDecision": "NEEDS_CONTEXT",
      "postDecision": "REQUIRE_BANK_REVIEW",
      "reasonCodes": ["NEW_PAYEE", "REPEATED_TRANSFER", "MISSED_RECURRING"],
      "algorithmVersion": "baseline-mad-v1.0.0",
      "policyVersion": "context-policy-v1.0.0"
    },
    "customerContext": {
      "responseCode": "UNABLE_TO_CONFIRM",
      "confirmedItems": [],
      "unconfirmedItems": ["신규 수취인", "반복송금 2회", "정기납부 누락 1건"]
    },
    "timeline": [
      {
        "type": "ALERT_CREATED",
        "title": "변화 알림 생성",
        "occurredAt": "2026-08-01T00:00:00Z",
        "evidenceIds": ["SIG_NEW_PAYEE_001", "SIG_REPEAT_001", "SIG_MISSED_001"]
      }
    ],
    "suggestedQuestions": [
      {
        "questionId": "Q_MOVE_001",
        "text": "최근 두 차례 송금의 수취인과 목적을 기억하시나요?",
        "basisReasonCodes": ["NEW_PAYEE", "REPEATED_TRANSFER"]
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
      "checklist": ["수취인 확인", "송금 목적 확인", "정기납부 변경 여부 확인"],
      "generatedBy": "TEMPLATE",
      "fallbackUsed": true
    },
    "trustedContactPolicy": {
      "granted": false,
      "contactActionEnabled": false,
      "disabledReasonCode": "BLOCKED_BY_CONSENT"
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
      }
    ]
  },
  "errors": [],
  "timestamp": "2026-08-14T01:07:00Z",
  "traceId": "staff-trace-0002"
}
```

`protectionCandidates`는 공식 조건과 상담 경로만 제공한다. `executionType`은 P0에서 항상 `GUIDANCE_ONLY`다.

#### 5.4.3 행원 검토 상태전이

`P0-CONTRACT`

```http
POST /api/v1/demo/sessions/{sessionId}/cases/{caseId}/review
Idempotency-Key: case-review-0001
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

##### 성공 응답 `200 OK`

```json
{
  "success": true,
  "status": 200,
  "code": "CASE_REVIEW_UPDATED",
  "message": "행원 검토 상태를 변경했습니다.",
  "data": {
    "caseId": "CASE_MOVE_001",
    "previousState": "PENDING_BANK_REVIEW",
    "currentState": "IN_BANK_REVIEW",
    "caseVersion": 2,
    "reviewedBy": "DEMO_STAFF",
    "followUpAt": null,
    "externalExecutionCreated": false,
    "updatedAt": "2026-08-14T01:08:00Z"
  },
  "errors": [],
  "timestamp": "2026-08-14T01:08:00Z",
  "traceId": "staff-trace-0003"
}
```

#### 5.4.4 안내 계획 승인

`P0-CONTRACT`

```http
POST /api/v1/demo/sessions/{sessionId}/cases/{caseId}/guidance-plan
Idempotency-Key: guidance-plan-0001
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
    "caseId": "CASE_MOVE_001",
    "previousState": "IN_BANK_REVIEW",
    "currentState": "CLOSED_GUIDANCE_PROVIDED",
    "caseVersion": 3,
    "approvedActionCodes": ["SAFE_BLOCK_INFO", "BANK_CONSULTATION"],
    "externalExecutionCreated": false,
    "approvedAt": "2026-08-14T01:09:00Z"
  },
  "errors": [],
  "timestamp": "2026-08-14T01:09:00Z",
  "traceId": "staff-trace-0004"
}
```

이 API의 `APPROVE`는 상담 계획 승인이다. 지급정지·이체차단·한도변경·외부 연락 승인으로 해석하지 않는다.

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

현재 구현상태는 모두 `CONTRACT`다. 구현 시 `SecurityConfig`의 공개 경로와 CORS 허용 헤더를 함께 갱신해야 한다.

---

### 공통 합성데이터 출처 필드

은행·카드·증권 읽기 모델은 다음 출처 필드를 공통으로 가진다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `sourceProvider` | string | P0에서는 항상 `SYNTHETIC_PROVIDER` |
| `sourceInstitutionId` | string | 합성 원천기관 ID |
| `sourceUpdatedAt` | ISO-8601 | 합성 snapshot 기준시각 |
| `dataFreshness` | enum | P0에서는 `FIXED_SNAPSHOT` |
| `connectionId` | string | 세션에 속한 합성 연결 ID |
| `consentId` | string | 분석 목적의 합성 동의 snapshot ID |
| `consentScope` | string[] | 허용된 읽기 범위 |

P0에서 허용하는 `dataFreshness` 값은 `FIXED_SNAPSHOT`, `FRESH`, `STALE`, `UNAVAILABLE`이며 실제 시연 fixture는 `FIXED_SNAPSHOT`을 사용한다.

금액은 항상 다음 구조 또는 동일 의미의 `amount` 문자열과 `currency` 조합으로 반환한다.

```json
{
  "amount": "18500000",
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
      "policyCatalog": "UP"
    }
  },
  "errors": [],
  "timestamp": "2026-08-14T01:00:00Z",
  "traceId": "frontend-trace-ready-0001"
}
```

데이터베이스 또는 필수 fixture가 준비되지 않으면 `503 Service Unavailable`과 `SYSTEM_NOT_READY`를 반환한다. 외부 LLM 장애는 템플릿 폴백이 가능하므로 readiness 실패 사유가 아니다.

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
    "supportedScenarioIds": ["MOVE_AB_001"],
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
    "schemaVersion": "1",
    "fixtureVersion": "move-ab-v1.0.0",
    "algorithmVersion": "baseline-mad-v1.0.0",
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
    "scenarioId": "MOVE_AB_001",
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
        "scenarioId": "MOVE_AB_001",
        "title": "이사·부동산 송금 A/B 비교",
        "baselineMonths": 9,
        "observationMonths": 3,
        "supportedContextFixtures": ["MOVE_A_VERIFIED", "MOVE_B_UNVERIFIED"],
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
    "items": [
      {
        "connectionId": "CONN_SYN_HANA_001",
        "institutionId": "HANA_BANK",
        "institutionName": "하나은행",
        "institutionType": "BANK",
        "status": "CONNECTED_SYNTHETIC",
        "sourceProvider": "SYNTHETIC_PROVIDER",
        "sourceUpdatedAt": "2026-07-31T23:59:59Z",
        "dataFreshness": "FIXED_SNAPSHOT",
        "consentId": "CONSENT_SYN_001",
        "consentScope": ["ACCOUNT", "BALANCE", "TRANSACTION", "RECURRING_PAYMENT"]
      },
      {
        "connectionId": "CONN_SYN_KBSEC_001",
        "institutionId": "KB_SECURITIES",
        "institutionName": "KB증권",
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
      "revocable": true,
      "trustedContactGranted": false
    }
  },
  "errors": [],
  "timestamp": "2026-08-14T01:02:00Z",
  "traceId": "frontend-trace-connections-0001"
}
```

화면에는 네 참여기관 배지를 사용할 수 있지만 연결 데이터가 합성이라는 표시를 고정한다. 기관 브랜드 UI를 복제하거나 실제 연결 완료로 표현하지 않는다.

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
    "customerId": "SYN_CUSTOMER_MOVE_001",
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
      "reasonCodes": ["NEW_PAYEE", "REPEATED_TRANSFER", "MISSED_RECURRING"],
      "summary": "신규 수취인과 반복송금, 정기납부 누락을 확인해 주세요."
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
    "syntheticData": true
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
    "customerId": "SYN_CUSTOMER_MOVE_001",
    "items": [
      {
        "accountId": "SYN_ACCOUNT_HANA_001",
        "institutionId": "HANA_BANK",
        "accountType": "DEMAND_DEPOSIT",
        "displayName": "생활비 통장",
        "maskedAccountNumber": "***-***-1234",
        "currentBalance": {"amount": "9250000", "currency": "KRW"},
        "availableBalance": {"amount": "9250000", "currency": "KRW"},
        "connectionId": "CONN_SYN_HANA_001",
        "consentId": "CONSENT_SYN_001",
        "sourceProvider": "SYNTHETIC_PROVIDER",
        "sourceUpdatedAt": "2026-07-31T23:59:59Z",
        "dataFreshness": "FIXED_SNAPSHOT"
      }
    ],
    "nextCursor": null,
    "hasMore": false
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
    "accountId": "SYN_ACCOUNT_HANA_001",
    "items": [
      {
        "transactionId": "TX_MOVE_101",
        "occurredAt": "2026-07-10T01:20:00Z",
        "postedAt": "2026-07-10T01:20:03Z",
        "direction": "OUT",
        "transactionType": "TRANSFER_OUT",
        "amount": "10000000",
        "currency": "KRW",
        "balanceAfter": "18250000",
        "counterpartyDisplayName": "합성수취인 A",
        "category": "HOUSING",
        "status": "POSTED",
        "sourceProvider": "SYNTHETIC_PROVIDER",
        "dataFreshness": "FIXED_SNAPSHOT"
      }
    ],
    "nextCursor": null,
    "hasMore": false
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
    "customerId": "SYN_CUSTOMER_MOVE_001",
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
        "baselineId": "BASELINE_TRANSFER_AMOUNT_001",
        "featureCode": "MONTHLY_TRANSFER_AMOUNT",
        "baselineValue": "5400000",
        "currentValue": "18500000",
        "unit": "KRW",
        "readiness": "READY",
        "comparisonText": "최근 송금액이 기준기간 중앙값보다 증가했습니다.",
        "reasonCodes": ["UNUSUAL_AMOUNT", "REPEATED_TRANSFER"],
        "algorithmVersion": "baseline-mad-v1.0.0",
        "calculatedAt": "2026-08-01T00:00:00Z"
      }
    ]
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
    ]
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
| 404 | `DEMO_SESSION_NOT_FOUND` | 세션이 없거나 다른 세션의 자원 접근 |
| 410 | `DEMO_SESSION_EXPIRED` | 세션 만료 |
| 404 | `SYNTHETIC_ACCOUNT_NOT_FOUND` | 세션 내 합성 계좌 없음 |
| 422 | `SYNTHETIC_FIXTURE_NOT_READY` | 시나리오 적재 전 금융생활 조회 |
| 503 | `SYSTEM_NOT_READY` | DB·마이그레이션·필수 fixture 미준비 |

수용기준:

- 모든 응답의 `syntheticData` 또는 데이터 모드 표시가 프론트에서 항상 보인다.
- 네 참여기관 배지는 합성 연결이며 실제 제휴·실연동으로 표현하지 않는다.
- 같은 Reset 뒤 계좌, 거래, 기준선, 연결, 자산 요약의 snapshot hash가 동일하다.
- 금액은 10진 문자열로 직렬화한다.
- 각 읽기 데이터에 원천기관·기준시각·신선도·동의 범위를 추적할 수 있다.
- 외부 금융회사 API, 외부 LLM, 원격 모델 저장소, 실제 푸시, 문자, 전화, 이체, 주문, 차단 호출은 0건이다.
- Spring·FastAPI 컨테이너의 외부 DNS·HTTPS 요청은 실패하고 내부 서비스 통신만 성공한다.
- `protection-actions`의 모든 P0 항목은 `GUIDANCE_ONLY`다.
- 계좌번호·카드번호는 마스킹하며 합성값이라도 실제 번호 형식을 그대로 노출하지 않는다.

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
| `CLOSED_GUIDANCE_PROVIDED` | 안내 계획 제공 후 종결 |
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
| `KNOWN_TRANSACTION` | 내가 알고 한 거래예요 | 강한 신호는 구조적 근거 추가 확인 |
| `LIFE_CHANGE` | 생활변화가 있었어요 | 구조적 근거 정합성 확인 |
| `UNABLE_TO_CONFIRM` | 본인 거래인지 확인하기 어려워요 | 행원 검토 전환 |
| `DEFER` | 나중에 확인할게요 | `CONTEXT_DEFERRED` |
| `CONTACT_BANK` | 은행에 문의할게요 | 행원 검토 전환 |

### 7.4 사유코드

| 값 | 의미 |
|---|---|
| `NEW_PAYEE` | 기준선에 없던 신규 수취인 |
| `REPEATED_TRANSFER` | 동일·유사 수취인·금액의 반복송금 |
| `DUPLICATE_PAYMENT` | 시간창 내 중복 가능 결제 |
| `MISSED_RECURRING` | 유예기간 내 예상 정기납부 미발생 |
| `CASH_WITHDRAWAL_TREND` | 현금인출 금액·빈도의 지속 증가 |
| `UNUSUAL_AMOUNT` | 개인 기준선 대비 금액 변화 |
| `UNUSUAL_TIME` | 개인 기준선 대비 이용시간 변화 |
| `TREND_SHIFT` | 일정 기간 지속된 수준·추세 변화 |

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
| 400 | `IDEMPOTENCY_KEY_REQUIRED` | 변경 API에서 키 누락 |
| 409 | `IDEMPOTENCY_KEY_CONFLICT` | 같은 키를 다른 요청 본문에 재사용 |
| 404 | `DEMO_SESSION_NOT_FOUND` | 세션 없음 |
| 410 | `DEMO_SESSION_EXPIRED` | 세션 만료 |
| 400 | `DEMO_SCENARIO_NOT_SUPPORTED` | `MOVE_AB_001` 외 시나리오 요청 |
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
              → CLOSED_GUIDANCE_PROVIDED
              → CLOSED_FALSE_POSITIVE
```

규칙:

1. 최초 `preDecision`과 근거 snapshot을 덮어쓰지 않는다.
2. `CLOSED_NORMAL`은 고객 응답과 서버가 확인한 구조적 근거가 일치할 때만 가능하다.
3. 강한 신호가 있는데 증거가 없거나 불일치하면 `PENDING_BANK_REVIEW`로 전환한다.
4. 신뢰연락인 미동의 상태의 연락 시도는 상태를 바꾸지 않고 `BLOCKED_BY_CONSENT` 감사이벤트만 남긴다.
5. 모든 변경 API는 상태 전후값, actor, traceId, 정책·알고리즘·스키마 버전을 감사로그에 기록한다.
6. 행원 승인 전과 승인 후 모두 P0에서는 외부 실행 이벤트를 생성하지 않는다.

---

### 7.8 보안·개인정보·감사 규칙

- 공개 데모 API는 익명 세션 단위로 격리한다.
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
- 동일 Reset 후 `scenarioSeed`, `snapshotHash`, `alertId`, 버전, 원시 거래가 동일하다.
- A/B 경로는 맥락 fixture 외의 원시 데이터가 동일하다.
- `KNOWN_TRANSACTION`만으로 강한 신호가 `CLOSED_NORMAL`이 되지 않는다.
- 미동의 연락 시도는 `BLOCKED_BY_CONSENT`이며 실제 외부 호출은 0건이다.
- `guidance-plan` 성공 후에도 `externalExecutionCreated=false`다.
- API 키나 LLM 장애가 있어도 템플릿 응답으로 전체 데모가 끝까지 동작한다.

---

### 7.10 변경관리

1. API path 또는 필드의 제거·의미 변경은 `/api/v2` 또는 명시적 마이그레이션 기간을 둔다.
2. enum 추가는 하위호환 변경이지만 프론트의 unknown fallback을 필수로 한다.
3. 이 문서, Spring DTO, OpenAPI, 프론트 TypeScript 타입의 명칭을 함께 변경한다. 현재 OpenAPI/Swagger 생성기는 아직 설치되지 않았다.
4. 구현 완료 시 엔드포인트 상태를 `P0-CONTRACT`에서 `IMPLEMENTED`로 변경하고 테스트 링크를 기록한다.
5. 최종 제출 전 공식 보호수단 URL과 기준일을 다시 확인한다.

