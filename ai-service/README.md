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

`validate-manifest`는 manifest 계약, 안전한 원문 경로, 크기·형식과 SHA-256을 검증한다.
HTML은 strict UTF-8 인코딩을, PDF는 구조·암호화·페이지 수와 능동 콘텐츠를 승인 전에
사전검증한다. 승인 및 생명주기는 결과에 표시하되, `IN_REVIEW` manifest 자체를 잘못된
manifest로 취급하지 않는다. 사전검증은 승인 행위가 아니며 본문 추출이나 chunk 생성도
수행하지 않는다. 실제 ingestion 진입점은 `ensure_ingestion_eligible`을 호출해 `APPROVED`,
`ACTIVE`, 명시적인 `asOf` 효력 조건을 추가로 강제한다.

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

합성 문서의 PostgreSQL ingestion부터 Spring 권위 카탈로그 import, FastAPI 하이브리드 검색,
사건 코파일럿 인용 및 AI 중단 시 결정론적 폴백까지 한 번에 검증하려면 저장소 루트에서
다음을 실행한다. 스크립트는 격리 Compose 프로젝트와 임시 볼륨만 사용하고 종료 시 폐기하며,
공식 원문을 승인하거나 외부 모델·외부 API를 호출하지 않는다.
합성 관리자 역할 부여와 Spring import payload 구성에 사용하는 DB 직접 접근도 이 폐기형
통합 테스트 내부로 제한되며, 운영 경로는 인증된 관리 API와 승인 워크플로만 사용한다.

```bash
python3 scripts/copilot_rag_e2e.py
```

PostgreSQL ingestion은 chunk와 함께 승인 manifest의 문서유형·ACL·audience·효력 스냅샷을
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
Spring이 문서 ID·버전·chunk 및 원문 해시를 최종 재검증해야 한다. 임계값을 통과한 결과는
`LAW > REGULATION > INTERNAL_POLICY > PUBLIC_GUIDE > PUBLIC_NOTICE > FORM` 순으로
권위 문서를 먼저 배치하고, 같은 유형 안에서는 하이브리드 점수로 정렬한다.

벡터는 `ai_knowledge.chunk_embedding`에 chunk·모델 ID·고정 모델 버전·차원과 함께
저장한다. 384차원 Hash/E5와 1024차원 Arctic-ko가 같은 chunk에 공존할 수 있고 검색은
현재 provider와 모델 ID·버전·차원이 모두 일치하는 행만 사용한다. 승인된 고정 revision마다
HNSW 부분 인덱스를 분리해 서로 다른 모델의 벡터 공간을 섞지 않는다. 기존
`ai_knowledge.chunk.embedding`은 순차 배포 호환성을 위한 Hash 전용 레거시 컬럼이며
신규 검색 경로에서는 사용하지 않는다.
같은 결정론적 chunk를 다른 모델로 재-ingestion하면 chunk 행을 유지하고 해당 모델의
벡터만 upsert하므로 기존 모델 벡터가 함께 보존된다. 새 파이프라인 결과에서 사라진
chunk만 삭제하며, 그때 해당 chunk의 모든 파생 벡터도 cascade로 정리한다.

### 로컬 한국어 임베딩 모델

운영 설정에서 명시적으로 활성화할 수 있는 신경망 후보는
`intfloat/multilingual-e5-small`과
`dragonkue/snowflake-arctic-embed-l-v2.0-ko`다. E5는 384차원이고 Arctic-ko는
1024차원이다. 두 모델의 벡터는 모델 ID·revision·차원별로 분리되며, 모델이 승인·반입되기
전에는 hash 어댑터가 기본값이다. 실행 중 외부 모델 다운로드는 허용하지 않는다.

E5와 Arctic-ko는 CPU 전용 공용 SentenceTransformer 어댑터를 사용한다. 모델별 ID·차원과
질의·문단 prefix는 명시적인 사양으로 분리한다. E5는 질의에 `query:`, 문단에 `passage:`를
붙이고 Arctic-ko는 질의에만 `query:`를 붙인다. 공용 로더는 항상
`local_files_only=True`, `trust_remote_code=False`, `device=cpu`로 실행한다.

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

