# Arctic-ko 단계적 활성화 E2E v1

- 실행일: 2026-08-28
- 상태: **PASS**
- 실행 범위: 격리 Docker Compose 내부 검증 환경
- 모델: `dragonkue/snowflake-arctic-embed-l-v2.0-ko`
- revision: `55ec6e9358a56d56af759bc8372e970caf8c305f`
- 벡터 차원: 1024
- 인덱스: `hybrid-arctic-ko-v1`

## 검증 결과

1. 모델 파일을 읽기 전용 volume으로 연결하고 고정 SHA-256을 검증했다.
2. 합성 승인 문서를 Arctic-ko로 두 차례 ingestion해 1024차원 벡터 재적재를 확인했다.
3. PostgreSQL/pgvector 검색이 현재 모델 ID·revision·차원과 일치하는 벡터만 사용했다.
4. FastAPI 내부 검색 결과를 Spring이 문서·버전·passage·인용 기준으로 재검증했다.
5. Copilot이 `RAG_GROUNDED_TEMPLATE`과 인용 1건을 반환했다.
6. 외부 LLM 호출과 외부 egress 시도는 없었다.
7. FastAPI 중단 후 Spring이 `DETERMINISTIC_TEMPLATE`로 폴백했다.
8. 실행 종료 후 격리 컨테이너와 PostgreSQL volume을 제거했다.

공식문서 운영 골든셋 27건의 검색 품질은 별도 `arctic-ko-promotion-decision-v3.md`에서
실제 승인 문서 corpus로 검증했다. 이 E2E는 고객 데이터 없이 합성 승인 문서만 사용해
런타임 연결, 저장, 인용 및 장애 폴백을 검증한다.

## 부하 결과

| 항목 | FastAPI | Spring |
| --- | ---: | ---: |
| 요청 수 | 100 | 100 |
| 동시성 | 4 | 4 |
| p95 | 789.96 ms | 787.40 ms |
| 처리량 | 6.82 RPS | 6.38 RPS |
| 오류율 | 0% | 0% |

- AI 서비스 기동: 12.48초
- AI 프로세스 peak RSS: 2,102.30 MiB
- 컨테이너 peak memory(page cache 포함): 2,454.99 MiB
- 부하 게이트: PASS

## 승격 해석

Arctic-ko의 `STAGED_GO` 근거가 런타임에서도 다시 확인됐다. 기본 Compose와 기본 Hash
backend는 변경하지 않는다. 다음 단계는 목표 배포 서버의 제한된 내부 인스턴스에서 같은
overlay를 활성화하고 장시간 안정성·관측 지표를 확인하는 것이다. 그 전에는 전면 운영
기본값으로 전환하지 않는다.
