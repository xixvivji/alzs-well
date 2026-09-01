#!/usr/bin/env bash
set -Eeuo pipefail

repository_root="/opt/alzs-well/repository"
runtime_root="/opt/alzs-well/runtime"
environment_file="$runtime_root/.env.aws-ai"
compose_file="$repository_root/backend/compose.aws-ai.yaml"

docker compose --env-file "$environment_file" -f "$compose_file" up --detach --force-recreate ai-service ai-gateway
gateway_container_id="$(docker compose --env-file "$environment_file" -f "$compose_file" ps -q ai-gateway)"
if [[ -z "$gateway_container_id" ]]; then
  echo "AI gateway container was not created" >&2
  exit 1
fi
for attempt in $(seq 1 60); do
  health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$gateway_container_id" 2>/dev/null || true)"
  if [[ $health == "healthy" ]]; then
    break
  fi
  sleep 10
done
if [[ $health != "healthy" ]]; then
  docker compose --env-file "$environment_file" -f "$compose_file" ps
  echo "AI gateway health timed out" >&2
  exit 1
fi
for attempt in $(seq 1 30); do
  if readiness="$(docker compose --env-file "$environment_file" -f "$compose_file" exec -T ai-service \
      python -c "import urllib.request; print(urllib.request.urlopen('http://127.0.0.1:8000/readiness', timeout=30).read().decode())" 2>/dev/null)"; then
    printf '%s\n' "$readiness"
    exit 0
  fi
  sleep 10
done
docker compose --env-file "$environment_file" -f "$compose_file" ps
echo "AI readiness timed out" >&2
exit 1
