create table auth_principal (
    principal_id uuid primary key,
    login_id varchar(80) not null unique,
    customer_id varchar(80) references customer_profile (customer_id),
    display_name varchar(80) not null,
    password_hash varchar(100) not null,
    status varchar(20) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_auth_principal_status check (status in ('ACTIVE', 'LOCKED', 'DISABLED')),
    constraint ck_auth_principal_customer check (customer_id is not null)
);

create table auth_role (role_code varchar(60) primary key, description varchar(200) not null);
create table auth_permission (permission_code varchar(100) primary key, description varchar(200) not null);
create table auth_principal_role (
    principal_id uuid not null references auth_principal (principal_id) on delete cascade,
    role_code varchar(60) not null references auth_role (role_code),
    primary key (principal_id, role_code)
);
create table auth_role_permission (
    role_code varchar(60) not null references auth_role (role_code) on delete cascade,
    permission_code varchar(100) not null references auth_permission (permission_code),
    primary key (role_code, permission_code)
);
create table auth_session (
    session_id uuid primary key,
    principal_id uuid not null references auth_principal (principal_id),
    access_token_hash char(64) not null unique,
    refresh_token_hash char(64) not null unique,
    access_expires_at timestamptz not null,
    refresh_expires_at timestamptz not null,
    created_at timestamptz not null,
    last_rotated_at timestamptz not null,
    revoked_at timestamptz,
    revoke_reason varchar(40),
    constraint ck_auth_session_expiry check (access_expires_at <= refresh_expires_at),
    constraint ck_auth_session_revoke check (
        (revoked_at is null and revoke_reason is null) or
        (revoked_at is not null and revoke_reason is not null)
    )
);
create index idx_auth_session_principal_active on auth_session (principal_id, refresh_expires_at)
    where revoked_at is null;

insert into auth_role values ('CUSTOMER', '자신의 고객 화면을 사용하는 합성 고객');
insert into auth_permission values
    ('CUSTOMER_PROFILE_READ', '자신의 고객 프로필 조회'),
    ('CUSTOMER_PROFILE_WRITE', '자신의 고객 프로필 변경');
insert into auth_role_permission values
    ('CUSTOMER', 'CUSTOMER_PROFILE_READ'),
    ('CUSTOMER', 'CUSTOMER_PROFILE_WRITE');
insert into auth_principal (
    principal_id, login_id, customer_id, display_name, password_hash, status, created_at, updated_at
) values (
    '91000000-0000-0000-0000-000000000001', 'synthetic-customer',
    'SYN_CUSTOMER_FIN_MGMT_001', '이용자 001',
    '$2y$12$Bu7SxonBbyIlnLnrupD/.eEWz3ZVBoC8bDvguOq9iJlsOAN8pGxBm', 'ACTIVE', now(), now()
);
insert into auth_principal_role values ('91000000-0000-0000-0000-000000000001', 'CUSTOMER');

comment on table auth_principal is '외부 IdP 전환 전 합성 인증 주체와 고객 연결';
comment on table auth_session is '원문을 저장하지 않는 회전형 access/refresh token 세션';
