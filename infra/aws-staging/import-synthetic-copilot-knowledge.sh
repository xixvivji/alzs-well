#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $EUID -ne 0 ]]; then
  echo "knowledge bootstrap must run as root" >&2
  exit 1
fi

region="ap-northeast-2"
repository_root="/opt/alzs-well/repository"
runtime_root="/opt/alzs-well/runtime"
certificate_root="/opt/alzs-well/certs"
document_id="DOC-SYN-COPILOT-001"
version_label="1.0.0"
principal_id="91000000-0000-0000-0000-000000000001"
container_name="alzs-knowledge-bootstrap"
bootstrap_port="18080"
bootstrap_url="http://127.0.0.1:${bootstrap_port}"
work_root="$(mktemp -d /run/alzswell-knowledge-bootstrap.XXXXXX)"
role_added=false

cleanup() {
  docker rm --force "$container_name" >/dev/null 2>&1 || true
  if [[ "$role_added" == true ]]; then
    psql --set=ON_ERROR_STOP=1 \
      -c "delete from auth_principal_role where principal_id='${principal_id}'::uuid and role_code='DETECTION_ADMIN'" \
      >/dev/null 2>&1 || true
  fi
  rm -rf "$work_root"
  unset migration_json PGPASSWORD access_token refresh_token
}
trap cleanup EXIT
umask 077

secret_value() {
  aws secretsmanager get-secret-value --region "$region" --secret-id "$1" \
    --query SecretString --output text
}

migration_json="$(secret_value /alzs-well-staging/db-migration)"
export PGHOST="$(sed -n 's/^RDS_HOST=//p' "$runtime_root/.env.aws-app")"
export PGPORT=5432
export PGDATABASE=alzswell
export PGUSER="$(jq -er '.username' <<<"$migration_json")"
export PGPASSWORD="$(jq -er '.password' <<<"$migration_json")"
export PGSSLMODE=verify-full
export PGSSLROOTCERT="$certificate_root/global-bundle.pem"

existing_imports="$(psql -At \
  -c "select count(*) from knowledge_ingestion_import where document_id='${document_id}' and version_label='${version_label}'")"
if [[ "$existing_imports" == "1" ]]; then
  existing_verified="$(psql -At -c "
    select i.chunk_count>0
      and i.ai_proof_version='AI_DB_SNAPSHOT_V1'
      and i.ai_verified_at is not null
      and count(b.chunk_id)=i.chunk_count
    from knowledge_ingestion_import i
    left join knowledge_ai_passage_binding b on b.import_id=i.import_id
    where i.document_id='${document_id}' and i.version_label='${version_label}'
    group by i.import_id,i.chunk_count,i.ai_proof_version,i.ai_verified_at")"
  if [[ "$existing_verified" == "t" ]]; then
    echo "synthetic copilot knowledge already imported and verified"
    exit 0
  fi
  echo "existing synthetic copilot import failed proof verification" >&2
  exit 1
fi
if [[ "$existing_imports" != "0" ]]; then
  echo "unexpected knowledge import count" >&2
  exit 1
fi

governance_count="$(psql -At \
  -c "select count(*) from knowledge_document_governance where document_id='${document_id}' and version_label='${version_label}'")"
if [[ "$governance_count" != "0" ]]; then
  echo "partial knowledge governance state requires manual review" >&2
  exit 1
fi

run_count="$(psql -At -c "
  select count(*) from ai_knowledge.ingestion_run
  where document_id='${document_id}' and version_label='${version_label}' and status='SUCCEEDED'")"
if [[ "$run_count" != "1" ]]; then
  echo "exactly one successful synthetic ingestion run is required" >&2
  exit 1
fi

psql --set=ON_ERROR_STOP=1 -c "
  insert into auth_principal_role(principal_id,role_code)
  values('${principal_id}'::uuid,'DETECTION_ADMIN') on conflict do nothing" >/dev/null
role_added=true

cd "$repository_root"
docker rm --force "$container_name" >/dev/null 2>&1 || true
docker compose --env-file "$runtime_root/.env.aws-app" -f backend/compose.aws-app.yaml run \
  --detach --name "$container_name" --no-deps \
  --publish "127.0.0.1:${bootstrap_port}:${bootstrap_port}" \
  --env SPRING_PROFILES_ACTIVE=development --env SERVER_PORT="$bootstrap_port" \
  backend --server.address=0.0.0.0 >/dev/null

for attempt in $(seq 1 30); do
  bootstrap_status="$(curl --silent --output /dev/null --write-out '%{http_code}' \
    "$bootstrap_url/actuator/health" || true)"
  if [[ "$bootstrap_status" =~ ^[1-5][0-9][0-9]$ && "$bootstrap_status" != "000" ]]; then
    break
  fi
  if [[ "$attempt" == "30" ]]; then
    docker logs --tail 80 "$container_name" >&2
    echo "local knowledge bootstrap application did not become healthy" >&2
    exit 1
  fi
  sleep 3
