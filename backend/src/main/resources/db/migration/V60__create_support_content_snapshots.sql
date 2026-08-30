-- P2 고객지원 읽기: 외부 고객센터나 금융기관 API 없이 승인된 합성 콘텐츠만 제공한다.
create table support_faq_snapshot (
    faq_id uuid primary key,
    category_code varchar(30) not null,
    question varchar(160) not null,
    answer_text varchar(1200) not null,
    display_order integer not null,
    status varchar(20) not null,
    provider_mode varchar(30) not null,
    data_as_of date not null,
    snapshot_hash char(64) not null,
    constraint ck_support_faq_category check (
        category_code in ('GENERAL','SECURITY','ALERTS','PRIVACY','ACCESSIBILITY')
    ),
    constraint ck_support_faq_order check (display_order > 0),
    constraint ck_support_faq_status check (status = 'PUBLISHED'),
    constraint ck_support_faq_provider check (provider_mode = 'INTERNAL_SYNTHETIC'),
    constraint ck_support_faq_hash check (snapshot_hash ~ '^[0-9a-f]{64}$'),
    unique (category_code, display_order)
);
create index idx_support_faq_catalog
    on support_faq_snapshot(status, category_code, display_order, faq_id);
create trigger trg_support_faq_append_only
before update or delete on support_faq_snapshot
for each row execute function reject_protected_event_mutation();

create table support_notice_snapshot (
    notice_id uuid primary key,
    institution_id varchar(40) not null references financial_institution(institution_id),
    category_code varchar(30) not null,
    title varchar(160) not null,
    body_text varchar(2000) not null,
    important boolean not null,
    published_at timestamptz not null,
    expires_at timestamptz,
    status varchar(20) not null,
    provider_mode varchar(30) not null,
    data_as_of date not null,
    snapshot_hash char(64) not null,
    constraint ck_support_notice_category check (
        category_code in ('SERVICE','SECURITY','MAINTENANCE','PRODUCT')
    ),
    constraint ck_support_notice_period check (expires_at is null or expires_at >= published_at),
    constraint ck_support_notice_status check (status = 'PUBLISHED'),
    constraint ck_support_notice_provider check (provider_mode = 'SYNTHETIC_PROVIDER'),
    constraint ck_support_notice_hash check (snapshot_hash ~ '^[0-9a-f]{64}$')
);
create index idx_support_notice_catalog
    on support_notice_snapshot(status, important desc, published_at desc, notice_id desc);
create trigger trg_support_notice_append_only
before update or delete on support_notice_snapshot
for each row execute function reject_protected_event_mutation();

insert into support_faq_snapshot values
('98200000-0000-0000-0000-000000000001','GENERAL','ALZ''s well은 어떤 서비스인가요?','금융생활의 평소 패턴과 달라진 변화를 합성 데이터로 설명하고, 고객 확인이 필요한 경우에만 보호업무로 연결하는 데모 서비스입니다.',1,'PUBLISHED','INTERNAL_SYNTHETIC','2026-08-25',repeat('1',64)),
('98200000-0000-0000-0000-000000000002','SECURITY','실제 계좌나 거래정보를 사용하나요?','아니요. 공개 데모는 안심은행과 안심증권의 마스킹된 합성 데이터만 사용하며 실제 금융기관 API를 호출하지 않습니다.',1,'PUBLISHED','INTERNAL_SYNTHETIC','2026-08-25',repeat('2',64)),
('98200000-0000-0000-0000-000000000003','ALERTS','변화 알림이 오면 거래가 자동으로 차단되나요?','아니요. 알림은 생활맥락을 확인하기 위한 안내이며 거래 차단, 송금 취소, 가족 연락을 자동 실행하지 않습니다.',1,'PUBLISHED','INTERNAL_SYNTHETIC','2026-08-25',repeat('3',64)),
('98200000-0000-0000-0000-000000000004','PRIVACY','신뢰연락인에게 정보가 자동 전달되나요?','아니요. 별도 수신자 인증과 승인 절차가 없는 현재 데모에서는 지정 상태와 최소 공개 범위만 관리하고 외부 연락은 실행하지 않습니다.',1,'PUBLISHED','INTERNAL_SYNTHETIC','2026-08-25',repeat('4',64)),
('98200000-0000-0000-0000-000000000005','ACCESSIBILITY','화면을 쉽게 읽을 수 있도록 설정할 수 있나요?','글자 크기, 대비, 쉬운 설명과 같은 합성 접근성 설정을 고객 프로필에서 선택할 수 있습니다.',1,'PUBLISHED','INTERNAL_SYNTHETIC','2026-08-25',repeat('5',64));

insert into support_notice_snapshot values
('98300000-0000-0000-0000-000000000001','SYNTHETIC_BANK','SECURITY','합성 데이터 전용 데모 이용 안내','본 서비스는 실제 금융정보를 수집하지 않으며 송금, 차단, 외부 연락을 실행하지 않습니다.',true,'2026-08-25T00:00:00+09:00',null,'PUBLISHED','SYNTHETIC_PROVIDER','2026-08-25',repeat('6',64)),
('98300000-0000-0000-0000-000000000002','SYNTHETIC_BANK','SERVICE','생활맥락 확인 기능 안내','변화 알림에서 본인의 생활맥락을 선택하면 결정론적 정책으로 다음 안내 단계를 보여줍니다.',false,'2026-08-24T09:00:00+09:00',null,'PUBLISHED','SYNTHETIC_PROVIDER','2026-08-25',repeat('7',64)),
('98300000-0000-0000-0000-000000000003','SYNTHETIC_BANK','MAINTENANCE','데모 데이터 초기화 안내','새 체험을 시작하면 기존 합성 세션과 분리된 새로운 실행 단위가 만들어집니다.',false,'2026-08-23T09:00:00+09:00','2027-08-23T23:59:59+09:00','PUBLISHED','SYNTHETIC_PROVIDER','2026-08-25',repeat('8',64));

insert into auth_permission(permission_code, description) values
('SUPPORT_CONTENT_READ','합성 FAQ와 안심은행 공지 조회');
insert into auth_role_permission(role_code, permission_code) values
('CUSTOMER','SUPPORT_CONTENT_READ'),
('PROTECTION_STAFF','SUPPORT_CONTENT_READ'),
('DETECTION_ADMIN','SUPPORT_CONTENT_READ');

do $$ begin
 if exists(select 1 from pg_roles where rolname = 'alzswell_app') then
  revoke insert, update, delete on support_faq_snapshot, support_notice_snapshot from alzswell_app;
 end if;
end $$;

comment on table support_faq_snapshot is '외부 고객센터 호출 없이 제공하는 ALZ''s well 합성 FAQ snapshot';
comment on table support_notice_snapshot is '안심은행의 외부 공지 API를 대신하는 추가 전용 합성 공지 snapshot';
