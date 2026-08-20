insert into auth_permission (permission_code, description) values
    ('AUDIT_READ_ALL', '통합 불변 감사이벤트 검색·상세 조회'),
    ('COMPLIANCE_TRACE_READ', '판단 근거와 합성 데이터 출처·버전 조회');

comment on column auth_permission.permission_code is
    '감사·컴플라이언스 권한은 기존 역할에 자동 부여하지 않고 별도 승인된 principal 역할에만 배정';
