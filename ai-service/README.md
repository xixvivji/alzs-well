# ALZ's well AI service

폐쇄망에서 승인된 지식 원문을 검증·추출·청킹하고 이후 검색 인덱스를 만드는 내부 AI/RAG 프로젝트다.
현재 단계는 공용 지식 계약 v1을 소비하는 manifest, HTML/PDF 원문 검증과 결정론적
chunk 생성 CLI, PostgreSQL·pgvector 하이브리드 검색용 내부 FastAPI를 제공한다.

## 실행

의존성을 설치하고 합성 승인 fixture를 검증한다.

```bash
uv sync
uv run python -m app.cli validate-manifest \
  --repo-root .. \
  --manifest contracts/knowledge/fixtures/synthetic-approved-active.yaml
```

저장소 루트는 현재 작업 디렉터리에서 추론하지 않는다. `--repo-root`를 생략하면
`ALZS_REPO_ROOT` 환경변수를 사용하고, 둘 다 없으면 `REPOSITORY_ROOT_REQUIRED`로 실패한다.

`validate-manifest`는 manifest 계약, 안전한 원문 경로, 크기·형식·인코딩과 SHA-256을
검증한다. 승인 및 생명주기는 결과에 표시하되, `IN_REVIEW` manifest 자체를 잘못된
manifest로 취급하지 않는다. 실제 ingestion 진입점은 `ensure_ingestion_eligible`을 호출해
`APPROVED`, `ACTIVE`, 명시적인 `asOf` 효력 조건을 추가로 강제한다.

승인된 합성 HTML의 구조화 추출을 검증한다. CLI에는 원문 본문을 출력하지 않는다.

```bash
uv run python -m app.cli extract-html \
  --repo-root .. \
  --manifest contracts/knowledge/fixtures/synthetic-approved-active.yaml \
  --as-of 2026-08-21
```

manifest 검증부터 결정론적 청킹과 JSONL 원자 교체까지 전체 ingestion을 실행한다.
파생 결과는 Git에서 제외된 `ai-service/data/derived/chunks/`에 저장한다.

```bash
uv run python -m app.cli ingest-html \
  --repo-root .. \
  --manifest contracts/knowledge/fixtures/synthetic-approved-active.yaml \
  --as-of 2026-08-21
```

승인된 PDF는 텍스트 추출 전에 크기·signature·SHA-256·구조·암호화·페이지 수와
능동 콘텐츠 여부를 검증한다. 본문이나 PDF 객체는 CLI에 출력하지 않는다.

```bash
uv run python -m app.cli validate-pdf \
  --repo-root .. \
  --manifest path/to/approved-pdf-manifest.yaml \
  --as-of 2026-08-25
```

검증된 PDF에서 텍스트를 페이지별로 추출한다. 반복 머리말·꼬리말과 페이지 번호를
제거하고 제목 계층을 구성하지만, 추출 본문은 CLI 응답에 포함하지 않는다.

```bash
uv run python -m app.cli extract-pdf \
  --repo-root .. \
  --manifest path/to/approved-pdf-manifest.yaml \
  --as-of 2026-08-25
```

PDF 검증부터 페이지 범위가 포함된 JSONL 원자 교체까지 전체 ingestion을 실행한다.
텍스트 계층이 없거나 5페이지 이상 문서에서 검색 가능한 페이지 비율이 10% 미만이면
빈 검색 결과를 만들지 않고 `OCR_REQUIRED`로 종료한다.

```bash
uv run python -m app.cli ingest-pdf \
  --repo-root .. \
  --manifest path/to/approved-pdf-manifest.yaml \
  --as-of 2026-08-25
```

기본 저장소는 기존과 동일한 `jsonl`이다. PostgreSQL을 선택하면 Spring의 업무
`knowledge_*` 테이블이 아니라 AI 파생 데이터 전용 `ai_knowledge.chunk`와
`ai_knowledge.ingestion_run`에 원자적으로 저장한다. 접속 비밀번호는 명령행 인자로
받지 않고 `ALZS_AI_DB_*` 환경변수로만 주입한다.

```bash
set -a
source .env
set +a
uv run python -m app.cli ingest-pdf \
  --repo-root .. \
  --manifest path/to/approved-pdf-manifest.yaml \
  --as-of 2026-08-25 \
  --storage postgres
```

같은 `documentId`와 `versionLabel`의 재실행은 advisory lock 안에서 기존 파생 chunk를
새 결정론적 결과로 교체한다. 성공 시 실행 상태와 chunk 교체가 같은 트랜잭션으로
커밋되며, 추출·청킹 실패 시 본문이나 원문 없이 안전한 오류코드만 `FAILED` 실행에 남긴다.

운영 compose에서는 DB 포트를 외부에 공개하지 않고 일회성 도구 프로필로 실행한다.

```bash
docker compose --project-directory backend --profile ai-tools run --rm ai-ingestion \
  ingest-pdf \
  --repo-root /workspace \
  --manifest knowledge/manifests/approved-document.yaml \
  --as-of 2026-08-25 \
  --storage postgres
```

