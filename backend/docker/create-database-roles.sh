#!/usr/bin/env bash
set -Eeuo pipefail

: "${PGHOST:?PGHOST must be set}"
: "${PGDATABASE:?PGDATABASE must be set}"
: "${PGUSER:?PGUSER must be set}"
: "${PGPASSWORD:?PGPASSWORD must be set}"
: "${POSTGRES_APP_PASSWORD:?POSTGRES_APP_PASSWORD must be set}"
: "${POSTGRES_MIGRATION_PASSWORD:?POSTGRES_MIGRATION_PASSWORD must be set}"
: "${POSTGRES_AI_PASSWORD:?POSTGRES_AI_PASSWORD must be set}"
: "${POSTGRES_AI_RUNTIME_PASSWORD:?POSTGRES_AI_RUNTIME_PASSWORD must be set}"

if (( ${#POSTGRES_APP_PASSWORD} < 32 || ${#POSTGRES_MIGRATION_PASSWORD} < 32 || ${#POSTGRES_AI_PASSWORD} < 32 || ${#POSTGRES_AI_RUNTIME_PASSWORD} < 32 )); then
  echo "database role passwords must be at least 32 characters" >&2
  exit 1
fi

psql --set=ON_ERROR_STOP=1 \
  --set=app_password="$POSTGRES_APP_PASSWORD" \
  --set=migration_password="$POSTGRES_MIGRATION_PASSWORD" \
  --set=ai_password="$POSTGRES_AI_PASSWORD" \
  --set=ai_runtime_password="$POSTGRES_AI_RUNTIME_PASSWORD" <<'SQL'
select format(
    'create role alzswell_migrator login password %L nosuperuser nocreatedb nocreaterole noreplication',
    :'migration_password'
)
where not exists (select 1 from pg_roles where rolname = 'alzswell_migrator')
\gexec

select format('alter role alzswell_migrator password %L', :'migration_password')
\gexec

select format(
    'create role alzswell_app login password %L nosuperuser nocreatedb nocreaterole noreplication',
    :'app_password'
)
where not exists (select 1 from pg_roles where rolname = 'alzswell_app')
\gexec

select format('alter role alzswell_app password %L', :'app_password')
\gexec

select format(
    'create role alzswell_ai_ingestor login password %L nosuperuser nocreatedb nocreaterole noreplication',
    :'ai_password'
)
where not exists (select 1 from pg_roles where rolname = 'alzswell_ai_ingestor')
\gexec

select format('alter role alzswell_ai_ingestor password %L', :'ai_password')
\gexec

select format(
    'create role alzswell_ai_runtime login password %L nosuperuser nocreatedb nocreaterole noreplication',
    :'ai_runtime_password'
)
where not exists (select 1 from pg_roles where rolname = 'alzswell_ai_runtime')
\gexec

select format('alter role alzswell_ai_runtime password %L', :'ai_runtime_password')
\gexec

revoke create on schema public from public;
create extension if not exists vector;
grant usage, create on schema public to alzswell_migrator;

select format('grant connect on database %I to alzswell_migrator, alzswell_app, alzswell_ai_ingestor, alzswell_ai_runtime', current_database())
\gexec
select format('grant create on database %I to alzswell_migrator', current_database())
\gexec
SQL
