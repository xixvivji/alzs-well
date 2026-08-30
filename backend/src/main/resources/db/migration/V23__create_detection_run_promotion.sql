alter table customer_detection_signal
    add column source_detection_run_id uuid references synthetic_detection_run (detection_run_id);

create unique index uq_customer_signal_detection_run_reason
    on customer_detection_signal (source_detection_run_id, reason_code)
    where source_detection_run_id is not null;

create table detection_run_promotion (
    promotion_id uuid primary key,
    detection_run_id uuid not null unique references synthetic_detection_run (detection_run_id),
    customer_id varchar(80) not null references customer_profile (customer_id) on delete cascade,
    status varchar(20) not null,
    signal_ids jsonb not null,
    alert_ids jsonb not null,
    promoted_signal_count integer not null,
    promoted_alert_count integer not null,
    input_result_hash varchar(80) not null,
    promotion_result_hash varchar(80) not null,
    promoted_at timestamptz not null,
    constraint ck_detection_promotion_status check (status = 'COMPLETED'),
    constraint ck_detection_promotion_counts check (
        promoted_signal_count >= 0 and promoted_alert_count >= 0
        and promoted_signal_count = promoted_alert_count
    )
);

create index idx_detection_promotion_customer_time
    on detection_run_promotion (customer_id, promoted_at desc, promotion_id desc);

insert into auth_permission (permission_code, description) values
    ('DETECTION_PROMOTE', '완료된 합성 탐지 실행을 운영형 신호·경보 snapshot으로 승격'),
    ('DETECTION_PROMOTION_READ', '합성 탐지 실행 승격 결과 조회');

insert into auth_role_permission (role_code, permission_code) values
    ('DETECTION_ADMIN', 'DETECTION_PROMOTE'),
    ('DETECTION_ADMIN', 'DETECTION_PROMOTION_READ');

comment on table detection_run_promotion is '합성 탐지 실행을 운영형 변화신호·경보로 단 한 번 승격한 결과';
comment on column customer_detection_signal.source_detection_run_id is '합성 탐지 실행에서 승격된 신호의 출처 run';
