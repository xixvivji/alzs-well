alter table ai_knowledge.document_snapshot
    add column document_type varchar(30);

alter table ai_knowledge.document_snapshot
    add constraint ck_ai_document_snapshot_type check (
        document_type is null
        or document_type in (
            'LAW', 'REGULATION', 'PUBLIC_GUIDE', 'PUBLIC_NOTICE',
            'FORM', 'INTERNAL_POLICY', 'SYNTHETIC_FIXTURE'
        )
    );

comment on column ai_knowledge.document_snapshot.document_type is
    'manifest 문서유형. 기존 스냅샷의 null은 재색인 전 권위 미확정 상태';

create function ai_knowledge.document_authority_rank(value varchar)
returns integer
language sql
immutable
parallel safe
as $$
    select case value
        when 'LAW' then 600
        when 'REGULATION' then 500
        when 'INTERNAL_POLICY' then 400
        when 'PUBLIC_GUIDE' then 300
        when 'PUBLIC_NOTICE' then 200
        when 'FORM' then 100
        else 0
    end
$$;

revoke all on function ai_knowledge.document_authority_rank(varchar) from public;
do $$ begin
    if exists(select 1 from pg_roles where rolname = 'alzswell_ai_runtime') then
        grant execute on function ai_knowledge.document_authority_rank(varchar)
            to alzswell_ai_runtime;
    end if;
end $$;

comment on function ai_knowledge.document_authority_rank(varchar) is
    '관련성 임계값 통과 후 적용하는 지식 문서 권위 순위';
