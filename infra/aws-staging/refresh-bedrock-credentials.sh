#!/usr/bin/env bash
set -Eeuo pipefail
# Run as root on the AI host. Never emit short-lived credentials to SSM or logs.
test "$EUID" -eq 0
umask 077
credential_root=/run/alzs-well-bedrock
install -d -m 0500 -o 10001 -g 10001 "$credential_root"
install -m 0400 -o 10001 -g 10001 /opt/alzs-well/repository/infra/aws-staging/bedrock-runtime-config "$credential_root/config"
temporary="$(mktemp /run/alzs-well-bedrock/.credentials.XXXXXX)"
trap 'rm -f "$temporary"' EXIT
AWS_CONFIG_FILE=/dev/null AWS_SHARED_CREDENTIALS_FILE=/dev/null AWS_EC2_METADATA_DISABLED=false \
  aws configure export-credentials --format process > "$temporary"
test -s "$temporary"
chown 10001:10001 "$temporary"
chmod 0400 "$temporary"
mv -f "$temporary" "$credential_root/credentials.json"
echo "Short-lived AI role credentials refreshed"
