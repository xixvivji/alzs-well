create table audit_export_request (
    request_id uuid primary key,
    requested_by uuid,
    actor_customer_id varchar(80),
    actor_session_id uuid,
    actor_type varchar(20) not null,
    from_at timestamptz not null,
    to_at timestamptz not null,
    source_types varchar(40)[] not null,
    purpose_code varchar(60) not null,
    approval_reference varchar(100) not null,
    status varchar(30) not null check (status in ('PENDING_APPROVAL')),
    idempotency_key varchar(100) not null,
    request_hash char(64) not null,
    requested_at timestamptz not null,
    expires_at timestamptz not null,
    constraint ck_audit_export_range check (from_at < to_at)
);

create unique index uq_audit_export_idempotency on audit_export_request
    (coalesce(requested_by, '00000000-0000-0000-0000-000000000000'::uuid), idempotency_key);

create table audit_export_request_event (
    event_id uuid primary key,
    request_id uuid not null references audit_export_request(request_id),
    event_type varchar(40) not null,
    status_snapshot varchar(30) not null,
    actor_principal_id uuid,
    actor_customer_id varchar(80),
    actor_session_id uuid,
    actor_type varchar(20) not null,
    detail jsonb not null default '{}'::jsonb,
    occurred_at timestamptz not null
);

create trigger trg_audit_export_request_event_append_only before update or delete on audit_export_request_event
for each row execute function reject_protected_event_mutation();

insert into auth_permission(permission_code, description) values
    ('AUDIT_EXPORT_REQUEST','외부 반출 없는 감사자료 내부 검토 요청 생성');

comment on table audit_export_request is '실제 산출물·다운로드·외부 전송을 만들지 않는 감사자료 내부 승인 요청';
