#!/usr/bin/env bash
set -Eeuo pipefail

REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND_DIRECTORY="${REPOSITORY_ROOT}/backend"
ENV_FILE="${COMPOSE_ENV_FILE:-${BACKEND_DIRECTORY}/.env.example}"
PROJECT_NAME="${COMPOSE_PROJECT_NAME:-alzs-well-smoke}"
DEMO_STAFF_USERNAME="${DEMO_STAFF_USERNAME:-$(sed -n 's/^DEMO_STAFF_USERNAME=//p' "${ENV_FILE}")}"
DEMO_STAFF_PASSWORD="${DEMO_STAFF_PASSWORD:-$(sed -n 's/^DEMO_STAFF_PASSWORD=//p' "${ENV_FILE}")}"
BACKEND_PORT="${BACKEND_PORT:-18080}"
export BACKEND_PORT
GATEWAY_PORT="${BACKEND_PORT}"
ARTIFACT_DIRECTORY="${COMPOSE_SMOKE_ARTIFACT_DIR:-${REPOSITORY_ROOT}/artifacts/compose-smoke}"
BASE_URL="http://127.0.0.1:${GATEWAY_PORT}"
EXPECTED_SCHEMA_VERSION="${EXPECTED_SCHEMA_VERSION:-$(sed -n 's/^    schema: "\([^"]*\)"/\1/p' "${BACKEND_DIRECTORY}/src/main/resources/application.yml")}"
SESSION_HEADER_FILE="$(mktemp "${TMPDIR:-/tmp}/alzs-well-smoke-headers.XXXXXX")"
STAFF_HEADER_FILE="$(mktemp "${TMPDIR:-/tmp}/alzs-well-smoke-staff-headers.XXXXXX")"
INGESTION_OUTPUT_FILE="$(mktemp "${TMPDIR:-/tmp}/alzs-well-smoke-ingestion.XXXXXX")"
IMPORT_PAYLOAD_FILE="$(mktemp "${TMPDIR:-/tmp}/alzs-well-smoke-import.XXXXXX")"
AI_MANIFEST="contracts/knowledge/fixtures/synthetic-copilot-approved-active.yaml"

# Compose smoke는 실제 내부 RAG 경로와 장애 폴백을 함께 검증한다.
export AI_RETRIEVAL_ENABLED=true
export COPILOT_RAG_ENABLED=true

test -n "${EXPECTED_SCHEMA_VERSION}"
test -n "${DEMO_STAFF_USERNAME}"
test -n "${DEMO_STAFF_PASSWORD}"

mkdir -p "${ARTIFACT_DIRECTORY}"
# 초기 개발 버전이 남긴 capability 헤더 증적은 재실행 전에 제거한다.
rm -f "${ARTIFACT_DIRECTORY}/session-headers.txt"
COMPOSE=(docker compose --project-directory "${BACKEND_DIRECTORY}" --env-file "${ENV_FILE}" --project-name "${PROJECT_NAME}")
COMPOSE_AI=("${COMPOSE[@]}" --profile ai)

psql_exec() {
  "${COMPOSE[@]}" exec --no-TTY postgres sh -c \
    'PGPASSWORD="$POSTGRES_PASSWORD" exec psql --set ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" "$@"' \
    sh "$@"
}

header_value() {
  local header_name="$1"
  local header_file="$2"
  awk -F ': ' -v expected="${header_name}" \
    'tolower($1) == tolower(expected) { sub(/\r$/, "", $2); print $2; exit }' "${header_file}"
}

cleanup() {
  exit_code=$?
  "${COMPOSE[@]}" ps --all > "${ARTIFACT_DIRECTORY}/compose-ps.txt" 2>&1 || true
  "${COMPOSE[@]}" logs --no-color --timestamps > "${ARTIFACT_DIRECTORY}/compose.log" 2>&1 || true
  "${COMPOSE[@]}" down --volumes --remove-orphans > "${ARTIFACT_DIRECTORY}/compose-down.log" 2>&1 || true
  rm -f "${SESSION_HEADER_FILE}" "${STAFF_HEADER_FILE}" \
    "${INGESTION_OUTPUT_FILE}" "${IMPORT_PAYLOAD_FILE}"
  exit "${exit_code}"
}
trap cleanup EXIT

"${COMPOSE[@]}" config --quiet
"${COMPOSE_AI[@]}" up --build --detach --wait --wait-timeout 300

ACTUATOR_STATUS="$(curl --silent --show-error --output "${ARTIFACT_DIRECTORY}/actuator-health.txt" \
  --write-out '%{http_code}' "${BASE_URL}/actuator/health")"
test "${ACTUATOR_STATUS}" = "404"

curl --fail --silent --show-error "${BASE_URL}/api/v1/system/readiness" \
  | tee "${ARTIFACT_DIRECTORY}/readiness.json" \
  | jq -e '.code == "SYSTEM_READY" and .data.ready == true and .data.checks.flyway == "UP"' > /dev/null

curl --fail --silent --show-error "${BASE_URL}/api/v1/system/versions" \
  | tee "${ARTIFACT_DIRECTORY}/versions.json" \
  | jq -e --arg schema_version "${EXPECTED_SCHEMA_VERSION}" \
      '.code == "SYSTEM_VERSIONS_RETRIEVED" and .data.schemaVersion == $schema_version' > /dev/null

curl --fail --silent --show-error "${BASE_URL}/api/v1/system/public-config" \
  | tee "${ARTIFACT_DIRECTORY}/public-config.json" \
  | jq -e '.data.syntheticDataOnly == true and .data.externalActionsEnabled == false
      and .data.externalEgressEnabled == false and .data.remoteModelEnabled == false' > /dev/null

session_status="$(curl --silent --show-error --output "${ARTIFACT_DIRECTORY}/session.json" \
  --dump-header "${SESSION_HEADER_FILE}" --write-out '%{http_code}' \
  --request POST "${BASE_URL}/api/v1/demo/sessions")"
test "${session_status}" = "201"
jq -e '.code == "DEMO_SESSION_CREATED" and .data.sessionId != null' \
  "${ARTIFACT_DIRECTORY}/session.json" > /dev/null
grep -qi '^X-Demo-Customer-Capability:' "${SESSION_HEADER_FILE}"
printf '%s\n' 'HTTP 201; X-Demo-Customer-Capability header present; value intentionally not retained' \
  > "${ARTIFACT_DIRECTORY}/session-contract.txt"

SESSION_ID="$(jq --raw-output '.data.sessionId' "${ARTIFACT_DIRECTORY}/session.json")"
CUSTOMER_CAPABILITY="$(header_value 'X-Demo-Customer-Capability' "${SESSION_HEADER_FILE}")"
test -n "${CUSTOMER_CAPABILITY}"

# 공개 배포에서는 로컬 로그인 API가 닫혀 있으므로 smoke 전용 관리자 세션을 DB에 직접 부트스트랩한다.
# 이후 문서 상태 전이와 ingestion 반영 자체는 반드시 기존 governance/import API를 통과한다.
ADMIN_ACCESS_TOKEN="$(openssl rand -hex 32)"
printf '%s' "${ADMIN_ACCESS_TOKEN}" | grep -Eq '^[0-9a-f]{64}$'
psql_exec --command "
  insert into auth_principal_role(principal_id, role_code)
  select principal_id, 'DETECTION_ADMIN' from auth_principal where login_id='synthetic-customer'
  on conflict do nothing;

  insert into auth_session(
    session_id, principal_id, access_token_hash, refresh_token_hash,
    access_expires_at, refresh_expires_at, created_at, last_rotated_at,
    revoked_at, revoke_reason, token_family_id, absolute_expires_at, compromised_at
  )
  select gen_random_uuid(), principal_id,
    encode(digest('${ADMIN_ACCESS_TOKEN}', 'sha256'), 'hex'),
    encode(digest('${ADMIN_ACCESS_TOKEN}-refresh', 'sha256'), 'hex'),
    now() + interval '30 minutes', now() + interval '1 hour', now(), now(),
    null, null, gen_random_uuid(), now() + interval '2 hours', null
  from auth_principal where login_id='synthetic-customer'
" > /dev/null

SOURCE_HASH="$(sed -n 's/^sourceHash: //p' "${REPOSITORY_ROOT}/${AI_MANIFEST}")"
printf '%s' "${SOURCE_HASH}" | grep -Eq '^sha256:[0-9a-f]{64}$'

jq --null-input --arg source_hash "${SOURCE_HASH}" --arg issuer "ALZ's well 테스트" '{
  documentId:"DOC-SYN-COPILOT-GROUNDING-001",
  versionLabel:"1.0.0",
  title:"합성 코파일럿 상담 근거",
  issuer:$issuer,
  sourceType:"SYNTHETIC_FIXTURE",
  sourcePath:"contracts/knowledge/fixtures/synthetic-copilot-source.html",
  sourceUrl:null,
  sourceHash:$source_hash,
  sourceTransformations:[],
  documentType:"SYNTHETIC_FIXTURE",
  classification:"INTERNAL",
  audience:"STAFF",
  allowedRoles:["PROTECTION_STAFF","DETECTION_ADMIN"],
  effectiveFrom:"2026-08-21",
  effectiveTo:null,
  checkedAt:"2026-08-26",
  usageRights:"SYNTHETIC_UNRESTRICTED",
  supersedesDocumentId:null,
  supersedesVersionLabel:null
}' > "${ARTIFACT_DIRECTORY}/rag-governance-register-request.json"

curl --fail --silent --show-error --request POST \
  "${BASE_URL}/api/v1/admin/knowledge/documents" \
  --header "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}" \
  --header 'Idempotency-Key: smoke-rag-governance-register-0001' \
  --header 'Content-Type: application/json' \
  --data-binary "@${ARTIFACT_DIRECTORY}/rag-governance-register-request.json" \
  | tee "${ARTIFACT_DIRECTORY}/rag-governance-register-response.json" \
  | jq -e '.code == "KNOWLEDGE_DOCUMENT_REGISTERED_FOR_REVIEW"
      and .data.approvalStatus == "IN_REVIEW"
      and .data.lifecycleStatus == "PENDING_ACTIVATION"' > /dev/null

curl --fail --silent --show-error --request POST \
  "${BASE_URL}/api/v1/admin/knowledge/documents/DOC-SYN-COPILOT-GROUNDING-001/publish" \
  --header "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}" \
  --header 'Idempotency-Key: smoke-rag-governance-publish-0001' \
  --header 'Content-Type: application/json' \
  --data '{"versionLabel":"1.0.0","expectedVersion":1,"approvalReference":"SMOKE-RAG-REVIEW-001"}' \
  | tee "${ARTIFACT_DIRECTORY}/rag-governance-publish-response.json" \
  | jq -e '.code == "KNOWLEDGE_DOCUMENT_PUBLISHED"
      and .data.approvalStatus == "APPROVED"
      and .data.lifecycleStatus == "ACTIVE"
      and .data.ingestionReady == true' > /dev/null

"${COMPOSE[@]}" --profile ai-tools run --build --no-deps --rm ai-ingestion \
  ingest-html --repo-root /workspace --manifest "${AI_MANIFEST}" \
  --as-of 2026-08-26 --storage postgres > "${INGESTION_OUTPUT_FILE}"
tail -n 1 "${INGESTION_OUTPUT_FILE}" > "${ARTIFACT_DIRECTORY}/rag-ai-ingestion.json"
jq -e '.ok == true and .code == "HTML_INGESTION_COMPLETED"
    and .storage == "POSTGRES" and .chunkCount > 0 and .runId != null' \
  "${ARTIFACT_DIRECTORY}/rag-ai-ingestion.json" > /dev/null
INGESTION_RUN_ID="$(jq --raw-output '.runId' "${ARTIFACT_DIRECTORY}/rag-ai-ingestion.json")"
printf '%s' "${INGESTION_RUN_ID}" \
  | grep -Eq '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'

# AI 전용 저장소의 검증된 파생 결과를 Spring import 계약으로 직렬화한다.
psql_exec --tuples-only --no-align --command "
  select jsonb_build_object(
    'contractVersion', '1.0.0',
    'ingestionRunId', r.run_id,
    'documentId', r.document_id,
    'versionLabel', r.version_label,
    'sourceHash', r.source_hash,
    'asOf', r.as_of,
    'extractorVersion', r.extractor_version,
    'chunkerVersion', r.chunker_version,
    'chunks', jsonb_agg(jsonb_build_object(
      'chunkId', c.chunk_id,
      'chunkOrder', c.chunk_order,
      'heading', c.heading,
      'sectionPath', to_jsonb(c.section_path),
      'page', c.page,
      'pageStart', c.page_start,
      'pageEnd', c.page_end,
      'text', c.content,
      'textHash', c.text_hash,
      'sourceHash', c.source_hash,
      'extractorVersion', c.extractor_version,
      'chunkerVersion', c.chunker_version
    ) order by c.chunk_order)
  )::text
  from ai_knowledge.ingestion_run r
  join ai_knowledge.chunk c on c.run_id=r.run_id
  where r.run_id='${INGESTION_RUN_ID}'::uuid and r.status='SUCCEEDED'
  group by r.run_id, r.document_id, r.version_label, r.source_hash, r.as_of,
           r.extractor_version, r.chunker_version
" > "${IMPORT_PAYLOAD_FILE}"
jq -e '.contractVersion == "1.0.0" and (.chunks | length) > 0' "${IMPORT_PAYLOAD_FILE}" > /dev/null

curl --fail --silent --show-error --request POST \
  "${BASE_URL}/api/v1/admin/knowledge/ingestion-imports" \
  --header "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}" \
  --header 'Idempotency-Key: smoke-rag-ingestion-import-0001' \
  --header 'Content-Type: application/json' \
  --data-binary "@${IMPORT_PAYLOAD_FILE}" \
  | tee "${ARTIFACT_DIRECTORY}/rag-spring-import-response.json" \
  | jq -e '.code == "KNOWLEDGE_INGESTION_IMPORTED"
      and .data.searchable == true and .data.chunkCount > 0' > /dev/null

staff_status="$(curl --silent --show-error --output "${ARTIFACT_DIRECTORY}/rag-staff-capability.json" \
  --dump-header "${STAFF_HEADER_FILE}" --write-out '%{http_code}' --request POST \
  --user "${DEMO_STAFF_USERNAME}:${DEMO_STAFF_PASSWORD}" \
  "${BASE_URL}/api/v1/demo/staff/sessions/${SESSION_ID}/capability")"
test "${staff_status}" = "200"
STAFF_CAPABILITY="$(header_value 'X-Demo-Staff-Capability' "${STAFF_HEADER_FILE}")"
test -n "${STAFF_CAPABILITY}"

curl --fail --silent --show-error --request POST \
  "${BASE_URL}/api/v1/demo/sessions/${SESSION_ID}/scenarios/FIN_MGMT_AB_001/ingest" \
  --header "X-Demo-Capability: ${CUSTOMER_CAPABILITY}" \
  --header 'Idempotency-Key: smoke-rag-demo-ingest-0001' \
  | tee "${ARTIFACT_DIRECTORY}/rag-demo-ingest.json" \
  | jq -e '.code == "DEMO_SCENARIO_INGESTED" and .data.demoRunId != null' > /dev/null
DEMO_RUN_ID="$(jq --raw-output '.data.demoRunId' "${ARTIFACT_DIRECTORY}/rag-demo-ingest.json")"

curl --fail --silent --show-error --request POST \
  "${BASE_URL}/api/v1/demo/sessions/${SESSION_ID}/alerts/ALERT_FIN_MGMT_001/context" \
  --header "X-Demo-Capability: ${CUSTOMER_CAPABILITY}" \
  --header "X-Demo-Run-Id: ${DEMO_RUN_ID}" \
  --header 'Idempotency-Key: smoke-rag-context-branch-b-0001' \
  --header 'Content-Type: application/json' \
  --data '{"responseCode":"UNABLE_TO_CONFIRM","demoBranchCode":"FIN_MGMT_B_NO_CONTEXT"}' \
  | tee "${ARTIFACT_DIRECTORY}/rag-branch-b.json" \
  | jq -e '.code == "ALERT_ESCALATED_TO_BANK_REVIEW"
      and .data.currentState == "PENDING_BANK_REVIEW"' > /dev/null

curl --fail --silent --show-error --request POST \
  "${BASE_URL}/api/v1/demo/sessions/${SESSION_ID}/cases/CASE_FIN_MGMT_001/copilot-drafts" \
  --header "X-Demo-Capability: ${STAFF_CAPABILITY}" \
  --header "X-Demo-Run-Id: ${DEMO_RUN_ID}" \
  --header 'Content-Type: application/json' \
  --data '{"draftType":"CONSULTATION_NOTE"}' \
  | tee "${ARTIFACT_DIRECTORY}/rag-copilot-grounded.json" \
  | jq -e '.code == "COPILOT_DRAFT_GENERATED"
      and .data.draft.generatedBy == "RAG_GROUNDED_TEMPLATE"
      and .data.draft.retrievalMode == "INTERNAL_RAG_HYBRID"
      and (.data.draft.citations | length) > 0
      and any(.data.draft.citations[]; .documentId == "DOC-SYN-COPILOT-GROUNDING-001")' > /dev/null

"${COMPOSE_AI[@]}" stop ai-service > /dev/null
curl --fail --silent --show-error --request POST \
  "${BASE_URL}/api/v1/demo/sessions/${SESSION_ID}/cases/CASE_FIN_MGMT_001/copilot-drafts" \
  --header "X-Demo-Capability: ${STAFF_CAPABILITY}" \
  --header "X-Demo-Run-Id: ${DEMO_RUN_ID}" \
  --header 'Content-Type: application/json' \
  --data '{"draftType":"CONSULTATION_NOTE"}' \
  | tee "${ARTIFACT_DIRECTORY}/rag-copilot-fallback.json" \
  | jq -e '.code == "COPILOT_DRAFT_GENERATED"
      and .data.draft.generatedBy == "DETERMINISTIC_TEMPLATE"
      and .data.draft.retrievalMode == "NONE"
      and .data.draft.fallbackUsed == true
      and (.data.draft.citations | length) == 0' > /dev/null

echo "Compose smoke test passed: PostgreSQL, Flyway V${EXPECTED_SCHEMA_VERSION}, backend, gateway, AI ingestion/search, grounded copilot, outage fallback"
