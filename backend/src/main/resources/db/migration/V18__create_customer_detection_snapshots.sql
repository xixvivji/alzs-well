create table customer_baseline_snapshot (
    baseline_id uuid primary key,
    customer_id varchar(80) not null references customer_profile (customer_id) on delete cascade,
    feature_code varchar(80) not null,
    baseline_value numeric(19, 4) not null,
    current_value numeric(19, 4) not null,
    unit varchar(30) not null,
    readiness varchar(30) not null,
    comparison_text varchar(300) not null,
    algorithm_version varchar(60) not null,
    baseline_from date not null,
    baseline_to date not null,
    observation_from date not null,
    observation_to date not null,
    calculated_at timestamptz not null,
    snapshot_hash varchar(80) not null,
    row_version bigint not null default 0,
    constraint uq_customer_baseline_feature unique (customer_id, feature_code),
    constraint ck_customer_baseline_readiness check (
        readiness in ('READY', 'COLD_START', 'INSUFFICIENT_DATA', 'STALE')
    ),
    constraint ck_customer_baseline_periods check (
        baseline_from <= baseline_to and observation_from <= observation_to
    ),
    constraint ck_customer_baseline_version check (row_version >= 0)
);

create index idx_customer_baseline_customer
    on customer_baseline_snapshot (customer_id, feature_code, baseline_id);

create table customer_baseline_feature_snapshot (
    feature_id uuid primary key,
    baseline_id uuid not null references customer_baseline_snapshot (baseline_id) on delete cascade,
    feature_code varchar(80) not null,
    feature_value numeric(19, 4) not null,
    unit varchar(30) not null,
    observed_from date not null,
    observed_to date not null,
    sample_count integer not null,
    snapshot_hash varchar(80) not null,
    constraint uq_customer_baseline_feature_point unique (baseline_id, feature_code),
    constraint ck_customer_baseline_feature_period check (observed_from <= observed_to),
    constraint ck_customer_baseline_feature_samples check (sample_count >= 0)
);

create table customer_detection_signal (
    signal_id uuid primary key,
    customer_id varchar(80) not null references customer_profile (customer_id) on delete cascade,
    baseline_id uuid not null references customer_baseline_snapshot (baseline_id),
    signal_type varchar(60) not null,
    severity varchar(20) not null,
    baseline_value numeric(19, 4) not null,
    current_value numeric(19, 4) not null,
    unit varchar(30) not null,
    reason_code varchar(60) not null,
    status varchar(30) not null,
    algorithm_version varchar(60) not null,
    detected_at timestamptz not null,
    snapshot_hash varchar(80) not null,
    constraint ck_customer_signal_severity check (severity in ('LOW', 'MEDIUM', 'HIGH')),
    constraint ck_customer_signal_status check (status in ('OPEN', 'ACKNOWLEDGED', 'CLOSED'))
);

create index idx_customer_signal_customer_time
    on customer_detection_signal (customer_id, detected_at desc, signal_id desc);

create table customer_signal_evidence_snapshot (
    evidence_id uuid primary key,
    signal_id uuid not null references customer_detection_signal (signal_id) on delete cascade,
    evidence_type varchar(30) not null,
    source_reference varchar(100) not null,
    occurred_at timestamptz not null,
    amount numeric(19, 0),
    currency char(3),
    description varchar(300) not null,
    integrity_hash varchar(80) not null,
    constraint uq_customer_signal_evidence_source unique (signal_id, source_reference),
    constraint ck_customer_signal_evidence_type check (evidence_type in ('TRANSACTION', 'INTERACTION')),
    constraint ck_customer_signal_evidence_amount check (amount is null or amount >= 0),
    constraint ck_customer_signal_evidence_currency check (
        (amount is null and currency is null) or (amount is not null and currency is not null)
    )
);

create index idx_customer_signal_evidence_time
    on customer_signal_evidence_snapshot (signal_id, occurred_at, evidence_id);

