#!/usr/bin/env bash
set -Eeuo pipefail

: "${RDS_HOST:?RDS_HOST must be set}"
: "${AI_PRIVATE_IP:?AI_PRIVATE_IP must be set}"

if [[ $EUID -ne 0 ]]; then
  echo "bootstrap must run as root" >&2
  exit 1
fi

region="ap-northeast-2"
repository_root="/opt/alzs-well/repository"
certificate_root="/opt/alzs-well/certs"
bootstrap_root="$(mktemp -d /run/alzswell-bootstrap.XXXXXX)"
umask 077
cleanup() {
  rm -rf "$bootstrap_root"
  unset master_json app_json migration_json ai_ingestion_json ai_runtime_json
  unset PGPASSWORD POSTGRES_APP_PASSWORD POSTGRES_MIGRATION_PASSWORD
  unset POSTGRES_AI_PASSWORD POSTGRES_AI_RUNTIME_PASSWORD
}
trap cleanup EXIT

dnf install -y jq openssl postgresql17
bash "$repository_root/infra/aws-staging/install-docker-compose.sh"
install -d -m 0700 "$certificate_root"
curl --fail --location --proto '=https' --tlsv1.2 \
  https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem \
  --output "$certificate_root/global-bundle.pem"
chmod 0444 "$certificate_root/global-bundle.pem"

secret_value() {
  aws secretsmanager get-secret-value \
    --region "$region" \
    --secret-id "$1" \
    --query SecretString \
    --output text
}

master_json="$(secret_value /alzs-well-staging/db-master-runtime)"
app_json="$(secret_value /alzs-well-staging/db-app)"
migration_json="$(secret_value /alzs-well-staging/db-migration)"
ai_ingestion_json="$(secret_value /alzs-well-staging/db-ai-ingestion)"
ai_runtime_json="$(secret_value /alzs-well-staging/db-ai-runtime)"

export PGHOST="$RDS_HOST"
export PGPORT=5432
export PGDATABASE=alzswell
export PGUSER="$(jq -er '.username' <<<"$master_json")"
export PGPASSWORD="$(jq -er '.password' <<<"$master_json")"
export PGSSLMODE=verify-full
export PGSSLROOTCERT="$certificate_root/global-bundle.pem"
export POSTGRES_APP_PASSWORD="$(jq -er '.password' <<<"$app_json")"
export POSTGRES_MIGRATION_PASSWORD="$(jq -er '.password' <<<"$migration_json")"
export POSTGRES_AI_PASSWORD="$(jq -er '.password' <<<"$ai_ingestion_json")"
export POSTGRES_AI_RUNTIME_PASSWORD="$(jq -er '.password' <<<"$ai_runtime_json")"
bash "$repository_root/backend/docker/create-database-roles.sh" >/dev/null

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$bootstrap_root/ca.key" 2>/dev/null
openssl req -x509 -new -sha256 -days 45 \
  -key "$bootstrap_root/ca.key" \
  -subj "/CN=ALZS Well AWS Staging Ephemeral CA/O=ALZS Well" \
  -out "$bootstrap_root/ca.crt"

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$bootstrap_root/server.key" 2>/dev/null
openssl req -new -sha256 -key "$bootstrap_root/server.key" \
  -subj "/CN=ai.internal/O=ALZS Well" -out "$bootstrap_root/server.csr"
cat >"$bootstrap_root/server.ext" <<EOF
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=DNS:ai.internal,IP:${AI_PRIVATE_IP}
EOF
openssl x509 -req -sha256 -days 30 \
  -in "$bootstrap_root/server.csr" -CA "$bootstrap_root/ca.crt" -CAkey "$bootstrap_root/ca.key" \
  -CAcreateserial -extfile "$bootstrap_root/server.ext" -out "$bootstrap_root/server.crt" 2>/dev/null

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$bootstrap_root/client.key" 2>/dev/null
openssl req -new -sha256 -key "$bootstrap_root/client.key" \
  -subj "/CN=alzs-well-app/O=ALZS Well" -out "$bootstrap_root/client.csr"
