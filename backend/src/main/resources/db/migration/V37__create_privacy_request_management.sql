create table compliance_retention_policy (
    policy_code varchar(60) primary key,
    resource_type varchar(60) not null,
    retention_days integer not null check (retention_days >= 0),
    legal_basis varchar(300) not null,
    disposal_method varchar(120) not null,
    effective_from date not null,
    version bigint not null default 1
);

create table customer_privacy_request (
    request_id uuid primary key,
    customer_id varchar(80) not null,
    request_type varchar(20) not null check (request_type in ('DELETION','CORRECTION')),
    target_type varchar(60) not null,
    target_reference varchar(120),
    reason_code varchar(60) not null,
    correction_value varchar(500),
    status varchar(30) not null check (status in ('RECEIVED','LEGAL_HOLD_REVIEW')),
    legal_exception_code varchar(60),
    idempotency_key varchar(100) not null,
    request_hash char(64) not null,
    requested_at timestamptz not null,
    actor_principal_id uuid,
    actor_customer_id varchar(80),
    actor_session_id uuid,
    actor_type varchar(20) not null,
    constraint uq_privacy_request_idempotency unique (customer_id, request_type, idempotency_key),
    constraint ck_privacy_correction_value check (
        (request_type = 'CORRECTION' and correction_value is not null)
        or (request_type = 'DELETION' and correction_value is null)
    )
);

create table customer_privacy_request_event (
    event_id uuid primary key,
    request_id uuid not null references customer_privacy_request(request_id),
    event_type varchar(40) not null,
    status_snapshot varchar(30) not null,
    detail jsonb not null default '{}'::jsonb,
    occurred_at timestamptz not null,
    actor_principal_id uuid,
    actor_customer_id varchar(80),
    actor_session_id uuid,
    actor_type varchar(20) not null
);

create trigger customer_privacy_request_event_append_only
before update or delete on customer_privacy_request_event
for each row execute function reject_protected_event_mutation();

insert into compliance_retention_policy values
    ('DECISION_AUDIT_5Y','DECISION_AUDIT',1825,'전자금융 보호업무 감사 및 분쟁 대응','보존기간 종료 후 복구 불가능한 논리·물리 파기',current_date,1),
    ('CUSTOMER_REQUEST_5Y','PRIVACY_REQUEST',1825,'정보주체 요청 처리와 법적 예외 입증','보존기간 종료 후 복구 불가능한 논리·물리 파기',current_date,1),
    ('DEMO_SESSION_TTL','DEMO_SESSION',0,'합성 데모 최소수집 원칙','TTL 종료 즉시 세션 데이터 파기, 불변 감사체인 별도 보존',current_date,1);

insert into auth_permission(permission_code, description) values
    ('RETENTION_POLICY_READ','보존·파기 정책 조회'),
    ('PRIVACY_REQUEST_WRITE','본인의 개인정보 삭제·정정 요청 등록'),
    ('PRIVACY_REQUEST_WRITE_ALL','보호업무 목적의 개인정보 요청 대행 등록');

insert into auth_role_permission(role_code, permission_code) values
    ('CUSTOMER','RETENTION_POLICY_READ'), ('CUSTOMER','PRIVACY_REQUEST_WRITE'),
    ('PROTECTION_STAFF','RETENTION_POLICY_READ'), ('PROTECTION_STAFF','PRIVACY_REQUEST_WRITE_ALL');

comment on table customer_privacy_request is '실제 즉시 삭제를 수행하지 않고 법적 예외 검토를 거치는 정보주체 권리 요청';
comment on table customer_privacy_request_event is '개인정보 권리 요청 추가 전용 불변 감사이력';
