# ALZ's well 백엔드 개발 인수인계 가이드

> 대상: 새로 합류한 백엔드·프론트엔드 개발자
> 기준 브랜치: `develop`
> 기술: Java 21 · Spring Boot 3.5.16 · PostgreSQL 17 · Flyway · Gradle
> 상세 계약: [`FINAL_BACKEND_API_SPEC.md`](./FINAL_BACKEND_API_SPEC.md)

## 1. 시작 전에 알아야 할 결론

- 현재 구조는 MSA가 아니라 도메인 패키지로 나눈 모듈형 모놀리스다.
- API 카탈로그는 281개이며 문서화된 업무 API는 236개다. 직원 capability 발급 경로까지 포함한 전체 코드 operation은 237개다.
- 나머지 문서 operation 45개 중 23개는 계획, 22개는 참조 전용이다. 코드 전용 직원 capability 1개는 281개 업무 카탈로그에 포함하지 않는다.
- 공개 데모는 완전 합성데이터만 사용한다. 실제 금융기관, 마이데이터, 가족 연락, 송금, 주문, 차단, 외부 LLM을 호출하지 않는다.
- 기존 Flyway V1~V74는 수정하지 않는다. 다음 스키마 변경은 반드시 V75부터 추가한다.
- V48은 직원 접근 목적·scope 매트릭스, 만료·거부 감사, 고객 변경 멱등 명령, 복합 소유권 FK를 추가했고 V49는 목적별 수임 역할, grant 핵심필드 불변성, 사건 배정 snapshot, 완료 멱등 응답 불변성, 탐지정책 DB 상태전이를 강화한다.
- V50은 기존 계좌·부채 원장에 연결된 예금·대출·투자 보유 읽기 projection을 추가하며 모든 원본은 합성 데이터이고 외부 금융기관 호출·주문·상환을 실행하지 않는다.
- V51은 안심은행 합성 예금·대출 상품, 금리, 만기 선택지 snapshot과 실행 없는 결정론적 이자·상환 모의계산을 추가한다.
- V52는 안심증권 합성 주문이력·시세·차트 snapshot과 버전·멱등성·불변 이벤트를 갖춘 고객 관심종목을 추가하며 실제 시세망 호출과 주문 실행은 하지 않는다.
- V53은 문서상 구현 완료였지만 실제 경로가 없던 연금 보유목록을 보완하고, 합성 연금 전망·신탁 목록·상세 snapshot을 추가한다. 전망은 보장·추천이 아니며 수익자 식별정보와 계약 실행 기능을 제공하지 않는다.
- V54는 고객 경보 이의신청과 배정 직원의 정책 재검토를 추가한다. 양쪽 모두 사유·멱등성·버전·민감정보를 검증해 불변 이력으로 남기며 실제 금융조치나 외부 알림은 실행하지 않는다.
- V55는 승인 지식문서의 manifest 메타데이터를 검토등록·게시하는 관리자 API를 추가한다. 게시 완료는 AI ingestion 가능 상태일 뿐 검색 반영 완료가 아니며 외부 호출·원문 수정·자동 승인을 실행하지 않는다.
- V56은 지식 조회·검색에 실제 로그인 역할, 문서 ACL, audience, 승인·활성·효력일을 함께 적용하고 검색 원문을 저장하지 않는 추가 전용 접근 감사를 남긴다. 검색 구현은 `KnowledgeRetrievalPort` 뒤의 결정론적 로컬 어댑터이며 FastAPI·외부 모델은 아직 연결하지 않는다.
- V57은 본인 인증 세션 목록과 선택 세션 폐기를 구현한다. 원문 token·IP·User-Agent는 응답이나 감사이력에 저장하지 않고, 다른 principal의 session ID는 존재 여부를 숨긴 `404`로 처리한다.
- V59는 고객 저장 이체 양식 조회·생성·삭제를 구현한다. 합성 계좌·마스킹 수취인만 참조하고 핵심 필드와 감사 snapshot을 불변으로 유지하며 실제 이체·승인·외부 호출은 실행하지 않는다. AI ingestion 저장 계층은 V58이다.
- V60은 승인된 합성 FAQ와 안심은행 공지 snapshot 조회를 추가한다. `SUPPORT_CONTENT_READ` 권한과 범주·기간·건수 제한을 적용하고 문의 접수나 외부 고객센터 호출은 실행하지 않는다.
- V62는 합성 환율, 마스킹 외화계좌, 합성 해외송금 이력과 실행 없는 환전 모의계산을 추가한다. 실제 환전·송금·외부 호출은 실행하지 않는다. AI 키워드 검색은 V61이다.
- V63은 `SMOKE/DEMO/LOAD/DEV` 결정론적 합성 고객·계좌·거래 생성 Job과 실행 manifest를 추가한다. 공개 API는 늘리지 않으며 migrator 역할의 일회성 Compose Job으로만 적재한다. V67은 250명 `LOAD` profile과 활성 정책 전체 고객 오탐·미탐 품질 리포트를 추가한다.
- V68은 AI chunk와 분리된 다중 차원 임베딩 테이블을 추가하고 Hash/E5 384차원과 Arctic-ko 1024차원 HNSW 인덱스를 고정 모델 버전별로 격리한다. 기존 384차원 chunk 컬럼은 배포 호환성을 위해 유지한다.
- V69는 publish를 `APPROVED/PENDING_ACTIVATION`으로 유지하고, Spring import를 실제 `SUCCEEDED` AI 실행 및 전체 chunk snapshot과 대조한 뒤 DB 생성 proof와 1:1 binding/passage 무결성을 강제한다. target 활성화·이전 governance 대체·새 지식 버전 적재·catalog head 전환·이전 version supersede는 한 트랜잭션이며 실패 시 기존 ACTIVE head를 보존한다. V28 legacy head는 명시적 supersedes로만 대체하고, import 전 막힌 pending은 후속 버전의 명시적 publish로 감사 가능한 `RETIRED` 상태로 교체한다.
- V71은 문서 목록·상세·passage·내부 citation·결정론적 폴백을 current `APPROVED/ACTIVE` governance와 `AI_DB_SNAPSHOT_V1` import proof에 묶는다. V28 legacy 문서는 검증 import 전까지 노출하지 않으며, fallback은 제목·본문의 `simple` stored `tsvector` GIN과 별도 keyword GIN에서 parameter-bound query로 최대 200개 DB 후보만 조회한다. 안내 후보와 보호수단 상세의 citation은 고정 passage UUID가 아니라 `actionCode → documentId` 매핑에서 같은 verified-current ACL을 통과한 최신 stable passage를 선택하며 근거가 없으면 빈 목록/후보로 fail-closed한다. 두 우회조회는 각각 `GUIDANCE_CITATION`, `PROTECTION_ACTION_CITATION`으로 calling permission·action code·기준일·반환 passage와 결과를 감사한다.
- V70은 계좌 표시·거래 범주/노트·관심종목 변경에 실제 Bearer principal·session·고객·actor type snapshot을 보존하고 관심종목 이벤트를 통합 감사 조회에 포함한다. 관심종목 신규 해시는 `ACTOR_SNAPSHOT_V2`, 기존 이력은 `LEGACY_V1`로 구분하며 rolling 배포 중에는 legacy `actor_id`와 신규 `actor_principal_id`를 동시 기록한다.
- V72는 AI ingestion 역할의 `chunk`, `chunk_embedding` UPDATE 권한을 전부 회수하고 `chunk_embedding` 직접 DELETE도 회수한다. 임베딩 정리는 부모 chunk cascade로만 수행한다. AI ingestion과 Spring publish/import는 같은 문서 단위 advisory lock을 사용한다. 검증 import 전에는 기존 chunk와 cascade embedding을 삭제한 뒤 완전한 파생 snapshot을 INSERT-only로 교체하지만, `AI_DB_SNAPSHOT_V1` proof가 만들어진 문서·버전의 chunk·`document_snapshot`과 proof가 참조하는 terminal `ingestion_run`은 DB trigger가 이후 변경을 모두 거부한다. UPDATE 시 OLD와 NEW 키를 모두 검사하므로 검증 행을 다른 키로 옮길 수도 없다. 이후 내용 변경은 새 `versionLabel`로 적재해야 한다. 미검증·신규 버전의 `document_snapshot` upsert에 필요한 UPDATE 권한과 검색 runtime의 두 파생 테이블 SELECT는 유지한다.
- V73은 익명 합성 데모의 고객 확인형 금융생활 의향 초안·승인 상태를 세션과 run에 귀속해 저장한다. 의향은 법적 효력이 없고 금융 실행·건강 추론에 사용하지 않으며 세션 폐기 시 함께 삭제된다. 내부 FastAPI는 구조화 초안, EWMA·CUSUM 장기 변화, 제한된 쉬운말 생성을 담당하고 Spring이 capability·버전·승인·폴백 경계를 소유한다.
- V74는 고객 확인 유예와 추가 전용 감사이력을 추가하고, 탐지 dataset·실행·결과·승격 증적 및 AI retrieval terminal run의 변경·삭제를 DB trigger와 최소권한으로 제한한다. 승격 전에는 dataset·입력·결과 hash를 재계산하며, 지식 keyword 후보 검색에는 표현식 GIN index를 사용한다.
- 감사 해시에 시각을 포함할 때는 반드시 `AuditTimestamp.canonical`로 UTC·PostgreSQL 마이크로초 정밀도를 먼저 고정하고, 그 동일 객체를 해시와 `timestamptz` INSERT에 사용한다. 나노초나 원래 offset 문자열을 직접 해시에 넣으면 DB 재조회 후 검증할 수 없다.
- V64는 검증된 AI ingestion import와 `chunkId ↔ passageId` 추가 전용 binding을 추가한다. Spring만 문서 권위·ACL·효력기간을 판정하며 AI 계정은 권위 테이블을 수정하지 않는다.
- V65는 `local-hash-ngram-ko-v1` 384차원 임베딩과 pgvector HNSW 인덱스를 추가하고, 내부 FastAPI가 전문검색과 cosine 유사도를 결합한 하이브리드 검색을 제공한다. 외부 모델 다운로드와 LLM 호출은 없다.
- 검색 평가는 `ai-service/evaluation/datasets/`의 합성 corpus·질의로 Recall@3/5, MRR, 무응답 오탐률과 ACL·audience·승인·효력 정책 위반을 측정한다. CI 기준을 통과하지 않은 가중치·임계값·임베딩 변경은 병합하지 않는다.
- V40~V47 DB 업그레이드는 `docs/runbooks/V48_UPGRADE_PREFLIGHT.md`의 사전검사를 먼저 통과해야 한다.
- 구현 순서는 `SSOT → API 상세 계약 → Flyway → Java 코드 → 통합 테스트 → OpenAPI·문서`다.

