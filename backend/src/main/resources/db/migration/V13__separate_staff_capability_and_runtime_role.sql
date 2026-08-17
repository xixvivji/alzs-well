-- 고객 세션 생성 시에는 고객 capability만 저장한다. 직원 capability는 인증된 직원 발급 API에서 나중에 설정한다.
create or replace function require_demo_capability_hashes()
returns trigger
language plpgsql
as $$
begin
    if new.customer_capability_hash is null then
        raise exception 'new demo sessions require a customer capability hash';
    end if;
    if new.staff_capability_hash is not null
       and new.customer_capability_hash = new.staff_capability_hash then
        raise exception 'customer and staff capability hashes must be distinct';
    end if;
    return new;
end;
$$;

comment on column demo_session.staff_capability_hash is
    '인증된 직원 발급 API 호출 후 설정되는 직원 화면 capability SHA-256';

-- Docker 배포에서는 bootstrap 서비스가 이 제한 역할을 먼저 만든다.
-- Testcontainers처럼 역할이 없는 환경에서는 현재 테스트 DB 사용자로 migration과 검증을 계속 수행한다.
do $$
declare
    runtime_role constant text := 'alzswell_app';
begin
    if exists (select 1 from pg_roles where rolname = runtime_role) then
        if not has_database_privilege(runtime_role, current_database(), 'CONNECT') then
            execute format('grant connect on database %I to %I', current_database(), runtime_role);
        end if;
        if not has_schema_privilege(runtime_role, 'public', 'USAGE') then
            execute format('grant usage on schema public to %I', runtime_role);
        end if;
        execute format(
                'grant select, insert, update, delete on all tables in schema public to %I',
                runtime_role
        );
        execute format('grant usage, select on all sequences in schema public to %I', runtime_role);
        execute format('revoke update, delete on decision_audit from %I', runtime_role);
        execute format('revoke update, delete on case_note from %I', runtime_role);

        execute format(
                'alter default privileges for role %I in schema public '
                'grant select, insert, update, delete on tables to %I',
                current_user,
                runtime_role
        );
        execute format(
                'alter default privileges for role %I in schema public '
                'grant usage, select on sequences to %I',
                current_user,
                runtime_role
        );
    end if;
end;
$$;
