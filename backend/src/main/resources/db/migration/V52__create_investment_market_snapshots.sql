-- P2 투자 읽기·관심종목: 실제 주문과 외부 시세 호출 없이 합성 snapshot만 사용한다.
create table market_instrument_snapshot (
 instrument_id uuid primary key,institution_id varchar(40) not null references financial_institution(institution_id),
 instrument_name varchar(100) not null,masked_instrument_code varchar(40) not null,asset_class varchar(30) not null,
 market_code varchar(20) not null,currency char(3) not null,status varchar(20) not null,provider_mode varchar(30) not null,
 data_as_of date not null,snapshot_hash char(64) not null,
 constraint ck_market_instrument_mask check(masked_instrument_code like '%*%' and masked_instrument_code !~ '[0-9]{6,}'),
 constraint ck_market_instrument_class check(asset_class in ('DOMESTIC_EQUITY','BOND','FUND')),
 constraint ck_market_instrument_currency check(currency='KRW'),constraint ck_market_instrument_status check(status='ACTIVE'),
 constraint ck_market_instrument_provider check(provider_mode='SYNTHETIC_PROVIDER'),constraint ck_market_instrument_hash check(snapshot_hash ~ '^[0-9a-f]{64}$')
);
create trigger trg_market_instrument_append_only before update or delete on market_instrument_snapshot for each row execute function reject_protected_event_mutation();

create table market_quote_snapshot (
 quote_id uuid primary key,instrument_id uuid not null references market_instrument_snapshot(instrument_id),quoted_at timestamptz not null,
 current_price numeric(19,0) not null,previous_close numeric(19,0) not null,change_amount numeric(19,0) not null,
 change_rate numeric(9,4) not null,currency char(3) not null,data_as_of date not null,snapshot_hash char(64) not null,
 constraint ck_market_quote_price check(current_price>=0 and previous_close>=0),constraint ck_market_quote_currency check(currency='KRW'),
 constraint ck_market_quote_hash check(snapshot_hash ~ '^[0-9a-f]{64}$'),unique(instrument_id,quoted_at)
);
create index idx_market_quote_latest on market_quote_snapshot(instrument_id,quoted_at desc,quote_id);
create trigger trg_market_quote_append_only before update or delete on market_quote_snapshot for each row execute function reject_protected_event_mutation();

create table market_price_point_snapshot (
 price_point_id uuid primary key,instrument_id uuid not null references market_instrument_snapshot(instrument_id),price_date date not null,
 open_price numeric(19,0) not null,high_price numeric(19,0) not null,low_price numeric(19,0) not null,close_price numeric(19,0) not null,
 volume numeric(19,0) not null,data_as_of date not null,snapshot_hash char(64) not null,
 constraint ck_market_price_ohlc check(low_price>=0 and open_price>=low_price and close_price>=low_price and high_price>=open_price and high_price>=close_price),
 constraint ck_market_price_volume check(volume>=0),constraint ck_market_price_hash check(snapshot_hash ~ '^[0-9a-f]{64}$'),unique(instrument_id,price_date)
);
create index idx_market_price_chart on market_price_point_snapshot(instrument_id,price_date,price_point_id);
create trigger trg_market_price_append_only before update or delete on market_price_point_snapshot for each row execute function reject_protected_event_mutation();

create table investment_order_snapshot (
 order_id uuid primary key,investment_account_id uuid not null references customer_investment_account_snapshot(investment_account_id),
 instrument_id uuid not null references market_instrument_snapshot(instrument_id),order_type varchar(20) not null,side varchar(10) not null,
 quantity numeric(19,4) not null,order_price numeric(19,0) not null,filled_quantity numeric(19,4) not null,status varchar(20) not null,
 ordered_at timestamptz not null,currency char(3) not null,data_as_of date not null,snapshot_hash char(64) not null,
 constraint ck_investment_order_type check(order_type in ('LIMIT','MARKET')),constraint ck_investment_order_side check(side in ('BUY','SELL')),
 constraint ck_investment_order_amount check(quantity>0 and order_price>=0 and filled_quantity>=0 and filled_quantity<=quantity),
 constraint ck_investment_order_status check(status in ('FILLED','CANCELLED')),constraint ck_investment_order_currency check(currency='KRW'),
 constraint ck_investment_order_hash check(snapshot_hash ~ '^[0-9a-f]{64}$')
);
create index idx_investment_order_account on investment_order_snapshot(investment_account_id,ordered_at desc,order_id);
create trigger trg_investment_order_append_only before update or delete on investment_order_snapshot for each row execute function reject_protected_event_mutation();

create table customer_watchlist_state (
 customer_id varchar(80) primary key references customer_profile(customer_id),version integer not null,updated_at timestamptz not null,
 constraint ck_watchlist_version check(version>0)
);
create table customer_watchlist_item (
 customer_id varchar(80) not null references customer_watchlist_state(customer_id),instrument_id uuid not null references market_instrument_snapshot(instrument_id),
 display_order integer not null,added_at timestamptz not null,primary key(customer_id,instrument_id),
 constraint ck_watchlist_order check(display_order>0),unique(customer_id,display_order)
);
create table customer_watchlist_event (
 event_id uuid primary key,customer_id varchar(80) not null references customer_profile(customer_id),event_type varchar(20) not null,
 version integer not null,instrument_ids jsonb not null,actor_id uuid not null,occurred_at timestamptz not null,event_hash char(64) not null,
 constraint ck_watchlist_event_type check(event_type='REPLACED'),constraint ck_watchlist_event_version check(version>0),
 constraint ck_watchlist_event_ids check(jsonb_typeof(instrument_ids)='array'),constraint ck_watchlist_event_hash check(event_hash ~ '^[0-9a-f]{64}$')
);
create index idx_watchlist_event_owner on customer_watchlist_event(customer_id,occurred_at,event_id);
create trigger trg_watchlist_event_append_only before update or delete on customer_watchlist_event for each row execute function reject_protected_event_mutation();

