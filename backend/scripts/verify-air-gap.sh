#!/usr/bin/env bash
set -euo pipefail

if docker compose exec -T backend timeout 3 bash -c '</dev/tcp/1.1.1.1/443' 2>/dev/null; then
  echo "FAIL: backend container reached an external HTTPS endpoint" >&2
  exit 1
fi

docker compose exec -T backend bash -c '</dev/tcp/postgres/5432'

if docker compose exec -T gateway sh -c 'nc -z -w 3 postgres 5432' 2>/dev/null; then
  echo "FAIL: gateway container reached PostgreSQL directly" >&2
  exit 1
fi

echo "PASS: backend egress is blocked, backend-to-DB works, and gateway-to-DB is isolated"
