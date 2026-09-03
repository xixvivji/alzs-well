# synthetic-v3 합성 운영 데이터 Runbook

## 목적과 경계

`synthetic-v3`는 배포된 PostgreSQL에서 조회·탐지·페이지네이션·AI 사건 초안을 검증하기 위한 완전 합성 데이터다. 실제 고객, 실제 금융기관, 외부 API, 실제 송금·알림·금융조치를 사용하지 않는다.

| Profile | 고객 | 계좌 | 거래 | 용도 |
|---|---:|---:|---:|---|
| `SMOKE` | 10 | 20 | 600 | 로컬·CI 빠른 회귀 |
| `DEMO` | 50 | 100 | 12,000 | 프론트·발표·통합 시연 |
| `PUBLIC` | 300 | 600 | 72,000 | 공개 합성 회원 로그인·회원별 데이터 시연 |
| `LOAD` | 250 | 500 | 75,000 | 탐지 품질·페이지네이션·일상 부하 검증 |
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

그다음 시연 환경은 `DEMO`, 일상적인 통합 검증은 `LOAD`, 대용량 성능 검증 환경은 `DEV`를 실행한다.

```bash
SYNTHETIC_SEED_PROFILE=DEMO \
SYNTHETIC_SEED_FIXTURE_VERSION=synthetic-v3.0.0 \
SYNTHETIC_SEED_VALUE=20260825 \
SYNTHETIC_SEED_BATCH_SIZE=10 \
docker compose --env-file .env --profile synthetic-tools run --rm synthetic-seed
```

탐지 품질까지 검증하는 권장 통합 실행은 다음과 같다. `LOAD`는 활성 탐지정책을 250명
전체에 적용하고 정상 고객 오탐과 이상 고객 미탐이 하나라도 있으면 Job을 실패시킨다.

```bash
SYNTHETIC_SEED_PROFILE=LOAD \
SYNTHETIC_SEED_VERIFY_DETECTION=true \
SYNTHETIC_SEED_FIXTURE_VERSION=synthetic-v3.1.0 \
SYNTHETIC_SEED_VALUE=20260826 \
SYNTHETIC_SEED_BATCH_SIZE=25 \
docker compose --env-file .env --profile synthetic-tools run --rm synthetic-seed
```

`DEV`는 데이터베이스 용량과 실행시간을 확인한 별도 검증 환경에서만 실행한다. `LOAD`와
`DEV` 모두 공개 운영 데이터베이스에는 실행하지 않는다.

공개 합성 회원 환경은 `PUBLIC`만 사용한다. 아래 Job은 `demo001`~`demo300`을 각기 다른
`customer_id`에 연결하고, 회원마다 계좌 2개·거래 240건과 카드·예금·대출·투자 snapshot을
프로비저닝한다. 비밀번호 평문을 저장소나 명령 기록에 넣지 말고 비밀 저장소에서 BCrypt
hash만 주입한다.

```bash
docker compose --env-file .env.aws-app \
  -f compose.aws-app.yaml --profile synthetic-member-seed \
  run --rm synthetic-member-seed
```

Job 성공 후에만 업무 백엔드의 `SYNTHETIC_MEMBER_AUTH_ENABLED=true`를 적용한다. 공개 인증은
성공한 `PUBLIC` run에 속한 `demo[0-9]{3}` 계정만 허용하며 기존 `synthetic-customer` 계정은
공개 경로에서 거부한다. access·refresh token은 Vercel BFF의 Secure·HttpOnly·SameSite 쿠키로
보관하고 브라우저 JavaScript나 저장소에는 전달하지 않는다.

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

`SYNTHETIC_SEED_VERIFY_DETECTION=true`이면 아래 품질 증적도 확인한다.

```sql
select run_id, policy_version, algorithm_version, status, policy_stable,
       evaluated_customer_count, expected_signal_count, actual_signal_count,
       false_positive_count, false_negative_count, precision_score, recall_score,
       report_hash, evaluated_at
  from synthetic_fixture_quality_report
 order by evaluated_at desc;
```

완료 조건은 `status=PASSED`, `policy_stable=true`, 오탐·미탐 0건, 기대·실제 신호 수
일치다. 정책이 평가 도중 바뀌거나 외부 실행·advisory AI 사용이 감지돼도 실패한다.
CI Compose smoke도 `SMOKE + SYNTHETIC_SEED_VERIFY_DETECTION=true`를 실행하고 동일 품질 조건을
검증 증적으로 업로드한다. `LOAD`는 로컬·통합 환경에서 명시적으로 실행한다.

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
`127.0.0.1`이다. 일반 로컬 인증은 공개 staging·운영 환경의 인증 수단으로 사용하지 않는다.
production 공개 합성 인증은 앞의 `PUBLIC` 300명 프로비저닝과 HttpOnly BFF 경계를 모두 갖춘
경우에만 별도 플래그로 활성화한다.

## 실패와 재개

실패 실행은 `FAILED`와 비민감 `error_code`만 저장한다. 원인을 수정한 뒤 같은 조합을 다시 실행하면 결정론적 ID와 `ON CONFLICT DO NOTHING`을 사용해 누락 batch를 보완한다. 프로세스 강제 종료로 `RUNNING`에 남은 경우에만 상태와 DB 연결을 확인한 후 아래 값을 사용한다.

```dotenv
SYNTHETIC_SEED_RESUME=true
```

동시에 두 seed Job을 실행하지 않는다. 생성된 snapshot은 추가 전용이므로 임의 UPDATE·DELETE로 정리하지 말고 새로운 fixture version 또는 seed를 사용한다.
