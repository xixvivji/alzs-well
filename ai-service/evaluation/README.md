# 검색 품질 평가 v1

승인된 내부 임베딩 모델로 교체하기 전에 검색 품질과 보안 필터 회귀를 수치로 확인한다.
외부 네트워크나 모델 다운로드 없이 현재 `local-hash-ngram-ko-v1`과 동일한 임베딩 함수를
사용한다.

## 데이터셋

- `datasets/retrieval-corpus-v1.jsonl`: 합성 문서 chunk와 ACL·audience·상태·효력기간
- `datasets/retrieval-v1.jsonl`: 질의, 요청자 문맥, 정답 chunk와 무응답 기대값

실제 고객명·사건·계좌·내부 원문은 평가 데이터에 넣지 않는다. 현재 v1은 파이프라인과
품질 게이트를 재현하기 위한 합성 기준선이다. 운영 전에는 업무 담당자와 준법 검토자가
비식별 질의의 정답 chunk 및 무응답 기대값을 이중 검수해야 한다.

## 실행

```bash
uv run python -m app.evaluation.cli evaluate \
  --corpus evaluation/datasets/retrieval-corpus-v1.jsonl \
  --dataset evaluation/datasets/retrieval-v1.jsonl \
  --output-json data/derived/evaluation/retrieval-v1.json \
  --output-markdown data/derived/evaluation/retrieval-v1.md \
  --fail-on-gate
```

가중치와 임계값 후보 125개를 같은 데이터로 비교한다.

```bash
uv run python -m app.evaluation.cli tune \
  --corpus evaluation/datasets/retrieval-corpus-v1.jsonl \
  --dataset evaluation/datasets/retrieval-v1.jsonl \
  --output-json data/derived/evaluation/tuning-v1.json
```

기본 운영 후보는 keyword `0.35`, vector `0.65`, vector candidate threshold `0.15`,
final result threshold `0.35`다. 튜닝 결과를 코드에 자동 반영하지 않는다. 데이터셋 검토,
회귀 결과, 보안 검토를 거쳐 명시적으로 변경한다.

## 품질 게이트

| 지표 | 기준 |
|---|---:|
| Recall@3 | 0.80 이상 |
| Recall@5 | 0.90 이상 |
| MRR | 0.70 이상 |
| 무응답 오탐률 | 0.10 이하 |
| ACL·audience·승인·효력 정책 위반 | 0건 |

`Recall@K`는 정답 chunk가 상위 K개에 포함된 질의 비율이고, `MRR`은 첫 정답 순위의
역수 평균이다. 무응답 오탐률은 결과가 없어야 하는 질의에 하나 이상 반환한 비율이다.
CI는 게이트 실패 시 PR을 차단하고 JSON·Markdown 평가 보고서를 artifact로 남긴다.

## 해석상의 한계

오프라인 keyword 점수는 PostgreSQL `ts_rank_cd`의 결정론적 대리 점수다. 임베딩 함수와
정책 필터는 운영 코드와 공유하지만 SQL 순위와 완전히 동일하다고 가정하지 않는다.
운영 전에는 PostgreSQL 통합 검색 회귀와 대표 질의에 대한 사람의 relevance 판단을 함께
수행한다. 내부 모델 교체 시에는 동일 데이터셋으로 기준선을 비교하고 모델·index 버전을
별도로 올린다.
