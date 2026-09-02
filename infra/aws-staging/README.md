# AWS staging IaC

`foundation.yaml`은 합성데이터 공모전 staging을 위한 비용절감형 CloudFormation
기반이다. 프론트는 Vercel 기본 도메인을 사용하고, Vercel BFF가 CloudFront 기본
HTTPS 도메인을 통해 AWS API를 호출한다. 2026-09-01 `alzs-well-staging` 스택에
실제 적용했으며, 배포 전에는 템플릿·change set 검증만 수행한다.

## 관리 리소스

- 전용 VPC, 2개 AZ의 public·application·database subnet
- CloudFront 기본 HTTPS 도메인, CloudFront에서만 접근 가능한 public ALB
- AWS WAF managed rule, IP rate rule
- private 업무 EC2 1대·AI EC2 1대, SSM 전용 접근, IMDSv2
- 단일 NAT Gateway 1개와 무료 S3 gateway endpoint
- Single-AZ PostgreSQL 17 RDS, TLS 강제, `alias/aws/rds` 암호화, 일일 자동 백업 1일 보존
- immutable ECR repository 2개·용도별 Secrets Manager 비밀

NAT Gateway, ALB, WAF, EC2 2대, RDS, EBS, Secrets Manager는 현재 staging에서
비용이 발생한다. 2026-09-11 23:59 KST까지 유지한 뒤 수동 철거하며, 재배포 때도
AWS Pricing Calculator 결과와 삭제 순서를 별도 승인한다.

## 배포 게이트

1. AWS 루트가 아닌 `alzswell-staging-deployer` 세션을 사용한다.
   CloudFormation execution 역할에는 계정 ID를 렌더링하고 검증한 템플릿 전용
   최소권한 정책만 적용한다.
2. 서울 리전의 AWS 관리 CloudFront origin-facing prefix list ID를 확인한다.
3. `cfn-lint` 및 AWS `validate-template`을 통과한다.
4. change set을 먼저 생성하고 교체·삭제 여부를 검토한다.
5. 생성·교체·삭제 리소스와 월 예상 비용을 승인한 후에만 실행한다.
6. 초기 생성·데이터 적재·리허설 성공 후 `DatabaseDeletionProtectionEnabled=true`,
   `AlbDeletionProtectionEnabled=true`로 갱신하고, 최종 철거 전에는 다시
   `false`로 변경한다.

## 검증 명령

```bash
uvx cfn-lint infra/aws-staging/foundation.yaml
aws cloudformation validate-template \
  --region ap-northeast-2 \
  --template-body file://infra/aws-staging/foundation.yaml

rendered_policy="$(mktemp)"
infra/aws-staging/render-cfn-execution-policy.sh 123456789012 > "$rendered_policy"
aws accessanalyzer validate-policy \
  --region ap-northeast-2 \
  --policy-document "file://$rendered_policy" \
  --policy-type IDENTITY_POLICY
rm -f "$rendered_policy"
```

CloudFormation execution 역할의 사전검토 정책은
`cfn-execution-policy.json`, 12일 운영비 산정은 `COST_ESTIMATE.md`를 기준으로
한다. 정책 원본의 `__AWS_ACCOUNT_ID__`는 ARN의 계정 구간이므로 IAM 정책 변수로
사용할 수 없다. `render-cfn-execution-policy.sh`로 실제 12자리 계정 ID를 렌더링하고
IAM Access Analyzer 검증을 통과한 결과만 역할에 적용한다. 최초 ALB 생성에 필요한
서비스 연결 역할 권한은 `elasticloadbalancing.amazonaws.com`으로 제한한다.
Secrets Manager의 `GetRandomPassword`는 리소스 수준 권한을 지원하지 않으므로
`Resource: "*"`를 사용하되 `ap-northeast-2` 요청으로 제한한다. 생성·수정·삭제는
계속 `/alzs-well-staging/*` 이름의 비밀에만 허용한다.
RDS 파라미터 그룹 생성 시 CloudFormation이 엔진 기본값을 비교하므로
`DescribeEngineDefaultParameters` 읽기 권한도 같은 서울 리전 조건 안에서 허용한다.
ECR·배포 비밀·로그 그룹은 정상 스택 삭제 시 보존하지만 최초 생성 실패 롤백에서는
자동 제거되도록 `RetainExceptOnCreate`를 사용한다.
CloudWatch Logs의 `DescribeLogGroups`도 리소스 수준 권한을 지원하지 않으므로
별도 읽기 문장에서 서울 리전 요청만 허용한다.
AWS CLI `aws login`으로 생성된 루트 세션은 역할을 AssumeRole할 수 없으므로,
Identity Center 사용자 세션 또는 별도 비루트 주체가 필요하다. 이 staging은
Organizations 활성화 시 무료 플랜 크레딧이 만료되는 계정 제약 때문에 Identity
Center를 활성화하지 않았다. 대신 `alzs-well-staging-cli` IAM 사용자의 콘솔 자격과
AWS 관리형 `SignInLocalDevelopmentAccess`를 사용해 단기 자격을 발급하며, IAM
Access Key는 만들지 않는다. 해당 사용자는 두 staging 역할의 `sts:AssumeRole`
외에는 AWS 리소스를 직접 조작할 수 없다.