cat >"$bootstrap_root/client.ext" <<'EOF'
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=clientAuth
EOF
openssl x509 -req -sha256 -days 30 \
  -in "$bootstrap_root/client.csr" -CA "$bootstrap_root/ca.crt" -CAkey "$bootstrap_root/ca.key" \
  -CAcreateserial -extfile "$bootstrap_root/client.ext" -out "$bootstrap_root/client.crt" 2>/dev/null

keystore_password="$(openssl rand -hex 24)"
truststore_password="$(openssl rand -hex 24)"
openssl pkcs12 -export -name alzs-well-app-client \
  -inkey "$bootstrap_root/client.key" -in "$bootstrap_root/client.crt" \
  -certfile "$bootstrap_root/ca.crt" -out "$bootstrap_root/ai-client.p12" \
  -passout "pass:$keystore_password"
openssl pkcs12 -export -nokeys -name alzs-well-ai-ca \
  -in "$bootstrap_root/ca.crt" -out "$bootstrap_root/ai-truststore.p12" \
  -passout "pass:$truststore_password"

openssl verify -CAfile "$bootstrap_root/ca.crt" "$bootstrap_root/server.crt" "$bootstrap_root/client.crt" >/dev/null
openssl x509 -in "$bootstrap_root/server.crt" -noout -checkend 1209600 >/dev/null
openssl x509 -in "$bootstrap_root/client.crt" -noout -checkend 1209600 >/dev/null

app_tls_json="$(jq -cn \
  --arg clientKeystoreBase64 "$(base64 -w0 "$bootstrap_root/ai-client.p12")" \
  --arg clientKeystorePassword "$keystore_password" \
  --arg truststoreBase64 "$(base64 -w0 "$bootstrap_root/ai-truststore.p12")" \
  --arg truststorePassword "$truststore_password" \
  --rawfile caCertPem "$bootstrap_root/ca.crt" \
  '{clientKeystoreBase64:$clientKeystoreBase64,clientKeystorePassword:$clientKeystorePassword,truststoreBase64:$truststoreBase64,truststorePassword:$truststorePassword,caCertPem:$caCertPem}')"
ai_tls_json="$(jq -cn \
  --rawfile serverCertPem "$bootstrap_root/server.crt" \
  --rawfile serverKeyPem "$bootstrap_root/server.key" \
  --rawfile clientCaCertPem "$bootstrap_root/ca.crt" \
  '{serverCertPem:$serverCertPem,serverKeyPem:$serverKeyPem,clientCaCertPem:$clientCaCertPem}')"
printf '%s' "$app_tls_json" >"$bootstrap_root/tls-app.json"
printf '%s' "$ai_tls_json" >"$bootstrap_root/tls-ai.json"
aws secretsmanager put-secret-value --region "$region" --secret-id /alzs-well-staging/tls-app --secret-string "file://$bootstrap_root/tls-app.json" >/dev/null
aws secretsmanager put-secret-value --region "$region" --secret-id /alzs-well-staging/tls-ai --secret-string "file://$bootstrap_root/tls-ai.json" >/dev/null
unset app_tls_json ai_tls_json
openssl rand -hex 32 >"$bootstrap_root/proxy-shared-secret.txt"
aws secretsmanager put-secret-value \
  --region "$region" \
  --secret-id /alzs-well-staging/proxy-shared-secret \
  --secret-string "file://$bootstrap_root/proxy-shared-secret.txt" >/dev/null

install -m 0400 "$bootstrap_root/ai-client.p12" "$certificate_root/ai-client.p12"
install -m 0400 "$bootstrap_root/ai-truststore.p12" "$certificate_root/ai-truststore.p12"
openssl x509 -in "$bootstrap_root/server.crt" -noout -serial -issuer -enddate \
  >"$certificate_root/mtls-deployment-metadata.txt"
chmod 0444 "$certificate_root/mtls-deployment-metadata.txt"

echo "database roles and mTLS material ready"
