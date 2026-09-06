# 고객 → 행원 연결: 백엔드 기준 화면 흐름

검토 기준: 2026-09-06, `feature/customer-ux-redesign` 작업본의 백엔드 코드·명세와 localhost의 공개 합성 계정 응답. 백엔드 코드·DB·권한은 변경하지 않았다.

## 기획서 반영 · 행원 작업 화면

사용자 제공 `2026_금융_AI_Challenge_기획서_수정본.pdf`의 2·3·5쪽을 UX 근거로 참고했다. 현재 응답과 허용된 사전의향을 같은 사건의 근거와 대조한 뒤 행원이 안내·종결·후속관리를 결정한다. 기획서의 Bedrock 연계 주장은 현재 로컬 회원 사건 API로 확인되지 않으므로 프론트에서 생성 버튼이나 결과를 만들어내지 않았다. 현재 제공하는 질문은 `DeterministicCopilotAdapter`의 사유별 기본 질문, 검색은 기존 회원 근거 검색이다.

- 사건 선택 시 긴 목록 대신 선택한 사건에 집중한다. `사건 목록으로`로 돌아가며 같은 사건의 입력은 유지한다.
- 공통 요약: 사건 상태·지금 할 일·고객 응답·평소 기준·최근 관찰.
- `고객·근거 확인`: 근거 날짜·수치·원문과 공유 의향 권한 조회.
- `검토·결정`: 사유별 상담 질문, 근거 검색, 내부 메모, 안내계획 또는 종결. 안내 승인은 항목 검토 후 별도 확정한다.
- `후속관리·이력`: 일정마다 별도 결과·취소 사유를 입력하고 새 일정·처리 이력을 조회한다. 종결 후에도 백엔드 허용 범위의 후속관리를 유지한다.
- 작업 탭 전환은 서버 상태 변경이 아니다. 승인·종결·일정 API의 역할·버전·멱등성 계약은 변경하지 않았다.
- 일반 로그인·도움 화면의 `/demo` 링크와 비로그인 `/help → /demo` 자동 이동을 제거했다. `/help`는 `/login?next=/banking/help`를 거친다. 데모 라우트와 API 자체는 삭제하지 않았다.
- `channel-switch`, `side-safety`, `prototype-role-switch`를 일반 화면에서 사용하지 않으며 남아 있던 앞의 두 CSS도 제거했다. 역할 선택은 상단 로그인 메뉴를 이용한다.

## 단일 고객 진입점

`/banking/help`에서 **변화와 근거 → 본인 응답 → 연결 결과**를 확인한다. `/banking/safety`는 같은 화면으로 이동하며 `alertId`를 보존한다. 의향 설정은 최초 이용·선호 변경 시에만 사용하는 별도 구획으로 남는다.

## 실제 회원용 상태 전이

| 사건 발생 전후 | 백엔드 동작 | 프론트 표시 |
| --- | --- | --- |
| 합성 탐지 결과의 운영 승격 | `DetectionPromotionService`가 신호와 `AWAITING_CONTEXT` 알림 생성 | 변화 근거를 먼저 표시. 행원 사건이 생겼다고 표시하지 않음 |
| `EXPECTED_CHANGE` 응답 | `OperationalAlertService.respond` → `CLOSED_NORMAL`, 사건 생성 없음 | 알고 있는 변화로 확인 완료 |
| `UNRECOGNIZED` / `NOT_SURE` 응답 | `BANK_REVIEW`로 변경, 같은 트랜잭션에서 `PENDING` 사건 생성 | 은행 검토 연결됨, 저장된 고객 응답 표시 |
| 확인 미루기 | `/alerts/{alertId}/defer` → `DEFERRED`, 사건 생성 없음 | 현재 UI는 하루 미루기. 백엔드는 미래 시각·최대 7일 검증 |
| 사람 재검토 이의신청 | 허용 상태에서 `/appeals`가 사건 생성·연결 | 기존 `/banking/settings` 경로 유지 |
| 행원 사건 조회 | 역할 권한 + 고객·목적별 유효한 `CASE_READ` grant 검사 | 허용된 목록만 표시, 30초 주기 선택적 조회와 수동 새로고침 |
| 담당자 배정·검토 | assignment → `START_REVIEW` → `IN_REVIEW` | 검토 시작, 고객 응답·근거·내부 기록 확인 |
| 안내계획 승인·종결 | guidance-plans → `GUIDANCE_APPROVED`; `COMPLETE_REVIEW` → `COMPLETED` | 안내 내용 검토 후 명시적 승인, 사유를 입력한 종결 |
| 후속관리 | follow-ups 일정·완료·취소 기록 | 내부 일정과 결과만 표시. 실제 연락 없음 |

