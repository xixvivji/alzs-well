# AWS AI 통합 staging 배포 기준

최종 공모전 staging 기준은 `업무 EC2 1대 + AI EC2 1대 + Private RDS PostgreSQL/pgvector`다. 단일 EC2는 AI를 끈 최소 검증 환경에서만 사용한다. 이 구성은 합성 데이터 전용이며 금융회사 실서비스 보안성 심사를 통과했다는 의미가 아니다.

```text
Internet → WAF → HTTPS ALB (public subnet)
                    ↓
업무 EC2 (private) ─ Nginx + Spring Boot
                    ↓ mTLS https://ai.internal:8443, 내부 토큰
AI EC2 (private) ─ mTLS Nginx + FastAPI + Arctic-ko + SSM 전용 ingestion CLI
                    ↓ TLS
Private RDS PostgreSQL + pgvector
```

## 네트워크와 권한

AWS 루트 자격증명으로 배포하지 않는다. 실제 리소스 생성·변경은 AWS IAM Identity
Center 전용 permission set 또는 최소 권한의 `alzswell-staging-deployer` 역할을
AssumeRole한 세션에서만 실행한다. 루트는 계정 복구·보안 설정 외의 일상
운영에 사용하지 않는다. 배포 역할과 EC2 runtime instance profile은 서로
분리한다.

실제 VPC·ALB·WAF·EC2·RDS·ECR·Secrets Manager 리소스는 승인된 IaC
스택으로만 생성한다. 임시 `aws` CLI 명령으로 수동 생성하지 않으며, change
set/plan에서 월 예상 비용·공인 IP·암호화·삭제 방지·백업 정책을 검토한 후
적용한다.

| 대상 | 허용 source |
|---|---|
| ALB 443 | WAF/인터넷 정책 |
| 업무 EC2 8080 | ALB 보안그룹만 |
| AI EC2 8443 | 업무 EC2 보안그룹만 |
| RDS 5432 | 업무 EC2·AI EC2 보안그룹만 |
| SSH | 차단; Session Manager 사용 |

Spring, AI, RDS는 public IP를 갖지 않는다. FastAPI 8000은 AI EC2 host에 publish하지 않고
같은 EC2의 mTLS gateway에서만 접근한다. AI health/search와 ingestion은 public ALB listener
rule에 추가하지 않는다. 사설 Route 53 zone에 `ai.internal` A record를 만들고 서버 인증서의
SAN에도 같은 이름을 포함한다. 인터넷 egress를 닫는 환경은 SSM 계열, ECR API/Docker,
Secrets Manager, CloudWatch Logs interface endpoint와 S3 gateway endpoint를 준비한다.
필요하면 KMS·STS endpoint를 추가한다.

업무 Compose의 `app` network는 Nginx↔Spring 전용 internal bridge이고 Spring만 별도 `egress`
bridge를 통해 사설 AI/RDS 주소에 접근한다. AI Compose도 FastAPI↔mTLS gateway 전용 internal
bridge를 사용한다. EC2 보안그룹 egress는 AI 8443, RDS 5432, 필요한 VPC endpoint로 제한한다.

## 배포 파일

- 업무 EC2: `docker compose --env-file .env.aws-app -f compose.aws-app.yaml config`
- AI EC2: `docker compose --env-file .env.aws-ai -f compose.aws-ai.yaml config`
- 예제: `.env.aws-app.example`, `.env.aws-ai.example`

예제의 `INVALID` 값은 기동용 값이 아니다. 이미지에는 ECR immutable digest를 사용한다. 비밀번호·내부 토큰·proxy secret은 Secrets Manager에서 주입하고 Git, AMI, user-data, CloudWatch 로그에 남기지 않는다.

RDS는 `rds.force_ssl=1`, 저장 암호화, 자동 백업, 삭제 방지를 적용한다. Spring runtime/migration 역할과 AI runtime/ingestion 역할을 분리하며 관리자 계정은 애플리케이션에 전달하지 않는다. AWS RDS CA bundle을 읽기 전용 mount하고 `sslmode=verify-full`을 강제한다. 업무 EC2에는 AI client PKCS#12 keystore와 사설 CA truststore를 읽기 전용으로 mount한다. AI EC2에는 서버 인증서·개인키와 client CA를 mount하며 개인키와 비밀번호는 Secrets Manager·SSM 배포 단계에서만 주입한다.

| 용도 | DB 역할 |
|---|---|
| Spring runtime | `alzswell_app` |
| Flyway migration | `alzswell_migrator` |
| AI 검색 runtime | `alzswell_ai_runtime` |
| AI 승인 문서 ingestion | `alzswell_ai_ingestor` |

Compose 기본값과 Secrets Manager에서 주입하는 사용자명은 `docker/create-database-roles.sh`가 생성하는 역할명과 정확히 일치해야 한다. CI의 AWS 배포 계약 검사는 이 값이 어긋나면 실패한다.

## 모델 기동 게이트

AI EC2는 다음 조건을 모두 만족해야 Arctic-ko를 로드한다.

```text
catalog.status == STAGED_APPROVED
deploymentEnvironment == AWS_STAGING
stagedApprovalEnabled == true
revision, model SHA-256, golden-set SHA-256 일치
```

Spring의 AI feature readiness는 FastAPI `/readiness`에서 `service=ai-rag`, 승인 embedding backend와
차원, 같은 status/revision/hash/index/environment, DB·검색 계약을 다시 확인한다. 예상 metadata가
하나라도 비어 있거나 런타임 값이 불일치하거나 AI가 응답하지 않으면 AI 기능은
`DOWN/MISMATCH`가 되지만, 공모전 staging의 core readiness는 유지해 결정론적 템플릿 폴백으로
시연을 계속한다. 승인 모델이 없으면 전체 트래픽도 막아야 하는 배포만
`AI_REQUIRE_FOR_CORE_READINESS=true`를 사용한다. RDS 장애 시 DB 기반 검색은 중단한다.

## 배포 순서

1. Private RDS와 역할을 승인 절차로 생성하고 Flyway를 1회 실행한다.
2. AI EC2에서 모델·카탈로그·골든셋 hash 검증 후 AI compose를 기동한다.
3. `/health` 승인 metadata를 배포 manifest와 대조한다.
4. 업무 compose를 기동하고 `/api/v1/system/readiness`를 확인한다.
5. ALB health path와 WAF rate rule을 적용한다.
6. SMOKE → DEMO → 조회·탐지 → RAG citation E2E를 검증한다.
7. AI 중단·RDS 장애·이전 image digest 복귀 훈련을 실행한다.

ingestion은 [`runbooks/AWS_AI_INGESTION.md`](./runbooks/AWS_AI_INGESTION.md), mTLS 인증서 운영은
[`runbooks/AWS_AI_MTLS.md`](./runbooks/AWS_AI_MTLS.md), 장애 대응은
[`runbooks/AWS_FAILURE_RECOVERY.md`](./runbooks/AWS_FAILURE_RECOVERY.md)를 따른다.
