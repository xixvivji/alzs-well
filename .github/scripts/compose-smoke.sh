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
SESSION_HEADER_FILE="$(mktemp "${TMPDIR:-/tmp}/alzs-well-smoke-headers.XXXXXX")"

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

curl --fail --silent --show-error "${BASE_URL}/actuator/health" \
  | tee "${ARTIFACT_DIRECTORY}/health.json" \
  | jq -e '.status == "UP"' > /dev/null

curl --fail --silent --show-error "${BASE_URL}/api/v1/system/readiness" \
  | tee "${ARTIFACT_DIRECTORY}/readiness.json" \
  | jq -e '.code == "SYSTEM_READY" and .data.ready == true and .data.checks.flyway == "UP"' > /dev/null

curl --fail --silent --show-error "${BASE_URL}/api/v1/system/versions" \
  | tee "${ARTIFACT_DIRECTORY}/versions.json" \
  | jq -e '.code == "SYSTEM_VERSIONS_RETRIEVED" and .data.schemaVersion == "33"' > /dev/null

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

echo "Compose smoke test passed: PostgreSQL, Flyway V33, backend, gateway, readiness, guardrails, demo session"