create table baseline_calculation_job (
    calculation_id uuid primary key,
    customer_id varchar(80) not null references customer_profile (customer_id) on delete cascade,
    status varchar(20) not null,
    algorithm_version varchar(60) not null,
    input_snapshot_hash varchar(80) not null,
    result_snapshot_hash varchar(80) not null,
    baselines_written integer not null,
    signals_written integer not null,
    reused_current_snapshot boolean not null,
    requested_at timestamptz not null,
    completed_at timestamptz not null,
    constraint ck_baseline_calculation_status check (status in ('COMPLETED', 'FAILED')),
    constraint ck_baseline_calculation_counts check (baselines_written >= 0 and signals_written >= 0),
    constraint ck_baseline_calculation_time check (requested_at <= completed_at)
);

create index idx_baseline_calculation_customer_time
    on baseline_calculation_job (customer_id, requested_at desc, calculation_id desc);

insert into auth_permission (permission_code, description) values
    ('DETECTION_READ', '자신의 기준선·변화신호·근거 조회'),
    ('DETECTION_CALCULATE', '자신의 합성 기준선 계산 작업 생성'),
    ('DETECTION_READ_ALL', '모든 고객의 기준선·변화신호·근거 조회'),
    ('DETECTION_CALCULATE_ALL', '모든 고객의 합성 기준선 계산 작업 생성');

insert into auth_role_permission (role_code, permission_code) values
    ('CUSTOMER', 'DETECTION_READ'),
    ('CUSTOMER', 'DETECTION_CALCULATE');

insert into customer_baseline_snapshot (
    baseline_id, customer_id, feature_code, baseline_value, current_value, unit, readiness,
    comparison_text, algorithm_version, baseline_from, baseline_to, observation_from,
    observation_to, calculated_at, snapshot_hash
) values
    ('93000000-0000-0000-0000-000000000001', 'SYN_CUSTOMER_FIN_MGMT_001',
     'MISSED_RECURRING_PAYMENT', 0, 1, 'COUNT', 'READY',
     '평소에는 없던 정기납부 누락이 1건 확인되었습니다.', 'baseline-rules-v2.0.0',
     '2025-11-01', '2026-07-31', '2026-08-01', '2026-08-31',
     '2026-08-14 00:00:00+00', 'sha256:baseline-missed-recurring-v1'),
    ('93000000-0000-0000-0000-000000000002', 'SYN_CUSTOMER_FIN_MGMT_001',
     'DUPLICATE_TRANSFER', 0, 2, 'COUNT', 'READY',
     '동일 수취인·금액의 근접 송금이 2건 확인되었습니다.', 'baseline-rules-v2.0.0',
     '2025-11-01', '2026-07-31', '2026-08-01', '2026-08-31',
     '2026-08-14 00:00:00+00', 'sha256:baseline-duplicate-transfer-v1'),
    ('93000000-0000-0000-0000-000000000003', 'SYN_CUSTOMER_FIN_MGMT_001',
     'REPEATED_CONFIRMATION', 1, 5, 'COUNT', 'READY',
     '거래 완료 확인 행동이 평소 1회에서 5회로 증가했습니다.', 'baseline-rules-v2.0.0',
     '2025-11-01', '2026-07-31', '2026-08-01', '2026-08-31',
     '2026-08-14 00:00:00+00', 'sha256:baseline-repeated-confirmation-v1');

insert into customer_baseline_feature_snapshot (
    feature_id, baseline_id, feature_code, feature_value, unit, observed_from, observed_to,
    sample_count, snapshot_hash
) values
    ('93100000-0000-0000-0000-000000000001', '93000000-0000-0000-0000-000000000001',
     'EXPECTED_RECURRING_PAYMENTS', 1, 'COUNT', '2025-11-01', '2026-07-31', 9,
     'sha256:feature-expected-recurring-v1'),
    ('93100000-0000-0000-0000-000000000002', '93000000-0000-0000-0000-000000000001',
     'OBSERVED_RECURRING_PAYMENTS', 0, 'COUNT', '2026-08-01', '2026-08-31', 1,
     'sha256:feature-observed-recurring-v1'),
    ('93100000-0000-0000-0000-000000000003', '93000000-0000-0000-0000-000000000002',
     'SAME_PAYEE_AMOUNT_TRANSFERS', 2, 'COUNT', '2026-08-01', '2026-08-31', 2,
     'sha256:feature-duplicate-transfer-v1'),
    ('93100000-0000-0000-0000-000000000004', '93000000-0000-0000-0000-000000000003',
     'TRANSACTION_CONFIRMATION_COUNT', 5, 'COUNT', '2026-08-01', '2026-08-31', 5,
     'sha256:feature-confirmation-count-v1');

