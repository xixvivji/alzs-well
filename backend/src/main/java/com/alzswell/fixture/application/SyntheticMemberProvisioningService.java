package com.alzswell.fixture.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SyntheticMemberProvisioningService {
    private static final Pattern BCRYPT = Pattern.compile("^\\$2[ayb]\\$1[012]\\$[./A-Za-z0-9]{53}$");
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public SyntheticMemberProvisioningService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public ProvisioningResult provision(UUID runId, String passwordHash) {
        if (passwordHash == null || !BCRYPT.matcher(passwordHash).matches()) {
            throw new IllegalArgumentException("공개 합성 회원의 BCrypt password hash가 필요합니다.");
        }
        RunContract run = jdbc.queryForObject("""
                select profile,status,expected_customer_count from synthetic_fixture_generation_run where run_id=?
                """, (rs, rowNumber) -> new RunContract(
                        rs.getString("profile"), rs.getString("status"), rs.getInt("expected_customer_count")), runId);
        if (run == null || !"PUBLIC".equals(run.profile()) || !"SUCCEEDED".equals(run.status())
                || run.expectedCustomerCount() != 300) {
            throw new IllegalStateException("성공한 PUBLIC 300명 합성 fixture에서만 회원을 만들 수 있습니다.");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        jdbc.update("""
                update auth_refresh_token set revoked_at=? where revoked_at is null and session_id in (
                    select s.session_id from auth_session s join auth_principal p on p.principal_id=s.principal_id
                    where p.login_id ~ '^demo[0-9]{3}$'
                )
                """, now);
        jdbc.update("""
                update auth_session set revoked_at=?,revoke_reason='MEMBER_REPROVISIONED'
                where revoked_at is null and principal_id in (
                    select principal_id from auth_principal where login_id ~ '^demo[0-9]{3}$'
                )
                """, now);
        int principals = jdbc.update("""
                insert into auth_principal(
                    principal_id,login_id,customer_id,display_name,password_hash,status,created_at,updated_at
                )
                select ?, ?, f.customer_id, p.display_name, ?, 'ACTIVE', ?, ?
                  from synthetic_fixture_customer f
                  join customer_profile p on p.customer_id=f.customer_id
                 where f.run_id=? and f.customer_index=?
                on conflict(login_id) do update set
                    customer_id=excluded.customer_id,display_name=excluded.display_name,
                    password_hash=excluded.password_hash,status='ACTIVE',updated_at=excluded.updated_at
                """, principalId(runId, 1), "demo001", passwordHash, now, now, runId, 1);
        for (int index = 2; index <= 300; index++) {
            principals += jdbc.update("""
                    insert into auth_principal(
                        principal_id,login_id,customer_id,display_name,password_hash,status,created_at,updated_at
                    )
                    select ?, ?, f.customer_id, p.display_name, ?, 'ACTIVE', ?, ?
                      from synthetic_fixture_customer f
                      join customer_profile p on p.customer_id=f.customer_id
                     where f.run_id=? and f.customer_index=?
                    on conflict(login_id) do update set
                        customer_id=excluded.customer_id,display_name=excluded.display_name,
                        password_hash=excluded.password_hash,status='ACTIVE',updated_at=excluded.updated_at
                    """, principalId(runId, index), "demo%03d".formatted(index), passwordHash,
                    now, now, runId, index);
        }
        int roles = jdbc.update("""
                insert into auth_principal_role(principal_id,role_code)
                select p.principal_id,'CUSTOMER' from auth_principal p
                join synthetic_fixture_customer f on f.customer_id=p.customer_id
                where f.run_id=?
                on conflict do nothing
                """, runId);
        provisionFinancialProducts(runId);
        Integer active = jdbc.queryForObject("""
                select count(*) from auth_principal p
                join synthetic_fixture_customer f on f.customer_id=p.customer_id
                where f.run_id=? and p.status='ACTIVE' and p.login_id ~ '^demo[0-9]{3}$'
                """, Integer.class, runId);
        if (active == null || active != 300) {
            throw new IllegalStateException("공개 합성 회원 300명 프로비저닝 결과가 계약과 다릅니다.");
        }
        return new ProvisioningResult(runId, active, principals, roles);
    }

    private void provisionFinancialProducts(UUID runId) {
        jdbc.update("""
                insert into customer_card_snapshot(
                    card_id,customer_id,institution_id,linked_account_id,display_name,masked_card_number,
                    card_type,brand_code,status,payment_day,next_payment_due_date,current_usage_amount,
                    current_due_amount,total_limit_amount,available_limit_amount,currency,provider_mode,
                    data_as_of,snapshot_hash
                )
                select synthetic_fixture_uuid(r.dataset_key||':member-card:'||f.customer_index),f.customer_id,
                    'SYNTHETIC_BANK',synthetic_fixture_uuid(r.dataset_key||':account:'||f.customer_index||':1'),
                    '안심 생활신용카드','안심카드 ****-****-****-'||lpad(f.customer_index::text,4,'0'),
                    'CREDIT','LOCAL','ACTIVE',15,'2026-09-15',
                    300000+(f.customer_index%20)*25000,200000+(f.customer_index%10)*20000,
                    5000000,4700000-(f.customer_index%20)*25000,'KRW','SYNTHETIC_PROVIDER','2026-08-31',
                    encode(digest(convert_to(r.dataset_key||':member-card:'||f.customer_index,'UTF8'),'sha256'),'hex')
                from synthetic_fixture_customer f join synthetic_fixture_generation_run r on r.run_id=f.run_id
                where f.run_id=? on conflict(card_id) do nothing
                """, runId);
        jdbc.update("""
                insert into card_transaction_snapshot(
                    card_transaction_id,card_id,occurred_at,merchant_display_name,category_code,amount,status,
                    installment_months,currency,provider_mode,data_as_of,snapshot_hash
                )
                select synthetic_fixture_uuid(r.dataset_key||':member-card-tx:'||f.customer_index||':'||n),
                    synthetic_fixture_uuid(r.dataset_key||':member-card:'||f.customer_index),
                    timestamptz '2026-08-31T10:00:00Z'-(n||' days')::interval,
                    (array['합성마트 01','합성통신 02','합성교통 03'])[n],
                    (array['FOOD','COMMUNICATION','TRANSPORT'])[n],
                    12000+f.customer_index*100+n*3500,'APPROVED',1,'KRW','SYNTHETIC_PROVIDER','2026-08-31',
                    encode(digest(convert_to(r.dataset_key||':member-card-tx:'||f.customer_index||':'||n,'UTF8'),'sha256'),'hex')
                from synthetic_fixture_customer f join synthetic_fixture_generation_run r on r.run_id=f.run_id
                cross join generate_series(1,3) n where f.run_id=? on conflict(card_transaction_id) do nothing
                """, runId);
        jdbc.update("""
                insert into card_statement_snapshot(
                    statement_id,card_id,period_from,period_to,statement_date,due_date,total_amount,paid_amount,
                    remaining_due_amount,status,currency,provider_mode,data_as_of,snapshot_hash
                )
                select synthetic_fixture_uuid(r.dataset_key||':member-card-statement:'||f.customer_index),
                    synthetic_fixture_uuid(r.dataset_key||':member-card:'||f.customer_index),
                    '2026-08-01','2026-08-31','2026-09-01','2026-09-15',
                    200000+(f.customer_index%10)*20000,0,200000+(f.customer_index%10)*20000,
                    'ISSUED','KRW','SYNTHETIC_PROVIDER','2026-08-31',
                    encode(digest(convert_to(r.dataset_key||':member-card-statement:'||f.customer_index,'UTF8'),'sha256'),'hex')
                from synthetic_fixture_customer f join synthetic_fixture_generation_run r on r.run_id=f.run_id
                where f.run_id=? on conflict(statement_id) do nothing
                """, runId);
        jdbc.update("""
                insert into customer_liability_snapshot(
                    liability_id,customer_id,institution_id,liability_type,display_name,masked_reference,
                    outstanding_amount,scheduled_amount,annual_interest_rate,next_due_date,status,currency,
                    data_as_of,snapshot_hash
                )
                select synthetic_fixture_uuid(r.dataset_key||':member-loan:'||f.customer_index),f.customer_id,
                    'SYNTHETIC_BANK','LOAN','안심 생활대출','LN-***-'||lpad(f.customer_index::text,4,'0'),
                    5000000+(f.customer_index%25)*200000,250000+(f.customer_index%5)*10000,
                    4.2000,'2026-09-20','ACTIVE','KRW','2026-08-31',
                    encode(digest(convert_to(r.dataset_key||':member-loan:'||f.customer_index,'UTF8'),'sha256'),'hex')
                from synthetic_fixture_customer f join synthetic_fixture_generation_run r on r.run_id=f.run_id
                where f.run_id=? on conflict(liability_id) do nothing
                """, runId);
        jdbc.update("""
                insert into customer_loan_holding_detail_snapshot(
                    loan_id,customer_id,original_principal,started_on,maturity_date,repayment_method,data_as_of,snapshot_hash
                )
                select synthetic_fixture_uuid(r.dataset_key||':member-loan:'||f.customer_index),f.customer_id,
                    10000000+(f.customer_index%25)*200000,'2025-01-20','2030-01-20',
                    'EQUAL_PRINCIPAL_INTEREST','2026-08-31',
                    encode(digest(convert_to(r.dataset_key||':member-loan-detail:'||f.customer_index,'UTF8'),'sha256'),'hex')
                from synthetic_fixture_customer f join synthetic_fixture_generation_run r on r.run_id=f.run_id
                where f.run_id=? on conflict(loan_id) do nothing
                """, runId);
        jdbc.update("""
                insert into loan_repayment_schedule_snapshot(
                    installment_id,loan_id,installment_number,due_date,principal_amount,interest_amount,status,
                    data_as_of,snapshot_hash
                )
                select synthetic_fixture_uuid(r.dataset_key||':member-loan-schedule:'||f.customer_index||':'||n),
                    synthetic_fixture_uuid(r.dataset_key||':member-loan:'||f.customer_index),20+n,
                    date '2026-09-20'+((n-1)||' months')::interval,220000,35000-(n*1000),'SCHEDULED','2026-08-31',
                    encode(digest(convert_to(r.dataset_key||':member-loan-schedule:'||f.customer_index||':'||n,'UTF8'),'sha256'),'hex')
                from synthetic_fixture_customer f join synthetic_fixture_generation_run r on r.run_id=f.run_id
                cross join generate_series(1,3) n where f.run_id=? on conflict(installment_id) do nothing
                """, runId);
        jdbc.update("""
                insert into customer_deposit_holding_snapshot(
                    holding_id,customer_id,account_id,opened_on,maturity_date,principal_amount,
                    expected_maturity_amount,product_type,data_as_of,snapshot_hash
                )
                select synthetic_fixture_uuid(r.dataset_key||':member-deposit:'||f.customer_index),f.customer_id,
                    synthetic_fixture_uuid(r.dataset_key||':account:'||f.customer_index||':2'),
                    '2026-01-01','2027-01-01',5000000+(f.customer_index%30)*100000,
                    5160000+(f.customer_index%30)*103200,'INSTALLMENT_SAVINGS','2026-08-31',
                    encode(digest(convert_to(r.dataset_key||':member-deposit:'||f.customer_index,'UTF8'),'sha256'),'hex')
                from synthetic_fixture_customer f join synthetic_fixture_generation_run r on r.run_id=f.run_id
                where f.run_id=? on conflict(holding_id) do nothing
                """, runId);
        jdbc.update("""
                insert into customer_investment_account_snapshot(
                    investment_account_id,customer_id,cash_account_id,institution_id,display_name,
                    masked_account_number,account_type,status,cash_balance,total_market_value,currency,
                    provider_mode,data_as_of,snapshot_hash
                )
                select synthetic_fixture_uuid(r.dataset_key||':member-investment:'||f.customer_index),f.customer_id,
                    synthetic_fixture_uuid(r.dataset_key||':account:'||f.customer_index||':2'),'SYNTHETIC_SECURITIES',
                    '안심 투자계좌','301-***-'||lpad(f.customer_index::text,4,'0'),'BROKERAGE','ACTIVE',
                    1000000+(f.customer_index%10)*100000,7000000+(f.customer_index%20)*250000,
                    'KRW','SYNTHETIC_PROVIDER','2026-08-31',
                    encode(digest(convert_to(r.dataset_key||':member-investment:'||f.customer_index,'UTF8'),'sha256'),'hex')
                from synthetic_fixture_customer f join synthetic_fixture_generation_run r on r.run_id=f.run_id
                where f.run_id=? on conflict(investment_account_id) do nothing
                """, runId);
        jdbc.update("""
                insert into investment_position_snapshot(
                    position_id,investment_account_id,asset_class,instrument_name,masked_instrument_code,
                    quantity,average_purchase_price,current_price,market_value,unrealized_profit_loss,
                    currency,data_as_of,snapshot_hash
                )
                select synthetic_fixture_uuid(r.dataset_key||':member-position:'||f.customer_index||':'||n),
                    synthetic_fixture_uuid(r.dataset_key||':member-investment:'||f.customer_index),
                    (array['DOMESTIC_EQUITY','BOND','FUND'])[n],
                    (array['안심 대표기업','안심 국채형 채권','안심 균형형 펀드'])[n],
                    (array['A***01','B***02','F***03'])[n],10,100000*n,105000*n,1050000*n,50000*n,
                    'KRW','2026-08-31',
                    encode(digest(convert_to(r.dataset_key||':member-position:'||f.customer_index||':'||n,'UTF8'),'sha256'),'hex')
                from synthetic_fixture_customer f join synthetic_fixture_generation_run r on r.run_id=f.run_id
                cross join generate_series(1,3) n where f.run_id=? on conflict(position_id) do nothing
                """, runId);
        jdbc.update("""
                insert into investment_order_snapshot(
                    order_id,investment_account_id,instrument_id,order_type,side,quantity,order_price,
                    filled_quantity,status,ordered_at,currency,data_as_of,snapshot_hash
                )
                select synthetic_fixture_uuid(r.dataset_key||':member-order:'||f.customer_index||':'||n),
                    synthetic_fixture_uuid(r.dataset_key||':member-investment:'||f.customer_index),
                    case n when 1 then '97800000-0000-0000-0000-000000000001'::uuid
                               else '97800000-0000-0000-0000-000000000003'::uuid end,
                    case n when 1 then 'LIMIT' else 'MARKET' end,'BUY',10,100000*n,10,'FILLED',
                    timestamptz '2026-08-20T02:00:00Z'-(f.customer_index%10)*interval '1 day',
                    'KRW','2026-08-31',
                    encode(digest(convert_to(r.dataset_key||':member-order:'||f.customer_index||':'||n,'UTF8'),'sha256'),'hex')
                from synthetic_fixture_customer f join synthetic_fixture_generation_run r on r.run_id=f.run_id
                cross join generate_series(1,2) n where f.run_id=? on conflict(order_id) do nothing
                """, runId);
        provisionExtendedFinancialProducts(runId);
        provisionBankingExperience(runId);
        verifyProductIsolation(runId);
    }

    private void provisionExtendedFinancialProducts(UUID runId) {
        jdbc.update("""
                insert into customer_foreign_currency_account_snapshot(
                    account_id,customer_id,institution_id,masked_account_number,account_name,currency,
                    balance,available_balance,status,provider_mode,data_as_of,snapshot_hash
                )
                select synthetic_fixture_uuid(r.dataset_key||':member-fx-account:'||f.customer_index),
                    f.customer_id,'SYNTHETIC_BANK','***-***-'||lpad(f.customer_index::text,4,'0'),
                    '안심 달러통장','USD',1000+(f.customer_index%25)*100,
                    900+(f.customer_index%25)*100,'ACTIVE','SYNTHETIC_PROVIDER','2026-08-31',
                    encode(digest(convert_to(r.dataset_key||':member-fx-account:'||f.customer_index,'UTF8'),'sha256'),'hex')
                from synthetic_fixture_customer f join synthetic_fixture_generation_run r on r.run_id=f.run_id
                where f.run_id=? on conflict(account_id) do nothing
                """, runId);
        jdbc.update("""
                insert into overseas_remittance_snapshot(
                    remittance_id,customer_id,source_account_id,destination_country_code,beneficiary_alias,
                    currency,foreign_amount,applied_rate,krw_amount,fee_amount,status,requested_at,
                    completed_at,provider_mode,data_as_of,snapshot_hash
                )
                select synthetic_fixture_uuid(r.dataset_key||':member-remittance:'||f.customer_index),
                    f.customer_id,synthetic_fixture_uuid(r.dataset_key||':member-fx-account:'||f.customer_index),
                    'US','합성수취인 *01','USD',100+(f.customer_index%10)*10,1406.4000,
                    round((100+(f.customer_index%10)*10)*1406.4),5000,'COMPLETED',
                    timestamptz '2026-08-10T01:00:00Z'-(f.customer_index%20)*interval '1 day',
                    timestamptz '2026-08-10T01:30:00Z'-(f.customer_index%20)*interval '1 day',
                    'SYNTHETIC_PROVIDER','2026-08-31',
                    encode(digest(convert_to(r.dataset_key||':member-remittance:'||f.customer_index,'UTF8'),'sha256'),'hex')
                from synthetic_fixture_customer f join synthetic_fixture_generation_run r on r.run_id=f.run_id
                where f.run_id=? on conflict(remittance_id) do nothing
                """, runId);
        jdbc.update("""
                insert into customer_pension_holding_snapshot(
                    holding_id,customer_id,institution_id,display_name,masked_contract_reference,
                    pension_type,status,contributed_amount,current_value,expected_benefit_start_date,
                    currency,provider_mode,data_as_of,snapshot_hash
                )
                select synthetic_fixture_uuid(r.dataset_key||':member-pension:'||f.customer_index),
                    f.customer_id,'SYNTHETIC_BANK','안심 개인형퇴직연금',
                    'PEN-***-'||lpad(f.customer_index::text,4,'0'),'IRP','ACTIVE',
                    12000000+(f.customer_index%30)*200000,13500000+(f.customer_index%30)*210000,
                    date '2036-03-01','KRW','SYNTHETIC_PROVIDER','2026-08-31',
                    encode(digest(convert_to(r.dataset_key||':member-pension:'||f.customer_index,'UTF8'),'sha256'),'hex')
                from synthetic_fixture_customer f join synthetic_fixture_generation_run r on r.run_id=f.run_id
                where f.run_id=? on conflict(holding_id) do nothing
                """, runId);
        jdbc.update("""
                insert into pension_projection_snapshot(
                    projection_id,holding_id,scenario_code,assumed_annual_return,projected_value,
                    projected_monthly_benefit,benefit_start_date,calculated_on,provider_mode,snapshot_hash
                )
                select synthetic_fixture_uuid(r.dataset_key||':member-pension-projection:'||f.customer_index||':'||n),
                    synthetic_fixture_uuid(r.dataset_key||':member-pension:'||f.customer_index),
                    (array['CONSERVATIVE','BASELINE'])[n],(array[2.0000,3.5000])[n],
                    (array[21000000,25000000])[n]+(f.customer_index%30)*250000,
                    (array[120000,145000])[n]+(f.customer_index%10)*1000,date '2036-03-01',
                    date '2026-08-31','SYNTHETIC_PROVIDER',
                    encode(digest(convert_to(r.dataset_key||':member-pension-projection:'||f.customer_index||':'||n,'UTF8'),'sha256'),'hex')
                from synthetic_fixture_customer f join synthetic_fixture_generation_run r on r.run_id=f.run_id
                cross join generate_series(1,2) n where f.run_id=? on conflict(projection_id) do nothing
                """, runId);
        jdbc.update("""
                insert into customer_trust_holding_snapshot(
                    trust_id,customer_id,institution_id,display_name,masked_contract_reference,trust_type,
                    purpose_code,status,entrusted_principal,current_value,beneficiary_count,started_on,
                    maturity_date,next_review_date,currency,provider_mode,data_as_of,snapshot_hash
                )
                select synthetic_fixture_uuid(r.dataset_key||':member-trust:'||f.customer_index),
                    f.customer_id,'SYNTHETIC_BANK','안심 생활지원신탁',
                    'TRU-***-'||lpad(f.customer_index::text,4,'0'),'LIVING_TRUST','LIVING_SUPPORT',
                    'ACTIVE',20000000+(f.customer_index%20)*500000,
                    20500000+(f.customer_index%20)*510000,1,date '2025-06-01',null,
                    date '2026-12-01','KRW','SYNTHETIC_PROVIDER','2026-08-31',
                    encode(digest(convert_to(r.dataset_key||':member-trust:'||f.customer_index,'UTF8'),'sha256'),'hex')
                from synthetic_fixture_customer f join synthetic_fixture_generation_run r on r.run_id=f.run_id
                where f.run_id=? on conflict(trust_id) do nothing
                """, runId);
    }

    private void provisionBankingExperience(UUID runId) {
        jdbc.update("""
                insert into recurring_payment(
                    recurring_payment_id,customer_id,institution_id,display_name,payment_type,category_code,
                    cadence,expected_amount,currency,next_expected_date,grace_days,status,provider_mode,
                    data_as_of,reminder_enabled,reminder_lead_days,row_version,updated_at
                )
                select synthetic_fixture_uuid(r.dataset_key||':member-recurring:'||f.customer_index||':'||n),
                    f.customer_id,'SYNTHETIC_BANK',(array['생활전기요금','안심 통신요금','생활보장보험'])[n],
                    (array['UTILITY','SUBSCRIPTION','INSURANCE'])[n],
                    (array['ESSENTIAL_UTILITY','COMMUNICATION','INSURANCE_PREMIUM'])[n],
                    'MONTHLY',(array[85000,69000,120000])[n]+(f.customer_index%7)*1000,'KRW',
                    (array[date '2026-09-10',date '2026-09-12',date '2026-09-28'])[n],
                    (array[5,3,3])[n],'ACTIVE','SYNTHETIC_PROVIDER','2026-08-31',n<>2,
                    (array[3,1,5])[n],0,timestamptz '2026-08-31T00:00:00Z'
                from synthetic_fixture_customer f join synthetic_fixture_generation_run r on r.run_id=f.run_id
                cross join generate_series(1,3) n where f.run_id=? on conflict(recurring_payment_id) do nothing
                """, runId);
        jdbc.update("""
                insert into recurring_payment_occurrence(
                    occurrence_id,recurring_payment_id,expected_date,observed_at,amount,occurrence_status,
                    source_reference_hash,recorded_at
                )
                select synthetic_fixture_uuid(r.dataset_key||':member-recurring-occurrence:'||f.customer_index||':'||p||':'||n),
                    synthetic_fixture_uuid(r.dataset_key||':member-recurring:'||f.customer_index||':'||p),
                    date '2026-06-01'+((n-1)||' months')::interval+(array[9,11,27])[p]*interval '1 day',
                    case when p=1 and n=3 and f.customer_index%3=0 then null
                         else timestamptz '2026-06-01T01:00:00Z'+((n-1)||' months')::interval+(array[9,11,27])[p]*interval '1 day' end,
                    (array[85000,69000,120000])[p]+(f.customer_index%7)*1000,
                    case when p=1 and n=3 and f.customer_index%3=0 then 'MISSED'
                         when p=2 and n=3 and f.customer_index%5=0 then 'DUPLICATE_CANDIDATE' else 'COMPLETED' end,
                    case when p=1 and n=3 and f.customer_index%3=0 then null
                         else encode(digest(convert_to(r.dataset_key||':member-recurring-source:'||f.customer_index||':'||p||':'||n,'UTF8'),'sha256'),'hex') end,
                    timestamptz '2026-08-31T00:00:00Z'
                from synthetic_fixture_customer f join synthetic_fixture_generation_run r on r.run_id=f.run_id
                cross join generate_series(1,3) p cross join generate_series(1,3) n
                where f.run_id=? on conflict(occurrence_id) do nothing
                """, runId);
        jdbc.update("""
                insert into customer_beneficiary_snapshot(
                    beneficiary_id,customer_id,institution_id,display_name,masked_account_reference,
                    beneficiary_type,status,favorite,provider_mode,data_as_of,snapshot_hash
                )
                select synthetic_fixture_uuid(r.dataset_key||':member-beneficiary:'||f.customer_index||':'||n),
                    f.customer_id,case when n=3 then 'SYNTHETIC_SECURITIES' else 'SYNTHETIC_BANK' end,
                    (array['합성수취인 *01','합성사업자 *02','합성본인계좌 *03'])[n],
                    (array['안심은행 110-***-**11','안심은행 110-***-**22','안심증권 301-***-**33'])[n],
                    (array['PERSON','MERCHANT','OWN_ACCOUNT'])[n],'ACTIVE',n=1,'SYNTHETIC_PROVIDER','2026-08-31',
                    encode(digest(convert_to(r.dataset_key||':member-beneficiary:'||f.customer_index||':'||n,'UTF8'),'sha256'),'hex')
                from synthetic_fixture_customer f join synthetic_fixture_generation_run r on r.run_id=f.run_id
                cross join generate_series(1,3) n where f.run_id=? on conflict(beneficiary_id) do nothing
                """, runId);
        jdbc.update("""
                insert into customer_transfer_limit_snapshot(
                    limit_snapshot_id,customer_id,currency,per_transfer_limit,daily_limit,daily_used_amount,
                    daily_remaining_amount,data_as_of,provider_mode,snapshot_hash
                )
                select synthetic_fixture_uuid(r.dataset_key||':member-transfer-limit:'||f.customer_index),
                    f.customer_id,'KRW',5000000,10000000,(f.customer_index%10)*100000,
                    10000000-(f.customer_index%10)*100000,'2026-08-31','SYNTHETIC_PROVIDER',
                    encode(digest(convert_to(r.dataset_key||':member-transfer-limit:'||f.customer_index,'UTF8'),'sha256'),'hex')
                from synthetic_fixture_customer f join synthetic_fixture_generation_run r on r.run_id=f.run_id
                where f.run_id=? on conflict(limit_snapshot_id) do nothing
                """, runId);
        jdbc.update("""
                insert into customer_notification_preference(customer_id,updated_at)
                select f.customer_id,timestamptz '2026-08-31T00:00:00Z'
                from synthetic_fixture_customer f where f.run_id=? on conflict(customer_id) do nothing
                """, runId);
        jdbc.update("""
                insert into customer_inbox_message(
                    message_id,customer_id,message_type,title,body,related_resource_type,
                    related_resource_id,read_at,message_version,created_at
                )
                select synthetic_fixture_uuid(r.dataset_key||':member-inbox:'||f.customer_index||':'||n),
                    f.customer_id,(array['SERVICE_NOTICE','CHANGE_ALERT','FOLLOW_UP'])[n],
                    (array['합성 금융서비스 이용 안내','이번 달 금융생활을 확인해 주세요','예약한 후속 확인 일정이 있습니다'])[n],
                    (array['회원별 합성데이터로 제공되는 조회 전용 금융서비스입니다.',
                           '평소와 다른 정기납부나 반복 확인이 있는지 쉬운 말로 확인할 수 있습니다.',
                           '고객이 요청한 시간에 금융생활 내용을 다시 확인합니다. 외부 연락은 실행하지 않습니다.'])[n],
                    null,null,case when n=1 then timestamptz '2026-08-31T02:00:00Z' else null end,1,
                    timestamptz '2026-08-31T00:00:00Z'+n*interval '1 hour'
                from synthetic_fixture_customer f join synthetic_fixture_generation_run r on r.run_id=f.run_id
                cross join generate_series(1,3) n where f.run_id=? on conflict(message_id) do nothing
                """, runId);
        jdbc.update("""
                insert into customer_protection_enrollment(
                    enrollment_id,customer_id,action_code,institution_id,enrollment_status,
                    observed_as_of,provider_mode
                )
                select synthetic_fixture_uuid(r.dataset_key||':member-protection:'||f.customer_index||':'||n),
                    f.customer_id,(array['SAFE_BLOCK_INFO','BANK_CONSULTATION'])[n],
                    'SYNTHETIC_BANK',case when (f.customer_index+n)%3=0 then 'ENROLLED'
                    when n=1 then 'NOT_ENROLLED' else 'UNKNOWN' end,'2026-08-31','SYNTHETIC_PROVIDER'
                from synthetic_fixture_customer f join synthetic_fixture_generation_run r on r.run_id=f.run_id
                cross join generate_series(1,2) n where f.run_id=? on conflict(customer_id,action_code,institution_id) do nothing
                """, runId);
        jdbc.update("""
                insert into customer_asset_calendar_snapshot(
                    event_id,customer_id,institution_id,account_id,event_type,title,scheduled_date,
                    direction,expected_amount,currency,certainty,data_as_of,snapshot_hash
                )
                select synthetic_fixture_uuid(r.dataset_key||':member-calendar:'||f.customer_index||':'||n),
                    f.customer_id,'SYNTHETIC_BANK',
                    synthetic_fixture_uuid(r.dataset_key||':account:'||f.customer_index||':'||case when n=1 then 1 else 2 end),
                    (array['SALARY','INTEREST','MATURITY'])[n],
                    (array['예상 급여 입금','저축 이자 반영','안심 적금 만기'])[n],
                    (array[date '2026-09-10',date '2026-09-30',date '2027-01-01'])[n],
                    (array['INFLOW','INFLOW','NEUTRAL'])[n],
                    (array[3200000,160000,5160000])[n]+(f.customer_index%10)*10000,
                    'KRW','EXPECTED','2026-08-31',
                    encode(digest(convert_to(r.dataset_key||':member-calendar:'||f.customer_index||':'||n,'UTF8'),'sha256'),'hex')
                from synthetic_fixture_customer f join synthetic_fixture_generation_run r on r.run_id=f.run_id
                cross join generate_series(1,3) n where f.run_id=? on conflict(event_id) do nothing
                """, runId);
    }

    private void verifyProductIsolation(UUID runId) {
        Integer complete = jdbc.queryForObject("""
                select count(*) from synthetic_fixture_customer f
                where f.run_id=?
                  and (select count(*) from customer_account_snapshot a where a.customer_id=f.customer_id)=2
                  and (select count(*) from customer_card_snapshot c where c.customer_id=f.customer_id)=1
                  and (select count(*) from customer_liability_snapshot l where l.customer_id=f.customer_id and l.liability_type='LOAN')=1
                  and (select count(*) from customer_deposit_holding_snapshot d where d.customer_id=f.customer_id)=1
                  and (select count(*) from customer_investment_account_snapshot i where i.customer_id=f.customer_id)=1
                  and (select count(*) from customer_foreign_currency_account_snapshot x where x.customer_id=f.customer_id)=1
                  and (select count(*) from overseas_remittance_snapshot o where o.customer_id=f.customer_id)=1
                  and (select count(*) from customer_pension_holding_snapshot p where p.customer_id=f.customer_id)=1
                  and (select count(*) from customer_trust_holding_snapshot t where t.customer_id=f.customer_id)=1
                  and (select count(*) from recurring_payment p where p.customer_id=f.customer_id)=3
                  and (select count(*) from customer_beneficiary_snapshot b where b.customer_id=f.customer_id)=3
                  and (select count(*) from customer_transfer_limit_snapshot t where t.customer_id=f.customer_id)=1
                  and (select count(*) from customer_inbox_message m where m.customer_id=f.customer_id)=3
                  and (select count(*) from customer_protection_enrollment e where e.customer_id=f.customer_id)=2
                  and (select count(*) from customer_asset_calendar_snapshot c where c.customer_id=f.customer_id)=3
                """, Integer.class, runId);
        if (complete == null || complete != 300) {
            throw new IllegalStateException("회원별 합성 금융상품 데이터 격리 검증에 실패했습니다.");
        }
    }

    private UUID principalId(UUID runId, int index) {
        return UUID.nameUUIDFromBytes((runId + ":member:" + index).getBytes(StandardCharsets.UTF_8));
    }

    private record RunContract(String profile, String status, int expectedCustomerCount) {}
    public record ProvisioningResult(UUID runId, int activeMembers, int changedPrincipals, int addedRoles) {}
}
