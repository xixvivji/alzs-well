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

## 합성 코파일럿 Spring 검증 import

AI DB ingestion만으로는 코파일럿 citation이 활성화되지 않는다. Spring 권위 저장소에 등록·게시하고 같은 DB의 정확한 `SUCCEEDED` run과 전체 chunk를 다시 검증해 import해야 한다.

AWS staging 최초 구성에서는 App EC2의 Session Manager 승인 세션에서 다음 스크립트를 실행한다.

```bash
sudo /opt/alzs-well/repository/infra/aws-staging/import-synthetic-copilot-knowledge.sh
```

스크립트는 공개 listener와 분리된 `127.0.0.1:18080` 임시 Spring 컨테이너만 사용한다. 합성 principal에 `DETECTION_ADMIN` 역할을 실행 중에만 부여하고 종료 trap에서 역할·컨테이너·임시 파일을 제거한다. 등록·게시·import는 기존 API와 불변 감사이력을 통과하며, DB 직접 catalog 삽입은 하지 않는다. 이미 검증 import가 한 건 있으면 아무 것도 변경하지 않고 종료하고, 일부 단계만 남은 상태는 자동 복구하지 않고 수동 검토로 중단한다.

완료 뒤 운영 Vercel 경로에서 주의 시나리오를 다시 실행해 다음을 확인한다.

- `generatedBy=RAG_GROUNDED_TEMPLATE`
- `fallbackUsed=false`
- citation 1건 이상
- Spring citation proof와 AI readiness 모두 정상