insert into customer_detection_signal (
    signal_id, customer_id, baseline_id, signal_type, severity, baseline_value, current_value,
    unit, reason_code, status, algorithm_version, detected_at, snapshot_hash
) values
    ('94000000-0000-0000-0000-000000000001', 'SYN_CUSTOMER_FIN_MGMT_001',
     '93000000-0000-0000-0000-000000000001', 'BEHAVIOR_CHANGE', 'HIGH', 0, 1,
     'COUNT', 'MISSED_RECURRING_PAYMENT', 'OPEN', 'baseline-rules-v2.0.0',
     '2026-08-14 00:01:00+00', 'sha256:signal-missed-recurring-v1'),
    ('94000000-0000-0000-0000-000000000002', 'SYN_CUSTOMER_FIN_MGMT_001',
     '93000000-0000-0000-0000-000000000002', 'BEHAVIOR_CHANGE', 'HIGH', 0, 2,
     'COUNT', 'DUPLICATE_TRANSFER', 'OPEN', 'baseline-rules-v2.0.0',
     '2026-08-14 00:02:00+00', 'sha256:signal-duplicate-transfer-v1'),
    ('94000000-0000-0000-0000-000000000003', 'SYN_CUSTOMER_FIN_MGMT_001',
     '93000000-0000-0000-0000-000000000003', 'BEHAVIOR_CHANGE', 'MEDIUM', 1, 5,
     'COUNT', 'REPEATED_CONFIRMATION', 'OPEN', 'baseline-rules-v2.0.0',
     '2026-08-14 00:03:00+00', 'sha256:signal-repeated-confirmation-v1');

insert into customer_signal_evidence_snapshot (
    evidence_id, signal_id, evidence_type, source_reference, occurred_at, amount, currency,
    description, integrity_hash
) values
    ('94100000-0000-0000-0000-000000000001', '94000000-0000-0000-0000-000000000001',
     'TRANSACTION', 'SYN_TX_RECURRING_EXPECTED_001', '2026-08-10 00:00:00+00', null, null,
     '예정일이 지났지만 대응 입금 거래가 없습니다.', 'sha256:evidence-recurring-v1'),
    ('94100000-0000-0000-0000-000000000002', '94000000-0000-0000-0000-000000000002',
     'TRANSACTION', 'SYN_TX_DUPLICATE_001', '2026-08-12 01:00:00+00', 500000, 'KRW',
     '동일 수취인에게 첫 번째 합성 송금이 발생했습니다.', 'sha256:evidence-duplicate-1-v1'),
    ('94100000-0000-0000-0000-000000000003', '94000000-0000-0000-0000-000000000002',
     'TRANSACTION', 'SYN_TX_DUPLICATE_002', '2026-08-12 01:02:00+00', 500000, 'KRW',
     '2분 이내 동일 수취인·금액의 두 번째 합성 송금이 발생했습니다.', 'sha256:evidence-duplicate-2-v1'),
    ('94100000-0000-0000-0000-000000000004', '94000000-0000-0000-0000-000000000003',
     'INTERACTION', 'SYN_INTERACTION_CONFIRM_001', '2026-08-13 03:00:00+00', null, null,
     '같은 거래 결과를 5회 반복 확인했습니다.', 'sha256:evidence-confirmation-v1');

comment on table customer_baseline_snapshot is '운영형 고객 API가 조회하는 개인 기준선 불변 snapshot';
comment on table customer_detection_signal is 'ALZ 행동변화 신호이며 금융기관 FDS 판정이 아님';
comment on table customer_signal_evidence_snapshot is '변화신호 생성 시점의 불변 합성 근거';
comment on table baseline_calculation_job is '현재 합성 snapshot을 결정론적으로 검증한 기준선 계산 작업 기록';
