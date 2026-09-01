#!/usr/bin/env bash
set -Eeuo pipefail

: "${RDS_HOST:?RDS_HOST must be set}"
: "${APP_BIND_ADDRESS:?APP_BIND_ADDRESS must be set}"
: "${FRONTEND_ORIGIN:?FRONTEND_ORIGIN must be set}"

if [[ $EUID -ne 0 ]]; then
  echo "deploy-app-host must run as root" >&2
  exit 1
fi

region="ap-northeast-2"
repository_root="/opt/alzs-well/repository"
certificate_root="/opt/alzs-well/certs"
runtime_root="/opt/alzs-well/runtime"
umask 077
trap 'unset app_db_json migration_db_json tls_json proxy_secret staff_token internal_token' EXIT
install -d -m 0700 "$runtime_root" "$certificate_root"

secret_value() {
  aws secretsmanager get-secret-value --region "$region" --secret-id "$1" --query SecretString --output text
}

app_db_json="$(secret_value /alzs-well-staging/db-app)"
migration_db_json="$(secret_value /alzs-well-staging/db-migration)"
tls_json="$(secret_value /alzs-well-staging/tls-app)"
proxy_secret="$(secret_value /alzs-well-staging/proxy-shared-secret)"
staff_token="$(secret_value /alzs-well-staging/staff-bootstrap-token)"
internal_token="$(secret_value /alzs-well-staging/internal-ai-token)"
[[ $proxy_secret =~ ^[0-9a-f]{64}$ ]]
printf '%s' "$tls_json" | jq -er '.clientKeystoreBase64' | base64 -d >"$certificate_root/ai-client.p12"
printf '%s' "$tls_json" | jq -er '.truststoreBase64' | base64 -d >"$certificate_root/ai-truststore.p12"
chmod 0400 "$certificate_root/ai-client.p12" "$certificate_root/ai-truststore.p12"

cat >"$runtime_root/.env.aws-app" <<EOF
NGINX_IMAGE=982689564927.dkr.ecr.ap-northeast-2.amazonaws.com/alzs-well/backend@sha256:53e6bfd81099eaa3ab9f01153292ef418dcdac73ba001be2879daffee1571b5d
AWS_REGION=ap-northeast-2
APP_LOG_GROUP=/alzs-well-staging/app
BACKEND_IMAGE=982689564927.dkr.ecr.ap-northeast-2.amazonaws.com/alzs-well/backend@sha256:d9c80af558fd7ea40610b7cd4f68d044e32db00a8e207df57d2f01438281cb79
GATEWAY_BIND_ADDRESS=${APP_BIND_ADDRESS}
TRUSTED_PROXY_CIDR=10.42.0.0/23
FRONTEND_PROXY_SHARED_SECRET=${proxy_secret}
CORS_CUSTOMER_ALLOWED_ORIGINS=${FRONTEND_ORIGIN}
CORS_STAFF_ALLOWED_ORIGINS=${FRONTEND_ORIGIN}
DEMO_STAFF_BOOTSTRAP_TOKEN=${staff_token}
RDS_HOST=${RDS_HOST}
RDS_DATABASE=alzswell
RDS_APP_USER=alzswell_app
RDS_APP_PASSWORD=$(jq -er '.password' <<<"$app_db_json")
RDS_MIGRATION_USER=alzswell_migrator
RDS_MIGRATION_PASSWORD=$(jq -er '.password' <<<"$migration_db_json")
RDS_CA_BUNDLE_PATH=${certificate_root}/global-bundle.pem
AI_PRIVATE_DNS=ai.internal
AI_INTERNAL_TOKEN=${internal_token}
AI_TLS_KEY_STORE_PATH=${certificate_root}/ai-client.p12
AI_TLS_KEY_STORE_PASSWORD=$(jq -er '.clientKeystorePassword' <<<"$tls_json")
AI_TLS_TRUST_STORE_PATH=${certificate_root}/ai-truststore.p12
AI_TLS_TRUST_STORE_PASSWORD=$(jq -er '.truststorePassword' <<<"$tls_json")
AI_EXPECTED_EMBEDDING_BACKEND=local-arctic-ko
AI_EXPECTED_EMBEDDING_DIMENSIONS=1024
AI_EXPECTED_MODEL_REVISION=55ec6e9358a56d56af759bc8372e970caf8c305f
AI_EXPECTED_ARTIFACT_SHA256=sha256:0b874517f0fd02dd9510fa2733aacaad1def6086387c88d1a21f4041351e15b0
AI_EXPECTED_GOLDEN_SET_SHA256=sha256:3fddb047d75674a64ac56467675959c0cee90615572f9eac2ed07dd491c989d3
EOF
chmod 0600 "$runtime_root/.env.aws-app"
aws ecr get-login-password --region "$region" | docker login --username AWS --password-stdin 982689564927.dkr.ecr.ap-northeast-2.amazonaws.com >/dev/null
docker compose --env-file "$runtime_root/.env.aws-app" -f "$repository_root/backend/compose.aws-app.yaml" config --quiet
docker compose --env-file "$runtime_root/.env.aws-app" -f "$repository_root/backend/compose.aws-app.yaml" pull >/dev/null
docker compose --env-file "$runtime_root/.env.aws-app" -f "$repository_root/backend/compose.aws-app.yaml" up --detach

for attempt in $(seq 1 30); do
  if curl --fail --silent "http://${APP_BIND_ADDRESS}:8080/api/v1/system/readiness" >/dev/null; then
    echo "app readiness succeeded"
    exit 0
  fi
  sleep 10
done
docker compose --env-file "$runtime_root/.env.aws-app" -f "$repository_root/backend/compose.aws-app.yaml" ps
echo "app readiness timed out" >&2
exit 1
