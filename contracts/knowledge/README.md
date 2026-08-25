# 지식 계약 v1

이 디렉터리는 Spring Boot와 내부 AI/RAG 서비스가 공동으로 따르는 지식 manifest, citation, 오류코드와 결정론적 chunk ID 계약의 단일 기준이다. 이 PR은 런타임 구현이나 문서 승인을 수행하지 않는다.

## 계약 파일

- `manifest.schema.json`: 문서 승인·생명주기·출처·ACL 메타데이터
- `citation.schema.json`: Spring이 재검증할 검색 인용 구조
- `error-codes.yaml`: CLI와 향후 내부 HTTP API의 안정적인 오류코드
- `chunk-id-test-vectors.json`: Python과 Java가 공통으로 검증할 고정 digest
- `pdf-source-validation-vectors.json`: PDF 입력 가드의 경계값과 고정 오류코드
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

현재 PostgreSQL 구현은 Spring 권위 테이블과 분리된 `ai_knowledge.ingestion_run`에 실행
상태를 기록하고 `ai_knowledge.chunk`에 파생 청크를 저장한다. AI 계정에는 이 스키마의
필요한 DML만 허용하며 Spring의 `knowledge_document`, `knowledge_document_version`,
`knowledge_passage` 쓰기 권한을 부여하지 않는다.

실제 `knowledge/manifests/DOC-FSC-SAFE-BLOCK-001.yaml`은 최신성·이용조건·보안·승인 검토 전이므로 `IN_REVIEW/PENDING_ACTIVATION`이다. 성공 경로는 공식 문서를 허위 승인하지 않고 `fixtures/synthetic-approved-active.yaml`로 검증한다.

현재 Flyway V28의 `knowledge_document.status`는 `APPROVED/EXPIRED`만 표현하고 같은 문서 ID의 데모 seed를 `APPROVED`로 둔다. 계약 manifest가 현재 런타임 DB를 즉시 변경하지 않으며, Spring V41 이후에서 승인·생명주기·ACL을 분리하고 레거시 seed의 호환·전환 정책을 구현해야 한다.

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

### 저장소 루트와 `sourcePath`

`sourcePath`는 프로세스의 현재 작업 디렉터리가 아니라 **명시적으로 주입된 저장소 루트 기준 POSIX 상대 경로**다. 절대 경로, `..` 구간, 역슬래시와 경로 구성요소 중 하나라도 심볼릭 링크인 입력은 거부한다.

CLI는 다음 우선순위로 저장소 루트를 받는다.

1. `--repo-root` 인자
2. `ALZS_REPO_ROOT` 환경변수

둘 다 없으면 `REPOSITORY_ROOT_REQUIRED`로 실행을 시작하지 않는다. ingestion 런타임은 `git rev-parse`나 현재 작업 디렉터리로 저장소 루트를 암묵적으로 추론하지 않는다. 배포 이미지에 `.git` 디렉터리가 없을 수 있기 때문이다. 경로를 정규화한 뒤 실제 대상이 주입된 저장소 루트 아래에 남는지 다시 확인하며, 실패 시 `SOURCE_PATH_OUTSIDE_CORPUS` 또는 `SOURCE_SYMLINK_FORBIDDEN`을 반환한다.

### 원문 크기·형식·인코딩 가드

모든 형식은 파일 크기를 경로 검증 후 본문 전체를 메모리에 읽기 전에 확인한다.

HTML ingestion profile v1은 다음을 강제한다.

