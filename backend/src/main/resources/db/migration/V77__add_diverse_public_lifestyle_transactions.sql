create or replace function seed_synthetic_fixture_lifestyle_batch(
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
    inserted_transactions integer;
begin
    if first_customer_index < 1 or last_customer_index < first_customer_index
       or transactions_per_customer < 240 or transactions_per_customer > 1500 then
        raise exception using errcode = '22023', message = 'invalid lifestyle fixture batch range';
    end if;

    select * into fixture_run from synthetic_fixture_generation_run
     where run_id = requested_run_id for update;
    if not found or fixture_run.status <> 'RUNNING' or fixture_run.lease_id <> requested_lease_id then
        raise exception using errcode = '55000', message = 'synthetic fixture run is not writable';
    end if;

    insert into financial_counterparty_snapshot(
        counterparty_id,customer_id,display_name,counterparty_type,first_seen_on,last_seen_on,
        transaction_count,new_counterparty,data_as_of,snapshot_hash
    )
    select synthetic_fixture_uuid(fixture_run.dataset_key||':lifestyle-counterparty:'||customer_index||':'||merchant_index),
        'SYN_V3_'||fixture_run.profile||'_'||substr(md5(fixture_run.dataset_key),1,8)||'_'||lpad(customer_index::text,6,'0'),
        (array[
            '한빛아파트관리','도시가스 생활요금','우리동네 전기요금','맑은수도 요금','든든생활보험','안심손해보험',
            '가까운 편의점','푸른마을마트','새벽식품배송','정다운반찬가게','행복한약국','늘봄내과',
            '마음치과','온누리교통카드','생활주유소','우리동네택시','한결통신','가족인터넷',
            '햇살카페','고향식당','우리온라인몰','생활백화점','문화생활센터','가족생활비'
        ])[merchant_index],
        case when merchant_index in (1,2,3,4) then 'PUBLIC_SERVICE'
             when merchant_index in (5,6,17,18,24) then 'FINANCIAL' else 'MERCHANT' end,
        date '2024-09-01',date '2026-08-14',greatest(1,(transactions_per_customer-240)/24),false,date '2026-08-14',
        encode(digest(convert_to(fixture_run.dataset_key||':lifestyle-counterparty:'||customer_index||':'||merchant_index,'UTF8'),'sha256'),'hex')
      from generate_series(first_customer_index,last_customer_index) customer_index
      cross join generate_series(1,24) merchant_index
    on conflict(counterparty_id) do nothing;

    insert into financial_transaction_snapshot(
        transaction_id,account_id,customer_id,counterparty_id,occurred_at,posted_on,direction,
        transaction_type,status,amount,currency,balance_after,display_description,provider_mode,data_as_of,snapshot_hash
    )
    select synthetic_fixture_uuid(fixture_run.dataset_key||':transaction:'||customer_index||':'||transaction_index),
        synthetic_fixture_uuid(fixture_run.dataset_key||':account:'||customer_index||':'||case when transaction_index%9=0 then 2 else 1 end),
        'SYN_V3_'||fixture_run.profile||'_'||substr(md5(fixture_run.dataset_key),1,8)||'_'||lpad(customer_index::text,6,'0'),
        synthetic_fixture_uuid(fixture_run.dataset_key||':lifestyle-counterparty:'||customer_index||':'||merchant_index),
        timestamptz '2026-08-14T12:00:00Z'-(transaction_index-1)*interval '1 day'
            -((customer_index*7+transaction_index)%15)*interval '1 hour'-(customer_index%60)*interval '1 minute',
        date '2026-08-14'-(transaction_index-1),
        case when transaction_index%31=0 then 'CREDIT' else 'DEBIT' end,
        case when transaction_index%31=0 then 'TRANSFER_IN'
             when merchant_index<=6 or merchant_index in (17,18) then 'AUTOPAY'
             when merchant_index=24 then 'TRANSFER_OUT' else 'CARD_SETTLEMENT' end,
        'POSTED',
        case when transaction_index%31=0 then 2300000+(customer_index%18)*100000
             when merchant_index=1 then 120000+(customer_index%12)*10000
             when merchant_index in (2,3,4) then 35000+((customer_index*1103+transaction_index*3571+fixture_run.seed)%110000)
             when merchant_index in (5,6) then 70000+(customer_index%9)*10000
             when merchant_index in (11,12,13) then 12000+((customer_index*7919+transaction_index*104729+fixture_run.seed)%280000)
             when merchant_index=24 then 200000+(customer_index%20)*50000
             else 3000+((customer_index*7919+transaction_index*104729+fixture_run.seed)%197000) end,
        'KRW',3000000+customer_index*7000+(transaction_index%90)*13000,
        case when transaction_index%31=0 then '정기 급여 입금'
             else (array[
                '아파트 관리비','도시가스 요금','전기요금','수도요금','생활보험료','손해보험료',
                '편의점 생활용품','마트 장보기','식품 배송','반찬 구매','약국 결제','내과 진료비',
                '치과 진료비','대중교통 이용','주유비','택시 이용','휴대전화 요금','인터넷 요금',
                '카페 이용','식당 결제','온라인 쇼핑','백화점 구매','문화생활 이용','가족 생활비 송금'
             ])[merchant_index] end,
        'SYNTHETIC_PROVIDER',date '2026-08-14',
        encode(digest(convert_to(fixture_run.dataset_key||':transaction:'||customer_index||':'||transaction_index,'UTF8'),'sha256'),'hex')
      from generate_series(first_customer_index,last_customer_index) customer_index
      cross join generate_series(241,transactions_per_customer) transaction_index
      cross join lateral (select ((transaction_index+customer_index-2)%24)+1 merchant_index) merchant
    on conflict(transaction_id) do nothing;
    get diagnostics inserted_transactions = row_count;

    insert into transaction_enrichment_snapshot(
        transaction_id,normalized_description,inferred_category,recurring_candidate,new_counterparty,
        confidence,enrichment_version,reason_codes,snapshot_hash
    )
    select synthetic_fixture_uuid(fixture_run.dataset_key||':transaction:'||customer_index||':'||transaction_index),
        case when transaction_index%31=0 then '정기 급여'
             else (array['주거 관리비','도시가스','전기요금','수도요금','생활보험','손해보험','편의점','마트','식품배송','반찬가게','약국','내과','치과','대중교통','주유','택시','이동통신','인터넷','카페','식당','온라인몰','백화점','문화생활','가족송금'])[merchant_index] end,
        case when transaction_index%31=0 then 'INCOME'
             when merchant_index=1 then 'HOUSING' when merchant_index between 2 and 4 then 'UTILITIES'
             when merchant_index in (5,6,24) then 'FINANCE' when merchant_index in (11,12,13) then 'HEALTH'
             when merchant_index between 14 and 16 then 'TRANSPORT' when merchant_index in (17,18) then 'COMMUNICATION'
             when merchant_index between 19 and 20 then 'FOOD' when merchant_index between 21 and 23 then 'SHOPPING'
             else 'FOOD' end,
        merchant_index<=6 or merchant_index in (17,18,24),false,0.9300,'synthetic-v3.1-rules',
        array['SYNTHETIC_LIFESTYLE_PATTERN'],
        encode(digest(convert_to(fixture_run.dataset_key||':enrichment:'||customer_index||':'||transaction_index,'UTF8'),'sha256'),'hex')
      from generate_series(first_customer_index,last_customer_index) customer_index
      cross join generate_series(241,transactions_per_customer) transaction_index
      cross join lateral (select ((transaction_index+customer_index-2)%24)+1 merchant_index) merchant
    on conflict(transaction_id) do nothing;

    insert into customer_transaction_preference(transaction_id,customer_id,category_code,note_text,row_version,updated_at)
    select synthetic_fixture_uuid(fixture_run.dataset_key||':transaction:'||customer_index||':'||transaction_index),
        'SYN_V3_'||fixture_run.profile||'_'||substr(md5(fixture_run.dataset_key),1,8)||'_'||lpad(customer_index::text,6,'0'),
        null,null,1,fixture_run.started_at
      from generate_series(first_customer_index,last_customer_index) customer_index
      cross join generate_series(241,transactions_per_customer) transaction_index
    on conflict(transaction_id) do nothing;

    update customer_data_inventory i set transaction_count=transactions_per_customer,updated_at=fixture_run.started_at
     where i.customer_id in (
        select customer_id from synthetic_fixture_customer where run_id=requested_run_id
          and customer_index between first_customer_index and last_customer_index
     );
    return inserted_transactions;
end;
$$;

revoke all on function seed_synthetic_fixture_lifestyle_batch(uuid,uuid,integer,integer,integer) from public;
do $$
begin
    if exists(select 1 from pg_roles where rolname='alzswell_migrator') then
        grant execute on function seed_synthetic_fixture_lifestyle_batch(uuid,uuid,integer,integer,integer) to alzswell_migrator;
    end if;
end $$;

comment on function seed_synthetic_fixture_lifestyle_batch(uuid,uuid,integer,integer,integer) is
    'synthetic-v3.1 공개 fixture에 회원별 생활거래와 거래처 다양성을 추가하는 결정론적 생성 함수';