사건 큐는 탐지 신호 전체를 실시간 푸시하는 화면이 아니다. 탐지 실행 자체와 운영 알림 승격도 별도 백엔드 단계다. 화면은 반환된 운영 알림과 사건을 조회하며 자동 탐지 작업이나 승격 명령을 실행하지 않는다.

## 고객 의견·의향의 범위

- 사건 상세의 `customerResponseCode`로 고객이 선택한 응답을 보여준다. 근거 수치나 행원 메모와 분리한다.
- `CaseworkResponses.CaseDetail`은 고객의 자유서술 의견·이의신청 원문을 제공하지 않는다. DB에 저장된 원문이 있다고 프론트가 조회 가능한 것처럼 꾸미지 않는다.
- 행원 의향은 `GET /api/v1/staff/customers/{customerId}/financial-intent-summary`만 사용한다. `FinancialIntentService.staff`는 최신 승인 의향의 공유 동의 필드만 반환하고 나머지는 null로 제외한다. 고객 전용 의향 이력 API로 우회하지 않는다.
- 회원 사건의 `검토·결정` 탭은 `POST /api/v1/staff/cases/{caseId}/copilot-drafts`로 선택형 Bedrock 검토 초안을 요청한다. 생성 성공과 기본 안내를 구분하고, 사건·버전 변경 시 이전 초안은 폐기한다. 정적 상담 질문 및 공식 근거 검색은 별도로 유지한다. `/demo/**/copilot-drafts`를 회원 사건에 호출하지 않는다. 전송 범위와 검증 한계는 [Bedrock 안내](BEDROCK_STAFF_DRAFT.md)를 따른다.

## 공개 데이터의 패턴과 담당 계정

`SyntheticMemberProvisioningService.provisionOperationalProtectionWorkflow`는 `MISSED_PAYMENT`, `DUPLICATE_TRANSFER`, `REPEATED_CONFIRMATION` 시나리오의 회원용 신호·근거·알림을 준비한다. `NORMAL`은 해당 알림을 만들지 않는다. `REPEATED_CONFIRMATION`은 완료된 거래 결과의 반복 확인이며 반복 송금 실패가 아니다. 현재 공개 시드의 별도 반복 송금 실패 패턴은 확인되지 않았다.

같은 서비스의 grant 생성 규칙은 고객 인덱스 기준 `((index - 1) % 5) + 1`이다. 예를 들어 `demo001/006/011…`는 `staff001`, `demo002/007/012…`는 `staff002`에 연결된다. 이는 공개 시드의 추천 로그인 안내일 뿐이며, 실제 열람 여부는 서버의 만료·철회·범위 검사가 결정한다.

읽기 전용 실환경 확인 시 `demo001`에는 `BANK_REVIEW` 알림과 `staff001`의 `PENDING` 사건이 있었다. `demo002`는 반복 송금 알림이 `AWAITING_CONTEXT`이고 `staff002` 사건 목록은 비어 있었다. 이 상태는 공유 합성 데이터이므로 이후 사용에 따라 변할 수 있다. 검증을 위해 고객 응답이나 사건 상태를 변경하지 않았다.

## 역할 전환과 공개 데모 분리

상단 로그인 메뉴에서 개인·운영자 진입을 선택한다. 본문의 프로토타입 역할 전환 박스는 사용하지 않는다. 고객의 알림 감사이력에 반환된 사건 ID를 운영자 로그인 다음 경로에 보존하고, 행원 화면에서는 현재 사건의 고객·알림으로 돌아가는 개인 로그인 경로를 제공한다. 쿠키 인증과 실제 역할 검사를 유지하며, 한 브라우저에서 역할 로그인은 이전 계정을 교체한다.