## 2. 문서와 코드의 우선순위

충돌 시 다음 순서로 판단한다.

1. 최신 대회 공식 공지
2. `ALZS_WELL_PROJECT_SSOT.md`
3. `docs/FINAL_BACKEND_API_SPEC.md`
4. Spring 코드와 자동 테스트
5. 이 인수인계 문서

코드가 문서와 다르다고 코드를 조용히 새 기준으로 삼지 않는다. 의도된 변경인지 먼저 확인하고 같은 PR에서 기준 문서, API 명세, 코드, 테스트를 함께 변경한다.

## 3. 로컬 실행과 검증

필수 도구:

- JDK 21
- Docker와 Docker Compose
- Git
- Node.js 22 이상은 프런트까지 검증할 때 필요

백엔드 전체 검증:

```bash
cd backend
./gradlew clean check jacocoTestReport jacocoTestCoverageVerification --no-daemon
```

API 카탈로그 합계·중복 검증:

```bash
python3 scripts/validate_api_catalog.py
```

합성 운영 데이터 적재는 [`runbooks/SYNTHETIC_DATASET_V3.md`](./runbooks/SYNTHETIC_DATASET_V3.md)를 따른다. 일반 백엔드 컨테이너가 아니라 `synthetic-tools` profile의 일회성 Job으로 실행한다.

