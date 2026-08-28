-- Preserve the authenticated principal/session that performed customer mutations.
-- Existing actor_id values are retained as legacy snapshots; new writes populate
-- the normalized AuditActor columns as well.

alter table account_display_setting_event
    add column actor_principal_id uuid,
    add column actor_customer_id varchar(80),
    add column actor_session_id uuid,
    add column actor_type varchar(20) not null default 'LEGACY';

alter table account_display_setting_event
    add constraint ck_account_display_event_actor_type
        check (actor_type in ('CUSTOMER','STAFF','SYSTEM','LEGACY'));

alter table customer_transaction_preference_event
    add column actor_principal_id uuid,
    add column actor_customer_id varchar(80),
    add column actor_session_id uuid,
    add column actor_type varchar(20) not null default 'LEGACY';

alter table customer_transaction_preference_event
    add constraint ck_transaction_preference_event_actor_type
        check (actor_type in ('CUSTOMER','STAFF','SYSTEM','LEGACY'));

alter table customer_watchlist_event
    add column actor_principal_id uuid,
    add column actor_customer_id varchar(80),
    add column actor_session_id uuid,
    add column actor_type varchar(20) not null default 'LEGACY',
    add column event_hash_version varchar(30) not null default 'LEGACY_V1';

alter table customer_watchlist_event
    add constraint ck_watchlist_event_actor_type
        check (actor_type in ('CUSTOMER','STAFF','SYSTEM','LEGACY'));

alter table customer_watchlist_event
    add constraint ck_watchlist_event_hash_version
        check (event_hash_version in ('LEGACY_V1','ACTOR_SNAPSHOT_V2'));

comment on column account_display_setting_event.actor_principal_id
    is '이벤트를 생성한 Bearer principal UUID; legacy·system 이벤트는 null';
comment on column account_display_setting_event.actor_session_id
    is '이벤트를 생성한 인증 세션 UUID; legacy·system 이벤트는 null';
comment on column customer_transaction_preference_event.actor_principal_id
    is '이벤트를 생성한 Bearer principal UUID; legacy·system 이벤트는 null';
comment on column customer_transaction_preference_event.actor_session_id
    is '이벤트를 생성한 인증 세션 UUID; legacy·system 이벤트는 null';
comment on column customer_watchlist_event.actor_principal_id
    is '이벤트를 생성한 Bearer principal UUID; legacy·system 이벤트는 null';
comment on column customer_watchlist_event.actor_session_id
    is '이벤트를 생성한 인증 세션 UUID; legacy·system 이벤트는 null';
comment on column customer_watchlist_event.actor_id
    is 'V70 rolling 배포 호환용 legacy principal UUID; 새 코드는 actor_principal_id에도 같은 값을 기록';
comment on column customer_watchlist_event.event_hash_version
    is 'LEGACY_V1은 기존 customer/version/instrument 해시, ACTOR_SNAPSHOT_V2는 이벤트·시각·인증 actor snapshot까지 결속';
comment on column account_display_setting_event.actor_type
    is 'V70 rolling 배포 중 구버전 writer는 LEGACY, 새 writer는 인증 actor type을 명시';
comment on column customer_transaction_preference_event.actor_type
    is 'V70 rolling 배포 중 구버전 writer는 LEGACY, 새 writer는 인증 actor type을 명시';
comment on column customer_watchlist_event.actor_type
    is 'V70 rolling 배포 중 구버전 writer는 LEGACY, 새 writer는 인증 actor type을 명시';
