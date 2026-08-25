create extension if not exists pgcrypto;

create table synthetic_fixture_generation_run (
    run_id uuid primary key,
    dataset_key varchar(80) not null unique,
    fixture_version varchar(40) not null,
    profile varchar(10) not null,
    seed bigint not null,
    lease_id uuid not null,
    status varchar(20) not null,
    expected_customer_count integer not null,
    expected_account_count integer not null,
    expected_transaction_count integer not null,
    actual_customer_count integer not null default 0,
    actual_account_count integer not null default 0,
    actual_transaction_count integer not null default 0,
    manifest_hash char(64),
    error_code varchar(80),
    started_at timestamptz not null,
    completed_at timestamptz,
    synthetic_data boolean not null default true,
    external_actions_created boolean not null default false,
    constraint uq_fixture_generation_identity unique(fixture_version, profile, seed),
    constraint ck_fixture_generation_profile check(profile in ('SMOKE','DEMO','DEV')),
    constraint ck_fixture_generation_seed check(seed > 0),
    constraint ck_fixture_generation_status check(status in ('RUNNING','SUCCEEDED','FAILED')),
    constraint ck_fixture_generation_expected_counts check(
        expected_customer_count > 0 and expected_account_count >= expected_customer_count
        and expected_transaction_count >= expected_customer_count
    ),
    constraint ck_fixture_generation_actual_counts check(
        actual_customer_count >= 0 and actual_account_count >= 0 and actual_transaction_count >= 0
    ),
    constraint ck_fixture_generation_hash check(manifest_hash is null or manifest_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_fixture_generation_completion check(
        (status = 'RUNNING' and completed_at is null and manifest_hash is null)
        or (status = 'SUCCEEDED' and completed_at is not null and manifest_hash is not null and error_code is null)
        or (status = 'FAILED' and completed_at is not null and error_code is not null)
    ),
    constraint ck_fixture_generation_safety check(synthetic_data and not external_actions_created)
);

create index idx_fixture_generation_status_time
    on synthetic_fixture_generation_run(status, started_at desc, run_id desc);

create table synthetic_fixture_customer (
    run_id uuid not null references synthetic_fixture_generation_run(run_id),
    customer_index integer not null,
    customer_id varchar(80) not null references customer_profile(customer_id),
    dataset_id uuid not null references synthetic_detection_dataset(dataset_id),
    scenario_code varchar(40) not null,
    expected_signal_count integer not null,
    primary key(run_id, customer_index),
    unique(run_id, customer_id),
    unique(run_id, dataset_id),
    constraint ck_fixture_customer_index check(customer_index > 0),
    constraint ck_fixture_customer_scenario check(
        scenario_code in ('NORMAL','MISSED_PAYMENT','DUPLICATE_TRANSFER','REPEATED_CONFIRMATION')
    ),
    constraint ck_fixture_customer_signal_count check(expected_signal_count between 0 and 1)
);

create index idx_fixture_customer_lookup on synthetic_fixture_customer(customer_id, run_id);
create trigger trg_synthetic_fixture_customer_append_only
before update or delete on synthetic_fixture_customer
for each row execute function reject_protected_event_mutation();

create or replace function synthetic_fixture_uuid(source_value text)
returns uuid
language sql
immutable
strict
parallel safe
as $$
    select (
        substr(md5(source_value), 1, 8) || '-' || substr(md5(source_value), 9, 4) || '-'
        || substr(md5(source_value), 13, 4) || '-' || substr(md5(source_value), 17, 4) || '-'
        || substr(md5(source_value), 21, 12)
    )::uuid
$$;

create or replace function seed_synthetic_fixture_batch(
    requested_run_id uuid,
    requested_lease_id uuid,
    first_customer_index integer,
    last_customer_index integer,
    transactions_per_customer integer
)
returns integer
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    fixture_run synthetic_fixture_generation_run%rowtype;
    generated_customers integer;
begin
    if first_customer_index < 1 or last_customer_index < first_customer_index
       or transactions_per_customer < 1 or transactions_per_customer > 1500 then
        raise exception using errcode = '22023', message = 'invalid synthetic fixture batch range';
    end if;

    select * into fixture_run
      from synthetic_fixture_generation_run
     where run_id = requested_run_id
     for update;
    if not found then
        raise exception using errcode = 'P0002', message = 'synthetic fixture run not found';
    end if;
    if fixture_run.status <> 'RUNNING' then
        raise exception using errcode = '55000', message = 'synthetic fixture run is not running';
    end if;
    if fixture_run.lease_id <> requested_lease_id then
        raise exception using errcode = '55000', message = 'synthetic fixture run lease was replaced';
    end if;
    if last_customer_index > fixture_run.expected_customer_count then
        raise exception using errcode = '22023', message = 'synthetic fixture batch exceeds expected customers';
    end if;

    insert into customer_profile(customer_id, display_name, organization, region, status, created_at, updated_at)
    select customer_id, '합성 이용자 ' || lpad(customer_index::text, 6, '0'),
           '안심금융 합성검증센터', 'KR-' || lpad((11 + customer_index % 7)::text, 2, '0'),
           'ACTIVE', fixture_run.started_at, fixture_run.started_at
      from (
        select customer_index,
               'SYN_V3_' || fixture_run.profile || '_' || substr(md5(fixture_run.dataset_key), 1, 8)
                   || '_' || lpad(customer_index::text, 6, '0') customer_id
          from generate_series(first_customer_index, last_customer_index) customer_index
      ) generated
    on conflict(customer_id) do nothing;

    insert into customer_preferences(customer_id, sms_notification_enabled, push_notification_enabled,
                                     in_app_notification_enabled, updated_at)
    select 'SYN_V3_' || fixture_run.profile || '_' || substr(md5(fixture_run.dataset_key), 1, 8)
               || '_' || lpad(customer_index::text, 6, '0'),
           false, false, true, fixture_run.started_at
      from generate_series(first_customer_index, last_customer_index) customer_index
    on conflict(customer_id) do nothing;

    insert into customer_accessibility_settings(customer_id, large_font, high_contrast, speech_guidance,
                                                one_hand_mode, updated_at)
    select 'SYN_V3_' || fixture_run.profile || '_' || substr(md5(fixture_run.dataset_key), 1, 8)
               || '_' || lpad(customer_index::text, 6, '0'),
           customer_index % 3 = 0, customer_index % 11 = 0,
           customer_index % 7 = 0, customer_index % 2 = 0, fixture_run.started_at
      from generate_series(first_customer_index, last_customer_index) customer_index
    on conflict(customer_id) do nothing;

    insert into customer_data_inventory(customer_id, institution_count, account_count, transaction_count,
                                        account_freshness, transaction_freshness, baseline_freshness,
                                        last_sync_at, updated_at)
    select 'SYN_V3_' || fixture_run.profile || '_' || substr(md5(fixture_run.dataset_key), 1, 8)
               || '_' || lpad(customer_index::text, 6, '0'),
           1, 2, transactions_per_customer, 'FIXED_SNAPSHOT', 'FIXED_SNAPSHOT',
           'CURRENT', fixture_run.started_at, fixture_run.started_at
      from generate_series(first_customer_index, last_customer_index) customer_index
    on conflict(customer_id) do nothing;

    insert into customer_connection(connection_id, customer_id, institution_id, connection_status,
                                    consented_at, consent_expires_at, last_synced_at, provider_mode, row_version)
    select synthetic_fixture_uuid(fixture_run.dataset_key || ':connection:' || customer_index),
           'SYN_V3_' || fixture_run.profile || '_' || substr(md5(fixture_run.dataset_key), 1, 8)
               || '_' || lpad(customer_index::text, 6, '0'),
           'SYNTHETIC_BANK', 'ACTIVE', '2026-08-01T00:00:00Z', '2027-08-01T00:00:00Z',
           '2026-08-14T00:00:00Z', 'SYNTHETIC_PROVIDER', 0
      from generate_series(first_customer_index, last_customer_index) customer_index
    on conflict(customer_id, institution_id) do nothing;

    insert into customer_connection_scope(connection_id, scope_code, consent_status)
    select synthetic_fixture_uuid(fixture_run.dataset_key || ':connection:' || customer_index),
           s.scope_code, 'CONSENTED'
      from generate_series(first_customer_index, last_customer_index) customer_index
      cross join financial_institution_scope s
     where s.institution_id = 'SYNTHETIC_BANK'
    on conflict(connection_id, scope_code) do nothing;

    insert into customer_account_snapshot(
        account_id, customer_id, connection_id, institution_id, account_type, display_name,
        masked_account_number, account_status, currency, current_balance, available_balance,
        balance_as_of, interest_type, annual_interest_rate, accrued_interest, interest_as_of,
        provider_mode, data_as_of, snapshot_hash
    )
    select synthetic_fixture_uuid(fixture_run.dataset_key || ':account:' || customer_index || ':' || account_index),
           customer_id, synthetic_fixture_uuid(fixture_run.dataset_key || ':connection:' || customer_index),
           'SYNTHETIC_BANK', case when account_index = 1 then 'CHECKING' else 'SAVINGS' end,
           case when account_index = 1 then '합성 생활계좌' else '합성 저축계좌' end,
           '***-***-' || lpad(((customer_index * 10 + account_index) % 10000)::text, 4, '0'),
           'ACTIVE', 'KRW', 5000000 + customer_index * 1000 + account_index * 100000,
           4900000 + customer_index * 1000 + account_index * 100000, '2026-08-14T00:00:00Z',
           'VARIABLE', case when account_index = 1 then 0.1000 else 0.2000 end,
           account_index * 1000, '2026-08-14', 'SYNTHETIC_PROVIDER', '2026-08-14',
           encode(digest(convert_to(fixture_run.dataset_key || ':account:' || customer_index || ':' || account_index, 'UTF8'), 'sha256'), 'hex')
      from generate_series(first_customer_index, last_customer_index) customer_index
      cross join generate_series(1, 2) account_index
      cross join lateral (
        select 'SYN_V3_' || fixture_run.profile || '_' || substr(md5(fixture_run.dataset_key), 1, 8)
                   || '_' || lpad(customer_index::text, 6, '0') customer_id
      ) generated
    on conflict(account_id) do nothing;

    insert into account_display_setting(account_id, customer_id, alias, display_order, hidden, row_version, updated_at)
    select synthetic_fixture_uuid(fixture_run.dataset_key || ':account:' || customer_index || ':' || account_index),
           'SYN_V3_' || fixture_run.profile || '_' || substr(md5(fixture_run.dataset_key), 1, 8)
               || '_' || lpad(customer_index::text, 6, '0'),
           case when account_index = 1 then '주거래' else null end, account_index, false, 1, fixture_run.started_at
      from generate_series(first_customer_index, last_customer_index) customer_index
      cross join generate_series(1, 2) account_index
    on conflict(account_id) do nothing;

    insert into customer_account_balance_snapshot(account_id, balance_date, current_balance, available_balance, snapshot_hash)
    select synthetic_fixture_uuid(fixture_run.dataset_key || ':account:' || customer_index || ':' || account_index),
           balance_date, 4800000 + customer_index * 1000 + account_index * 100000 + month_index * 50000,
           4700000 + customer_index * 1000 + account_index * 100000 + month_index * 50000,
           encode(digest(convert_to(fixture_run.dataset_key || ':balance:' || customer_index || ':' || account_index || ':' || month_index, 'UTF8'), 'sha256'), 'hex')
      from generate_series(first_customer_index, last_customer_index) customer_index
      cross join generate_series(1, 2) account_index
      cross join (values (1, date '2026-06-30'), (2, date '2026-07-31'), (3, date '2026-08-14')) months(month_index, balance_date)
    on conflict(account_id, balance_date) do nothing;

    insert into financial_counterparty_snapshot(
        counterparty_id, customer_id, display_name, counterparty_type, first_seen_on, last_seen_on,
        transaction_count, new_counterparty, data_as_of, snapshot_hash
    )
    select synthetic_fixture_uuid(fixture_run.dataset_key || ':counterparty:' || customer_index || ':' || counterparty_index),
           'SYN_V3_' || fixture_run.profile || '_' || substr(md5(fixture_run.dataset_key), 1, 8)
               || '_' || lpad(customer_index::text, 6, '0'),
           (array['안심급여','안심마켓','안심통신','안심의료'])[counterparty_index],
           (array['FINANCIAL','MERCHANT','MERCHANT','PUBLIC_SERVICE'])[counterparty_index],
           '2025-09-01', '2026-08-14', greatest(1, transactions_per_customer / 4), false, '2026-08-14',
           encode(digest(convert_to(fixture_run.dataset_key || ':counterparty:' || customer_index || ':' || counterparty_index, 'UTF8'), 'sha256'), 'hex')
      from generate_series(first_customer_index, last_customer_index) customer_index
      cross join generate_series(1, 4) counterparty_index
    on conflict(counterparty_id) do nothing;

    insert into financial_transaction_snapshot(
        transaction_id, account_id, customer_id, counterparty_id, occurred_at, posted_on,
        direction, transaction_type, status, amount, currency, balance_after,
        display_description, provider_mode, data_as_of, snapshot_hash
    )
    select synthetic_fixture_uuid(fixture_run.dataset_key || ':transaction:' || customer_index || ':' || transaction_index),
           synthetic_fixture_uuid(fixture_run.dataset_key || ':account:' || customer_index || ':1'),
           'SYN_V3_' || fixture_run.profile || '_' || substr(md5(fixture_run.dataset_key), 1, 8)
               || '_' || lpad(customer_index::text, 6, '0'),
           synthetic_fixture_uuid(fixture_run.dataset_key || ':counterparty:' || customer_index || ':' || (((transaction_index - 1) % 4) + 1)),
           timestamptz '2026-08-14T12:00:00Z' - ((transaction_index - 1) % 365) * interval '1 day'
               - ((transaction_index - 1) % 24) * interval '1 hour' - (customer_index % 60) * interval '1 minute',
           date '2026-08-14' - ((transaction_index - 1) % 365),
           case when transaction_index % 10 = 0 then 'CREDIT' else 'DEBIT' end,
           case when transaction_index % 10 = 0 then 'TRANSFER_IN'
                when transaction_index % 7 = 0 then 'AUTOPAY'
                when transaction_index % 5 = 0 then 'TRANSFER_OUT' else 'CARD_SETTLEMENT' end,
           'POSTED',
           case when transaction_index % 10 = 0 then 2800000 + (customer_index % 10) * 10000
                else 10000 + ((customer_index * 7919 + transaction_index * 104729 + fixture_run.seed) % 490000) end,
           'KRW', 5000000 + customer_index * 1000 + (transaction_index % 100) * 1000,
           (array['안심급여 합성거래','안심마켓 합성거래','안심통신 합성거래','안심의료 합성거래'])[((transaction_index - 1) % 4) + 1],
           'SYNTHETIC_PROVIDER', '2026-08-14',
           encode(digest(convert_to(fixture_run.dataset_key || ':transaction:' || customer_index || ':' || transaction_index, 'UTF8'), 'sha256'), 'hex')
      from generate_series(first_customer_index, last_customer_index) customer_index
      cross join generate_series(1, transactions_per_customer) transaction_index
    on conflict(transaction_id) do nothing;

    insert into transaction_enrichment_snapshot(
        transaction_id, normalized_description, inferred_category, recurring_candidate,
        new_counterparty, confidence, enrichment_version, reason_codes, snapshot_hash
    )
    select synthetic_fixture_uuid(fixture_run.dataset_key || ':transaction:' || customer_index || ':' || transaction_index),
           (array['안심급여','안심마켓','안심통신','안심의료'])[((transaction_index - 1) % 4) + 1],
           (array['INCOME','FOOD','COMMUNICATION','HEALTH'])[((transaction_index - 1) % 4) + 1],
           transaction_index % 7 = 0, false, 0.9500, 'synthetic-v3-rules', array['SYNTHETIC_PATTERN'],
           encode(digest(convert_to(fixture_run.dataset_key || ':enrichment:' || customer_index || ':' || transaction_index, 'UTF8'), 'sha256'), 'hex')
      from generate_series(first_customer_index, last_customer_index) customer_index
      cross join generate_series(1, transactions_per_customer) transaction_index
    on conflict(transaction_id) do nothing;

    insert into customer_transaction_preference(transaction_id, customer_id, category_code, note_text, row_version, updated_at)
    select synthetic_fixture_uuid(fixture_run.dataset_key || ':transaction:' || customer_index || ':' || transaction_index),
           'SYN_V3_' || fixture_run.profile || '_' || substr(md5(fixture_run.dataset_key), 1, 8)
               || '_' || lpad(customer_index::text, 6, '0'), null, null, 1, fixture_run.started_at
      from generate_series(first_customer_index, last_customer_index) customer_index
      cross join generate_series(1, transactions_per_customer) transaction_index
    on conflict(transaction_id) do nothing;

    insert into synthetic_detection_dataset(
        dataset_id, customer_id, dataset_name, status, payload, payload_hash,
        observation_count, evidence_count, validation_errors, row_version,
        created_at, validated_at, ingested_at
    )
    select dataset_id, customer_id, 'synthetic-v3 ' || scenario_code,
           'INGESTED', payload,
           encode(digest(convert_to(payload::text, 'UTF8'), 'sha256'), 'hex'),
           1, case when scenario_code = 'DUPLICATE_TRANSFER' then 2 else 1 end,
           '[]'::jsonb, 2, fixture_run.started_at, fixture_run.started_at, fixture_run.started_at
      from (
        select synthetic_fixture_uuid(fixture_run.dataset_key || ':dataset:' || customer_index) dataset_id,
               'SYN_V3_' || fixture_run.profile || '_' || substr(md5(fixture_run.dataset_key), 1, 8)
                   || '_' || lpad(customer_index::text, 6, '0') customer_id,
               case customer_index % 4 when 0 then 'NORMAL' when 1 then 'MISSED_PAYMENT'
                    when 2 then 'DUPLICATE_TRANSFER' else 'REPEATED_CONFIRMATION' end scenario_code,
               jsonb_build_array(jsonb_build_object(
                   'featureCode', case customer_index % 4 when 0 then 'MISSED_RECURRING_PAYMENT'
                        when 1 then 'MISSED_RECURRING_PAYMENT' when 2 then 'DUPLICATE_TRANSFER'
                        else 'REPEATED_CONFIRMATION' end,
                   'baselineValue', case when customer_index % 4 = 3 then 1 else 0 end,
                   'currentValue', case customer_index % 4 when 0 then 0 when 1 then 1 when 2 then 2 else 5 end,
                   'unit', 'COUNT',
                   'evidence', case when customer_index % 4 = 2 then jsonb_build_array(
                       jsonb_build_object('evidenceType','TRANSACTION','sourceReference',
                           synthetic_fixture_uuid(fixture_run.dataset_key || ':transaction:' || customer_index || ':1')::text,
                           'occurredAt','2026-08-12T01:00:00Z','amount',500000,'currency','KRW','description','첫 번째 합성 송금'),
                       jsonb_build_object('evidenceType','TRANSACTION','sourceReference',
                           synthetic_fixture_uuid(fixture_run.dataset_key || ':transaction:' || customer_index || ':2')::text,
                           'occurredAt','2026-08-12T01:02:00Z','amount',500000,'currency','KRW','description','두 번째 합성 송금')
                   ) else jsonb_build_array(jsonb_build_object(
                       'evidenceType', case when customer_index % 4 = 3 then 'INTERACTION' else 'TRANSACTION' end,
                       'sourceReference', synthetic_fixture_uuid(fixture_run.dataset_key || ':transaction:' || customer_index || ':1')::text,
                       'occurredAt','2026-08-12T01:00:00Z','description','결정론적 합성 근거')) end
               )) payload
          from generate_series(first_customer_index, last_customer_index) customer_index
      ) generated
    on conflict(dataset_id) do nothing;

    insert into synthetic_fixture_customer(
        run_id, customer_index, customer_id, dataset_id, scenario_code, expected_signal_count
    )
    select fixture_run.run_id, customer_index,
           'SYN_V3_' || fixture_run.profile || '_' || substr(md5(fixture_run.dataset_key), 1, 8)
               || '_' || lpad(customer_index::text, 6, '0'),
           synthetic_fixture_uuid(fixture_run.dataset_key || ':dataset:' || customer_index),
           case customer_index % 4 when 0 then 'NORMAL' when 1 then 'MISSED_PAYMENT'
                when 2 then 'DUPLICATE_TRANSFER' else 'REPEATED_CONFIRMATION' end,
           case when customer_index % 4 = 0 then 0 else 1 end
      from generate_series(first_customer_index, last_customer_index) customer_index
    on conflict(run_id, customer_index) do nothing;

    select count(*) into generated_customers
      from synthetic_fixture_customer
     where run_id = fixture_run.run_id
       and customer_index between first_customer_index and last_customer_index;
    return generated_customers;
end;
$$;

revoke all on function synthetic_fixture_uuid(text) from public;
revoke all on function seed_synthetic_fixture_batch(uuid, uuid, integer, integer, integer) from public;

do $$
begin
    if exists(select 1 from pg_roles where rolname = 'alzswell_migrator') then
        grant execute on function synthetic_fixture_uuid(text) to alzswell_migrator;
        grant execute on function seed_synthetic_fixture_batch(uuid, uuid, integer, integer, integer) to alzswell_migrator;
    end if;
    if exists(select 1 from pg_roles where rolname = 'alzswell_app') then
        revoke insert, update, delete on synthetic_fixture_generation_run, synthetic_fixture_customer from alzswell_app;
        grant select on synthetic_fixture_generation_run, synthetic_fixture_customer to alzswell_app;
    end if;
end $$;

comment on table synthetic_fixture_generation_run is 'SMOKE·DEMO·DEV 결정론적 합성 데이터 생성 실행과 검증 건수';
comment on table synthetic_fixture_customer is '합성 고객별 탐지 시나리오와 기대 신호 수를 고정한 manifest';
comment on function seed_synthetic_fixture_batch(uuid, uuid, integer, integer, integer)
    is 'migrator 전용 결정론적 합성 고객·계좌·거래 배치 적재 함수';