Compose 계약 검증:

```bash
docker compose --project-directory backend --env-file backend/.env.example config --quiet
```

로컬 development 기동은 프로필과 직원 bootstrap Bearer 토큰을 명시해야 한다. 기본 프로필로는 기동되지 않는다.

```bash
cd backend
SPRING_PROFILES_ACTIVE=development \
DEMO_STAFF_BOOTSTRAP_TOKEN="$(openssl rand -hex 32)" \
./gradlew bootRun
```

PostgreSQL은 먼저 실행되어 있어야 한다. development 기본 DB 접속값은 `application-development.yml`에만 있고 production은 환경변수가 없으면 기동에 실패한다.

## 4. 패키지 책임

| 패키지 | 책임 | 주요 진입점 |
|---|---|---|
| `common.api` | 공통 응답 envelope와 필드 오류 | `ApiResponse`, `ApiResponses` |
| `common.exception` | 공통 예외 매핑 | `GlobalExceptionHandler` |
| `common.security` | Bearer·capability 인증, CORS, URL 경계 | `SecurityConfig` |
| `system` | health, readiness, 공개 설정, 버전 | `SystemController` |
| `demo` | 익명 세션, fixture, 경보, 사건, 합성 금융생활 | `DemoSessionController`, `P0WorkflowController` |
| `identity` | 로컬 합성 로그인과 opaque token 세션 | `AuthController`, `IdentityProviderPort` |
| `customer` | 고객 표시 프로필·환경설정·접근성 | `CustomerController` |
| `connection` | 합성 금융기관과 읽기 전용 연결 상태 | `FinancialInstitutionController`, `CustomerConnectionController` |
| `detection` | 운영형 고객 기준선·변화신호·불변 근거 snapshot | `CustomerDetectionController`, `SignalController` |
| `copilot` | 승인 근거 기반 템플릿과 결정론적 폴백을 분리한 초안 포트 | `CopilotPort`, `RetrievalGroundedCopilotAdapter` |

