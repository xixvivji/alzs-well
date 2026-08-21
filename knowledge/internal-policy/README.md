# ALZ's well 자체 정책 문서 통합 색인

> 문서 세트 버전: 1.0-draft  
> 작성 기준일: 2026-08-21  
> 성격: 공모전 MVP 및 금융회사 PoC 설계를 위한 자체 정책 초안

이 문서 세트는 공개 공식문서 자체가 아니라, 공식 근거와 프로젝트 SSOT·구현을 바탕으로 ALZ's well이 자체 정의해야 하는 운영 기준이다. 금융회사의 법무·준법·소비자보호·정보보호 승인을 받기 전 최종 내규로 사용할 수 없다.

## 문서 목록

| 번호 | 문서 | 핵심 책임 |
|---|---|---|
| AW-POL-001 | [치매머니 안내 정책](01_DEMENTIA_MONEY_GUIDANCE_POLICY.md) | 안내 개시·금지·공식 확인 |
| AW-POL-002 | [서비스 카탈로그](02_ALZS_WELL_SERVICE_CATALOG.md) | 안심은행·안심증권 서비스 코드 |
| AW-POL-003 | [사유코드·상태 전이](03_REASON_CODE_AND_STATE_TRANSITION.md) | 권위 상태와 불변조건 |
| AW-POL-004 | [행원 플레이북](04_STAFF_RESPONSE_PLAYBOOK.md) | 상황별 사실확인·후속업무 |
| AW-POL-005 | [AI 출력 정책](05_AI_COPILOT_OUTPUT_POLICY.md) | 생성 범위·거부·폴백 |
| AW-POL-006 | [개인정보·동의 기준](06_PRIVACY_AND_CONSENT_STANDARD.md) | 최소처리·철회·신뢰연락인 |
| AW-POL-007 | [RAG 지식 운영](07_RAG_KNOWLEDGE_GOVERNANCE.md) | 폐쇄망·ACL·검색·인용 |
| AW-POL-008 | [RAG 평가 기준](08_RAG_EVALUATION_STANDARD.md) | 안전 게이트·회귀시험 |
| AW-POL-009 | [문서 반입 절차](09_DOCUMENT_INGESTION_SECURITY_PROCEDURE.md) | 격리·악성검사·승인 |
| AW-POL-010 | [접근권한 정책](10_ACCESS_CONTROL_POLICY.md) | 역할·최소권한·직무분리 |
| AW-POL-011 | [감사·모니터링](11_AUDIT_AND_MONITORING_POLICY.md) | 기록·무결성·관제 |
| AW-POL-012 | [사고 대응](12_INCIDENT_RESPONSE_PROCEDURE.md) | 격리·폴백·복구 |
| AW-POL-013 | [문서 생명주기](13_DOCUMENT_LIFECYCLE_PROCEDURE.md) | 최신성·교체·폐기 |

## 공통 권위 원칙

1. 최상위 제품 범위와 표현은 `ALZS_WELL_PROJECT_SSOT.md`를 따른다.
2. 상태·코드·권한의 실행 가능 값은 현재 DB 제약과 애플리케이션 정책을 따른다.
3. 법령·감독규정·공식 공공자료는 `knowledge/official-source`의 승인된 최신 버전을 사용한다.
4. 자체 정책은 공식 근거를 대체하거나 법적 권한을 새로 만들지 않는다.
5. AI는 진단·최종판단·상태변경·연락·금융실행을 하지 않는다.
6. 실제 금융회사명 대신 `안심은행`, `안심증권`을 사용한다.

## 추적성

| 정책 영역 | 주요 코드·데이터 구조 | 공식 근거 묶음 |
|---|---|---|
| 변화신호·사건 | `customer_detection_signal`, `operational_alert`, `operational_protection_case` | 02, 06, 13, 14 |
| 보호서비스 | `protection_action_catalog`, `ProtectionCatalogService` | 05, 08, 12, 14, 16 |
| 코파일럿 | `CopilotPort`, `DeterministicCopilotAdapter` | 03, 04 |
| 동의·연락인 | `customer_consent`, `trusted_contact`, `ConsentService` | 01, 03, 16 |
| 지식 카탈로그 | `knowledge_document`, `knowledge_document_version`, `knowledge_passage` | 공식 corpus 전체 |
| 감사·권한 | `auth_role_permission`, 각 audit/event 테이블 | 01, 03, 04 |

## 승인 전 남은 TBD

- 실제 조직의 문서 소유자·승인자·비상연락망
- 법적 근거별 보유·삭제기간과 로그 보유기간
- 공공서비스·금융회사 서비스의 운영 기준일 및 갱신 주기
- P1 역할·권한과 이중승인 구현
- 내부 임베딩·LLM·벡터 DB 제품 및 승인 버전
- 모델 제한시간, 검색 임계값, 품질 지표와 사고 목표시간

## 변경 규칙

모든 문서 변경은 문서번호와 버전을 유지하고 변경이력에 사유·영향·승인자를 기록한다. 코드와 불일치하는 문서 변경은 배포하지 않는다. 공식 원문은 이 디렉터리에 복사·수정하지 않고 `knowledge/official-source`에서 해시로 참조한다.

