# 사유코드 및 상태 전이 정의서

| 항목 | 값 |
|---|---|
| 문서번호 | AW-POL-003 |
| 버전 | 1.0-draft |
| 권위 원천 | DB 제약조건·결정론적 정책엔진 |
| 승인자·시행일 | TBD |

## 사유코드

| 코드 | 뜻 | 허용되는 설명 | 금지되는 해석 |
|---|---|---|---|
| MISSED_RECURRING_PAYMENT | 평소 반복되던 납부가 관찰기간에 확인되지 않음 | 납부 처리상태 확인 필요 | 기억력 저하·연체능력 판정 |
| DUPLICATE_TRANSFER | 유사 수취처·금액의 근접 이체 | 의도·취소·환불 여부 확인 | 사기 또는 치매 확정 |
| REPEATED_CONFIRMATION | 결과·상담 내용을 반복 확인 | 화면지연·설명부족 등 사실확인 | 인지기능 평가 |

새 코드는 정의, 계산식, 관찰기간, 최소 근거, 반례, 테스트를 갖추고 `detection_policy_version` 승인 후만 활성화한다.

## 고객 경보 상태

```text
AWAITING_CONTEXT
 ├─ EXPECTED_CHANGE + 충돌근거 없음 → CLOSED_NORMAL
 ├─ UNRECOGNIZED 또는 NOT_SURE → BANK_REVIEW
 └─ 고객의 확인 연기 → DEFERRED → AWAITING_CONTEXT
```

허용 상태는 코드와 동일하게 `AWAITING_CONTEXT`, `DEFERRED`, `CLOSED_NORMAL`, `BANK_REVIEW`다. `DEFERRED`에는 `deferredUntil`이 반드시 존재한다. AI는 상태를 쓰지 못하며 애플리케이션 정책서비스가 명시적 명령·버전·멱등키를 검증한다.

## 행원 사건 상태

```text
PENDING → IN_REVIEW → GUIDANCE_APPROVED → COMPLETED
             ↑                              │
             └──────── REOPEN_REVIEW ──────┘
```

- `START_REVIEW`: 배정된 권한자가 검토 시작
- `COMPLETE_REVIEW`: 사실확인 및 기록 완료
- `REOPEN_REVIEW`: 새 사실·오류·이의제기로 재검토
- 안내계획 승인에는 `STAFF_GUIDANCE_APPROVE`가 필요하며 실제 전달·금융실행은 생성하지 않는다.

## 신호·동의·연락인 상태

- 신호: `OPEN`, `ACKNOWLEDGED`, `CLOSED`
- 동의: `GRANTED`, `WITHDRAWN`; 만료시 유효하지 않음
- 신뢰연락인: `ACTIVE`, `REVOKED`, `REVOKED_BY_CONSENT`
- 동의 철회는 연결된 활성 신뢰연락인을 `REVOKED_BY_CONSENT`로 전환한다.

## 불변 조건

- 정상종결에는 고객 응답과 충돌근거 부재가 필요하다.
- `BANK_REVIEW`에서만 행원 사건을 만든다.
- 심각도는 검토 우선순위일 뿐 질병·피해 확률이 아니다.
- 모든 상태변경은 이전·결과 상태, 행위자, 시각, 정책버전 또는 무결성 해시를 남긴다.