insert into market_instrument_snapshot values
('97800000-0000-0000-0000-000000000001','SYNTHETIC_SECURITIES','안심 대표기업','A***01','DOMESTIC_EQUITY','SYN-KRX','KRW','ACTIVE','SYNTHETIC_PROVIDER','2026-08-14',repeat('1',64)),
('97800000-0000-0000-0000-000000000002','SYNTHETIC_SECURITIES','안심 국채형 채권','B***02','BOND','SYN-BOND','KRW','ACTIVE','SYNTHETIC_PROVIDER','2026-08-14',repeat('2',64)),
('97800000-0000-0000-0000-000000000003','SYNTHETIC_SECURITIES','안심 균형형 펀드','F***03','FUND','SYN-FUND','KRW','ACTIVE','SYNTHETIC_PROVIDER','2026-08-14',repeat('3',64));
insert into market_quote_snapshot values
('97900000-0000-0000-0000-000000000001','97800000-0000-0000-0000-000000000001','2026-08-14T06:30:00Z',550000,540000,10000,1.8519,'KRW','2026-08-14',repeat('4',64)),
('97900000-0000-0000-0000-000000000002','97800000-0000-0000-0000-000000000002','2026-08-14T06:30:00Z',620000,618000,2000,0.3236,'KRW','2026-08-14',repeat('5',64)),
('97900000-0000-0000-0000-000000000003','97800000-0000-0000-0000-000000000003','2026-08-14T06:30:00Z',140000,139000,1000,0.7194,'KRW','2026-08-14',repeat('6',64));
insert into market_price_point_snapshot values
('98000000-0000-0000-0000-000000000001','97800000-0000-0000-0000-000000000001','2026-08-12',530000,545000,525000,540000,12000,'2026-08-14',repeat('7',64)),
('98000000-0000-0000-0000-000000000002','97800000-0000-0000-0000-000000000001','2026-08-13',540000,552000,535000,540000,13500,'2026-08-14',repeat('8',64)),
('98000000-0000-0000-0000-000000000003','97800000-0000-0000-0000-000000000001','2026-08-14',542000,558000,540000,550000,14800,'2026-08-14',repeat('9',64));
insert into investment_order_snapshot values
('98100000-0000-0000-0000-000000000001','97200000-0000-0000-0000-000000000001','97800000-0000-0000-0000-000000000001','LIMIT','BUY',10,500000,10,'FILLED','2026-07-10T01:10:00Z','KRW','2026-08-14',repeat('a',64)),
('98100000-0000-0000-0000-000000000002','97200000-0000-0000-0000-000000000001','97800000-0000-0000-0000-000000000003','MARKET','BUY',10,130000,10,'FILLED','2026-07-15T02:20:00Z','KRW','2026-08-14',repeat('b',64));
insert into customer_watchlist_state values('SYN_CUSTOMER_FIN_MGMT_001',1,'2026-08-14T00:00:00Z');
insert into customer_watchlist_item values
('SYN_CUSTOMER_FIN_MGMT_001','97800000-0000-0000-0000-000000000001',1,'2026-08-14T00:00:00Z'),
('SYN_CUSTOMER_FIN_MGMT_001','97800000-0000-0000-0000-000000000003',2,'2026-08-14T00:00:00Z');

insert into auth_permission(permission_code,description) values
('INVESTMENT_MARKET_READ','본인의 합성 주문이력과 합성 시장정보 조회'),('INVESTMENT_WATCHLIST_READ','본인의 합성 관심종목 조회'),
('INVESTMENT_WATCHLIST_WRITE','본인의 합성 관심종목 변경');
insert into auth_role_permission(role_code,permission_code) values
('CUSTOMER','INVESTMENT_MARKET_READ'),('CUSTOMER','INVESTMENT_WATCHLIST_READ'),('CUSTOMER','INVESTMENT_WATCHLIST_WRITE');

do $$ begin if exists(select 1 from pg_roles where rolname='alzswell_app') then
 revoke insert,update,delete on market_instrument_snapshot,market_quote_snapshot,market_price_point_snapshot,investment_order_snapshot from alzswell_app;
 revoke update,delete on customer_watchlist_event from alzswell_app;
 end if; end $$;

comment on table market_instrument_snapshot is '실제 종목코드 없이 제공하는 안심증권 합성 종목 snapshot';
comment on table market_quote_snapshot is '외부 시세 호출 없이 기준일이 고정된 합성 quote snapshot';
comment on table market_price_point_snapshot is '차트 표시 전용 합성 OHLC snapshot';
comment on table investment_order_snapshot is '주문 실행·취소 기능이 없는 과거 합성 주문 snapshot';
comment on table customer_watchlist_event is '고객 관심종목 전체 교체의 추가 전용 감사 event';
