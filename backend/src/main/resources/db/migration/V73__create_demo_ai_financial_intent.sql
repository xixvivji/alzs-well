create table demo_financial_intent (
    demo_session_id uuid not null,
    demo_run_id uuid not null,
    intent_id uuid not null,
    customer_id varchar(80) not null,
    status varchar(20) not null check (status in ('DRAFT', 'APPROVED')),
    version bigint not null check (version > 0),
    payment_continuity varchar(40) not null
        check (payment_continuity in ('KEEP_ESSENTIAL_PAYMENTS', 'REVIEW_BEFORE_CHANGE')),
    explanation_mode varchar(40) not null
        check (explanation_mode in ('SIMPLE_TEXT', 'VOICE_AND_TEXT', 'STAFF_EXPLANATION')),
    help_condition varchar(40) not null
        check (help_condition in ('ON_REPEATED_CHANGE', 'ON_CUSTOMER_REQUEST', 'NEVER_AUTOMATIC')),
    share_scopes varchar(40)[] not null default '{}',
    disclaimer_accepted boolean not null default false,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    approved_at timestamptz,
    primary key (demo_session_id, demo_run_id),
    unique (intent_id),
    constraint fk_demo_financial_intent_run foreign key (demo_session_id, demo_run_id)
        references demo_run (demo_session_id, demo_run_id) on delete cascade,
    constraint ck_demo_financial_intent_scope check (
        share_scopes <@ array[
            'PAYMENT_PREFERENCE', 'EXPLANATION_PREFERENCE', 'HELP_CONDITION', 'ACCESSIBILITY'
        ]::varchar[]
        and cardinality(share_scopes) <= 4
    ),
    constraint ck_demo_financial_intent_approval check (
        (status = 'DRAFT' and not disclaimer_accepted and approved_at is null)
        or (status = 'APPROVED' and disclaimer_accepted and approved_at is not null)
    )
);

do $$
begin
    if exists(select 1 from pg_roles where rolname = 'alzswell_app') then
        grant select, insert on demo_financial_intent to alzswell_app;
        grant update(status, version, payment_continuity, explanation_mode, help_condition,
            share_scopes, disclaimer_accepted, updated_at, approved_at)
            on demo_financial_intent to alzswell_app;
        revoke delete on demo_financial_intent from alzswell_app;
    end if;
end $$;

comment on table demo_financial_intent is
    '익명 합성 데모 세션에만 귀속되고 세션 폐기 시 cascade 삭제되는 고객 확인형 금융생활 의향';
