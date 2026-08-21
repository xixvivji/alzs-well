# 지식 계약 v1

이 디렉터리는 Spring Boot와 내부 AI/RAG 서비스가 공동으로 따르는 지식 manifest, citation, 오류코드와 결정론적 chunk ID 계약의 단일 기준이다. 이 PR은 런타임 구현이나 문서 승인을 수행하지 않는다.

## 계약 파일

- `manifest.schema.json`: 문서 승인·생명주기·출처·ACL 메타데이터
- `citation.schema.json`: Spring이 재검증할 검색 인용 구조
- `error-codes.yaml`: CLI와 향후 내부 HTTP API의 안정적인 오류코드
- `chunk-id-test-vectors.json`: Python과 Java가 공통으로 검증할 고정 digest
- `fixtures/`: 유효한 합성 manifest와 의도적으로 잘못된 manifest

JSON Schema는 Draft 2020-12를 사용한다. 모든 객체는 선언하지 않은 필드를 거부한다.

## YAML 1.2 처리 규칙

manifest와 YAML fixture는 다음 조건으로 읽는다.

1. UTF-8만 허용한다.
2. YAML 1.2 Core Schema를 적용한다.
3. custom tag, alias, anchor를 거부한다.
4. 중복 키를 파싱 단계에서 거부한다.
5. 날짜와 date-time은 문자열로 읽은 뒤 JSON Schema `format` 검증을 활성화한다.
6. YAML을 일반 JSON 자료형으로 변환한 뒤 `manifest.schema.json`으로 검증한다.
7. `effectiveTo >= effectiveFrom`은 JSON Schema 이후 의미 검증으로 강제한다.
8. 원본 credential 값은 manifest, 오류, 로그 또는 감사 이벤트에 기록하지 않는다.

## 승인·생명주기·폐기 상태

승인 상태와 검색 생명주기는 독립적으로 관리한다.

```text
approvalStatus
DRAFT → IN_REVIEW → APPROVED
                  → REJECTED

lifecycleStatus
PENDING_ACTIVATION → ACTIVE → SUPERSEDED
                           → EXPIRED
                           → RETIRED
```

- `SUPERSEDED`: 승인된 새 버전으로 대체
- `EXPIRED`: 효력기간 종료
- `RETIRED`: 효력기간과 무관한 운영상 철회
- `DISPOSED`: 검색 생명주기가 아니라 별도 물리적 보존·폐기 상태 및 감사기록

`ACTIVE`, `SUPERSEDED`, `EXPIRED`, `RETIRED`는 과거 또는 현재에 승인된 문서에만 허용한다. AI ingestion은 `approvalStatus=APPROVED`와 `lifecycleStatus=ACTIVE`를 모두 만족하는 문서만 처리한다. AI는 두 상태를 변경하지 않는다. ingestion 실행 상태 `PENDING/RUNNING/SUCCEEDED/FAILED`는 manifest가 아니라 파생 리포트와 향후 `ai_ingestion_run`에 기록한다.

실제 `knowledge/manifests/DOC-FSC-SAFE-BLOCK-001.yaml`은 최신성·이용조건·보안·승인 검토 전이므로 `IN_REVIEW/PENDING_ACTIVATION`이다. 성공 경로는 공식 문서를 허위 승인하지 않고 `fixtures/synthetic-approved-active.yaml`로 검증한다.

현재 Flyway V28의 `knowledge_document.status`는 `APPROVED/EXPIRED`만 표현하고 같은 문서 ID의 데모 seed를 `APPROVED`로 둔다. 계약 manifest가 현재 런타임 DB를 즉시 변경하지 않으며, Spring V40에서 승인·생명주기·ACL을 분리하고 레거시 seed의 호환·전환 정책을 구현해야 한다.

## 역할, permission, audience

- permission은 API 작업 실행 권한이다.
  - 검색: `KNOWLEDGE_SEARCH`
  - 문서·버전·문단 조회: `KNOWLEDGE_READ`
- `allowedRoles`는 문서 단위 ACL이다.
- `audience`는 콘텐츠 대상 독자이며 ACL을 대신하지 않는다.

Spring은 인증 주체의 모든 역할을 `principalRoles`로 제공하고 아래 합집합을 `requesterAudiences`로 계산한다.

