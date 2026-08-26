# 임베딩 모델 비교 v1

> 측정일: 2026-08-26 KST  
> 상태: Hash 기준선 측정 완료 · E5 실측 대기

## 측정 결과

| 평가셋 | 모델 | Recall@3 | Recall@5 | MRR | nDCG@10 | 무응답 오탐률 | 정책 위반 |
|---|---|---:|---:|---:|---:|---:|---:|
| 고정 합성 회귀 17문항 | `local-hash-ngram-ko-v1` | 1.0000 | 1.0000 | 1.0000 | 1.0000 | 0.0000 | 0 |
| 사람 검수 합성 46문항 | `local-hash-ngram-ko-v1` | 0.4872 | 0.4872 | 0.4744 | 0.4777 | 0.0000 | 0 |
| 고정 합성 회귀 17문항 | `multilingual-e5-small` | 미측정 | 미측정 | 미측정 | 미측정 | 미측정 | 미측정 |
| 사람 검수 합성 46문항 | `multilingual-e5-small` | 미측정 | 미측정 | 미측정 | 미측정 | 미측정 | 미측정 |

고정 합성 회귀 결과는 구현 오류를 찾기 위한 값이며 실제 검색 성능으로 해석하지 않는다.
모델 선택에는 사람 검수 평가셋과 이후 승인 공식문서 골든셋을 사용한다.

## E5 실측 대기 사유

저장소와 지정된 로컬 경로에 승인된 `multilingual-e5-small` 모델 artifact가 없다. 폐쇄망
원칙에 따라 평가 과정에서 외부 다운로드나 자동 fallback을 허용하지 않는다. 따라서 현재
수치만으로 Hash와 E5의 우열을 확정하지 않는다.

E5 artifact 반입 후에는 모델 디렉터리, 고정 revision, `model.safetensors` SHA-256을 설정하고
`ALZS_EMBEDDING_ALLOW_HASH_FALLBACK=false`로 실행한다. 평가 보고서의
`embeddingModelVersion`이 `multilingual-e5-small@<revision>`인지 확인해야 하며 Hash로
fallback된 결과는 E5 결과로 인정하지 않는다.

## 현재 결정

- 운영 검색 가중치와 임계값은 변경하지 않는다.
- E5 실측 전에는 Hash를 폐쇄망 MVP의 결정론적 기준선으로 유지한다.
- 실제 법률·시행령 manifest는 사람 검수 전까지 `IN_REVIEW/PENDING_ACTIVATION`을 유지한다.
- E5와 Hash 비교는 동일한 corpus·질의·권한 문맥에서 Recall@5, MRR, nDCG@10 및 CPU
  지연시간을 함께 측정한 뒤 확정한다.
