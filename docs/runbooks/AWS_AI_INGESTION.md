# AWS AI ingestion 런북

ingestion은 HTTP API가 아니라 AI EC2의 Session Manager 승인 명령으로만 실행한다. 공식 원문은 `APPROVED + ACTIVE + 효력기간 충족` 전까지 적재하지 않으며 최초 검증은 승인된 합성 fixture로 수행한다.

1. 승인 PR, manifest `sourceHash`, 기준일, document/version을 이중 확인한다.
2. S3 object version과 SHA-256을 검증해 읽기 전용 repository 경로에 배치한다.
3. Secrets Manager에서 ingestion 전용 DB 자격증명을 임시 주입한다.
4. `validate-manifest` 실행 후 승인된 명령만 실행한다.

```bash
docker compose --env-file .env.aws-ai -f compose.aws-ai.yaml --profile ingestion run --rm ingestion \
  ingest-html --repo-root /workspace --manifest contracts/knowledge/fixtures/synthetic-approved-active.yaml \
  --as-of 2026-08-29 --storage postgres
```

PDF는 `ingest-pdf`를 사용한다. 로그에는 credential, 원문 본문, 자유입력, 내부 토큰을 남기지 않고 run ID·문서 ID·version·hash·결과 코드만 남긴다. 완료 후 container 종료, terminal run, chunk 수, Spring import/citation 재검증을 확인한다. 실패 run은 성공으로 바꾸지 말고 새 run으로 재실행한다.