의존방향은 `api/controller → application/service → domain/port → persistence adapter`다. Controller에서 SQL을 실행하거나 다른 도메인의 repository를 직접 호출하지 않는다.

## 5. 현재 구현된 API 묶음

| 묶음 | 업무 operation | 상세 계약 |
|---|---:|---|
| 시스템 | 4 | 최종 명세 5.1, 6장 |
| 데모 세션·시나리오 | 5 | 최종 명세 5.2, 6장 |
| 합성 금융생활 읽기 | 6 | 최종 명세 6장 |
| 고객 알림 | 4 | 최종 명세 5.3 |
| 행원 사건 P0 | 4 | 최종 명세 5.4 |
| 사건·세션 P1 조기구현 | 9 | 최종 명세 5.2·5.4 |
| 고객 프로필·접근성 | 7 | 최종 명세 6.1 |
| 로컬 합성 인증 | 6 | 최종 명세 6.2 |
| 합성 금융기관·연결 | 4 | 최종 명세 6.3 |
| 고객 기준선·변화신호 | 7 | 최종 명세 6.4 |
| 합성 데이터셋·탐지 실행·승격 | 8 | 최종 명세 6.5 |
| 운영형 경보·생활맥락 | 6 | 최종 명세 6.6 |
| 운영형 행원 사건·후속일정 | 12 | 최종 명세 6.7 |
| 운영형 인앱 알림·설정·미리보기 | 6 | 최종 명세 3.3.20 |
| 고객지원 FAQ·합성 공지 | 2 | 최종 명세 3.3.20 |
| 외환 읽기·환전 모의계산 | 5 | 최종 명세 3.3.24 |
| 승인 근거·결정론적 검색·안내 후보 | 6 | 최종 명세 3.3.19 |
| 감사·컴플라이언스 조회 | 4 | 최종 명세 3.3.21 |
| 감사자료 내부 승인 요청 | 1 | 최종 명세 3.3.21 |
| 금융생활 준비·의향 | 7 | 최종 명세 3.3.3-A |
| 데모 AI 금융생활 지원 | 6 | 최종 명세 3.3.3-B·6.10 |
| 저장 이체 양식 | 3 | 최종 명세 3.3.9 |
| 예금·대출 상품 조회·모의계산 | 8 | 최종 명세 3.3.11·3.3.12 |
| 투자 주문이력·합성 시세·관심종목 | 5 | 최종 명세 3.3.13 |
| 연금 보유·전망·신탁 조회 | 4 | 최종 명세 3.3.14 |
| 경보 이의신청·사건 정책 재검토 | 2 | 최종 명세 3.3.16·3.3.17 |
| 보존정책·개인정보 권리 요청 | 3 | 최종 명세 3.3.21 |
| 직원 capability 발급 | 업무 집계 외 1 | 최종 명세 5.2.1-A |

