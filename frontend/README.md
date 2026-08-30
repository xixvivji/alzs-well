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

공개 행원 시연은 임의 방문자에게 직원 토큰을 바로 발급하지 않습니다. 고객 화면에서 생성된 현재 합성 세션 capability를 서버가 AWS에 검증한 후에만 단기 직원 capability를 발급합니다.

## 화면과 API 연결

- `/demo`: 합성 세션 생성과 정상·주의·오탐 리허설 시작
- `/demo/finance`: 통합자산, 계좌, 거래, 기준선, 동의, 보호 안내
- `/demo/ai-assistant`: AI 금융생활 의향서 현재 상태·초안·승인, 장기 변화, 쉬운말·음성
- `/demo/alerts`: 고객 변화 확인, 맥락 응답, 알림 감사이력
- `/demo/products`: 사설 Bearer 인증 기반 합성 카드·예금·대출·투자·외환·연금·신탁 조회, 동의관리, 실행 없는 이자·상환·환전 모의계산
- `/demo/settings`: 고객 프로필·알림 채널·접근성 설정, 최소정보 신뢰 연락처, 사람 재검토 이의신청
- `/demo/services`: 고객 금융서비스 전체 계약과 연결 상태
- `/staff/cases`: 합성 사건 큐, 타임라인·내부 메모·후속관리, 근거 기반 코파일럿, 행원 검토 폐루프
- `/staff/operations`: 실제 데모 사건 큐와 행원 처리 단계, 추가 행원 API 계약
- `/staff/control-center`: 실제 readiness·버전·AI 폴백 상태와 감사·준법·정책 API 계약
- `/staff/system-status`: health·readiness·공개 설정·버전과 AI 검색 장애 폴백 상태

`scripts/generate-api-catalog.mjs`는 최종 API 명세 278개와 Spring Controller 234개를 대조해 `lib/generated/api-operation-catalog.ts`를 만듭니다. 문서와 코드의 교집합 233개, 코드 전용 직원 capability 1개, 미구현 계획 23개, 외부 참고 22개가 바뀌면 검증이 실패합니다.

생성된 공통 클라이언트 계약은 method, path parameter, query, 인증 방식, 실행 경계를 일관되게 처리합니다. 공개 Vercel 화면이 운영용 Bearer API를 임의로 호출하지는 않습니다. 금융상품 및 고객 설정 화면의 로컬 합성 로그인은 development 또는 사설 staging에서만 사용할 수 있고 production에서는 서버가 강제 비활성화합니다. 실제 서비스에서는 기업 IdP·MFA·RBAC로 교체해야 합니다. `PLANNED`와 `REFERENCE_ONLY`는 네트워크 요청 전에 차단됩니다.

## 로컬 실행

```bash
npm ci
npm run dev
npm run catalog:check
npm run lint
npm test
```

로컬에서 Spring을 직접 호출하려면 `.env.local`에 `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080`을 설정합니다. Vercel 동작과 동일한 BFF를 확인하려면 이 값을 비우고 `BACKEND_API_ORIGIN`, `BACKEND_PROXY_SHARED_SECRET`, `DEMO_STAFF_BOOTSTRAP_TOKEN`, `DEMO_PUBLIC_STAFF_MODE`를 설정합니다.

## Vercel 설정

- 저장소 연결 후 Root Directory를 `frontend`로 지정합니다.
- Framework Preset은 Next.js를 사용합니다.
- `.env.example`의 서버 전용 값을 Preview와 Production 환경에 각각 등록합니다.
- `BACKEND_API_ORIGIN`은 경로가 없는 AWS HTTPS origin만 허용합니다.
- AWS CORS는 Vercel 브라우저가 아닌 서버 BFF만 호출하므로 공개 wildcard를 사용하지 않습니다.

본 프로젝트는 합성데이터만 사용하며 질병·사기 자동 판정, 실제 송금, 지급정지 또는 외부 연락을 수행하지 않습니다.
