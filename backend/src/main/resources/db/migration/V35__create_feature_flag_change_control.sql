create table operational_feature_flag (
    flag_key varchar(80) primary key,
    property_key varchar(120) not null unique,
    desired_enabled boolean not null,
    mutable boolean not null,
    safety_class varchar(30) not null,
    description varchar(300) not null,
    row_version bigint not null default 0,
    updated_by varchar(80) not null,
    updated_at timestamptz not null,
    constraint ck_feature_flag_safety_class check (
        safety_class in ('PRIVATE_ONLY', 'FAIL_CLOSED_GUARDRAIL')
    ),
    constraint ck_feature_flag_guardrail_immutable check (
        safety_class <> 'FAIL_CLOSED_GUARDRAIL' or mutable = false and desired_enabled = false
    ),
    constraint ck_feature_flag_version check (row_version >= 0)
);

create table feature_flag_change_event (
    event_id uuid primary key,
    flag_key varchar(80) not null references operational_feature_flag (flag_key),
    actor_subject varchar(80) not null,
    previous_desired_enabled boolean not null,
    requested_enabled boolean not null,
    approval_reference varchar(100) not null,
    change_reason varchar(500) not null,
    occurred_at timestamptz not null
);

create trigger trg_feature_flag_change_event_append_only
before update or delete on feature_flag_change_event
for each row execute function reject_protected_event_mutation();

insert into operational_feature_flag (
    flag_key, property_key, desired_enabled, mutable, safety_class, description,
    updated_by, updated_at
) values
    ('CUSTOMER_PROFILE_API_ENABLED', 'app.features.customer-profile-api-enabled', false, true,
     'PRIVATE_ONLY', '사설 검증 환경의 고객 프로필 API', 'SYSTEM_MIGRATION_V35', now()),
    ('LOCAL_AUTH_API_ENABLED', 'app.features.local-auth-api-enabled', false, true,
     'PRIVATE_ONLY', '사설 검증 환경의 합성 로컬 인증 API', 'SYSTEM_MIGRATION_V35', now()),
    ('EXTERNAL_ACTIONS_ENABLED', 'app.guardrails.external-actions-enabled', false, false,
     'FAIL_CLOSED_GUARDRAIL', '실제 금융 실행 차단 가드레일', 'SYSTEM_MIGRATION_V35', now()),
    ('EXTERNAL_EGRESS_ENABLED', 'app.guardrails.external-egress-enabled', false, false,
     'FAIL_CLOSED_GUARDRAIL', '외부 네트워크 송신 차단 가드레일', 'SYSTEM_MIGRATION_V35', now()),
    ('REMOTE_MODEL_ENABLED', 'app.guardrails.remote-model-enabled', false, false,
     'FAIL_CLOSED_GUARDRAIL', '외부 모델 호출 차단 가드레일', 'SYSTEM_MIGRATION_V35', now());

insert into auth_permission (permission_code, description) values
    ('FEATURE_FLAG_READ', '환경별 기능 플래그와 런타임 적용 상태 조회'),
    ('FEATURE_FLAG_WRITE', '승인 근거가 있는 사설 기능 플래그 변경 요청');

insert into auth_role_permission (role_code, permission_code) values
    ('DETECTION_ADMIN', 'FEATURE_FLAG_READ'),
    ('DETECTION_ADMIN', 'FEATURE_FLAG_WRITE');

comment on table operational_feature_flag is 'DB에 기록된 승인 희망값; 런타임 적용은 배포 환경변수와 재기동으로만 수행';
comment on table feature_flag_change_event is '기능 플래그 변경 요청 append-only 감사이력';
