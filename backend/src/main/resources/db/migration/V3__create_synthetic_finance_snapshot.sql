create table synthetic_consent (
    demo_session_id uuid not null references demo_session (session_id) on delete cascade,
    consent_id varchar(80) not null,
    customer_id varchar(80) not null,
    purpose varchar(80) not null,
    granted boolean not null,
    granted_at timestamptz not null,
    expires_at timestamptz not null,
    revocable boolean not null,
    trusted_contact_granted boolean not null,
    snapshot_hash varchar(80) not null,
    primary key (demo_session_id, consent_id)
);

create table synthetic_connection (
    demo_session_id uuid not null references demo_session (session_id) on delete cascade,
    connection_id varchar(80) not null,
    customer_id varchar(80) not null,
    institution_id varchar(80) not null,
    institution_name varchar(120) not null,
    institution_type varchar(30) not null,
    connection_status varchar(40) not null,
    source_provider varchar(40) not null,
    source_updated_at timestamptz not null,
    data_freshness varchar(30) not null,
    consent_id varchar(80) not null,
    display_order integer not null,
    snapshot_hash varchar(80) not null,
    primary key (demo_session_id, connection_id),
    constraint fk_synthetic_connection_consent
        foreign key (demo_session_id, consent_id)
        references synthetic_consent (demo_session_id, consent_id) on delete cascade,
    constraint ck_synthetic_connection_provider
        check (source_provider = 'SYNTHETIC_PROVIDER'),
    constraint ck_synthetic_connection_freshness
        check (data_freshness in ('FIXED_SNAPSHOT', 'FRESH', 'STALE', 'UNAVAILABLE'))
);

create table synthetic_connection_scope (
    demo_session_id uuid not null,
    connection_id varchar(80) not null,
    scope_code varchar(60) not null,
    display_order integer not null,
    primary key (demo_session_id, connection_id, scope_code),
    constraint fk_synthetic_connection_scope_connection
        foreign key (demo_session_id, connection_id)
        references synthetic_connection (demo_session_id, connection_id) on delete cascade
);

create table synthetic_account (
    demo_session_id uuid not null references demo_session (session_id) on delete cascade,
    account_id varchar(80) not null,
    customer_id varchar(80) not null,
    institution_id varchar(80) not null,
    account_type varchar(40) not null,
    display_name varchar(120) not null,
    masked_account_number varchar(40) not null,
    current_balance numeric(19, 0) not null,
    available_balance numeric(19, 0) not null,
    currency char(3) not null,
    connection_id varchar(80) not null,
    consent_id varchar(80) not null,
    source_provider varchar(40) not null,
    source_updated_at timestamptz not null,
    data_freshness varchar(30) not null,
    display_order integer not null,
    snapshot_hash varchar(80) not null,
    primary key (demo_session_id, account_id),
    constraint fk_synthetic_account_connection
        foreign key (demo_session_id, connection_id)
        references synthetic_connection (demo_session_id, connection_id) on delete cascade,
    constraint ck_synthetic_account_provider
        check (source_provider = 'SYNTHETIC_PROVIDER'),
    constraint ck_synthetic_account_masked
        check (masked_account_number like '%*%')
);

create index idx_synthetic_account_customer
    on synthetic_account (demo_session_id, customer_id, display_order);

create table synthetic_transaction (
    demo_session_id uuid not null references demo_session (session_id) on delete cascade,
    transaction_id varchar(80) not null,
    account_id varchar(80) not null,
    occurred_at timestamptz not null,
    posted_at timestamptz not null,
    direction varchar(10) not null,
    transaction_type varchar(40) not null,
    amount numeric(19, 0) not null,
    currency char(3) not null,
    balance_after numeric(19, 0) not null,
    counterparty_display_name varchar(120) not null,
    category varchar(40) not null,
    transaction_status varchar(30) not null,
    source_provider varchar(40) not null,
    data_freshness varchar(30) not null,
    snapshot_hash varchar(80) not null,
    primary key (demo_session_id, transaction_id),
    constraint fk_synthetic_transaction_account
        foreign key (demo_session_id, account_id)
        references synthetic_account (demo_session_id, account_id) on delete cascade,
    constraint ck_synthetic_transaction_direction check (direction in ('IN', 'OUT')),
    constraint ck_synthetic_transaction_amount_non_negative check (amount >= 0),
    constraint ck_synthetic_transaction_provider check (source_provider = 'SYNTHETIC_PROVIDER')
);

create index idx_synthetic_transaction_account_time
    on synthetic_transaction (demo_session_id, account_id, occurred_at desc, transaction_id desc);

create table synthetic_baseline (
    demo_session_id uuid not null references demo_session (session_id) on delete cascade,
    baseline_id varchar(80) not null,
    customer_id varchar(80) not null,
    feature_code varchar(80) not null,
    baseline_value varchar(80) not null,
    current_value varchar(80) not null,
    unit varchar(30) not null,
    readiness varchar(30) not null,
    comparison_text varchar(300) not null,
    algorithm_version varchar(60) not null,
    calculated_at timestamptz not null,
    baseline_from date not null,
    baseline_to date not null,
    observation_from date not null,
    observation_to date not null,
    display_order integer not null,
    snapshot_hash varchar(80) not null,
    primary key (demo_session_id, baseline_id)
);

create index idx_synthetic_baseline_customer
    on synthetic_baseline (demo_session_id, customer_id, display_order);

create table synthetic_baseline_reason (
    demo_session_id uuid not null,
    baseline_id varchar(80) not null,
    reason_code varchar(60) not null,
    display_order integer not null,
    primary key (demo_session_id, baseline_id, reason_code),
    constraint fk_synthetic_baseline_reason_baseline
        foreign key (demo_session_id, baseline_id)
        references synthetic_baseline (demo_session_id, baseline_id) on delete cascade
);

create table synthetic_financial_profile (
    demo_session_id uuid not null references demo_session (session_id) on delete cascade,
    customer_id varchar(80) not null,
    as_of_date date not null,
    period_from date not null,
    period_to date not null,
    monthly_income numeric(19, 0) not null,
    monthly_expense numeric(19, 0) not null,
    upcoming_obligations numeric(19, 0) not null,
    liabilities numeric(19, 0) not null,
    open_alert_count integer not null,
    change_summary varchar(300) not null,
    snapshot_hash varchar(80) not null,
    primary key (demo_session_id, customer_id)
);

create table synthetic_asset_trend (
    demo_session_id uuid not null references demo_session (session_id) on delete cascade,
    customer_id varchar(80) not null,
    trend_month date not null,
    total_assets numeric(19, 0) not null,
    snapshot_hash varchar(80) not null,
    primary key (demo_session_id, customer_id, trend_month)
);

comment on table synthetic_connection is '세션별 완전 합성 금융기관 연결 snapshot';
comment on table synthetic_account is '세션별 마스킹된 합성 계좌 snapshot';
comment on table synthetic_transaction is '세션별 합성 거래 원장';
comment on table synthetic_baseline is '세션별 개인 금융생활 기준선';
