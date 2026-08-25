create table ai_knowledge.document_snapshot (
    document_id varchar(80) not null,
    version_label varchar(40) not null,
    contract_version varchar(20) not null,
    title varchar(200) not null,
    issuer varchar(160) not null,
    source_url varchar(1000),
    source_hash varchar(71) not null,
    classification varchar(30) not null,
    audience varchar(20) not null,
    allowed_roles text[] not null,
    approval_status varchar(20) not null,
    lifecycle_status varchar(30) not null,
    effective_from date not null,
    effective_to date,
    indexed_at timestamptz not null,
    primary key(document_id, version_label),
    constraint ck_ai_document_snapshot_contract check (contract_version = '1.0.0'),
    constraint ck_ai_document_snapshot_source_hash check (source_hash ~ '^sha256:[0-9a-f]{64}$'),
    constraint ck_ai_document_snapshot_classification
        check (classification in ('PUBLIC_OFFICIAL', 'INTERNAL', 'CONFIDENTIAL')),
    constraint ck_ai_document_snapshot_audience check (audience in ('CUSTOMER', 'STAFF', 'BOTH')),
    constraint ck_ai_document_snapshot_roles check (
        cardinality(allowed_roles) > 0
        and allowed_roles <@ array[
            'CUSTOMER', 'PROTECTION_STAFF', 'DETECTION_ADMIN',
            'COMPLIANCE_REVIEWER', 'KNOWLEDGE_ADMIN', 'SECURITY_ADMIN'
        ]::text[]
    ),
    constraint ck_ai_document_snapshot_approval
        check (approval_status in ('DRAFT', 'IN_REVIEW', 'APPROVED', 'REJECTED')),
    constraint ck_ai_document_snapshot_lifecycle check (
        lifecycle_status in ('PENDING_ACTIVATION', 'ACTIVE', 'SUPERSEDED', 'EXPIRED', 'RETIRED')
    ),
    constraint ck_ai_document_snapshot_effective
        check (effective_to is null or effective_to >= effective_from)
);
create index idx_ai_document_snapshot_acl
    on ai_knowledge.document_snapshot using gin(allowed_roles);
create index idx_ai_document_snapshot_effective
    on ai_knowledge.document_snapshot(approval_status, lifecycle_status, effective_from, effective_to);

create table ai_knowledge.retrieval_run (
    run_id uuid primary key,
    request_id uuid not null unique,
    query_hash varchar(71) not null,
    as_of date not null,
    principal_roles text[] not null,
    requester_audiences text[] not null,
    requested_limit integer not null,
    index_version varchar(80) not null,
    status varchar(20) not null,
    result_count integer,
    failure_code varchar(80),
    started_at timestamptz not null,
    finished_at timestamptz,
    constraint ck_ai_retrieval_query_hash check (query_hash ~ '^sha256:[0-9a-f]{64}$'),
    constraint ck_ai_retrieval_limit check (requested_limit between 1 and 20),
    constraint ck_ai_retrieval_status check (status in ('RUNNING', 'SUCCEEDED', 'FAILED')),
    constraint ck_ai_retrieval_result_count check (result_count is null or result_count >= 0),
    constraint ck_ai_retrieval_terminal_state check (
        (status = 'RUNNING' and finished_at is null and result_count is null and failure_code is null)
        or
        (status = 'SUCCEEDED' and finished_at is not null and result_count is not null and failure_code is null)
        or
        (status = 'FAILED' and finished_at is not null and result_count is null and failure_code is not null)
    )
);
create index idx_ai_retrieval_run_started on ai_knowledge.retrieval_run(started_at desc);

revoke all on ai_knowledge.document_snapshot from public;
revoke all on ai_knowledge.retrieval_run from public;
do $$ begin
    if exists(select 1 from pg_roles where rolname = 'alzswell_ai_ingestor') then
        grant select, insert, update on ai_knowledge.document_snapshot to alzswell_ai_ingestor;
    end if;
    if exists(select 1 from pg_roles where rolname = 'alzswell_ai_runtime') then
        grant usage on schema ai_knowledge to alzswell_ai_runtime;
        grant select on ai_knowledge.document_snapshot, ai_knowledge.chunk to alzswell_ai_runtime;
        grant select, insert on ai_knowledge.retrieval_run to alzswell_ai_runtime;
        grant update(status, result_count, failure_code, finished_at)
            on ai_knowledge.retrieval_run to alzswell_ai_runtime;
    end if;
end $$;

comment on table ai_knowledge.document_snapshot is '검색 시 재검증하는 승인 manifest ACL·효력 스냅샷';
comment on table ai_knowledge.retrieval_run is '원문 질의를 저장하지 않는 AI 검색 감사 실행 이력';
