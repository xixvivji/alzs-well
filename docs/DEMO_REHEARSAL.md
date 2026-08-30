# 정상·주의·오탐 데모 리허설

이 문서는 합성데이터로 고객 알림부터 행원 최종 통제까지 세 가지 발표 흐름을 고정한다. 세 흐름 모두 질병·사기 여부를 자동 판단하거나 외부 연락·금융 실행을 만들지 않는다.

## 원클릭 검증

저장소 루트에서 다음 명령을 실행한다.

```bash
python3 scripts/run_demo_rehearsal.py
```

명령은 아래 검증을 순서대로 수행하고 한 단계라도 실패하면 종료 코드 1을 반환한다.

1. Spring 상태머신 통합 테스트
2. 프런트 빌드와 UI 계약 테스트
3. 격리 Compose의 승인 문서 ingestion과 Spring import
4. 정상·주의·오탐 세 상태 전이
5. FastAPI 정상 시 승인 근거 citation
6. FastAPI 중단 시 결정론적 템플릿 폴백

Docker 없이 계약만 빠르게 확인하려면 다음 명령을 사용한다.

```bash
python3 scripts/run_demo_rehearsal.py --contracts-only
```

통합 결과는 `artifacts/copilot-rag-e2e/result.json`에 기록된다. capability, bootstrap token, 원문 검색 질의는 증적에 저장하지 않는다.

## 발표용 UI 흐름

`/demo`의 **발표 리허설** 영역에서 시나리오를 선택한다. 화면은 각 단계에서 눌러야 할 고객 응답과 목표 상태를 표시한다.

| 시나리오 | 고객 응답 | 행원 단계 | 최종 상태 |
|---|---|---|---|
| 정상 | 제가 알고 있는 금융활동입니다 | 사건이 생성되지 않음 | `CLOSED_NORMAL` |
| 주의 | 잘 모르겠습니다. 도움받겠습니다 | 검토 시작 → AI 초안 citation 확인 → 안내계획 승인 | `GUIDANCE_PLAN_APPROVED` |
| 오탐 | 잘 모르겠습니다. 도움받겠습니다 | 검토 시작 → 사실확인 근거 입력 → 오탐 종결 | `CLOSED_FALSE_POSITIVE` |

직원 화면은 ChatGPT 로그인, 서버측 직원 allowlist, 단기 staff capability를 모두 통과해야 한다. 이 경계를 우회하는 로컬 전용 인증 코드는 추가하지 않는다.

## AI 장애 리허설 판정

- 정상: `generatedBy=RAG_GROUNDED_TEMPLATE`, `retrievalMode=INTERNAL_RAG_HYBRID`, citation 1개 이상
- 장애: `generatedBy=DETERMINISTIC_TEMPLATE`, `fallbackUsed=true`, citation 0개
- 공통: `modelInvoked=false`, `externalEgressAttempted=false`, `humanReviewRequired=true`

발표 화면에서 AI 초안을 다시 생성하면 현재 결과가 **승인 근거 연결**인지 **안전 폴백 사용**인지 표시된다. 자동화 스크립트는 AI 서비스를 실제로 중단한 뒤 같은 사건에서 폴백을 검증한다.
