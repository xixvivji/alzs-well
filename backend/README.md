# ALZ's well 백엔드

ALZ's well의 Java 백엔드 기초 골격이다. 공모전 MVP에서는 완전 합성데이터만 사용하며 실제 송금, 지급정지, 가족 연락 같은 외부 실행은 하지 않는다.

## 기술 기준

- Java 21
- Spring Boot 3.5.16
- Gradle 8.14.3 Wrapper
- PostgreSQL 17
- Spring MVC, Spring Security, Spring Data JPA
- Flyway, Actuator, Testcontainers

## 로컬 실행

요구사항은 Java 21과 Docker다.

```bash
docker compose up -d postgres
./gradlew bootRun
```

기본 로컬 DB 설정은 `compose.yaml`과 `application.yml`이 일치한다. 환경별 값을 바꿀 때는 `.env.example`을 참고해 환경변수로 주입한다.

서버 확인:

```bash
curl -i http://localhost:8080/api/v1/system/health
curl -i http://localhost:8080/actuator/health
```

애플리케이션 헬스 응답은 모든 API와 동일한 공통 응답 형식을 사용하고, `X-Trace-Id` 응답 헤더와 본문의 `traceId`가 일치한다.

## 공통 응답

```json
{
  "success": true,
  "status": 200,
  "code": "SYSTEM_HEALTHY",
  "message": "서비스가 정상 동작 중입니다.",
  "data": {
    "status": "UP",
    "service": "alzs-well-backend",
    "syntheticDataOnly": true,
    "externalActionsEnabled": false
  },
  "errors": [],
  "timestamp": "2026-08-14T00:00:00Z",
  "traceId": "0123456789abcdef0123456789abcdef"
}
```

## 패키지 방향

```text
com.alzswell
├── common       공통 응답, 예외, 보안, 요청 추적
├── system       헬스체크와 운영 정보
├── demo         익명 세션, 시나리오 seed, 멱등 Reset
├── ledger       합성 계좌·거래 원장
├── detection    기준선·MAD·변화 신호
├── casework     사건 병합·상태기계·행원 검토
├── consent      분석·신뢰연락인 동의
├── policy       정상종결·검토상향·실행금지 정책
├── explanation  결정론적 설명과 선택형 LLM
└── audit        판단·상태전이·직원행위 감사
```

의존 방향은 `demo → casework → detection → ledger`, `casework → consent·policy·explanation`을 기준으로 하고 중요 이벤트는 `audit`에 남긴다.

## 테스트

```bash
./gradlew test
```

일반 단위 테스트는 Docker 없이 실행된다. PostgreSQL 통합 테스트는 Docker를 사용할 수 있을 때 Testcontainers로 Flyway 마이그레이션까지 검증한다.
