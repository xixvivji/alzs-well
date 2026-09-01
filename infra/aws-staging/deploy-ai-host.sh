#!/usr/bin/env bash
set -Eeuo pipefail

repository_root="/opt/alzs-well/repository"
runtime_root="/opt/alzs-well/runtime"
environment_file="$runtime_root/.env.aws-ai"
compose_file="$repository_root/backend/compose.aws-ai.yaml"

docker compose --env-file "$environment_file" -f "$compose_file" up --detach ai-service ai-gateway
for attempt in $(seq 1 60); do
  health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' alzs-well-ai-ai-gateway-1 2>/dev/null || true)"
  if [[ $health == "healthy" ]]; then
    docker compose --env-file "$environment_file" -f "$compose_file" exec -T ai-service \
      python -c "import urllib.request; print(urllib.request.urlopen('http://127.0.0.1:8000/readiness', timeout=5).read().decode())"
    exit 0
  fi
  sleep 10
done
docker compose --env-file "$environment_file" -f "$compose_file" ps
echo "AI readiness timed out" >&2
exit 1
