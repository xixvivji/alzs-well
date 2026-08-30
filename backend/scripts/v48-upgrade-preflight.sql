\set ON_ERROR_STOP on

-- V40~V47 운영 DB를 V48 이상으로 올리기 전에 실행하는 읽기 전용 검사입니다.
-- 결과가 한 행이라도 나오면 해당 grant를 기존 API로 철회한 뒤 목적별 grant로 재발급해야 합니다.
with purpose_scope(purpose_code, scope_code) as (values
    ('CUSTOMER_CONSENT_MANAGEMENT','CONSENT_READ'),
    ('CUSTOMER_CONSENT_MANAGEMENT','CONSENT_WRITE'),
    ('TRUSTED_CONTACT_MANAGEMENT','TRUSTED_CONTACT_READ'),
    ('TRUSTED_CONTACT_MANAGEMENT','TRUSTED_CONTACT_WRITE'),
    ('FINANCIAL_INTENT_REVIEW','FINANCIAL_INTENT_READ'),
    ('PROTECTION_CASE_MANAGEMENT','CASE_READ'),
    ('PROTECTION_CASE_MANAGEMENT','CASE_ASSIGN'),
    ('PROTECTION_CASE_MANAGEMENT','CASE_REVIEW'),
    ('PROTECTION_CASE_MANAGEMENT','CASE_GUIDANCE'),
    ('PROTECTION_CASE_MANAGEMENT','CASE_NOTE'),
    ('PROTECTION_CASE_MANAGEMENT','CASE_FOLLOW_UP'),
    ('PRIVACY_REQUEST_ASSISTANCE','PRIVACY_REQUEST_WRITE'),
    ('ALERT_MANAGEMENT','ALERT_READ'),
    ('ALERT_MANAGEMENT','ALERT_RESPOND'),
    ('PROTECTION_ENROLLMENT_REVIEW','PROTECTION_ENROLLMENT_READ')
), classified as (
    select g.grant_id,g.staff_principal_id,g.customer_id,g.purpose_code,g.scopes,g.status,g.row_version,
           array_agg(distinct p.purpose_code order by p.purpose_code)
               filter(where p.purpose_code is not null) candidate_purposes
      from staff_access_grant g
      left join lateral unnest(g.scopes) requested(scope_code) on true
      left join purpose_scope p on p.scope_code=requested.scope_code
     group by g.grant_id
)
select * from classified
 where coalesce(cardinality(candidate_purposes),0) <> 1
 order by customer_id,grant_id;
