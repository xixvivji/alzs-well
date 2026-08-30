create table customer_inbox_message (
    message_id uuid primary key,
    customer_id varchar(80) not null references customer_profile (customer_id) on delete cascade,
    message_type varchar(30) not null,
    title varchar(120) not null,
    body varchar(1000) not null,
    related_resource_type varchar(30),
    related_resource_id uuid,
    read_at timestamptz,
    message_version bigint not null default 1,
    created_at timestamptz not null,
    constraint ck_customer_inbox_type check (message_type in ('CHANGE_ALERT', 'FOLLOW_UP', 'SERVICE_NOTICE')),
    constraint ck_customer_inbox_version check (message_version > 0),
    constraint ck_customer_inbox_content check (btrim(title) <> '' and btrim(body) <> '')
);
create index idx_customer_inbox_page on customer_inbox_message (customer_id, created_at desc, message_id desc);
create index idx_customer_inbox_unread on customer_inbox_message (customer_id, created_at desc, message_id desc) where read_at is null;

create table customer_notification_preference (
    customer_id varchar(80) primary key references customer_profile (customer_id) on delete cascade,
    change_alert_enabled boolean not null default true,
    follow_up_enabled boolean not null default true,
    service_notice_enabled boolean not null default true,
    preference_version bigint not null default 1,
    updated_at timestamptz not null,
    constraint ck_customer_notification_preference_version check (preference_version > 0)
);

insert into auth_permission (permission_code, description) values
    ('INBOX_READ', '자신의 서비스 내부 알림함 조회'),
    ('INBOX_WRITE', '자신의 서비스 내부 알림 읽음·설정 변경'),
    ('NOTIFICATION_PREVIEW', '외부 발송 없는 승인 문구 미리보기');
insert into auth_role_permission (role_code, permission_code) values
    ('CUSTOMER', 'INBOX_READ'), ('CUSTOMER', 'INBOX_WRITE'),
    ('PROTECTION_STAFF', 'NOTIFICATION_PREVIEW');
insert into customer_notification_preference (customer_id, updated_at)
select customer_id, now() from customer_profile;
insert into customer_inbox_message (
    message_id, customer_id, message_type, title, body,
    related_resource_type, related_resource_id, created_at
)
select gen_random_uuid(), customer_id, 'CHANGE_ALERT', '금융생활 변화 확인이 필요합니다',
       '평소와 다른 금융생활 변화가 확인되었습니다. 내용을 확인하고 생활맥락을 알려주세요.',
       'ALERT', alert_id, created_at from operational_alert;

comment on table customer_inbox_message is '문자·푸시 발송 없이 서비스 안에서만 제공하는 고객 알림';
comment on table customer_notification_preference is '인앱 알림 유형별 표시 설정이며 외부 발송 동의가 아님';
