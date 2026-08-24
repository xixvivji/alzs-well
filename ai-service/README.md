# ALZ's well AI service

폐쇄망에서 승인된 지식 원문을 검증·추출·청킹하고 이후 검색 인덱스를 만드는 내부 AI/RAG 프로젝트다.
현재 단계는 공용 지식 계약 v1을 소비하는 manifest 및 HTML 원문 검증 CLI만 제공한다.

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

## 테스트

```bash
uv run pytest
uv run pytest --cov=app --cov-report=term-missing
```

원문, 오류 메시지, credential과 디코딩 실패 바이트는 로그나 CLI 오류에 출력하지 않는다.