- HTML 원문 최대 크기는 `5,242,880` bytes(5 MiB)다. 초과하면 `SOURCE_TOO_LARGE`로 실패한다.
- 지원 형식은 구현이 명시적으로 허용한 확장자와 파일 signature가 일치해야 한다. 지원하지 않거나 불일치하면 `SOURCE_TYPE_UNSUPPORTED`로 실패한다.
- 텍스트는 UTF-8 또는 UTF-8 BOM만 허용하고 strict 모드로 디코딩한다. 휴리스틱 인코딩 감지나 자동 재인코딩은 하지 않는다.
- HTML charset 선언이 있으면 UTF-8이어야 한다. 서로 충돌하거나 UTF-8 이외의 선언이면 `SOURCE_ENCODING_INVALID`로 실패한다.
- 디코딩 오류의 원본 바이트, 문서 본문과 credential 값은 오류 응답·로그·감사 이벤트에 기록하지 않는다.

PDF ingestion profile v1은 현재 코퍼스 37개 PDF의 최대 크기 `81,906,956` bytes와 최대 `334` pages를 기준으로 다음을 강제한다.

- 확장자는 대소문자와 관계없이 `.pdf`여야 하고 파일의 첫 5 bytes는 ASCII `%PDF-`여야 한다. 둘 중 하나라도 다르면 `SOURCE_TYPE_UNSUPPORTED`로 실패한다. 파일 앞의 BOM, 공백, polyglot prefix는 허용하지 않는다.
- PDF 원문 최대 크기는 `104,857,600` bytes(100 MiB)다. 정확히 100 MiB는 허용하고 이를 초과하면 `SOURCE_TOO_LARGE`로 실패한다.
- 마지막 `2,048` bytes 안에 ASCII `%%EOF`가 있어야 한다. 이후 PDF parser가 trailer, cross-reference와 page tree를 정상적으로 해석하지 못하면 `SOURCE_STRUCTURE_INVALID`로 실패한다.
- 암호화 여부와 관계없이 비밀번호 입력이나 해제 시도를 하지 않는다. 암호화된 PDF는 `SOURCE_ENCRYPTED_UNSUPPORTED`로 실패한다.
- 페이지 수는 `1..500`만 허용한다. 0 pages 또는 500 pages 초과 문서는 `SOURCE_PAGE_LIMIT_EXCEEDED`로 실패한다.
- parser는 embedded file, JavaScript, launch action, rich media를 실행하거나 추출하지 않는다. 외부 URI도 호출하지 않는다. 능동 콘텐츠가 발견되면 `SOURCE_ACTIVE_CONTENT_FORBIDDEN`으로 실패한다.
- 원본 바이트, parser stack trace, 비밀번호 후보와 추출 본문은 오류 응답·로그·감사 이벤트에 기록하지 않는다.
- 검증 순서는 안전한 경로와 symlink → 확장자와 크기 → header와 EOF → SHA-256 → parser 구조 → 암호화·페이지·능동 콘텐츠 검사 순으로 고정한다.
- 입력 보안 검증을 통과했더라도 모든 페이지에서 정규화된 텍스트를 추출할 수 없으면 빈 chunk를 만들지 않고 `OCR_REQUIRED`로 실패한다. 5페이지 이상 PDF는 영숫자가 하나 이상 추출되는 페이지가 전체의 10% 미만이어도 이미지 기반 본문으로 판정하여 같은 코드로 실패한다. OCR은 이 profile에서 자동 실행하지 않는다.
- PDF 파생 chunk는 기존 nullable `page`를 시작 페이지로 채우고, 여러 페이지를 합친 범위를 `pageStart`와 `pageEnd`에 함께 기록한다. HTML chunk의 세 필드는 모두 `null`이다.
- HTML과 PDF의 구조·페이지 처리 알고리즘이 다르므로 PDF 파생 chunk는 `chunkerVersion=pdf-structure-ko-v1`, HTML은 기존 `structure-ko-v1`을 사용한다.

`pdf-source-validation-vectors.json`의 경계값은 Python 구현이 독립적으로 검증해야 한다. 실제 추출 parser의 성공 여부는 테스트용 합성 PDF fixture로 추가 검증한다.

향후 HWP/HWPX 등 형식을 추가할 때도 형식별 최대 바이트 수와 signature 검증 규칙을 구현 전에 이 계약에 추가한다.

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
