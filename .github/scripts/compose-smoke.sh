#!/usr/bin/env bash
set -Eeuo pipefail

REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND_DIRECTORY="${REPOSITORY_ROOT}/backend"
ENV_FILE="${COMPOSE_ENV_FILE:-${BACKEND_DIRECTORY}/.env.example}"
PROJECT_NAME="${COMPOSE_PROJECT_NAME:-alzs-well-smoke}"
BACKEND_PORT="${BACKEND_PORT:-18080}"
export BACKEND_PORT
GATEWAY_PORT="${BACKEND_PORT}"
ARTIFACT_DIRECTORY="${COMPOSE_SMOKE_ARTIFACT_DIR:-${REPOSITORY_ROOT}/artifacts/compose-smoke}"
BASE_URL="http://127.0.0.1:${GATEWAY_PORT}"
EXPECTED_SCHEMA_VERSION="${EXPECTED_SCHEMA_VERSION:-}"
if [[ -z "${EXPECTED_SCHEMA_VERSION}" ]]; then
  EXPECTED_SCHEMA_VERSION="$(sed -n 's/^    schema: "\([^"]*\)"/\1/p' \
    "${BACKEND_DIRECTORY}/src/main/resources/application.yml")"
fi
SESSION_HEADER_FILE="$(mktemp "${TMPDIR:-/tmp}/alzs-well-smoke-headers.XXXXXX")"
PROXY_SECRET_FOR_SMOKE="${FRONTEND_PROXY_SHARED_SECRET:-}"

if [[ -z "${PROXY_SECRET_FOR_SMOKE}" || "${PROXY_SECRET_FOR_SMOKE}" = "replace-with-64-lowercase-hex" ]]; then
  command -v openssl > /dev/null || {
    printf '%s\n' 'openssl is required to create an isolated smoke-test proxy secret' >&2
    exit 1
  }
  PROXY_SECRET_FOR_SMOKE="$(openssl rand -hex 32)"
elif [[ ! "${PROXY_SECRET_FOR_SMOKE}" =~ ^[a-f0-9]{64}$ ]]; then
  printf '%s\n' 'FRONTEND_PROXY_SHARED_SECRET must be a 32-byte lowercase hex value' >&2
  exit 1
fi
export FRONTEND_PROXY_SHARED_SECRET="${PROXY_SECRET_FOR_SMOKE}"

test -n "${EXPECTED_SCHEMA_VERSION}"

mkdir -p "${ARTIFACT_DIRECTORY}"
# 초기 개발 버전이 남긴 capability 헤더 증적은 재실행 전에 제거한다.
rm -f "${ARTIFACT_DIRECTORY}/session-headers.txt"
COMPOSE=(docker compose --project-directory "${BACKEND_DIRECTORY}" --env-file "${ENV_FILE}" --project-name "${PROJECT_NAME}")

cleanup() {
  exit_code=$?
  "${COMPOSE[@]}" ps --all > "${ARTIFACT_DIRECTORY}/compose-ps.txt" 2>&1 || true
  "${COMPOSE[@]}" logs --no-color --timestamps > "${ARTIFACT_DIRECTORY}/compose.log" 2>&1 || true
  "${COMPOSE[@]}" down --volumes --remove-orphans > "${ARTIFACT_DIRECTORY}/compose-down.log" 2>&1 || true
  rm -f "${SESSION_HEADER_FILE}"
  exit "${exit_code}"
}
trap cleanup EXIT

"${COMPOSE[@]}" config --quiet
"${COMPOSE[@]}" up --build --detach --wait --wait-timeout 300

ACTUATOR_STATUS="$(curl --silent --show-error --output "${ARTIFACT_DIRECTORY}/actuator-health.txt" \
  --write-out '%{http_code}' "${BASE_URL}/actuator/health")"
test "${ACTUATOR_STATUS}" = "404"

curl --fail --silent --show-error "${BASE_URL}/api/v1/system/readiness" \
  | tee "${ARTIFACT_DIRECTORY}/readiness.json" \
  | jq -e '.code == "SYSTEM_READY" and .data.ready == true and .data.checks.flyway == "UP"' > /dev/null

INVALID_PROXY_STATUS="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
  --header 'X-Alzs-Client-Key: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' \
  "${BASE_URL}/api/v1/system/readiness")"
test "${INVALID_PROXY_STATUS}" = "403"
INVALID_SECRET_STATUS="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
  --header 'X-Alzs-Proxy-Secret: invalid' \
  --header 'X-Alzs-Client-Key: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' \
  "${BASE_URL}/api/v1/system/readiness")"
