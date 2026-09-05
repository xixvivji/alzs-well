# ALZ's well 프론트엔드

2026 금융 AI Challenge용 고객·행원 웹 MVP입니다. 프론트와 BFF는 Vercel Next.js에 배포하고, Spring Boot·FastAPI·PostgreSQL은 AWS에서 운영합니다.

## 구조

```text
브라우저
  → Vercel Next.js 화면
  → 같은 origin /api Route Handler
  → AWS HTTPS Gateway
  → Spring Boot
  → FastAPI / PostgreSQL
```

브라우저에는 AWS origin, 프록시 공유 비밀값, 직원 bootstrap 토큰을 노출하지 않습니다. 배포 환경에서 `NEXT_PUBLIC_API_BASE_URL`을 비워 두면 모든 API 요청은 같은 origin의 Vercel Route Handler를 통과합니다. 고객 capability는 생성 응답에서 제거해 `Secure`·`HttpOnly`·`SameSite=Strict` host cookie에만 보관하고, `sessionStorage`에는 비밀값이 아닌 세션·시나리오 식별자만 저장합니다.

공개 행원 시연은 임의 방문자에게 직원 토큰을 바로 발급하지 않습니다. 고객 화면에서 생성된 현재 합성 세션 capability를 서버가 AWS에 검증한 후에만 단기 직원 capability를 발급합니다. 운영 모드에서는 신뢰 프록시가 전달한 RS256 identity JWT의 서명·issuer·audience·만료·직원 역할을 Vercel 서버가 모두 검증하며, 원문 사용자 ID 헤더만으로는 권한을 발급하지 않습니다.

Vercel BFF의 호출량 제한은 Vercel이 위조 방지를 위해 덮어쓴 `x-vercel-forwarded-for`를 서버 비밀값으로 HMAC 처리한 비식별 network key를 사용합니다. 원문 IP는 AWS·로그·cookie로 전달하지 않으므로 cookie를 삭제해도 새 rate bucket이 발급되지 않습니다. 로컬 개발에서는 서버가 생성한 무작위 client ID의 HMAC 서명 cookie를 사용합니다. 고객 Bearer access token은 만료 전에 refresh token으로 한 번 갱신하고, 갱신 실패 또는 재시도 401이면 두 token을 메모리에서 즉시 폐기해 안전하게 로그아웃합니다.

## 화면과 API 연결

- `/demo`: 합성 세션 생성과 정상·주의·오탐 리허설 시작
- `/demo/protection`: 알림·AI 의향서·장기 변화·감사이력을 묶은 고객 안심 보호센터
- `/demo/finance`: 통합자산, 계좌, 거래, 기준선, 동의, 보호 안내
- `/demo/ai-assistant`: AI 금융생활 의향서 현재 상태·초안·승인, 장기 변화, 쉬운말·음성
- `/demo/alerts`: 고객 변화 확인, 맥락 응답, 알림 감사이력
- `/help`: 로그인 상태를 판별해 회원별 도움 허브 또는 비로그인 공개 시나리오로 연결
- `/login`: `demo001`~`demo300` 합성 회원 전용 로그인. 회원가입은 없고 token은 Vercel Secure·HttpOnly 쿠키에만 저장
- `/banking`: 로그인 회원의 통합자산·현금흐름·지출·금융일정 대시보드
- `/banking/help`: 로그인 회원과 동일한 customerId의 의향·알림·기준선·변화신호를 사용해 1단계 도움 방식, 2단계 AI 변화 요약·확인 질문, 3단계 본인 확인으로 안내하는 도움 허브
- `/banking/accounts`: 계좌 상세·잔액 추세·계좌별 거래·거래처·부채·납부 달력, 거래 분류·기억 메모, 정기납부 확인 알림, 월별 명세서 상세
- `/banking/transfer`: 등록 수취인·한도·이체 양식과 실제 실행 없는 이체 사전검증
- `/banking/products`: 회원별 카드·예금·대출·투자·외환·연금·신탁, 관심종목 지연 시세·차트 조회와 실행 없는 모의계산
- `/banking/life`: 금융생활 의향서·인앱 알림·기관 연결·보호수단·승인 근거·보안 세션
- `/banking/safety`: 로그인 회원별 기준선·변화신호·불변 근거·본인 확인 선택지·감사이력
- `/banking/settings`: 프로필·접근성·신뢰 연락처·이의신청
- `/demo/products`: 로그인 회원 본인의 합성 카드·예금·대출·투자·외환·연금·신탁 조회, 동의관리, 실행 없는 이자·상환·환전 모의계산
- `/demo/settings`: 로그인 회원 본인의 프로필·알림 채널·접근성 설정, 최소정보 신뢰 연락처, 사람 재검토 이의신청
- `/demo/services`: 실제 금융업무 메뉴가 아닌 개발·시연용 API 계약과 연결 상태
- `/staff/cases`: 합성 사건 큐, 타임라인·내부 메모·후속관리, 근거 기반 코파일럿, 행원 검토 폐루프
- `/staff/operations`: 실제 데모 사건 큐와 운영 사건 상세·타임라인·근거·메모·후속관리·금융생활 의향 요약
- `/staff/control-center`: core/AI/통합 readiness·버전·AI 폴백 상태와 규칙·감사 상세, 준법·정책 API 계약
- `/staff/login`: `staff001`~`staff005` 보호업무 역할과 `admin001`~`admin002` 탐지관리 역할의 합성 운영 로그인
- `/staff/system-status`: health·readiness·공개 설정·버전과 AI 검색 장애 폴백 상태

