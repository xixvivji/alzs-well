#!/usr/bin/env bash
set -Eeuo pipefail

: "${RDS_HOST:?RDS_HOST must be set}"
: "${AI_BIND_ADDRESS:?AI_BIND_ADDRESS must be set}"

if [[ $EUID -ne 0 ]]; then
  echo "prepare-ai-host must run as root" >&2
  exit 1
fi

region="ap-northeast-2"
repository_root="/opt/alzs-well/repository"
certificate_root="/opt/alzs-well/certs"
runtime_root="/opt/alzs-well/runtime"
model_root="/opt/alzs-well/models"
temporary="$(mktemp -d /run/alzswell-ai-prepare.XXXXXX)"
umask 077
trap 'rm -rf "$temporary"; unset tls_json runtime_db_json ingestion_db_json internal_token' EXIT

dnf install -y jq openssl
bash "$repository_root/infra/aws-staging/install-docker-compose.sh"
install -d -m 0700 "$certificate_root" "$runtime_root" "$model_root"
curl --fail --location --proto '=https' --tlsv1.2 \
  https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem \
  --output "$certificate_root/global-bundle.pem"
chmod 0444 "$certificate_root/global-bundle.pem"

secret_value() {
  aws secretsmanager get-secret-value --region "$region" --secret-id "$1" --query SecretString --output text
}

tls_json="$(secret_value /alzs-well-staging/tls-ai)"
runtime_db_json="$(secret_value /alzs-well-staging/db-ai-runtime)"
ingestion_db_json="$(secret_value /alzs-well-staging/db-ai-ingestion)"
internal_token="$(secret_value /alzs-well-staging/internal-ai-token)"
printf '%s' "$tls_json" | jq -er '.serverCertPem' >"$certificate_root/ai-server.crt"
printf '%s' "$tls_json" | jq -er '.serverKeyPem' >"$certificate_root/ai-server.key"
printf '%s' "$tls_json" | jq -er '.clientCaCertPem' >"$certificate_root/app-client-ca.crt"
chmod 0444 "$certificate_root/ai-server.crt" "$certificate_root/app-client-ca.crt"
chmod 0400 "$certificate_root/ai-server.key"
openssl verify -CAfile "$certificate_root/app-client-ca.crt" "$certificate_root/ai-server.crt" >/dev/null
openssl x509 -in "$certificate_root/ai-server.crt" -noout -checkend 1209600 >/dev/null
certificate_key_hash="$(openssl x509 -in "$certificate_root/ai-server.crt" -pubkey -noout | sha256sum | cut -d' ' -f1)"
private_key_hash="$(openssl pkey -in "$certificate_root/ai-server.key" -pubout 2>/dev/null | sha256sum | cut -d' ' -f1)"
test "$certificate_key_hash" = "$private_key_hash"

python3 "$repository_root/infra/aws-staging/download-approved-model.py" \
  --catalog "$repository_root/ai-service/evaluation/model-artifacts-v1.json" \
  --model snowflake-arctic-embed-l-v2.0-ko \
  --destination "$model_root"

cat >"$runtime_root/.env.aws-ai" <<EOF
AI_IMAGE=982689564927.dkr.ecr.ap-northeast-2.amazonaws.com/alzs-well/ai-service@sha256:256be42ce56a874347d72799c722b83147dbef6407843b631823e3ccd0f3af4c
AWS_REGION=ap-northeast-2
AI_LOG_GROUP=/alzs-well-staging/ai
AI_GATEWAY_IMAGE=982689564927.dkr.ecr.ap-northeast-2.amazonaws.com/alzs-well/ai-service@sha256:53e6bfd81099eaa3ab9f01153292ef418dcdac73ba001be2879daffee1571b5d
AI_BIND_ADDRESS=${AI_BIND_ADDRESS}
AI_TLS_CERT_PATH=${certificate_root}/ai-server.crt
AI_TLS_KEY_PATH=${certificate_root}/ai-server.key
AI_CLIENT_CA_PATH=${certificate_root}/app-client-ca.crt
RDS_HOST=${RDS_HOST}
RDS_DATABASE=alzswell
RDS_AI_RUNTIME_USER=alzswell_ai_runtime
RDS_AI_RUNTIME_PASSWORD=$(jq -er '.password' <<<"$runtime_db_json")
RDS_AI_INGESTION_USER=alzswell_ai_ingestor
RDS_AI_INGESTION_PASSWORD=$(jq -er '.password' <<<"$ingestion_db_json")
RDS_CA_BUNDLE_PATH=${certificate_root}/global-bundle.pem
AI_INTERNAL_TOKEN=${internal_token}
AI_MODEL_ROOT=${model_root}
AI_EVALUATION_ROOT=${repository_root}/ai-service/evaluation
AI_MODEL_REVISION=55ec6e9358a56d56af759bc8372e970caf8c305f
AI_MODEL_SHA256=sha256:0b874517f0fd02dd9510fa2733aacaad1def6086387c88d1a21f4041351e15b0
REPOSITORY_ROOT=${repository_root}
EOF
chmod 0600 "$runtime_root/.env.aws-ai"
aws ecr get-login-password --region "$region" | docker login --username AWS --password-stdin 982689564927.dkr.ecr.ap-northeast-2.amazonaws.com >/dev/null
docker compose --env-file "$runtime_root/.env.aws-ai" -f "$repository_root/backend/compose.aws-ai.yaml" config --quiet
docker compose --env-file "$runtime_root/.env.aws-ai" -f "$repository_root/backend/compose.aws-ai.yaml" pull ai-service ai-gateway >/dev/null
echo "AI host model, certificates, images, and compose configuration ready"
