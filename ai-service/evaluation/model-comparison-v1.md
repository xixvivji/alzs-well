# 임베딩 모델 비교 v1

> 측정일: 2026-08-26~27 KST
> 상태: 로컬 CPU·합성 E2E·제한 부하 게이트 완료 · 운영 기본 모델 승인 대기

## 측정 결과

| 설정 | 모델 | Recall@3 | Recall@5 | MRR | nDCG@10 | 무응답 오탐률 | 정책 위반 |
|---|---|---:|---:|---:|---:|---:|---:|
| 기본 | `local-hash-ngram-ko-v1` | 0.4872 | 0.4872 | 0.4744 | 0.4777 | 0.0000 | 0 |
| 기존 125개 후보 최선 | `local-hash-ngram-ko-v1` | 0.7692 | 0.7692 | 0.7564 | 0.7598 | 0.1429 | 0 |
| 기본 | `multilingual-e5-small` | 0.6410 | 0.6923 | 0.3366 | 0.4944 | 1.0000 | 0 |
| 모델별 보정 | `multilingual-e5-small` | 0.9231 | 0.9231 | 0.8974 | 0.9042 | 0.2857 | 0 |
| 기본 | `snowflake-arctic-embed-l-v2.0-ko` | 0.8718 | 0.8718 | 0.8419 | 0.8495 | 0.0000 | 0 |
| 모델별 보정 | `snowflake-arctic-embed-l-v2.0-ko` | 0.9487 | 0.9487 | 0.9103 | 0.9203 | 0.0000 | 0 |

모든 행은 같은 사람 검수 합성 46문항(답변형 39개, 무응답형 7개)에서 측정했다.
모델별 보정은 keyword 비중 0, vector 비중 1의 비교 실험이며 운영 설정 변경을 의미하지
않는다. E5는 final threshold `0.85`, Arctic-ko는 `0.45`에서 각각 가장 좋은 게이트 결과를
냈다. Dense cosine 분포가 모델마다 다르므로 Hash용 임계값을 그대로 재사용할 수 없다.

## 로컬 artifact

| 모델 | 고정 revision | `model.safetensors` SHA-256 | 크기 | 라이선스 |
|---|---|---|---:|---|
| E5-small | `614241f622f53c4eeff9890bdc4f31cfecc418b3` | `1a55775f...c98477` | 470,641,600 B | MIT |
| Arctic-ko | `55ec6e9358a56d56af759bc8372e970caf8c305f` | `0b874517...e15b0` | 2,271,064,456 B | Apache-2.0 |

전체 값과 prefix·차원, 런타임 소비 파일별 경로·크기·SHA-256 계약은
`model-artifacts-v1.json`에 기록한다. 실제 모델 파일은 저장소의 `/models/` 아래에만 두고
Git에서 제외하며 catalog에 없는 파일과 symlink를 반입하지 않는다. 두 artifact는 현재
`EVALUATION_ONLY`이며 운영 승인 또는 배포 이미지 반입 승인을 뜻하지 않는다. 실행 중
자동 다운로드는 계속 금지한다.

## CPU 성능

측정 환경은 macOS arm64, Python 3.12.13, `sentence-transformers` 5.7.0,
`transformers` 5.15.1, PyTorch 2.13.0 CPU다. corpus 문단 임베딩은 실제 벡터 저장 구조처럼
한 번만 계산해 캐시하고 46개 고유 질의를 측정했다.

| 모델 | 질의 p50 | 질의 p95 | 문단 p50 | 문단 p95 | 프로세스 peak RSS |
|---|---:|---:|---:|---:|---:|
| E5-small | 5.08 ms | 5.77 ms | 8.14 ms | 8.90 ms | 950.5 MiB |
| Arctic-ko | 41.67 ms | 44.02 ms | 59.13 ms | 63.86 ms | 1,729.8 MiB |

Arctic-ko는 이 장비에서 질의 추론이 E5-small보다 약 8.2배 느리고 peak RSS가 약 1.8배다.
모델 로드 시간은 OS 파일 캐시 영향을 크게 받으므로 선택 지표에서 제외했다.

## 컨테이너 부하 게이트

2026-08-27 KST에 Docker Desktop(macOS arm64), AI 컨테이너 CPU 2개·메모리 3 GiB에서
고정 Arctic-ko artifact와 합성 문서를 사용해 측정했다. 동시성 4로 FastAPI와 Spring에
각각 100건을 보냈고, 측정 전 각 경로에 동시 warm-up 8건을 수행했다.

| 경로 | 성공/전체 | p50 | p95 | 처리량 | 오류율 |
|---|---:|---:|---:|---:|---:|
| FastAPI 내부 검색 | 100/100 | 607.36 ms | 919.36 ms | 6.17 RPS | 0.0000 |
| Spring 검색 API | 100/100 | 590.98 ms | 716.56 ms | 6.74 RPS | 0.0000 |

기동은 12.56초, AI 프로세스 peak RSS는 2,107.03 MiB, page cache를 포함한 컨테이너
peak memory는 2,527.70 MiB였다. p95 1초, 오류율 0%, 최소 2 RPS, peak RSS 2.5 GiB,
기동 30초 게이트를 모두 통과했다. 측정법·제약·재현 명령은
[`arctic-ko-load-test-v1.md`](arctic-ko-load-test-v1.md)에 기록한다. 이 결과는 합성 corpus의
제한 부하 결과이며 실제 동시 사용자 용량이나 운영 승인을 의미하지 않는다.

## 현재 결정

- 운영 검색 가중치와 임계값은 변경하지 않는다.
- 합성 검수셋 기준 정확도 후보는 Arctic-ko가 우세하며 유일하게 모든 품질 게이트를 통과했다.
- 1024차원 pgvector 공존 구조와 모델별 인덱스를 적용했고, 제한된 합성 E2E에서 실제
  Arctic-ko 재-ingestion·검색·Spring 인용 재검증·FastAPI 장애 폴백을 통과했다.
- Arctic-ko를 즉시 운영 기본값으로 지정하지 않는다. 합성 제한 부하 게이트는 통과했지만
  승인 공식문서 골든셋 재평가와 목표 배포 장비의 장시간 부하 시험이 먼저다.
- E5-small은 현재 384차원 스키마와 지연시간을 유지할 수 있지만 무응답 오탐 게이트를
  통과하지 못했으므로 기본 모델로 확정하지 않는다.
- Hash는 폐쇄망 MVP와 모델 장애 시 결정론적 fallback으로 유지한다.
- 실제 법률·시행령 manifest는 사람 검수 전까지 `IN_REVIEW/PENDING_ACTIVATION`을 유지한다.
- 다음 평가는 승인 공식문서 corpus에서 근거 조항 적중률과 무응답 질의를 함께 검수한다.
