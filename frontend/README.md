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

## 로컬 실행

```bash
npm ci
npm run dev
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
