create table financial_intent (
 intent_id uuid primary key, customer_id varchar(80) not null, status varchar(20) not null check(status in ('DRAFT','APPROVED','REVOKED')),
 version bigint not null, payment_continuity varchar(40) not null, explanation_mode varchar(40) not null,
 help_condition varchar(40) not null, share_scopes varchar(40)[] not null, disclaimer_accepted boolean not null,
 created_at timestamptz not null, updated_at timestamptz not null, approved_at timestamptz, revoked_at timestamptz,
 constraint ck_intent_approval check(status<>'APPROVED' or (disclaimer_accepted and approved_at is not null)),
 constraint ck_intent_revoke check(status<>'REVOKED' or revoked_at is not null)
);
create unique index uq_financial_intent_active on financial_intent(customer_id) where status='APPROVED';
create unique index uq_financial_intent_draft on financial_intent(customer_id) where status='DRAFT';
create table financial_intent_revision(
 intent_id uuid not null references financial_intent(intent_id),version bigint not null,status varchar(20) not null,
 payment_continuity varchar(40) not null,explanation_mode varchar(40) not null,help_condition varchar(40) not null,
 share_scopes varchar(40)[] not null,disclaimer_accepted boolean not null,recorded_at timestamptz not null,
 primary key(intent_id,version)
);
create table financial_intent_event(
 event_id uuid primary key,intent_id uuid not null references financial_intent(intent_id),event_type varchar(30) not null,
 status_snapshot varchar(20) not null,version bigint not null,actor_principal_id uuid,actor_customer_id varchar(80),
 actor_session_id uuid,actor_type varchar(20) not null,detail jsonb not null default '{}'::jsonb,occurred_at timestamptz not null
);
create trigger trg_financial_intent_event_append_only before update or delete on financial_intent_event
for each row execute function reject_protected_event_mutation();
create trigger trg_financial_intent_revision_append_only before update or delete on financial_intent_revision
for each row execute function reject_protected_event_mutation();
create table financial_intent_command(
 command_scope varchar(140) not null,idempotency_key varchar(100) not null,request_hash char(64) not null,
 result_intent_id uuid references financial_intent(intent_id),result_version bigint,created_at timestamptz not null,
 primary key(command_scope,idempotency_key)
);
insert into auth_permission(permission_code,description) values
 ('FINANCIAL_INTENT_READ','본인의 금융생활 준비·의향 조회'),('FINANCIAL_INTENT_WRITE','본인의 의향 초안·승인·철회'),
 ('FINANCIAL_INTENT_SHARED_READ','동의 범위 내 승인 의향의 행원 요약 조회');
insert into auth_role_permission(role_code,permission_code) values
 ('CUSTOMER','FINANCIAL_INTENT_READ'),('CUSTOMER','FINANCIAL_INTENT_WRITE'),('PROTECTION_STAFF','FINANCIAL_INTENT_SHARED_READ');
comment on table financial_intent is '법적 후견·유언·대리권을 만들지 않는 고객 승인형 금융생활 참고 의향';
