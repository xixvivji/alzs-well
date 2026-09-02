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
- `datasets/ai-safety-policy-v1.jsonl`: 정책 차단 공격 문장 100건과 기대 결과

현재 합성 기준선은 14개 chunk와 17개 질의로 구성한다. 통신사기피해환급법·시행령·
과거 보도자료 역할의 합성 chunk 3개와 법령 검색 질의 2개를 포함하지만, 실제 공식 manifest의
승인 상태를 변경하거나 원문을 승인 corpus로 간주하지 않는다.

실제 고객명·사건·계좌·내부 원문은 평가 데이터에 넣지 않는다. 현재 v1은 파이프라인과
품질 게이트를 재현하기 위한 합성 기준선이다. 운영 전에는 업무 담당자와 준법 검토자가
비식별 질의의 정답 chunk 및 무응답 기대값을 이중 검수해야 한다.

### AI 안전 정책 회귀 100건

`ai-safety-policy-v1.jsonl`은 다음 10개 범주를 각 10건씩 검사한다.

- 사건별 최종 판단, 고객 동의 없는 외부 조치
- 의학적 진단, 개인화 투자 추천, 미래 법령 단정
- 프롬프트 인젝션, 권한 상승, 비밀 추출, 타인 개인정보 추출, 근거 조작

이 데이터는 사람이 승인한 검색 골든셋이 아니라 기계 작성 정책 회귀셋이다. 따라서 공식
27건 검색 성능에 합산하거나 실제 공격 방어율로 표현하지 않는다. CI는 100건 모두
`POLICY_ABSTAIN`인지 확인하고 범주 또는 문항 수가 달라져도 실패한다.

```bash
uv run python -m app.evaluation.safety_cli \
  --dataset evaluation/datasets/ai-safety-policy-v1.jsonl \
  --output-json data/derived/evaluation/ai-safety-policy-v1.json
```

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

### 독립 검수 후보 150건

`reviews/independent-review-candidates-v1.jsonl`과 사람이 검토할
`reviews/independent-review-v1.csv`는 공식 운영 골든셋을 변경하지 않고 검색 강건성과
무응답 경계를 추가 검토하기 위한 별도 후보 팩이다.

- 승인 근거 21개에 고객 안내·행원 검토·규정 확인·쉬운말·핵심 조건 표현을 적용한 답변형 105건
- ACL, 만료·미래 문서, 미승인 문서, 금융 외 질문, 근거 없음 무응답형 45건

150건은 모두 `PENDING`이고 `MACHINE_AUTHORED_REVIEW_CANDIDATE`로 표시한다. 기존 27건의
사람 승인 상태를 상속하지 않으며, 데이터 작성자와 다른 검수자가 질문·정답 근거·무응답
기대값을 확인하기 전에는 공식 검색 성능이나 독립 평가 결과로 사용할 수 없다. 계약 테스트는
문항 수, 범주 분포, 근거 추적성, `PENDING` 상태를 고정한다.

승인 문서에서 검수 corpus를 재생성한 뒤 기존 `prepare` 명령의 `--candidates`에 이 파일을
지정하면 사람이 검토할 CSV를 만들 수 있다. 검수 후에도 원본이나 공식 골든셋을 덮어쓰지
않고 새 버전의 별도 데이터셋으로 finalize해야 한다.

Arctic-ko 기본 설정의 검수 전 임시 측정과 실패 우선순위는
`independent-review-provisional-benchmark-v1.md`에 기록한다. 기계 판정 결과와 재현 입력은
동명의 JSON에 고정하며, 사람 검수 완료나 공식 검색 성능으로 해석하지 않는다.
정책 우회 3건을 보완한 동일 조건 재측정은
`independent-review-provisional-benchmark-v2.md`에 기록한다.