`scripts/generate-api-catalog.mjs`는 최종 API 명세 283개와 Spring Controller 239개를 대조해 `lib/generated/api-operation-catalog.ts`를 만듭니다. 문서와 코드의 교집합 238개, 코드 전용 직원 bootstrap operation 1개, 미구현 계획 23개, 외부 참고 22개가 바뀌면 검증이 실패합니다. 카탈로그의 239개는 백엔드 구현 계약 수이지, 현재 모든 화면에서 실제 호출되는 API 수가 아닙니다.

생성된 공통 클라이언트 계약은 method, path parameter, query, 인증 방식, 실행 경계를 일관되게 처리합니다. 브라우저는 Bearer token을 받지 않으며 같은 origin의 `/api/member-auth/*` BFF가 로그인·회전·로그아웃을 처리합니다. 나머지 고객·운영 API에는 BFF가 HttpOnly access token을 서버에서 주입하고, 직접 `/api/v1/auth/login`·`token/refresh`를 호출해 원문 token을 받는 경로는 차단합니다. 공개 계정은 성공한 `PUBLIC` fixture의 고객 300명·보호업무 직원 5명·탐지관리자 2명으로 제한합니다. 고객 API는 customerId 소유권을, 운영 API는 `PROTECTION_STAFF` 또는 `DETECTION_ADMIN` 권한을 다시 검증합니다. `PLANNED`와 `REFERENCE_ONLY`는 네트워크 요청 전에 차단됩니다.

`npm run catalog:ui-coverage`는 구현 operation 중 프론트 코드가 명시적으로 호출하는 계약을 역할·도메인별로 출력합니다. 2026-09-05 재집계는 239개 중 158개(66.1%)이며, 동적 데모 경로처럼 문자열을 조립하는 호출은 보수적으로 누락될 수 있습니다. API 구현 수와 화면 연결 수를 같은 의미로 발표하지 않으며, 고객·직원·관리자 API는 서로 다른 권한 화면에 배치하고 관리자 변경 API를 고객 token으로 우회 노출하지 않습니다. 역할별 운영 원칙과 서버 전용·간접 연결 분류는 `../docs/FRONTEND_API_ROLE_MATRIX.md`를 따릅니다.

고객 알림의 `나중에 확인`은 `POST /api/v1/demo/sessions/{sessionId}/alerts/{alertId}/defer`를 호출하며 `{ expectedVersion, deferredUntil }`, `Idempotency-Key`, 데모 capability/run ID를 전달합니다. 백엔드는 같은 세션·run·알림의 version을 검증하고 `DEFERRED` 상태 및 변경된 알림 데이터를 반환해야 합니다.

## 로컬 실행

```bash
npm ci
npm run dev
npm run catalog:check
npm run lint
npm test
```

합성 회원 인증은 항상 BFF를 거쳐야 하므로 `.env.local`의 `NEXT_PUBLIC_API_BASE_URL`을 비우고 `BACKEND_API_ORIGIN`, `BACKEND_PROXY_SHARED_SECRET`, `DEMO_STAFF_BOOTSTRAP_TOKEN`, `DEMO_PUBLIC_STAFF_MODE`를 설정합니다. 운영 직원 모드(`DEMO_PUBLIC_STAFF_MODE=false`)에서는 `.env.example`의 `STAFF_IDENTITY_JWT_*` 항목이 모두 필요합니다.

## Vercel 설정

- 저장소 연결 후 Root Directory를 `frontend`로 지정합니다.
- Framework Preset은 Next.js를 사용합니다.
- `.env.example`의 서버 전용 값을 Preview와 Production 환경에 각각 등록합니다.
- `BACKEND_API_ORIGIN`은 경로가 없는 AWS HTTPS origin만 허용합니다.
- AWS CORS는 Vercel 브라우저가 아닌 서버 BFF만 호출하므로 공개 wildcard를 사용하지 않습니다.
- Vercel Firewall에서 `POST /api/v1/demo/sessions`를 IP별 분당 10회로 제한하고 `/api/**` 일반 제한과 Bot Protection을 함께 활성화합니다. 애플리케이션 HMAC key는 심층 방어이며 edge WAF를 대체하지 않습니다.
- Preview는 Deployment Protection을 켜고, 배포 후 cookie를 지운 반복 세션 생성도 같은 IP 예산을 사용하는지 확인합니다.

Vercel은 배포 함수에 전달하는 `x-forwarded-for`를 덮어써 외부 IP spoofing을 막는다고 명시합니다. 운영 rate limit은 [Vercel request headers](https://vercel.com/docs/headers/request-headers)와 [Vercel WAF rate limiting](https://examples.vercel.com/kb/guide/add-rate-limiting-vercel)을 함께 적용합니다.

본 프로젝트는 합성데이터만 사용하며 질병·사기 자동 판정, 실제 송금, 지급정지 또는 외부 연락을 수행하지 않습니다.
