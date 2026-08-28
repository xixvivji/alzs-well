# 검색 품질 평가 v1

승인된 내부 임베딩 모델로 교체하기 전에 검색 품질과 보안 필터 회귀를 수치로 확인한다.
외부 네트워크나 모델 다운로드 없이 선택된 임베딩 어댑터를 사용한다. 기본값은
`local-hash-ngram-ko-v1`이며 평가 전용 catalog·모델명·model root 옵션을 명시하면 같은
평가 명령이 고정 SentenceTransformer revision을 사용하고 보고서에 모델 버전을 기록한다.
현재 측정값과 모델 선택 보류 조건은 `model-comparison-v1.md`에 기록한다.
Arctic-ko 합성 E2E 부하 게이트의 측정법과 결과는 `arctic-ko-load-test-v1.md`에 기록한다.
평가에 반입한 모델의 고정 revision·차원·prefix와 SentenceTransformer가 소비하는 모든
파일의 상대경로·크기·SHA-256은 `model-artifacts-v1.json`에 기록하며 모델 바이너리 자체는
Git에 커밋하지 않는다.
사람 승인 후 단계적 활성화 재검증 결과는 `arctic-ko-staged-e2e-v1.md`에 기록한다.

## 데이터셋

- `datasets/retrieval-corpus-v1.jsonl`: 합성 문서 chunk와 문서유형·ACL·audience·상태·효력기간
- `datasets/retrieval-v1.jsonl`: 질의, 요청자 문맥, 정답 chunk와 무응답 기대값

현재 합성 기준선은 14개 chunk와 17개 질의로 구성한다. 통신사기피해환급법·시행령·
과거 보도자료 역할의 합성 chunk 3개와 법령 검색 질의 2개를 포함하지만, 실제 공식 manifest의
승인 상태를 변경하거나 원문을 승인 corpus로 간주하지 않는다.

실제 고객명·사건·계좌·내부 원문은 평가 데이터에 넣지 않는다. 현재 v1은 파이프라인과
품질 게이트를 재현하기 위한 합성 기준선이다. 운영 전에는 업무 담당자와 준법 검토자가
비식별 질의의 정답 chunk 및 무응답 기대값을 이중 검수해야 한다.

## 사람 검수

`reviews/retrieval-review-v1.csv`에는 답변형 40개와 무응답·정책형 10개 후보가 있다.
현재 공식 manifest가 `IN_REVIEW/PENDING_ACTIVATION` 상태이고 실제 승인 문서 corpus가
충분하지 않으므로 모든 행은 `SYNTHETIC_REVIEW_ONLY`다. 2026-08-26 2차 검수에서는
46개를 `ACCEPTED`, 아래 4개를 `AMBIGUOUS`로 판정했다.

- `RC-013`: 질문이 기록 위치를 요구하지만 근거에 위치 정보가 없음
- `RC-044`: 폐기 문서 필터와 활성 안전 근거 반환 기준이 한 질의에 섞임
- `RC-047`: 검색 무응답과 동의 없는 자동 연락 차단 평가의 경계가 불명확
- `RC-048`: 검색 무응답과 금융 실행 차단 평가의 경계가 불명확

이 검수 결과와 점수는 합성 검색 회귀 기준선일 뿐 실제 업무 품질이나 출시 근거로
사용하지 않는다.

검수자는 질문과 `evidenceExcerpt`를 비교한 뒤 다음 두 열만 수정한다.

- `reviewDecision`: `ACCEPTED`, `REJECTED`, `AMBIGUOUS` 중 하나
- `reviewComment`: 틀리거나 애매한 이유 및 수정 의견

CSV를 다시 생성하려면 다음 명령을 실행한다. Excel에서 한글이 깨지지 않도록 UTF-8 BOM을
사용한다.

```bash
uv run python -m app.evaluation.review_cli prepare \
  --corpus evaluation/datasets/retrieval-corpus-v1.jsonl \
  --candidates evaluation/reviews/retrieval-review-candidates-v1.jsonl \
  --output-csv evaluation/reviews/retrieval-review-v1.csv
```

검수 후 `ACCEPTED` 행만 별도 평가 데이터셋으로 승격한다. 원본 평가셋을 자동으로
덮어쓰지 않는다.

