alter table knowledge_passage
    add column search_vector tsvector generated always as (
        setweight(to_tsvector('pg_catalog.simple'::regconfig, coalesce(heading, '')), 'A')
        || setweight(to_tsvector('pg_catalog.simple'::regconfig, coalesce(content, '')), 'B')
    ) stored;

create index idx_knowledge_passage_search_vector
    on knowledge_passage using gin (search_vector);

alter table knowledge_access_audit_event drop constraint ck_knowledge_access_event_type;
alter table knowledge_access_audit_event add constraint ck_knowledge_access_event_type
    check(event_type in ('DOCUMENT_LIST','DOCUMENT_DETAIL','VERSION_LIST','PASSAGE_DETAIL','SEARCH',
        'GUIDANCE_CITATION','PROTECTION_ACTION_CITATION'));
alter table knowledge_access_audit_event drop constraint ck_knowledge_access_permission;
alter table knowledge_access_audit_event add constraint ck_knowledge_access_permission
    check(permission_code in ('KNOWLEDGE_READ','KNOWLEDGE_SEARCH','GUIDANCE_CANDIDATE_READ',
        'PROTECTION_ACTION_READ'));

do $$ begin
    if exists(select 1 from pg_roles where rolname = 'alzswell_app') then
        grant select(search_vector) on knowledge_passage to alzswell_app;
    end if;
end $$;

comment on column knowledge_passage.search_vector is
    'simple 사전으로 생성한 제목·본문의 불변 전문검색 벡터. 기존 keyword GIN과 함께 fallback 후보를 최대 200개만 조회';
comment on index idx_knowledge_passage_search_vector is
    '사용자 문자열을 SQL에 결합하지 않고 plainto_tsquery 바인딩으로 조회하는 지식 passage GIN 인덱스';
comment on constraint ck_knowledge_access_event_type on knowledge_access_audit_event is
    '직접 지식조회뿐 아니라 안내 후보·보호수단 상세이 우회 조회한 citation도 출처별로 감사';
