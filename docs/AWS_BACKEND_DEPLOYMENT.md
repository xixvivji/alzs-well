# AWS 백엔드 합성데모 배포 기준

현재 백엔드는 외부 AI 없이도 결정론적 규칙과 템플릿으로 전체 P0 흐름을 실행한다. 따라서 프론트 선택을 확정하거나 AI를 붙이기 전에 AWS staging에 먼저 배포해 HTTPS, CORS, REST 계약, 재시작, 만료 데이터 정리를 검증할 수 있다.

## 권장 경계

```text
인터넷 → HTTPS ALB → EC2 보안그룹 → Nginx gateway → Spring Boot → PostgreSQL
```

- ALB만 인터넷에 공개하고 EC2의 gateway port는 ALB 보안그룹에서만 접근시킨다.
- Spring과 PostgreSQL port는 host나 인터넷에 게시하지 않는다.
- 공모전 단기 데모는 단일 EC2의 Docker Compose로 시작할 수 있다. 장기 운영·복구가 필요하면 DB를 private subnet의 RDS PostgreSQL로 분리한다.
- 현재 시스템에는 실제 고객정보를 넣지 않고 완전 합성 fixture만 사용한다.
- AI·외부 금융사·푸시·문자·전화 연결은 계속 비활성화한다.
- 공개 세션 생성은 고객 capability만 발급한다. 직원 capability는 직원 origin에서 별도 HTTP Basic 인증을 거친 staging 운영자에게만 발급한다. 이 임시 인증은 기업 직원 인증을 대체하지 않으므로 실제 IdP·MFA·RBAC가 붙기 전 배포는 합성 staging 경계로 취급한다.

## 운영 환경값

```dotenv
SPRING_PROFILES_ACTIVE=production
CORS_CUSTOMER_ALLOWED_ORIGINS=https://customer-demo.example.com
CORS_STAFF_ALLOWED_ORIGINS=https://staff-demo.example.com
DEMO_STAFF_USERNAME=replace-with-a-non-default-operator-name
DEMO_STAFF_PASSWORD=replace-with-a-long-random-staff-password
POSTGRES_PASSWORD=replace-with-a-long-random-admin-password
POSTGRES_MIGRATION_PASSWORD=replace-with-a-long-random-migration-password
POSTGRES_APP_PASSWORD=replace-with-a-long-random-runtime-password
GATEWAY_BIND_ADDRESS=0.0.0.0
TRUSTED_PROXY_CIDR=10.0.0.0/16
NETWORK_MODE=AIR_GAPPED_DEMO
EXTERNAL_EGRESS_ENABLED=false
REMOTE_MODEL_ENABLED=false
SYNTHETIC_PROVIDER_ONLY=true
```

`TRUSTED_PROXY_CIDR`는 임의의 전역망이 아니라 ALB가 위치한 실제 VPC/private subnet CIDR로 좁힌다. 운영 프로필은 고객·직원 CORS 항목이 비어 있거나 서로 겹치거나 wildcard, HTTP, path/query가 포함되면 애플리케이션 기동을 실패시킨다. 두 화면은 서로 다른 HTTPS origin을 allowlist에 명시하되, CORS만 믿지 않고 각각 `CUSTOMER_DEMO`, `DEMO_STAFF` capability와 서버 메서드 권한을 적용한다. 직원 bootstrap 자격증명은 브라우저 번들·정적 파일·프론트 환경변수에 넣지 않는다.

## 배포 전 체크

1. PostgreSQL 관리자·migration·runtime 비밀번호와 직원 bootstrap 비밀번호는 `.env`에 장기 보관하지 말고 AWS Secrets Manager 또는 SSM Parameter Store에서 각각 주입한다. Spring은 `alzswell_app`, Flyway는 `alzswell_migrator` 역할을 사용하고 관리자 계정을 애플리케이션에 전달하지 않는다.
2. nginx·PostgreSQL·애플리케이션 이미지는 검증 후 tag 대신 immutable digest로 고정한다.
3. ALB 인증서와 HTTP→HTTPS redirect를 구성한다.
4. EC2 보안그룹은 gateway port의 source를 ALB 보안그룹으로 한정하고 SSH는 Session Manager로 대체한다.
5. ALB 앞 AWS WAF에도 세션 생성 rate rule을 두고 `DEMO_MAX_ACTIVE_SESSIONS`를 예상 시연 인원에 맞게 낮춘다. 단일 IP 제한만으로 분산 요청을 완전히 막을 수는 없다.
6. `docker compose config`, 전체 자동시험, `/api/v1/system/readiness`를 통과시키고 ALB target group health check path도 동일한 readiness 경로로 설정한다.
7. capability 원문과 요청 본문을 ALB·Nginx access log에 남기지 않는지 확인한다.
8. EBS 암호화·스냅샷·복구 절차 또는 RDS 백업을 설정한다.
9. CloudWatch에는 상태·지연·429/5xx 같은 운영 지표만 보내고 자유입력·거래 원문은 보내지 않는다.
10. ALB가 넘긴 `X-Forwarded-Proto`·`X-Forwarded-Port`가 gateway에서 내부 HTTP 값으로 덮어써지지 않는지 redirect·절대 URL·보안 헤더를 확인한다.
11. production 기본값처럼 OpenAPI·Swagger UI와 readiness 외 관리 endpoint를 공개하지 않는다. 일시적으로 문서가 필요하면 접근통제된 내부 환경에서만 활성화한다.

이 구성은 공모전용 합성데모 staging 기준이다. 금융회사 실서비스 보안성 심사, 개인정보·신용정보 처리 승인 또는 법규 준수 완료를 의미하지 않는다.