```bash
uv run python -m app.evaluation.review_cli finalize \
  --corpus evaluation/datasets/retrieval-corpus-v1.jsonl \
  --candidates evaluation/reviews/retrieval-review-candidates-v1.jsonl \
  --input-csv evaluation/reviews/retrieval-review-v1.csv \
  --output-jsonl data/derived/evaluation/retrieval-reviewed-v1.jsonl
```

현재 2차 검수 결과를 finalize하면 답변형 39개와 무응답형 7개, 총 46개가 생성된다.
기본 설정의 사람 검수 기준선은 Recall@3/5 `0.4872`, MRR `0.4744`, nDCG@10
`0.4777`, 무응답 오탐률 `0`, 정책 위반 `0`으로 품질 게이트를 통과하지 못한다.
125개 조합 중 최선도 keyword `0.2`, vector `0.8`, vector threshold `0.15`, result
threshold `0.2`에서 Recall@3/5 `0.7692`, MRR `0.7564`, nDCG@10 `0.7598`, 무응답
오탐률 `0.1429`이므로 설정을 자동 변경하지 않는다.

17개 고정 합성 질의의 회귀 기준선은 기본 설정에서 Recall@3/5, MRR, nDCG@10이 모두
`1.0`이고 무응답 오탐과 정책 위반은 `0`이다. 이는 구현 회귀를 잡기 위한 결과이며 실제
검색 품질을 의미하지 않는다.

실제 공식 문서가 `APPROVED/ACTIVE`가 되면 해당 ingestion chunk로 corpus를 새로 만들고,
동일한 검수 흐름에서 최소 2명의 담당자가 정답 근거를 확인한 데이터셋을 별도 버전으로
추가한다.

### 공식문서 사전 검수 팩

`reviews/official-retrieval-review-candidates-v1.jsonl`과
`reviews/official-retrieval-review-v1.csv`는 저장소에 반입된 공식문서 7개를 대상으로 만든
검수 팩이다. 승인·활성 문서 5개가 완전히 지원하는 답변형 21개와 무응답형 6개,
총 27개는 2026-08-28 사람 최종 검수로 `ACCEPTED`가 되었다. 아직 승인되지 않은 문서에
의존하는 ORC-004, ORC-005, ORC-013은 `PENDING`을 유지한다. 승인된 27개만
`datasets/official-operational-golden-v1.jsonl`에 고정한다.

공식 manifest 7개 중 5개는 2026-08-28 사람 승인으로 `APPROVED/ACTIVE`가 되었고,
2개는 `IN_REVIEW/PENDING_ACTIVATION` 상태를 유지한다. 사전 검수 corpus는
운영 ingestion 경로와 분리해
`data/derived/evaluation/retrieval-official-review-corpus-<date>.jsonl`에만 원자적으로 생성한다.
따라서 이 흐름은 검색 정답 후보를 검토하기 위한 도구일 뿐 문서 승인이나 검색 노출을
수행하지 않는다.

승인된 5개 문서의 E5-small/Arctic-ko 비교 결과와 pgvector 공존 검증은
`approved-model-comparison-v1.md`에 기록한다. 공식 후보 중 승인 corpus가 완전히 지원하는
질의만 잠정 비교셋으로 만들려면 다음 명령을 사용한다. 운영 회귀에는 잠정 출력이 아니라
사람이 최종 확정한 `datasets/official-operational-golden-v1.jsonl`을 사용한다.

```bash
uv run python -m app.evaluation.review_cli benchmark \
  --corpus data/derived/evaluation/retrieval-official-corpus-2026-08-28.jsonl \
  --candidates evaluation/reviews/official-retrieval-review-candidates-v1.jsonl \
  --output-jsonl data/derived/evaluation/retrieval-approved-provisional-v1.jsonl
```

```bash
uv run python -m app.evaluation.official_review_cli \
  --repo-root .. \
  --as-of 2026-08-27 \
  --manifest knowledge/manifests/DOC-FSC-DESIGNATED-PERSON-NOTICE-001.yaml \
  --manifest knowledge/manifests/DOC-FSC-FACE-TO-FACE-PHISHING-RELIEF-001.yaml \
  --manifest knowledge/manifests/DOC-FSC-NONFACE-ACCOUNT-BLOCK-QA-001.yaml \
  --manifest knowledge/manifests/DOC-FSC-SAFE-BLOCK-001.yaml \
  --manifest knowledge/manifests/DOC-KDIC-MISTAKEN-REMITTANCE-ELIGIBILITY-001.yaml \
  --manifest knowledge/manifests/DOC-LAW-TELECOM-FRAUD-REFUND-ACT-001.yaml \
  --manifest knowledge/manifests/DOC-REG-TELECOM-FRAUD-REFUND-DECREE-001.yaml
```