test "${INVALID_SECRET_STATUS}" = "403"
curl --fail --silent --show-error \
  --header "X-Alzs-Proxy-Secret: ${PROXY_SECRET_FOR_SMOKE}" \
  --header 'X-Alzs-Client-Key: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' \
  "${BASE_URL}/api/v1/system/readiness" > /dev/null

curl --fail --silent --show-error "${BASE_URL}/api/v1/system/versions" \
  | tee "${ARTIFACT_DIRECTORY}/versions.json" \
  | jq -e --arg schema_version "${EXPECTED_SCHEMA_VERSION}" \
      '.code == "SYSTEM_VERSIONS_RETRIEVED" and .data.schemaVersion == $schema_version' > /dev/null

"${COMPOSE[@]}" exec -T postgres psql -X -qAt \
  -U "${POSTGRES_USER:-alzswell_admin}" -d "${POSTGRES_DB:-alzs_well}" \
  -c "select json_build_object(
      'tableExists',to_regclass('ai_knowledge.chunk_embedding') is not null,
      'dimensionConstraintExists',exists(
        select 1 from pg_constraint
        where conname='ck_ai_chunk_embedding_vector_dimensions'),
      'hnsw384Count',count(*) filter (
        where indexdef ilike '%using hnsw%'
          and indexdef like '%vector(384)%'),
      'hnsw1024Count',count(*) filter (
        where indexdef ilike '%using hnsw%'
          and indexdef like '%vector(1024)%'))
    from pg_indexes
    where schemaname='ai_knowledge' and tablename='chunk_embedding'" \
  > "${ARTIFACT_DIRECTORY}/pgvector-multi-dimension.json"
jq -e '.tableExists == true and .dimensionConstraintExists == true
    and .hnsw384Count == 2 and .hnsw1024Count == 1' \
  "${ARTIFACT_DIRECTORY}/pgvector-multi-dimension.json" > /dev/null

curl --fail --silent --show-error "${BASE_URL}/api/v1/system/public-config" \
  | tee "${ARTIFACT_DIRECTORY}/public-config.json" \
  | jq -e '.data.syntheticDataOnly == true and .data.externalActionsEnabled == false
      and .data.externalEgressEnabled == false and .data.remoteModelEnabled == false' > /dev/null

SYNTHETIC_SEED_PROFILE=SMOKE \
SYNTHETIC_SEED_VERIFY_DETECTION=true \
"${COMPOSE[@]}" --profile synthetic-tools run --build --rm synthetic-seed

"${COMPOSE[@]}" exec -T postgres psql -X -qAt \
  -U "${POSTGRES_USER:-alzswell_admin}" -d "${POSTGRES_DB:-alzs_well}" \
  -c "select json_build_object(
      'status',q.status,
      'evaluatedCustomerCount',q.evaluated_customer_count,
      'expectedSignalCount',q.expected_signal_count,
      'actualSignalCount',q.actual_signal_count,
      'falsePositiveCount',q.false_positive_count,
      'falseNegativeCount',q.false_negative_count,
      'precision',q.precision_score,
      'recall',q.recall_score,
      'reportHashLength',length(q.report_hash))
    from synthetic_fixture_quality_report q
    join synthetic_fixture_generation_run r on r.run_id=q.run_id
    where r.profile='SMOKE' and r.seed=20260825
    order by q.evaluated_at desc limit 1" \
  > "${ARTIFACT_DIRECTORY}/synthetic-quality.json"
jq -e '.status == "PASSED" and .evaluatedCustomerCount == 10
    and .expectedSignalCount == .actualSignalCount
    and .falsePositiveCount == 0 and .falseNegativeCount == 0
    and .precision == 1 and .recall == 1 and .reportHashLength == 64' \
  "${ARTIFACT_DIRECTORY}/synthetic-quality.json" > /dev/null

session_status="$(curl --silent --show-error --output "${ARTIFACT_DIRECTORY}/session.json" \
  --dump-header "${SESSION_HEADER_FILE}" --write-out '%{http_code}' \
  --request POST "${BASE_URL}/api/v1/demo/sessions")"
test "${session_status}" = "201"
jq -e '.code == "DEMO_SESSION_CREATED" and .data.sessionId != null' \
  "${ARTIFACT_DIRECTORY}/session.json" > /dev/null
grep -qi '^X-Demo-Customer-Capability:' "${SESSION_HEADER_FILE}"
printf '%s\n' 'HTTP 201; X-Demo-Customer-Capability header present; value intentionally not retained' \
  > "${ARTIFACT_DIRECTORY}/session-contract.txt"

echo "Compose smoke test passed: PostgreSQL, Flyway V${EXPECTED_SCHEMA_VERSION}, backend, gateway, readiness, guardrails, synthetic detection quality, demo session"