done

login_response="$(curl --fail --silent --show-error \
  --header 'Content-Type: application/json' \
  --data '{"loginId":"synthetic-customer","password":"local-synthetic-customer-password"}' \
  "$bootstrap_url/api/v1/auth/login")"
access_token="$(jq -er '.data.accessToken' <<<"$login_response")"
refresh_token="$(jq -er '.data.refreshToken' <<<"$login_response")"
unset login_response

register_payload="$(jq -cn '{
  documentId:"DOC-SYN-COPILOT-001",versionLabel:"1.0.0",
  title:"합성 금융생활 변화 상담 안내",issuer:"ALZ\u0027s well 테스트",
  sourceType:"SYNTHETIC_FIXTURE",
  sourcePath:"contracts/knowledge/fixtures/synthetic-copilot-grounding.html",sourceUrl:null,
  sourceHash:"sha256:19f4ed930d266d28ab682eede71c28db99cdd5b1f0ec7efb5fe0bfc38f7927fb",
  sourceTransformations:[],documentType:"SYNTHETIC_FIXTURE",classification:"INTERNAL",
  audience:"STAFF",allowedRoles:["PROTECTION_STAFF","DETECTION_ADMIN"],
  effectiveFrom:"2026-08-21",effectiveTo:null,checkedAt:"2026-08-21",
  usageRights:"SYNTHETIC_UNRESTRICTED",supersedesDocumentId:null,supersedesVersionLabel:null
}')"
register_code="$(curl --fail --silent --show-error \
  --header "Authorization: Bearer $access_token" --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: aws-staging-copilot-register-v1' --data "$register_payload" \
  "$bootstrap_url/api/v1/admin/knowledge/documents" | jq -er '.code')"
[[ "$register_code" == "KNOWLEDGE_DOCUMENT_REGISTERED_FOR_REVIEW" ]]

publish_code="$(curl --fail --silent --show-error \
  --header "Authorization: Bearer $access_token" --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: aws-staging-copilot-publish-v1' \
  --data '{"versionLabel":"1.0.0","expectedVersion":1,"approvalReference":"AWS_STAGING_SYNTHETIC_APPROVAL_V1"}' \
  "$bootstrap_url/api/v1/admin/knowledge/documents/$document_id/publish" | jq -er '.code')"
[[ "$publish_code" == "KNOWLEDGE_DOCUMENT_PUBLISHED" ]]

import_payload="$(psql -At -c "
  with selected_run as (
    select * from ai_knowledge.ingestion_run
    where document_id='${document_id}' and version_label='${version_label}' and status='SUCCEEDED'
  )
  select jsonb_build_object(
    'contractVersion','1.0.0','ingestionRunId',r.run_id,'documentId',r.document_id,
    'versionLabel',r.version_label,'sourceHash',r.source_hash,'asOf',r.as_of,
    'extractorVersion',r.extractor_version,'chunkerVersion',r.chunker_version,
    'chunks',jsonb_agg(jsonb_build_object(
      'chunkId',c.chunk_id,'chunkOrder',c.chunk_order,'heading',c.heading,
      'sectionPath',to_jsonb(c.section_path),'page',c.page,'pageStart',c.page_start,
      'pageEnd',c.page_end,'text',c.content,'textHash',c.text_hash,
      'sourceHash',c.source_hash,'extractorVersion',c.extractor_version,
      'chunkerVersion',c.chunker_version) order by c.chunk_order))::text
  from selected_run r join ai_knowledge.chunk c on c.run_id=r.run_id
  group by r.run_id,r.document_id,r.version_label,r.source_hash,r.as_of,
    r.extractor_version,r.chunker_version")"
import_code="$(curl --fail --silent --show-error \
  --header "Authorization: Bearer $access_token" --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: aws-staging-copilot-import-v1' --data-binary "$import_payload" \
  "$bootstrap_url/api/v1/admin/knowledge/ingestion-imports" | jq -er '.code')"
[[ "$import_code" == "KNOWLEDGE_INGESTION_IMPORTED" ]]

curl --fail --silent --show-error --request POST \
  --header "Authorization: Bearer $access_token" "$bootstrap_url/api/v1/auth/logout" >/dev/null

verified="$(psql -At -c "
  select i.chunk_count>0
    and i.ai_proof_version='AI_DB_SNAPSHOT_V1'
    and i.ai_verified_at is not null
    and count(b.chunk_id)=i.chunk_count
  from knowledge_ingestion_import i
  left join knowledge_ai_passage_binding b on b.import_id=i.import_id
  where i.document_id='${document_id}' and i.version_label='${version_label}'
  group by i.import_id,i.chunk_count,i.ai_proof_version,i.ai_verified_at")"
if [[ "$verified" != "t" ]]; then
  echo "Spring knowledge import proof verification failed" >&2
  exit 1
fi

echo "synthetic copilot knowledge governance and verified import completed"