생성된 corpus로 검수 CSV를 재현할 수 있다.

```bash
uv run python -m app.evaluation.review_cli prepare \
  --corpus data/derived/evaluation/retrieval-official-review-corpus-2026-08-27.jsonl \
  --candidates evaluation/reviews/official-retrieval-review-candidates-v1.jsonl \
  --output-csv evaluation/reviews/official-retrieval-review-v1.csv
```

검수자는 XLSX 또는 CSV에서 `reviewDecision`과 `reviewComment`만 수정한다. `ACCEPTED`는
질문-근거 쌍을 평가셋으로 승격해도 된다는 뜻이며, 원문 manifest의 승인 상태는 별도
지식 거버넌스 절차에서 변경해야 한다.

## 실행

```bash
uv run python -m app.evaluation.cli evaluate \
  --corpus evaluation/datasets/retrieval-corpus-v1.jsonl \
  --dataset evaluation/datasets/retrieval-v1.jsonl \
  --output-json data/derived/evaluation/retrieval-v1.json \
  --output-markdown data/derived/evaluation/retrieval-v1.md \
  --fail-on-gate
```

반입 완료된 평가 모델은 catalog·모델 이름·절대 model root를 세 옵션 모두 지정해야 한다.
CLI는 catalog의 고정 revision과 전체 소비 파일 manifest를 확인한 뒤 CPU에서 오프라인으로만
로드한다. catalog에 없는 추가 파일, 누락 파일, `../`, 절대 local path, 중첩 심볼릭 링크,
비정규 파일·hard link, 크기·해시 불일치가 있으면 평가를 시작하지 않는다. Hugging Face
snapshot symlink를 그대로 사용하지 말고 catalog에 열거된 파일만 일반 파일로 복사한다.

```bash
uv run python -m app.evaluation.cli evaluate \
  --corpus evaluation/datasets/retrieval-corpus-v1.jsonl \
  --dataset data/derived/evaluation/retrieval-reviewed-v1.jsonl \
  --output-json data/derived/evaluation/arctic-ko-v1.json \
  --output-markdown data/derived/evaluation/arctic-ko-v1.md \
  --evaluation-model-catalog evaluation/model-artifacts-v1.json \
  --evaluation-model-name snowflake-arctic-embed-l-v2.0-ko \
  --evaluation-model-root /absolute/path/to/models
```

이 명령은 평가 전용이다. 현재 PostgreSQL에는 384/1024차원 공존 스키마와 모델별
pgvector 인덱스가 적용되어 있지만, FastAPI 검색에서 Arctic-ko를 사용하려면 승인 문서를
해당 고정 모델 버전으로 재-ingestion해야 한다. 기본 이미지에는 모델 런타임이 없으므로
승인된 `sentence-transformers` wheel과 전이 의존성을 내부 wheelhouse에서 별도로
설치해야 하며 실행 중 인터넷 다운로드를 허용하지 않는다.

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
| nDCG@10 | 0.75 이상 |
| 무응답 오탐률 | 0.10 이하 |
| ACL·audience·승인·효력 정책 위반 | 0건 |

`Recall@K`는 정답 chunk가 상위 K개에 포함된 질의 비율이고, `MRR`은 첫 정답 순위의
역수 평균이다. `nDCG@10`은 복수 정답의 상위 순위 배치를 반영한다. 무응답 오탐률은
결과가 없어야 하는 질의에 하나 이상 반환한 비율이다.
CI는 게이트 실패 시 PR을 차단하고 JSON·Markdown 평가 보고서를 artifact로 남긴다.

## 해석상의 한계

오프라인 keyword 점수는 PostgreSQL `ts_rank_cd`의 결정론적 대리 점수다. 임베딩 함수와
정책 필터를 운영 코드와 공유하고 PostgreSQL의 문서 권위 순위를 동일하게 적용하지만,
SQL 관련성 점수와 완전히 동일하다고 가정하지 않는다.
운영 전에는 PostgreSQL 통합 검색 회귀와 대표 질의에 대한 사람의 relevance 판단을 함께
수행한다. 내부 모델 교체 시에는 동일 데이터셋으로 기준선을 비교하고 모델·index 버전을
별도로 올린다.
