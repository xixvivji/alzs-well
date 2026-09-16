# 클라우드·인프라 설계와 운영 사례

ALZ's well은 합성 금융 데이터를 사용하는 고객·행원 서비스입니다. 이 문서는 저장소에서 확인할 수 있는 인프라 구현과 배포 기록을 중심으로, 설계 이유·운영 절차·한계를 정리합니다. 합성 데이터 staging의 경험이며 실금융사 운영 경험과 구분합니다.

## 담당 역할

전체 인프라를 담당했습니다. AWS 아키텍처와 CloudFormation 구성, 네트워크·IAM·비밀 관리, 컨테이너 배포 환경, 배포 검증과 운영 종료까지 직접 수행했습니다. 본문에는 각 설계와 운영 절차를 확인할 수 있는 코드·기록을 연결했습니다.

## 지원 직무와 연결되는 경험

뱅크웨어글로벌 [Cloud 인프라 및 DevOps 개발자 공식 공고](https://careers.bankwareglobal.com/jobs/75-26nyeon-habangi-daejolsinib-susichaeyong-cloud-inpeura-mich-devops-gaebalja-sabon)의 주요 업무를 기준으로 연결한 내용입니다. 확인일은 2026-09-16입니다.

| 직무에서 다루는 영역 | 이 프로젝트에서 보여주는 경험 |
|---|---|
| Public Cloud 운영 | AWS IaC, private 네트워크, IAM, Secrets, 배포·운영 종료 |
| Java 애플리케이션 빌드·배포와 CI/CD | Spring Boot·Gradle·Docker, GitHub Actions 품질 게이트, digest 기반 배포 스크립트 |
| 시스템 인터페이스 연계 | 업무 API·AI 서비스·DB 사이의 인증·암호화·권한 경계 설계 |

실제 고객사의 온프레미스 연계는 수행 범위에 포함하지 않습니다. 실행 환경은 EC2·Docker Compose이며 EKS/Kubernetes나 Jenkins 구축 경험으로 표현하지 않습니다.

## 1. 서비스 특성에 맞춘 네트워크와 실행 환경

업무 API와 모델 실행 환경은 필요한 자원과 변경 주기가 다릅니다. 업무 EC2와 AI EC2를 분리하고, 데이터는 private RDS에 저장하는 구성을 사용했습니다. 외부 요청은 Vercel BFF → CloudFront/WAF → ALB → 업무 gateway를 거칩니다.

- **네트워크 경계:** public·application·database subnet을 분리하고, ALB→업무 8080, 업무→AI 8443, 업무·AI→DB 5432를 보안 그룹 참조로 허용합니다.
- **관리 경로:** EC2 public IP와 SSH 접근 대신 SSM을 사용하며 IMDSv2를 요구합니다.
- **전송 구간:** 업무→AI mTLS와 DB TLS 인증서 검증을 적용합니다. CloudFront→ALB는 origin-facing prefix list로 제한한 HTTP이므로 전 구간 TLS라고 표현하지 않습니다.
- **컨테이너 경계:** read-only filesystem, capability 제한, `no-new-privileges`, 쓰기가 필요한 경로의 tmpfs를 구성합니다.

근거: [CloudFormation](../infra/aws-staging/foundation.yaml), [업무 Compose](../backend/compose.aws-app.yaml), [AI Compose](../backend/compose.aws-ai.yaml).

**선택의 대가:** 업무·AI 각 1대와 Single-AZ RDS, NAT Gateway 1개로 staging 비용을 제한했습니다. 2개 AZ에 서브넷이 있어도 서비스 장애 자동 전환이 보장되지는 않습니다. 또한 EC2의 80/443 outbound를 허용하므로 private subnet 자체를 완전한 외부 통신 차단으로 해석하지 않습니다.

## 2. 배포할 때 필요한 권한과 실행 중 권한의 분리

애플리케이션 실행에 필요한 권한과 DB 초기화·마이그레이션·이미지 게시 권한을 분리했습니다. 용도별 Secrets Manager 비밀과 DB 계정을 사용하고, App·AI의 mTLS 개인키도 서로 다른 비밀로 관리합니다.

배포 시에는 `DatabaseBootstrapEnabled`, `AppMigrationDeploymentEnabled`, `ImagePublishDeploymentEnabled` 중 필요한 권한만 열고 작업 후 회수하는 절차를 사용합니다. 2026-09-05 기록에는 세 플래그가 모두 `false`로 돌아간 상태가 남아 있습니다.

DB도 migration, 업무 runtime, AI ingestion, AI runtime 역할을 구분합니다. 런타임이 스키마 변경이나 ingestion 권한을 상시 갖지 않도록 구성과 역할 생성 스크립트를 대조합니다.

근거: [인프라 운영 안내](../infra/aws-staging/README.md), [DB 역할 생성](../backend/docker/create-database-roles.sh), [권한 회수 기록](./RELEASE_V0_1_4.md).

## 3. 배포 재현성과 실패 시 복구

이미지는 ECR digest로 지정하고 배포 스크립트가 비밀값과 인증서를 주입한 뒤 Compose 서비스를 시작합니다. 업무 배포는 readiness를 확인하고, AI는 승인된 모델 revision·artifact hash·golden-set hash 등 배포 계약을 확인합니다.

배포 절차는 다음 순서로 문서화되어 있습니다.

1. CloudFormation lint·템플릿 검증과 change set 검토.
2. 이미지 digest와 모델 승인 자료 확인.
3. 필요한 임시 권한 부여 및 Secrets·인증서 준비.
4. 배포·Flyway 실행 후 readiness·합성 업무 시나리오 검증.
5. 임시 권한 회수와 배포 결과 기록.

[장애 런북](./runbooks/AWS_FAILURE_RECOVERY.md)은 AI·RDS·업무 EC2 장애별 점검과 직전 승인본 복구 절차를 제공합니다. 이미지 복구와 DB downgrade는 같은 작업이 아닙니다. [배포 기록](./RELEASE_V0_1_4.md)도 V77 이후 구 이미지의 스키마 호환성 확인이 필요하다고 명시합니다.

**검증 범위:** 배포 스크립트와 런북이 존재하며 합성 시나리오 실행 기록이 있습니다. 무중단 배포, 자동 롤백, PITR 복구 훈련의 성공이나 RTO·RPO 달성은 이 자료만으로 주장하지 않습니다. CI 자동 검증과 AWS 배포 스크립트를 사용한 운영 절차를 구분하며, 완전 자동 CD로 표현하지 않습니다.

근거: [업무 배포](../infra/aws-staging/deploy-app-host.sh), [AI 배포](../infra/aws-staging/deploy-ai-host.sh), [AWS 배포 기준](./AWS_BACKEND_DEPLOYMENT.md).

## 4. CI에서 기능·보안·배포 구성을 함께 검증

GitHub Actions는 테스트뿐 아니라 배포 구성과 의존성을 함께 검증합니다.

| 검증 영역 | 구성된 검사 |
|---|---|
| Java | 테스트·JaCoCo 기준, SpotBugs/FindSecBugs, 실행 artifact와 이미지 취약점 검사 |
| Python·AI | pytest 커버리지, 검색 품질 평가, uv audit, 모델 런타임 이미지 검사 |
| 프론트 | npm audit, lint, build를 포함한 npm test |
| 인프라·통합 | cfn-lint, IAM 정책 렌더링, Compose 구성 검사·기동·업무 smoke·AI 인용/폴백 검사 |
| 저장소 | CodeQL, Gitleaks, 조건부 Dependency Review |

`CI quality gate`는 필수 작업의 실패·취소·예상치 못한 건너뛰기를 실패로 처리합니다. **설정된 기준과 특정 커밋의 통과 결과는 구분**해야 합니다. PR #182의 검사 통과는 [릴리스 기록](./RELEASE_V0_1_4.md)에 남아 있고, 현재 브랜치 결과는 해당 실행에서 확인해야 합니다.

근거: [CI](../.github/workflows/ci.yml), [CodeQL](../.github/workflows/codeql.yml), [Gitleaks](../.github/workflows/gitleaks.yml), [보안 운영 안내](./CI_SECURITY_GUIDE.md).

## 운영 종료와 비용 관리

staging은 상시 운영 서비스가 아닌 기한이 있는 시연 환경으로 설계했습니다. CloudWatch 로그 보존은 14일, RDS 자동 백업 보존은 1일입니다. [비용 문서](../infra/aws-staging/COST_ESTIMATE.md)는 당시 12일·288시간 가정의 사전 추정이며 실제 청구액이나 최신 가격표가 아닙니다.

2026-09-14 운영 종료 기록에 따르면 Vercel 프로젝트 삭제와 CloudFormation 스택의 `DELETE_COMPLETE`, 잔여 EBS·RDS 스냅샷 점검이 완료되었습니다. 이 요약은 해당 기록을 바탕으로 하며 AWS 계정을 재조회한 결과는 아닙니다.

철거 과정에서는 삭제 보호를 해제하고 보존 리소스 처리 방침을 변경했으며, 사설 DNS 삭제 실패에 필요한 조회 권한을 임시 추가한 뒤 회수했습니다. 스택 밖의 이전 DB 비밀은 복구 기간을 두고 삭제 예약되었습니다. 2026-09-16 기준 영구 삭제 완료로 표현하지 않습니다. 계정 전체의 모든 리소스가 제거되었거나 과금이 없다고 보장하는 기록도 아닙니다.

이 사례에서 확인할 수 있는 운영 범위는 **생성 → 배포 → 검증 → 권한 회수 → 비용·보존 관리 → 철거 확인**입니다.

## 다음 단계

| 현재 한계 | 실서비스 전 검증할 내용 |
|---|---|
| 업무·AI 단일 인스턴스, Single-AZ RDS | 다중 AZ 구성, 장애 전환, 부하·용량 시험 |
| 로그 수집과 복구 런북 중심 | 경보·대시보드·SLO, 실제 복구 훈련과 RTO·RPO 측정 |
| 승인 기반 배포 스크립트 | OIDC와 환경 승인에 기반한 CD, 배포 후 자동 검증·롤백 조건 |
| 합성 데이터와 합성 계정 검증 | 조직 IdP·MFA, 데이터 수명주기, 실제 업무·사용성 검증 |

상세 승인 기준은 [실서비스 준비도](./PRODUCTION_READINESS.md)를 따릅니다. 금융회사 운영환경의 규정 준수나 실고객 서비스 승인을 완료한 프로젝트로 표현하지 않습니다.