v2에서 Top-1 또는 정상 무응답이 아니었던 36건은
`reviews/independent-review-ai-triage-v1.csv`와
`independent-review-ai-triage-v1.md`에서 검수 우선순위별로 기술 분류한다. 이 분류는
기대 근거와 실제 Top-5의 문서·절 위치를 비교해 동일 문서·제목 상세 검토, 일반 Top-3 검토,
문맥 민감, 정의 절 불일치, 법률과 세부 시행령의 순위 경합을 구분한다. AI가 만든 검수
보조자료일 뿐 질문-근거 쌍을 승인하거나 공식 성능을 확정하지 않으며, 모든 행은 독립
검수자가 확인할 때까지 `PENDING`을 유지한다.

동일 문서·제목으로 자동 분류됐던 12건은 2026-09-02 대회 팀 소유자가 원문을 대조했다.
Top-1 청크는 질문의 답을 포함하지 않거나 질의 기준일 이후 시행 예정 문구를 포함해 대체
relevance를 12건 모두 `REJECTED`했다. 이 결정은 대체 청크 추가 여부에만 적용되며 원본
150개 후보의 질문·기대 근거 상태는 계속 `PENDING`이다. 상세 기록은
`reviews/independent-review-near-match-human-v1.csv`와
`independent-review-near-match-human-v1.md`에 고정한다.

이 검수에서 발견된 현재 조문과 미래 시행 예정 개정문의 혼재는 청크 본문의 명시적
`[시행일: YYYY. M. D.]`를 기준으로 분리했다. 분할된 조문은 시행일 표기가 있는 마지막
청크에서 같은 조문의 `①` 시작 청크까지 효력일을 전파한다. 2026-08-28 기준 corpus 재생성과
Arctic-ko 재측정 결과는 `independent-review-temporal-filter-v1.md`에 기록한다. 사전 승인된
6건은 모두 현행 조문 Top-1을 확인했지만, 이는 150건 전체의 사람 검수 완료나 공식 성능
승격을 의미하지 않는다.

파생 corpus와 v2 순위 결과가 준비된 환경에서는 다음 명령으로 표를 재현한다.

```bash
uv run python -m app.evaluation.triage_cli \
  --corpus data/derived/evaluation/retrieval-official-corpus-2026-08-28.jsonl \
  --candidates evaluation/reviews/independent-review-candidates-v1.jsonl \
  --ranking-json data/derived/evaluation/independent-review-arctic-ko-v2.json \
  --output-csv evaluation/reviews/independent-review-ai-triage-v1.csv \
  --output-json evaluation/independent-review-ai-triage-v1.json \
  --output-markdown evaluation/independent-review-ai-triage-v1.md
```

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

### 데모 검색 순위 최종 검수

운영 골든셋의 각 질문에 대해 Arctic-ko Top-5 문서·문단과 결합·키워드·벡터 점수를
JSON과 Markdown으로 출력한다. 기대 근거가 Top-1이면 `PASS_TOP_1`, Top-2~3이면
`REVIEW_TOP_2_OR_3`, Top-3 밖이거나 없으면 `FAIL_BELOW_TOP_3`으로 분류한다. 무응답
질의는 결과가 없을 때만 `PASS_NO_RESULTS`다. `--fail-on-top-3-miss`를 사용하면 Top-3
실패나 무응답 오탐이 하나라도 있을 때 종료 코드 1을 반환한다.

```bash
uv run python -m app.evaluation.ranking_review_cli \
  --corpus data/derived/evaluation/retrieval-official-corpus-2026-08-28.jsonl \
  --dataset evaluation/datasets/official-operational-golden-v1.jsonl \
  --output-json data/derived/evaluation/demo-ranking-review-v1.json \
  --output-markdown data/derived/evaluation/demo-ranking-review-v1.md \
  --evaluation-model-catalog evaluation/model-artifacts-v1.json \
  --evaluation-model-name snowflake-arctic-embed-l-v2.0-ko \
  --evaluation-model-root /absolute/path/to/models \
  --fail-on-top-3-miss
```

생성 결과는 파생 데이터이므로 Git에 커밋하지 않는다. 모델 revision과 artifact SHA-256은
기존 권위 카탈로그 검증을 그대로 통과해야 하며 실행 중 모델 다운로드는 허용하지 않는다.

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
