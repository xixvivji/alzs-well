insert into auth_role_permission(role_code, permission_code) values
    ('CUSTOMER', 'KNOWLEDGE_READ'),
    ('CUSTOMER', 'KNOWLEDGE_SEARCH')
on conflict do nothing;

comment on table knowledge_access_audit_event is
    '고객·행원·관리자가 승인된 audience 범위 안에서 수행한 근거 조회 감사이력';
