create table knowledge_document (
    document_id varchar(80) primary key,
    title varchar(200) not null,
    source_type varchar(40) not null,
    issuer varchar(160) not null,
    source_url varchar(500),
    audience varchar(30) not null,
    status varchar(20) not null,
    effective_from date not null,
    effective_to date,
    checked_at date not null,
    current_version varchar(40) not null,
    constraint ck_knowledge_source_type check (source_type in ('OFFICIAL_PUBLIC', 'SYNTHETIC_DEMO')),
    constraint ck_knowledge_audience check (audience in ('CUSTOMER', 'STAFF', 'BOTH')),
    constraint ck_knowledge_status check (status in ('APPROVED', 'EXPIRED')),
    constraint ck_knowledge_effective_period check (effective_to is null or effective_to >= effective_from),
    constraint ck_knowledge_source_url check (source_type <> 'OFFICIAL_PUBLIC' or source_url is not null)
);

create table knowledge_document_version (
    document_version_id uuid primary key,
    document_id varchar(80) not null references knowledge_document (document_id) on delete restrict,
    version_label varchar(40) not null,
    content_checksum varchar(71) not null,
    published_at timestamptz not null,
    approved_at timestamptz not null,
    superseded_at timestamptz,
    constraint uq_knowledge_document_version unique (document_id, version_label),
    constraint ck_knowledge_checksum check (content_checksum ~ '^sha256:[0-9a-f]{64}$')
);

create table knowledge_passage (
    passage_id uuid primary key,
    document_version_id uuid not null references knowledge_document_version (document_version_id) on delete restrict,
    passage_order integer not null,
    heading varchar(160) not null,
    content varchar(2000) not null,
    keywords text[] not null,
    citation_label varchar(240) not null,
    constraint uq_knowledge_passage_order unique (document_version_id, passage_order),
    constraint ck_knowledge_passage_order check (passage_order > 0),
    constraint ck_knowledge_passage_content check (btrim(content) <> '')
);
create index idx_knowledge_passage_keywords on knowledge_passage using gin (keywords);

insert into knowledge_document values
    ('DOC-FSC-SAFE-BLOCK-001', '금융거래 안심차단 안내 근거', 'OFFICIAL_PUBLIC', '금융위원회',
     'https://www.fsc.go.kr/no010101/85644', 'BOTH', 'APPROVED', '2024-08-23', null, '2026-08-14', '2026-08'),
    ('DOC-SYN-BANK-SUPPORT-001', '안심은행 보호상담 시연 안내', 'SYNTHETIC_DEMO', '안심은행',
     null, 'BOTH', 'APPROVED', '2026-08-14', null, '2026-08-14', '1.0.0');

insert into knowledge_document_version values
    ('94000000-0000-0000-0000-000000000001', 'DOC-FSC-SAFE-BLOCK-001', '2026-08',
     'sha256:44c30bb710880309df21821efb12a71a87a3e137df9a226681199e71d8d1f0bc',
     '2026-08-14T00:00:00Z', '2026-08-14T00:00:00Z', null),
    ('94000000-0000-0000-0000-000000000002', 'DOC-SYN-BANK-SUPPORT-001', '1.0.0',
     'sha256:8b3c32d4f5e2074132bbc6db42c57b67c695c3828f6be42a7dcf63c89132e11f',
     '2026-08-14T00:00:00Z', '2026-08-14T00:00:00Z', null);

insert into knowledge_passage values
    ('95000000-0000-0000-0000-000000000001', '94000000-0000-0000-0000-000000000001', 1,
     '신청 전 확인', '안심차단 신청 가능 여부와 세부 범위는 해당 금융회사에서 최종 확인해야 합니다.',
     array['안심차단','보호수단','금융회사','상담'], '금융거래 안심차단 안내 근거 — 신청 전 확인'),
    ('95000000-0000-0000-0000-000000000002', '94000000-0000-0000-0000-000000000002', 1,
     '외부 실행 금지', '상담 연결은 안내 계획에만 담으며 전화·문자·예약을 자동 실행하지 않습니다.',
     array['상담','안내계획','외부연락','실행금지'], '안심은행 보호상담 시연 안내 — 외부 실행 금지');

insert into auth_permission (permission_code, description) values
    ('KNOWLEDGE_READ', '승인된 공식·합성 근거와 인용 passage 조회'),
    ('KNOWLEDGE_SEARCH', '권한·효력기간이 적용된 근거 검색'),
    ('GUIDANCE_CANDIDATE_READ', '정책이 허용한 보호수단 안내 후보 조회');
insert into auth_role_permission (role_code, permission_code) values
    ('PROTECTION_STAFF', 'KNOWLEDGE_READ'), ('PROTECTION_STAFF', 'KNOWLEDGE_SEARCH'),
    ('PROTECTION_STAFF', 'GUIDANCE_CANDIDATE_READ'),
    ('DETECTION_ADMIN', 'KNOWLEDGE_READ'), ('DETECTION_ADMIN', 'KNOWLEDGE_SEARCH'),
    ('DETECTION_ADMIN', 'GUIDANCE_CANDIDATE_READ');

comment on table knowledge_document is '공개 공모전에서 허용된 공식 공개자료 또는 합성 안내 문서';
comment on table knowledge_document_version is '승인시각과 checksum을 가진 불변 문서 버전';
comment on table knowledge_passage is '결정론적 검색과 인용에 사용하는 승인 문서 구절';