```bash
aws login --remote --profile alzswell-cli --region ap-northeast-2
aws sts get-caller-identity --profile alzswell-cli
aws sts get-caller-identity --profile alzswell-operator
aws sts get-caller-identity --profile alzswell-staging
```

`alzswell-operator`와 `alzswell-staging`의 `source_profile`은 모두
`alzswell-cli`다. `default`에는 루트 `login_session`을 두지 않는다. 단기 세션
만료 시에만 `aws login`을 다시 수행한다.

별도 도메인과 ACM 인증서는 필요하지 않다. CloudFront 기본 인증서로 외부 HTTPS를
종단하고 ALB는 AWS 관리 CloudFront origin-facing 네트워크에서 오는 HTTP만
허용한다. EC2는 인프라 기반만 준비하며, 실제 서비스는 ECR image digest·mTLS
인증서·DB 역할 생성·Secrets 주입을 완료한 후 SSM으로 기동한다.
최초 bootstrap 동안에만 AI EC2 역할이 backend·AI ECR 저장소에 이미지를 게시할
수 있다. 현재는 검증된 AMD64 digest를 기록하고 `DatabaseBootstrapEnabled=false`로
갱신해 이미지 게시·DB bootstrap·ingestion 정책을 제거했다. 상시 런타임 역할에는
ECR push 권한을 부여하지 않는다.
App·AI mTLS 개인키는 각각 `/alzs-well-staging/tls-app`,
`/alzs-well-staging/tls-ai` 비밀에 분리하며 상대 인스턴스의 개인키를 읽을 수 없다.
컨테이너 로그는 `/alzs-well-staging/app`, `/alzs-well-staging/ai` CloudWatch
로그 그룹으로 보내고 14일 뒤 만료한다.
인스턴스 조작은 루트가 아니라 `alzs-well-staging-operator` 단기 세션으로 수행한다.
운영 역할의 `SendCommand` 대상은 `Project=alzs-well`, `Environment=staging` 태그가
모두 일치하는 EC2와 AWS 관리 `AWS-RunShellScript` 문서로 제한하며 Secrets Manager
직접 읽기 권한은 두지 않는다.

공개 합성 회원 로그인을 활성화할 때는 평문 비밀번호를 저장하지 않고
`/alzs-well-staging/synthetic-member-password-hash`에 BCrypt hash만 보관한다.
`deploy-app-host.sh`에 immutable `BACKEND_IMAGE`·`NGINX_IMAGE` digest와
`SYNTHETIC_MEMBER_AUTH_ENABLED=true`를 전달하면 최신 앱 기동 후 `PUBLIC` fixture를
재생하고 `demo001`~`demo300`, `staff001`~`staff005`, `admin001`~`admin002`를
멱등 프로비저닝한다. 실행이 끝나면 고객·직원·관리자 각각의 로그인과 역할 간 403을
확인해야 한다.
고객 포털의 한 화면은 여러 읽기 API를 조합하므로 gateway 읽기 한도는
network key와 capability key 각각 `300r/m`, burst `80`으로 운영한다. 로그인·변경
요청 한도는 기존의 낮은 별도 bucket을 유지하며 WAF IP rate rule도 그대로 적용한다.
일반 앱 재배포에는 전체 `DatabaseBootstrapEnabled`를 다시 켜지 않는다.
`AppMigrationDeploymentEnabled=true`로 migration DB 비밀 읽기만 잠시 허용하고,
배포와 Flyway 완료 직후 `false`로 되돌려 런타임 역할에서 해당 정책을 제거한다.

## 운영 종료와 수동 철거

- 서비스 유지 기한: **2026-09-11 23:59 KST**
- 자동 삭제는 구성하지 않는다. 소유자가 기한 이후 수동으로 철거한다.
- 먼저 RDS 삭제 보호와 ALB 삭제 보호를 해제한 변경을 적용한다.
- 스택 삭제 후 NAT Gateway·EIP·ALB·CloudFront·WAF·EC2·RDS가 남지 않았는지
  확인한다.
- 최종 RDS snapshot, ECR image와 배포 secret은 제출 증적 보존 필요성을 확인한
  후 별도로 삭제한다. 이 리소스들은 데이터 손실 방지를 위해 stack 삭제 시
  `Retain` 또는 snapshot 정책을 사용하므로 반드시 별도로 재확인한다.