Arctic-ko는 저장소에 고정된 revision과 SHA-256이 정확히 일치할 때만 선택된다. 로컬
반입본으로 모델 런타임 오버레이를 사용할 때는 저장소 루트에서 다음처럼 실행한다.

```bash
AI_MODEL_HOST_ROOT="$PWD/models" docker compose \
  -f backend/compose.yaml \
  -f backend/compose.arctic-ko.yaml \
  --profile ai up --build
```

`Dockerfile.model-runtime`은 `model-runtime` 의존성 그룹의 고정 버전을 설치하며 Linux에서는
공식 PyTorch CPU wheel만 사용한다. 모델 파일은 이미지에 복사하지 않고 읽기 전용 volume으로
마운트한다. Compose 오버레이는 각 AI 컨테이너에 3 GiB와 CPU 2개를 배정한다.

모델 경로는 root 기준 상대경로만 허용하고 `../`, 심볼릭 링크, 해시 불일치를 거부한다.
해시 불일치는 fallback하지 않으며, 검증된 모델이 런타임에서 로드되지 않을 때만 기본
hash 어댑터로 시작할 수 있다. 검색 시 다른 모델 버전으로 생성된 벡터에는 cosine 점수를
적용하지 않지만 keyword 검색 대상에서는 제외하지 않는다. 모델을 전환하면 승인 문서를
새 모델 버전으로 재-ingestion한 뒤 동일 검수 평가셋으로 Recall@K와 MRR을 다시 측정한다.

Arctic-ko는 품질 검토 상태가 `EVALUATION_ONLY`이므로 기본 모델로 자동 승격하지 않는다.
다만 제한된 합성 E2E와 부하 시험에서는 `local-arctic-ko` backend로 활성화할 수 있다.
고정 artifact 검증, 1024차원 pgvector 저장, 모델별 인덱스, 재-ingestion 및 Spring citation
재검증이 모두 통과한 뒤에만 운영 기본값 변경을 별도 승인한다.

Arctic-ko의 격리 부하 게이트는 합성 문서를 재-ingestion한 뒤 FastAPI 내부 검색과 Spring
검색 API를 각각 동시성 4, 100건으로 측정한다. p95 1초 이하, 오류율 0%, 처리량 2 RPS
이상, AI 프로세스 peak RSS 2.5 GiB 이하, 기동 30초 이하를 모두 만족해야 한다. Linux
`/proc/1/status`의 `VmHWM`을 peak RSS로 사용하며, 모델 파일 page cache가 포함된 cgroup
`memory.peak`도 별도 증거로 남긴다.

```bash
AI_MODEL_HOST_ROOT="$PWD/models" \
COPILOT_RAG_EXTRA_COMPOSE_FILE=backend/compose.arctic-ko.yaml \
COPILOT_RAG_EMBEDDING_MODE=arctic-ko \
COPILOT_RAG_LOAD_TEST_ENABLED=true \
AI_LOAD_TEST_PORT=18085 \
BACKEND_LOAD_TEST_PORT=18086 \
COMPOSE_PROJECT_NAME=alzs-well-arctic-load-e2e \
BACKEND_PORT=18084 \
COPILOT_RAG_E2E_ARTIFACT_DIR=/tmp/alzs-well-arctic-load-e2e \
ai-service/.venv/bin/python scripts/copilot_rag_e2e.py
```

`compose.load-test.yaml`은 이 명시적 시험에서만 FastAPI와 Spring을 `127.0.0.1`에
노출한다. Spring 구간은 AI 처리시간을 측정하기 위해 Nginx의 쓰기 요청 rate limit을
우회하지만 인증·권한·ACL·인용 재검증은 그대로 통과한다. 운영 Compose의 내부 네트워크
격리는 변경하지 않는다. 실행 결과는 지정한 artifact 디렉터리의 `load-test.json`,
`load-test.md`, `result.json`에 기록된다. 기준 실측은
[`evaluation/arctic-ko-load-test-v1.md`](evaluation/arctic-ko-load-test-v1.md)에 남긴다.

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