시작 화면은 인증 응답 기준 CUSTOMER → `/banking`, PROTECTION_STAFF → `/staff/cases`, DETECTION_ADMIN → `/staff/control-center`다. 공용 `/staff/system-status`에서도 로그인 역할의 메뉴를 유지한다. 이 상태 API는 `SecurityConfig`에서 공개 조회를 허용하며, UI에서 공개 상태 조회를 행원·관리자 권한 부여로 해석하지 않는다.

행원 메뉴는 업무 현황·사건 검토·서비스 상태 순서다. 업무 현황은 불러온 최대 100건의 상태별 건수 선택으로 목록을 필터링하고, 종결 사건을 포함해 개별 사건 진입을 제공한다. 사건 상태 변경은 사건 검토에서만 수행한다. 시연용 사건은 `/demo/staff/cases` 및 하위 상세 경로로 분리했으며 기존 회원용 상세 주소는 `/staff/cases?caseId=`로 이동한다.

`/demo`는 별도 capability·세션의 고정 발표 시나리오다. 공개 데모의 `PENDING_BANK_REVIEW`, `IN_BANK_REVIEW`, `GUIDANCE_PLAN_APPROVED`와 회원용 `BANK_REVIEW`, `PENDING`, `IN_REVIEW`, `GUIDANCE_APPROVED`는 서로 다른 계약이다. 특히 공개 데모의 정상 종결은 추가 구조적 근거를 검사하므로 회원용 `EXPECTED_CHANGE` 처리와 혼동하지 않는다.

화면 문구는 업무 목적 중심으로 간결하게 표시한다. 예시 데이터·외부 실행 없음 고지는 셸 하단에 모으고, 기술 설명은 상세·개발 문서로 분리한다. 고정 시드의 세 근거 문구는 프론트 표시 문장만 바꾸며 DB·API 원본은 그대로 둔다. 시스템 상태의 OPTIONAL/REQUIRED는 설정값으로 표시하고 정상/장애 집계에 섞지 않는다. AI 대체 설명 설정과 실제 개별 응답의 대체 사용 여부도 구분한다.

서비스 상태의 초기 조회는 `/system/core-readiness`, `/system/ai-readiness`, `/system/public-config` 3개로 제한하고 health·versions는 상세를 열 때 조회한다. `backend/docker/nginx.conf.template`에서 통합 readiness는 별도의 작은 burst 제한을 적용하므로 역할 이동 때 불필요한 통합 조회를 반복하지 않는다. 제한은 수정하지 않으며 429이면 자동 조회를 멈추고 재확인을 안내한다.

## 근거 파일

- [최종 백엔드 API 명세](FINAL_BACKEND_API_SPEC.md): 3.3.16 고객 경보, 3.3.17 사건·후속관리
- [백엔드 실행·데모·E2E 설명](../backend/README.md)
- [운영 고객 응답과 사건 생성](../backend/src/main/java/com/alzswell/alert/application/OperationalAlertService.java)
- [운영 사건 상태·열람·후속관리](../backend/src/main/java/com/alzswell/casework/application/OperationalCaseService.java)
- [사건 요청 계약](../backend/src/main/java/com/alzswell/casework/api/CaseworkRequests.java), [응답 계약](../backend/src/main/java/com/alzswell/casework/api/CaseworkResponses.java)
- [운영 알림 승격](../backend/src/main/java/com/alzswell/detection/application/DetectionPromotionService.java)
- [공개 합성 회원·패턴·접근권 준비](../backend/src/main/java/com/alzswell/fixture/application/SyntheticMemberProvisioningService.java)
- [공유 의향 필터링](../backend/src/main/java/com/alzswell/intent/application/FinancialIntentService.java)
- [결정론적 검토 질문](../backend/src/main/java/com/alzswell/copilot/application/DeterministicCopilotAdapter.java)
