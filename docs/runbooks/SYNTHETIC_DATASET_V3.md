# synthetic-v3 합성 운영 데이터 Runbook

## 목적과 경계

`synthetic-v3`는 배포된 PostgreSQL에서 조회·탐지·페이지네이션·AI 사건 초안을 검증하기 위한 완전 합성 데이터다. 실제 고객, 실제 금융기관, 외부 API, 실제 송금·알림·금융조치를 사용하지 않는다.

| Profile | 고객 | 계좌 | 거래 | 용도 |
|---|---:|---:|---:|---|
| `SMOKE` | 10 | 20 | 600 | 로컬·CI 빠른 회귀 |
| `DEMO` | 50 | 100 | 12,000 | 프론트·발표·통합 시연 |
| `DEV` | 1,000 | 2,000 | 1,000,000 | 페이지네이션·탐지·인덱스·운영 검증 |

고객마다 `NORMAL`, `MISSED_PAYMENT`, `DUPLICATE_TRANSFER`, `REPEATED_CONFIRMATION` 중 하나가 결정론적으로 배정된다. 고객별 적재된 탐지 데이터셋과 기대 신호 수는 `synthetic_fixture_customer`에서 확인한다.

## 실행 전 확인

1. `develop` 기준 이미지와 Flyway V63이 배포돼 있어야 한다.
2. 백엔드 `/api/v1/system/readiness`가 `ready=true`여야 한다.
3. `.env`의 DB 비밀번호는 32자 이상이어야 한다.
4. `SYNTHETIC_DATA_ONLY=true`, `SYNTHETIC_PROVIDER_ONLY=true`, `EXTERNAL_ACTIONS_ENABLED=false`를 변경하지 않는다.
5. `fixtureVersion`, profile, seed를 배포 기록에 남긴다.

## 실행

먼저 `SMOKE`로 배포 연결과 권한을 검증한다.

```bash
cd backend
SYNTHETIC_SEED_PROFILE=SMOKE \
docker compose --env-file .env --profile synthetic-tools run --rm synthetic-seed
```

그다음 시연 환경은 `DEMO`, 성능 검증 환경은 `DEV`를 실행한다.

```bash
SYNTHETIC_SEED_PROFILE=DEMO \
SYNTHETIC_SEED_FIXTURE_VERSION=synthetic-v3.0.0 \
SYNTHETIC_SEED_VALUE=20260825 \
SYNTHETIC_SEED_BATCH_SIZE=10 \
docker compose --env-file .env --profile synthetic-tools run --rm synthetic-seed
```

`DEV`는 데이터베이스 용량과 실행시간을 확인한 별도 검증 환경에서만 실행한다. 공개 운영 데이터베이스에는 실행하지 않는다.

## 완료 검증

```sql
select run_id, fixture_version, profile, seed, status,
       actual_customer_count, actual_account_count, actual_transaction_count,
       manifest_hash, synthetic_data, external_actions_created
  from synthetic_fixture_generation_run
 order by started_at desc;
```

완료 조건은 다음과 같다.

- `status=SUCCEEDED`
- expected 건수와 actual 건수 일치
- `manifest_hash` 64자리 소문자 hex
- `synthetic_data=true`
- `external_actions_created=false`
- 생성 거래의 `provider_mode=SYNTHETIC_PROVIDER`

같은 버전·profile·seed를 다시 실행하면 새 행을 만들지 않고 동일 run과 manifest를 재생해야 한다.

실제 HTTP 조회·탐지 E2E에서 합성 로그인 API가 필요하면 외부에 노출되지 않는 로컬 환경에서만
`compose.integration.yaml`을 기본 Compose 파일과 함께 적용한다.

```bash
docker compose --env-file .env \
  -f compose.yaml -f compose.integration.yaml up --build --detach --wait
```

SMOKE 적재도 같은 두 Compose 파일을 사용한다.

```bash
SYNTHETIC_SEED_PROFILE=SMOKE \
docker compose --env-file .env \
  -f compose.yaml -f compose.integration.yaml \
  --profile synthetic-tools run --build --rm synthetic-seed
```

통합 override는 `development`, `publicExposure=false`, `localAuth=true`를 한 묶음으로 강제하고
서버를 컨테이너 내부의 모든 인터페이스에 바인딩한다. 게이트웨이의 host bind 기본값은 계속
`127.0.0.1`이다. 기본 Compose와 production 프로필에서는 로컬 인증이 계속 비활성화되며,
이 설정을 공개 staging·운영 환경의 인증 수단으로 사용하지 않는다.

## 실패와 재개

실패 실행은 `FAILED`와 비민감 `error_code`만 저장한다. 원인을 수정한 뒤 같은 조합을 다시 실행하면 결정론적 ID와 `ON CONFLICT DO NOTHING`을 사용해 누락 batch를 보완한다. 프로세스 강제 종료로 `RUNNING`에 남은 경우에만 상태와 DB 연결을 확인한 후 아래 값을 사용한다.

```dotenv
SYNTHETIC_SEED_RESUME=true
```

동시에 두 seed Job을 실행하지 않는다. 생성된 snapshot은 추가 전용이므로 임의 UPDATE·DELETE로 정리하지 말고 새로운 fixture version 또는 seed를 사용한다.
