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
