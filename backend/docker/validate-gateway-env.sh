#!/bin/sh
set -eu

secret="${FRONTEND_PROXY_SHARED_SECRET:-}"
placeholder="replace-with-64-lowercase-hex"

if [ "$secret" = "$placeholder" ] || ! printf '%s\n' "$secret" | grep -Eq '^[0-9a-f]{64}$'; then
    printf '%s\n' 'FRONTEND_PROXY_SHARED_SECRET must be a non-default 32-byte lowercase hex secret' >&2
    exit 1
fi

exec /docker-entrypoint.sh "$@"