PostgreSQL ingestion은 chunk와 함께 승인 manifest의 ACL·audience·효력 스냅샷을
`ai_knowledge.document_snapshot`에 저장한다. 검색 런타임은 별도 최소 권한 계정으로
이 스냅샷과 chunk만 읽으며 Spring 업무 지식 테이블은 조회하거나 변경하지 않는다.

내부 검색 API는 다음처럼 실행한다. 호스트 포트를 공개하지 않고 Spring과 같은 내부
애플리케이션 네트워크에서만 접근한다.

```bash
docker compose --project-directory backend --profile ai up -d ai-service
```

```http
POST /internal/v1/search
X-Internal-Service-Token: <32자 이상의 내부 서비스 토큰>
Content-Type: application/json

{
  "contractVersion": "1.0.0",
  "requestId": "99000000-0000-0000-0000-000000000001",
  "query": "금융거래 안심차단",
  "permissions": ["KNOWLEDGE_SEARCH"],
  "principalRoles": ["PROTECTION_STAFF"],
  "requesterAudiences": ["STAFF"],
  "asOf": "2026-08-25",
  "limit": 10
}
```

검색은 PostgreSQL `simple` 전문검색과 기본 `local-hash-ngram-ko-v1` 384차원 임베딩의
pgvector cosine 유사도를 결합하고 역할 교집합, audience,
`APPROVED/ACTIVE`, 효력기간을 모두 만족하는 chunk만 반환한다. 감사 이력에는 원문
검색어 대신 `sha256:<hex>`만 남긴다. 응답 citation은 권한 부여 결과가 아니므로
Spring이 문서 ID·버전·chunk 및 원문 해시를 최종 재검증해야 한다.

### 로컬 한국어 임베딩 모델

신경망 모델의 첫 운영 후보는 `intfloat/multilingual-e5-small`이다. 한국어를 포함하는
다국어 검색 모델이며 출력이 384차원이어서 현재 pgvector 스키마를 변경하지 않는다.
모델이 승인·반입되기 전에는 hash 어댑터가 기본값이고 외부 모델 다운로드는 허용하지
않는다. E5 어댑터는 질의에 `query:`, 문단에 `passage:` 접두사를 적용한다.

모델 포함 이미지는 승인된 `sentence-transformers` CPU wheel과 전이 의존성을 내부
wheelhouse 및 SBOM으로 함께 반입해야 한다. 기본 이미지는 모델 런타임을 설치하지 않으며,
실행 중 패키지나 모델을 다운로드하지 않는다.

활성화할 때는 승인된 `model.safetensors`를 읽기 전용 경로에 반입하고 다음 값을 모두
지정한다.

```text
ALZS_EMBEDDING_BACKEND=local-e5
ALZS_EMBEDDING_MODEL_ROOT=/opt/alzs-well/models
ALZS_EMBEDDING_MODEL_PATH=multilingual-e5-small
ALZS_EMBEDDING_MODEL_REVISION=<승인된 revision>
ALZS_EMBEDDING_MODEL_SHA256=sha256:<model.safetensors의 64자리 lowercase hex>
ALZS_EMBEDDING_ALLOW_HASH_FALLBACK=true
```

모델 경로는 root 기준 상대경로만 허용하고 `../`, 심볼릭 링크, 해시 불일치를 거부한다.
해시 불일치는 fallback하지 않으며, 검증된 모델이 런타임에서 로드되지 않을 때만 기본
hash 어댑터로 시작할 수 있다. 검색 시 다른 모델 버전으로 생성된 벡터에는 cosine 점수를
적용하지 않지만 keyword 검색 대상에서는 제외하지 않는다. 모델을 전환하면 승인 문서를
새 모델 버전으로 재-ingestion한 뒤 동일 검수 평가셋으로 Recall@K와 MRR을 다시 측정한다.

최종 결합 점수가 `0.35` 미만이면 관련 keyword가 일부 겹치더라도 결과를 반환하지 않는다.
이 무응답 임계값과 keyword/vector 가중치는 합성 검색 평가 데이터셋의 Recall@K, MRR,
무응답 오탐률 및 정책 위반 게이트로 회귀 검증한다. 실행법과 지표 해석은
[`evaluation/README.md`](evaluation/README.md)를 따른다.

사람 검수용 50개 질문·정답 후보는 `evaluation/reviews/retrieval-review-v1.csv`에 있다.
검수자는 `reviewDecision`과 `reviewComment`만 작성하며, `ACCEPTED`로 확인된 행만 별도
평가 데이터셋으로 승격할 수 있다. 현재 후보는 합성 corpus 전용이며 공식문서 승인이나
운영 품질을 의미하지 않는다.

## 테스트

```bash
uv run pytest
uv run pytest --cov=app --cov-report=term-missing
```

원문, 오류 메시지, credential과 디코딩 실패 바이트는 로그나 CLI 오류에 출력하지 않는다.