## 6. API 하나를 구현하기 전 Definition of Ready

다음 항목이 명세에 없으면 코딩을 시작하지 않는다.

- 우선순위: P0, P1, P2, REFERENCE
- 구현 상태: CONTRACT 또는 구현 승인을 받은 DRAFT
- 경계: OWNED, EXTERNAL_INTEGRATION, REFERENCE_ONLY
- Method와 path
- 호출 주체와 필수 authority
- path·query·header 제약
- 요청 DTO 필드, 타입, 필수 여부, 길이와 enum
- 성공 HTTP status, 응답 code, 응답 DTO
- endpoint별 오류 code와 조건
- 멱등성 적용 여부와 scope
- 상태전이와 DB write set
- 실제 외부 실행 여부
- 최소 정상·권한·검증·동시성 테스트

`REFERENCE_ONLY`는 Controller를 만들지 않는다. `EXTERNAL_INTEGRATION`은 먼저 port와 결정론적 합성 adapter를 만들고 실제 기관 adapter를 기본 bean으로 등록하지 않는다.

## 7. 구현 완료 Definition of Done

- API 상세 계약과 코드가 동일하다.
- 요청·응답에 `Map<String, Object>`를 새로 도입하지 않고 명시적 record DTO를 사용한다.
- Bean Validation과 DB constraint가 핵심 불변식을 함께 막는다.
- 소유권·authority·교차 고객 접근 거부 테스트가 있다.
- 변경 API는 멱등성 또는 중복 호출 정책이 명시돼 있다.
- Flyway는 신규 버전 파일만 추가하며 기존 migration을 수정하지 않는다.
- PostgreSQL Testcontainers 통합 테스트가 정상·오류·동시성 경계를 검증한다.
- `/v3/api-docs` operation 수와 schema가 계약 테스트를 통과한다.
- 모든 노출 operation에 summary·description과 `x-alzs-authority-mode`, `x-alzs-required-authorities`, `x-alzs-data-classification`, `x-alzs-runtime-boundary`, `x-alzs-external-action`이 존재한다.
- 인증 API는 `BearerAuth`, 데모 API는 `DemoCapability` 또는 `DemoStaffBootstrap` 보안 scheme을 사용한다. 공통 400·401·403·409 응답 예시는 traceId를 포함한다.
- API 명세의 상태가 `IMPLEMENTED`로 변경된다.
- 백엔드 테스트, JaCoCo, SpotBugs/FindSecBugs, Compose CI가 통과한다.
- 실제 외부 호출·전송·금융 실행이 0건임을 유지한다.

## 8. 신규 API 구현 기본 형태

```java
public record ExampleCommand(
        @NotNull Long expectedVersion,
        @NotBlank @Size(max = 80) String value
) {}

public record ExampleResponse(
        UUID resourceId,
        String status,
        long version,
        OffsetDateTime updatedAt
) {}
```

```java
@PostMapping
@PreAuthorize("hasAuthority('EXAMPLE_WRITE')")
public ResponseEntity<ApiResponse<ExampleResponse>> create(
        @Valid @RequestBody ExampleCommand request
) {
    return ApiResponses.created("EXAMPLE_CREATED", "예시 자원을 생성했습니다.", service.create(request));
}
```

서비스는 transaction, 소유권, 현재 상태, `expectedVersion`을 검증하고 DTO만 반환한다. 원문 token, 개인정보, 자유입력, 외부 응답 전문을 로그에 남기지 않는다.

## 9. 테스트 최소 세트

읽기 API:

- 정상 조회
- 존재하지 않는 ID
- 인증 누락·만료
- 다른 customerId 접근
- 필요한 authority 누락
- 빈 목록과 nullable 필드

변경 API:

- 정상 상태전이
- Bean Validation 실패
- 오래된 `expectedVersion`
- 같은 멱등키·같은 요청 재생
- 같은 멱등키·다른 요청 충돌
- 중복 클릭 또는 병렬 요청
- 감사이력 생성
- 외부 실행 0건

