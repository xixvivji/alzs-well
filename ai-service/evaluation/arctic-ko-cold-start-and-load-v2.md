# Arctic-ko cold-start·제한 부하 재검증 v2

> 측정일: 2026-09-02 KST  
> 범위: 합성 승인 문서와 로컬 격리 Compose  
> 외부 AI API·실제 고객 데이터·외부 금융 조치: 사용하지 않음

## 확인 결과

- 실제 embedding backend: `local-arctic-ko`
- 모델 revision: `55ec6e9358a56d56af759bc8372e970caf8c305f`
- 벡터 차원: 1024
- 검색 index: `hybrid-arctic-ko-v1`
- cold-start 첫 검색: 5,015.34 ms
- AI 중단 시: `DETERMINISTIC_TEMPLATE`, citation 없음
- 정상·주의·오탐 시나리오: 모두 통과

승인 카탈로그 밖의 Hugging Face `.cache`와 모델 카드가 모델 디렉터리에 있으면 런타임은
`EMBEDDING_CONFIGURATION_INVALID`로 기동을 거부했다. 승인된 9개 소비 파일만 둔 뒤
revision·전체 파일 hash·골든셋 검증과 ingestion이 성공했다. hash backend로 자동 강등되지
않았다.

## 동시성 4 공식 게이트

각 경로 100건, 오류율 0%, p95 1초 이하 기준으로 재검증했다.

| 경로 | 성공/전체 | p50 | p95 | 처리량 | 오류율 |
|---|---:|---:|---:|---:|---:|
| FastAPI 내부 검색 | 100/100 | 638.87 ms | 686.02 ms | 6.16 RPS | 0% |
| Spring 검색 API | 100/100 | 672.98 ms | 724.85 ms | 5.99 RPS | 0% |

- AI 서비스 기동: 5.17초
- AI 프로세스 peak RSS: 2,101.41 MiB
- 컨테이너 peak memory: 2,528.08 MiB
- 결과: PASS

## 포화 탐색

CPU 2개·Uvicorn worker 1개에서 동시성 10을 시도했을 때 20건의 병렬 warm-up 중 일부가
3초 제한을 초과했다. 이 결과를 통과로 완화하지 않는다. 현재 공모전 staging의 검증 용량은
동시성 4이며, 동시성 10 이상을 목표로 할 경우 worker·CPU·메모리 조정 후 별도 게이트를
정의해야 한다.

검색 품질과 부하 성능을 섞지 않기 위해 부하 구간은 E2E에서 이미 citation이 확인된 고정
합성 질의를 사용한다. 다양한 질문의 관련성·무응답 평가는 독립 골든셋 회귀평가가 담당한다.

원시 실행 증적은 로컬 `/tmp/alzs-well-arctic-load-e2e-v2`에 생성하며 저장소에는 질의 원문,
token, 고객 식별자 또는 Compose 전체 로그를 커밋하지 않는다.
