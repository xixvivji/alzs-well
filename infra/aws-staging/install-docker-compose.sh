#!/usr/bin/env bash
set -Eeuo pipefail

version="v5.4.0"
expected_sha256="837fd1d35bf6a494f41b5b5988269a7be79de337cf1a1a6ff0e45ab51bb4e9be"
plugin_dir="/usr/local/libexec/docker/cli-plugins"
plugin_path="$plugin_dir/docker-compose"
download_url="https://github.com/docker/compose/releases/download/${version}/docker-compose-linux-x86_64"

if [[ $(uname -m) != "x86_64" ]]; then
  echo "unsupported architecture: $(uname -m)" >&2
  exit 1
fi

temporary="$(mktemp)"
trap 'rm -f "$temporary"' EXIT
curl --fail --location --proto '=https' --tlsv1.2 "$download_url" --output "$temporary"
echo "$expected_sha256  $temporary" | sha256sum --check --status
install -d -m 0755 "$plugin_dir"
install -m 0755 "$temporary" "$plugin_path"
docker compose version