목록 API:

- 고정 정렬
- 첫 페이지와 다음 cursor
- 마지막 페이지
- 잘못된 cursor
- 정렬키가 같은 항목
- 필터와 cursor를 함께 사용한 누락·중복 방지

핵심 폐루프 E2E:

- `BackendCoreFlowE2ETest`를 기준으로 기준선 계산부터 안내계획 승인까지 실제 HTTP 호출로 연결한다.
- 탐지 run·승격·경보·사건·안내계획이 각각 한 번만 생성됐는지 PostgreSQL에서 확인한다.
- 경보 감사이력과 사건 타임라인이 단절 없이 이어지고 실제 금융 실행·외부 알림이 0건인지 확인한다.
- 동일 요청 재시도는 기존 자원을 반환하고, 같은 멱등키의 다른 요청과 오래된 버전은 충돌해야 한다.
- 실패한 상태전이 뒤에는 맥락 이벤트·사건이 일부만 생성되지 않아야 하며 타 고객 조회는 숨겨야 한다.
- 호출자가 전달한 `X-Trace-Id`는 응답 헤더와 본문에서 동일해야 한다.
- 실행 명령은 `./gradlew test --tests 'com.alzswell.e2e.BackendCoreFlowE2ETest'`다.

## 10. Git 작업 규칙

```text
develop 최신화
→ feature/{기능명}
→ 구현·테스트·문서
→ feat: 한글 또는 fix: 한글 커밋
→ develop 대상 PR
→ 필수 CI와 리뷰
→ merge commit
→ feature 브랜치 삭제
```

`main`은 운영·제출 기준점이다. 사용자가 명시하지 않으면 `develop`까지만 병합한다. 기존 migration, 다른 사람의 작업 파일, 로컬 생성물은 임의로 삭제하지 않는다.

## 11. 현재 알려진 인수인계 주의사항

### 11.1 capability 저장 경계

Vercel 공개 배포에서는 BFF가 고객 capability를 `Secure`·`HttpOnly`·`SameSite=Strict` host cookie에 보관하고 AWS로 전달할 때만 헤더로 변환한다. 프런트 자바스크립트가 읽을 수 있는 `sessionStorage`에는 비밀값이 아닌 세션·시나리오 식별자만 저장한다. 로컬 직접 호출 모드는 capability를 모듈 메모리에만 둔다. 이 경계를 변경할 때에는 SSOT·최종 명세·프런트 보안 테스트를 함께 갱신한다.

### 11.2 고객 응답 DTO

고객 프로필 7개 API는 `CustomerResponses`의 typed record를 사용한다. 신규 고객 API도 `Map<String, Object>` 대신 명시적 응답 record를 추가하고 OpenAPI schema가 구체적으로 생성되는지 확인한다.

### 11.3 합성 인증 한계

`/api/v1/auth/**`는 development 전용 로컬 합성 인증이다. production에서는 비활성화된다. 실제 고객·행원 인증은 `IdentityProviderPort` 뒤에 기업 IdP·MFA adapter를 구현한 후 별도 보안검토를 받아야 한다.

### 11.4 미구현 카탈로그 45개

미구현 45개를 한꺼번에 Controller로 생성하지 않는다. 다음 wave에서 필요한 5~10개를 골라 상세 계약을 `CONTRACT`로 확정한 뒤 구현한다. 금융 실행 API와 신뢰연락인 실제 연락 API는 계속 `REFERENCE_ONLY` 또는 외부 소유다.

## 12. 추천 다음 작업

1. Vercel Preview에 서버 전용 환경변수를 연결하고 HttpOnly capability 경계를 E2E로 검증
2. 구현된 236개 문서 operation의 도메인별 summary·개별 오류 예시를 지속적으로 구체화
3. 운영형 인앱 알림함 API의 프론트 연동과 사용성 검증
4. Compose 스모크 증적의 보존기간·실패 로그를 운영 기준에 맞게 확장

새 개발자는 3번에서 다음 API를 고르기 전에 1~2번 중 자신의 담당 범위를 확인해야 한다.
