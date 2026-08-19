create table synthetic_detection_dataset (
    dataset_id uuid primary key,
    customer_id varchar(80) not null references customer_profile (customer_id),
    dataset_name varchar(100) not null,
    status varchar(20) not null,
    payload jsonb not null,
    payload_hash varchar(80) not null,
    observation_count integer not null,
    evidence_count integer not null,
    validation_errors jsonb not null default '[]'::jsonb,
    row_version bigint not null default 0,
    created_at timestamptz not null,
    validated_at timestamptz,
    ingested_at timestamptz,
    constraint ck_detection_dataset_status check (status in ('DRAFT', 'VALIDATED', 'INVALID', 'INGESTED')),
    constraint ck_detection_dataset_counts check (observation_count between 1 and 50 and evidence_count between 1 and 1000),
    constraint ck_detection_dataset_version check (row_version >= 0),
    constraint ck_detection_dataset_times check (
        (validated_at is null or created_at <= validated_at)
        and (ingested_at is null or validated_at is not null and validated_at <= ingested_at)
    )
);

create index idx_detection_dataset_customer_time
    on synthetic_detection_dataset (customer_id, created_at desc, dataset_id desc);

create table synthetic_detection_run (
    detection_run_id uuid primary key,
    dataset_id uuid not null references synthetic_detection_dataset (dataset_id),
    customer_id varchar(80) not null references customer_profile (customer_id),
    status varchar(20) not null,
    algorithm_version varchar(60) not null,
    idempotency_key_hash varchar(80) not null,
    request_hash varchar(80) not null,
    input_payload_hash varchar(80) not null,
    result_payload jsonb not null,
    result_hash varchar(80) not null,
    signal_count integer not null,
    started_at timestamptz not null,
    completed_at timestamptz not null,
    constraint uq_detection_run_idempotency unique (customer_id, idempotency_key_hash),
    constraint ck_detection_run_status check (status in ('COMPLETED', 'FAILED')),
    constraint ck_detection_run_signal_count check (signal_count >= 0),
    constraint ck_detection_run_time check (started_at <= completed_at)
);

create index idx_detection_run_customer_time
    on synthetic_detection_run (customer_id, started_at desc, detection_run_id desc);

insert into auth_role (role_code, description)
values ('DETECTION_ADMIN', '합성 탐지 데이터셋과 실행을 관리하는 사설 검증 관리자');

insert into auth_permission (permission_code, description) values
    ('SYNTHETIC_DATASET_ADMIN', '합성 탐지 데이터셋 등록·검증·적재'),
    ('DETECTION_RUN_CREATE', '합성 데이터셋 탐지 실행 생성'),
    ('DETECTION_RUN_READ', '합성 데이터셋 탐지 실행 결과 조회');

insert into auth_role_permission (role_code, permission_code) values
    ('DETECTION_ADMIN', 'SYNTHETIC_DATASET_ADMIN'),
    ('DETECTION_ADMIN', 'DETECTION_RUN_CREATE'),
    ('DETECTION_ADMIN', 'DETECTION_RUN_READ');

comment on table synthetic_detection_dataset is '사설 검증환경에서만 사용하는 크기 제한 합성 특징·근거 데이터셋';
comment on table synthetic_detection_run is '외부 호출 없이 수행한 결정론적 합성 탐지 실행과 결과';
