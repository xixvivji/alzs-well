# Arctic-ko 운영 승격 판단 보고서 v3

- 승인일: 2026-08-28
- 대상 모델: `dragonkue/snowflake-arctic-embed-l-v2.0-ko`
- 모델 revision: `55ec6e9358a56d56af759bc8372e970caf8c305f`
- 운영형 골든셋: `datasets/official-operational-golden-v1.jsonl`
- 사람 최종 승인: 27/27
- 결정: **STAGED_GO**

## 결정

승인·활성 공식문서 5개가 지원하는 질의·근거 쌍 27건을 모두 `ACCEPTED`로 확정했다.
Arctic-ko는 모델 카탈로그의 `STAGED_APPROVED`로 승격하되, 기본 Compose와 기본 환경값을
변경하지 않는다. 승인된 AWS AI staging에서 `compose.arctic-ko.yaml`,
`ALZS_ARCTIC_ROLLOUT_ENABLED=true`, `ALZS_DEPLOYMENT_ENVIRONMENT=AWS_STAGING`,
`ALZS_MODEL_STAGED_APPROVAL_ENABLED=true`를 모두 명시할 때만 단계적으로 활성화한다.

## 승인 근거

| 항목 | 결과 |
| --- | ---: |
| 사람 최종 승인 | 27/27 |
| Recall@3 | 1.0000 |
| Recall@5 | 1.0000 |
| MRR | 0.8730 |
| nDCG@10 | 0.9059 |
| 무응답 오탐률 | 0.0000 |
| 정책 위반 | 0 |
| 제한 부하 시험 | PASS |

질의 수정 3건과 고위험 정책 게이트 3건도 회귀 테스트를 통과했다. 승인되지 않은 문서에
의존하는 ORC-004, ORC-005, ORC-013은 골든셋에 포함하지 않고 `PENDING`을 유지한다.

## 단계적 활성화

1. 기본 환경은 hash backend와 결정론적 Copilot 폴백을 유지한다.
2. 내부 검증 환경에서만 Arctic-ko overlay를 활성화하고 승인 문서를 같은 revision으로 재-ingestion한다.
3. 헬스 응답의 모델 버전과 `arcticRolloutEnabled=true`를 확인한다.
4. 공식 골든셋, ACL, 인용 재검증, 장애 폴백 E2E를 실행한다.
5. 오류율·지연시간·메모리 기준을 벗어나면 overlay를 제거하고 hash backend로 롤백한다.
6. 운영 서버 장시간 안정성 측정과 별도 배포 승인 전에는 전면 기본값으로 변경하지 않는다.

## 롤백 원칙

모델 파일 누락이나 런타임 로드 실패는 허용된 경우 hash provider로 폴백한다. 해시 불일치,
revision 불일치, 경로 이탈은 설정 오류로 실패시키며 검증되지 않은 모델을 사용하지 않는다.
FastAPI 장애 시 Spring은 기존 결정론적 Copilot 구현으로 폴백한다.
