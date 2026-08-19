# ALZ's well 백엔드 개발 인수인계 가이드

> 대상: 새로 합류한 백엔드·프론트엔드 개발자
> 기준 브랜치: `develop`
> 기술: Java 21 · Spring Boot 3.5.16 · PostgreSQL 17 · Flyway · Gradle
> 상세 계약: [`FINAL_BACKEND_API_SPEC.md`](./FINAL_BACKEND_API_SPEC.md)

## 1. 시작 전에 알아야 할 결론

- 현재 구조는 MSA가 아니라 도메인 패키지로 나눈 모듈형 모놀리스다.
- API 카탈로그는 264개이며 구현된 업무 API는 98개다. 직원 capability 발급 경로까지 포함한 코드 operation은 99개다.
- 나머지 166개는 구현 완료가 아니라 P1·P2·참조 카탈로그다.
- 공개 데모는 완전 합성데이터만 사용한다. 실제 금융기관, 마이데이터, 가족 연락, 송금, 주문, 차단, 외부 LLM을 호출하지 않는다.
- 기존 Flyway V1~V29는 수정하지 않는다. 다음 스키마 변경은 반드시 V30부터 추가한다.
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
./gradlew clean test jacocoTestReport jacocoTestCoverageVerification spotbugsMain --no-daemon
```

API 카탈로그 합계·중복 검증:

```bash
python3 scripts/validate_api_catalog.py
```

Compose 계약 검증:

```bash
docker compose --project-directory backend --env-file backend/.env.example config --quiet
```

로컬 development 기동은 프로필과 직원 bootstrap 비밀번호를 명시해야 한다. 기본 프로필로는 기동되지 않는다.

```bash
cd backend
SPRING_PROFILES_ACTIVE=development \
DEMO_STAFF_USERNAME=demo-staff \
DEMO_STAFF_PASSWORD='32자 이상의 로컬 전용 임의 비밀번호' \
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
| `copilot` | 외부 모델과 분리된 결정론적 초안 포트 | `CopilotPort` |

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
| 승인 근거·결정론적 검색·안내 후보 | 6 | 최종 명세 3.3.19 |
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

### 11.1 capability 저장 방식 불일치

SSOT와 최종 명세는 capability를 브라우저 메모리에만 두도록 요구한다. 현재 프런트 `frontend/lib/demo-session.ts`는 페이지 간 상태 유지를 위해 `sessionStorage`를 사용한다. 실서비스 또는 외부 공개 전에 메모리+BFF/HttpOnly 경계로 바꾸거나, 보안 검토를 거쳐 SSOT를 명시적으로 개정해야 한다. 조용히 현 구현을 기준으로 삼지 않는다.

### 11.2 고객 응답 DTO

고객 프로필 7개 API는 `CustomerResponses`의 typed record를 사용한다. 신규 고객 API도 `Map<String, Object>` 대신 명시적 응답 record를 추가하고 OpenAPI schema가 구체적으로 생성되는지 확인한다.

### 11.3 합성 인증 한계

`/api/v1/auth/**`는 development 전용 로컬 합성 인증이다. production에서는 비활성화된다. 실제 고객·행원 인증은 `IdentityProviderPort` 뒤에 기업 IdP·MFA adapter를 구현한 후 별도 보안검토를 받아야 한다.

### 11.4 카탈로그 166개

미구현 166개를 한꺼번에 Controller로 생성하지 않는다. 다음 wave에서 필요한 5~10개를 골라 상세 계약을 `CONTRACT`로 확정한 뒤 구현한다. 금융 실행 API와 신뢰연락인 실제 연락 API는 계속 `REFERENCE_ONLY` 또는 외부 소유다.

## 12. 추천 다음 작업

1. capability의 `sessionStorage` 불일치 해소
2. 구현된 99개 operation의 도메인별 summary·개별 오류 예시를 지속적으로 구체화
3. 운영형 인앱 알림함 API의 프론트 연동과 사용성 검증
4. Compose 스모크 증적의 보존기간·실패 로그를 운영 기준에 맞게 확장

새 개발자는 3번에서 다음 API를 고르기 전에 1~2번 중 자신의 담당 범위를 확인해야 한다.