| principal role | requester audience |
|---|---|
| `CUSTOMER` | `CUSTOMER` |
| `PROTECTION_STAFF` | `STAFF` |
| `DETECTION_ADMIN` | `STAFF` |

현재 코드에만 존재하거나 이후 추가되는 역할의 audience 매핑은 계약 버전 변경 없이 암묵적으로 추론하지 않는다. 계약 또는 버전이 지정된 정책에서 명시적으로 추가한다.

공통 조회 조건은 다음과 같다.

```text
작업별 permission 보유
AND principalRoles ∩ document.allowedRoles ≠ ∅
AND (
  document.audience = BOTH
  OR document.audience ∈ requesterAudiences
)
AND approvalStatus = APPROVED
AND lifecycleStatus = ACTIVE
AND effectiveFrom <= asOf
AND (
  effectiveTo IS NULL
  OR effectiveTo >= asOf
)
```

현재 `CUSTOMER` 역할에는 Knowledge permission이 없으므로 `audience=BOTH`만으로 고객 조회가 허용되지 않는다.

## `asOf` 결정

`effectiveFrom`, `effectiveTo`, `asOf`는 timezone이 없는 ISO 8601 달력 날짜 `YYYY-MM-DD`다.

1. 외부 요청에 `asOf`가 없으면 Spring이 `LocalDate.now(ZoneId.of("Asia/Seoul"))`로 계산한다.
2. Spring은 내부 AI 요청에 `asOf`를 항상 명시한다.
3. FastAPI와 ingestion 코드는 시스템 시각으로 `asOf`를 다시 계산하거나 대체하지 않는다.
4. 전달된 `asOf`는 ACL·검색·인용·감사에서 같은 값으로 사용한다.

## 출처 변환과 해시

`sourceHash`는 AI가 실제로 읽는 저장소 반입본 바이트의 SHA-256이며 `sha256:<lowercase hex>`로 기록한다. 공식 HTML 보존본에서 웹 자격증명을 제거한 경우 다음처럼 기록한다.

```yaml
sourceTransformations:
  - type: CREDENTIAL_REDACTION
    ruleId: PUBLIC_WEB_CREDENTIAL_REDACTION_V1
    replacement: REDACTED_SOURCE_CREDENTIAL
```

원래 credential 값은 어디에도 기록하지 않는다. source 경로의 `..` 구간, 저장소 밖 경로, 심볼릭 링크는 schema 통과 여부와 관계없이 ingestion 보안 검증에서 거부한다.

## 결정론적 chunk ID

chunk ID 입력 배열의 위치와 형식은 다음과 같다.

```json
[
  "documentId",
  "versionLabel",
  ["sectionPath", "elements"],
  1,
  "sha256:textHash",
  "chunkerVersion"
]
```

생성 순서는 다음과 같다.

1. `documentId`, `versionLabel`, `sectionPath`의 각 원소, `textHash`, `chunkerVersion`을 Unicode NFC로 정규화한다.
2. `chunkOrder`는 1부터 시작하는 JSON 정수, 빈 `sectionPath`는 `[]`로 표현한다.
3. 정규화된 배열을 RFC 8785 JSON Canonicalization Scheme으로 직렬화한다. RFC 8785 자체는 Unicode 정규화를 수행하지 않는다.
4. canonical JSON의 UTF-8 바이트를 SHA-256으로 계산한다.
5. 소문자 hex 앞에 `chk_`를 붙인다.

Python과 Java는 서로의 결과만 비교하지 않고 `chunk-id-test-vectors.json`의 `canonicalJson`과 `expectedChunkId`를 각각 독립적으로 검증해야 한다. nullable 계약 필드는 JSON `null`로 표현하며 문자열 `"null"`이나 YAML 암시적 날짜로 바꾸지 않는다.

## Citation 재검증

FastAPI가 반환한 citation은 권한 부여 결과가 아니다. Spring은 `documentId`, `versionLabel`, `chunkId`, `sourceHash`, `textHash`, 명시적 `retrievedAsOf`를 사용해 최종 ACL·효력·활성 상태와 인용 일치를 다시 검증한다. 검증에 실패한 passage는 응답과 생성 문맥에서 제외하고 감사 이벤트에 실패 사유코드만 기록한다.
