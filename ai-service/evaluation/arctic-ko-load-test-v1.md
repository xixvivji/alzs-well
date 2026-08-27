# Arctic-ko 제한 부하 시험 v1

> 측정일: 2026-08-27 KST
> 결과: PASS
> 범위: 합성 지식 corpus를 사용한 MVP 검색 경로 게이트

## 대상

- 모델: `dragonkue/snowflake-arctic-embed-l-v2.0-ko`
- revision: `55ec6e9358a56d56af759bc8372e970caf8c305f`
- 차원: 1024
- 인덱스: `hybrid-arctic-ko-v1`
- 실행 환경: Docker Desktop on macOS arm64
- AI 컨테이너 제한: CPU 2개, 메모리 3 GiB, Uvicorn worker 1개
- 데이터: `DOC-SYN-COPILOT-001` 합성 승인 문서

모델 파일은 로컬 읽기 전용 volume으로 주입했으며 실행 중 모델 다운로드와 외부 AI API
호출은 없었다. 모델 해시 검증, 재-ingestion, pgvector 저장, FastAPI 검색, Spring 인용
재검증, AI 중단 시 결정론적 폴백을 같은 격리 실행에서 검증했다.

## 게이트

| 항목 | 기준 |
|---|---:|
| endpoint별 요청 수 | 100 |
| 동시성 | 4 |
| warm-up | endpoint별 8 |
| p95 | 1,000 ms 이하 |
| 오류율 | 0% |
| 처리량 | 2 RPS 이상 |
| AI 프로세스 peak RSS | 2.5 GiB 이하 |
| AI 서비스 기동 | 30초 이하 |

## 결과

| 경로 | 성공/전체 | p50 | p95 | p99 | 처리량 | 오류율 |
|---|---:|---:|---:|---:|---:|---:|
| FastAPI 내부 검색 | 100/100 | 607.36 ms | 919.36 ms | 928.00 ms | 6.17 RPS | 0.0000 |
| Spring 검색 API | 100/100 | 590.98 ms | 716.56 ms | 817.58 ms | 6.74 RPS | 0.0000 |

- AI 서비스 기동: 12.56초
- AI 프로세스 peak RSS (`/proc/1/status`의 `VmHWM`): 2,107.03 MiB
- 컨테이너 peak memory (`memory.peak`, page cache 포함): 2,527.70 MiB
- 최종 결과: PASS

FastAPI 직접 경로와 Spring 경로는 순서대로 측정하므로 두 처리량을 합산해 전체 시스템
처리량으로 해석하지 않는다. Spring 구간은 Nginx rate limit의 영향을 분리하기 위해
테스트 전용 loopback 포트를 사용했다. 인증, 역할·audience ACL, 문서 생명주기, 인용
재검증은 우회하지 않았다.

## 재현

저장소 루트에 승인된 모델 반입본이 `models/snowflake-arctic-embed-l-v2.0-ko`로 존재할 때
다음을 실행한다.

```bash
AI_MODEL_HOST_ROOT="$PWD/models" \
COPILOT_RAG_EXTRA_COMPOSE_FILE=backend/compose.arctic-ko.yaml \
COPILOT_RAG_EMBEDDING_MODE=arctic-ko \
COPILOT_RAG_LOAD_TEST_ENABLED=true \
AI_LOAD_TEST_PORT=18085 \
BACKEND_LOAD_TEST_PORT=18086 \
AI_LOAD_TEST_CONCURRENCY=4 \
COMPOSE_PROJECT_NAME=alzs-well-arctic-load-e2e \
BACKEND_PORT=18084 \
COPILOT_RAG_E2E_ARTIFACT_DIR=/tmp/alzs-well-arctic-load-e2e \
ai-service/.venv/bin/python scripts/copilot_rag_e2e.py
```

`compose.load-test.yaml`은 두 서비스를 `127.0.0.1`에만 임시 공개하고 종료 시 격리
프로젝트·볼륨을 제거한다. 원시 결과는 `/tmp/alzs-well-arctic-load-e2e/load-test.json`과
`load-test.md`에 생성된다.

## 해석과 다음 조건

이 시험은 작은 합성 corpus에서의 제한 부하 게이트다. 운영 기본 모델 승격에는 다음이
추가로 필요하다.

1. 사람이 승인한 공식문서와 검색 정답 골든셋
2. 목표 배포 서버에서의 장시간 부하·메모리 안정성 측정
3. 운영 승인 및 기능 플래그 활성화 결정

따라서 이 PASS만으로 Arctic-ko를 운영 기본값으로 변경하지 않는다.
