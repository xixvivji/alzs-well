# AWS staging IaC

`foundation.yaml`은 합성데이터 공모전 staging을 위한 비용절감형 CloudFormation
기반이다. 프론트는 Vercel 기본 도메인을 사용하고, Vercel BFF가 CloudFront 기본
HTTPS 도메인을 통해 AWS API를 호출한다. 실제 생성 전까지는 요금이 발생하지
않는 템플릿·change set 검증만 수행한다.

## 생성 예정 리소스

- 전용 VPC, 2개 AZ의 public·application·database subnet
- CloudFront 기본 HTTPS 도메인, CloudFront에서만 접근 가능한 public ALB
- AWS WAF managed rule, IP rate rule
- private 업무 EC2 1대·AI EC2 1대, SSM 전용 접근, IMDSv2
- 단일 NAT Gateway 1개와 무료 S3 gateway endpoint
- Single-AZ PostgreSQL 17 RDS, TLS 강제, 암호화, 7일 백업, 삭제 방지
- immutable ECR repository 2개·Secrets Manager 3개

NAT Gateway, ALB, WAF, EC2 2대, RDS, EBS, Secrets Manager는 스택 실행 즉시
고정 또는 사용량 비용이 발생한다. 실행 전 AWS Pricing Calculator 결과와 삭제
순서를 별도 승인한다.

## 배포 게이트

1. AWS 루트가 아닌 `alzswell-staging-deployer` 세션을 사용한다.
   CloudFormation execution 역할에는 계정 ID를 렌더링하고 검증한 템플릿 전용
   최소권한 정책만 적용한다.
2. 서울 리전의 AWS 관리 CloudFront origin-facing prefix list ID를 확인한다.
3. `cfn-lint` 및 AWS `validate-template`을 통과한다.
4. change set은 생성만 하고 자동 실행하지 않는다.
5. 생성·교체·삭제 리소스와 월 예상 비용을 검토한 후에만 실행한다.

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
AWS CLI `aws login`으로 생성된 루트 세션은 역할을 AssumeRole할 수 없으므로,
Identity Center 사용자 세션 또는 별도 비루트 주체가 필요하다. 장기 access key를
새로 발급해 우회하지 않는다.

별도 도메인과 ACM 인증서는 필요하지 않다. CloudFront 기본 인증서로 외부 HTTPS를
종단하고 ALB는 AWS 관리 CloudFront origin-facing 네트워크에서 오는 HTTP만
허용한다. EC2는 인프라 기반만 준비하며, 실제 서비스는 ECR image digest·mTLS
인증서·DB 역할 생성·Secrets 주입을 완료한 후 SSM으로 기동한다.
최초 bootstrap 동안에만 AI EC2 역할이 backend·AI ECR 저장소에 이미지를 게시할
수 있다. 검증된 `develop` 커밋을 AMD64로 빌드하고 digest를 기록한 뒤
`DatabaseBootstrapEnabled=false`로 갱신하여 이미지 게시·DB bootstrap·ingestion
권한을 함께 제거한다. 상시 런타임 역할에는 ECR push 권한을 부여하지 않는다.
App·AI mTLS 개인키는 각각 `/alzs-well-staging/tls-app`,
`/alzs-well-staging/tls-ai` 비밀에 분리하며 상대 인스턴스의 개인키를 읽을 수 없다.
컨테이너 로그는 `/alzs-well-staging/app`, `/alzs-well-staging/ai` CloudWatch
로그 그룹으로 보내고 14일 뒤 만료한다.
인스턴스 조작은 루트가 아니라 `alzs-well-staging-operator` 단기 세션으로 수행한다.
운영 역할의 `SendCommand` 대상은 `Project=alzs-well`, `Environment=staging` 태그가
모두 일치하는 EC2와 AWS 관리 `AWS-RunShellScript` 문서로 제한하며 Secrets Manager
직접 읽기 권한은 두지 않는다.

## 운영 종료와 수동 철거

- 서비스 유지 기한: **2026-09-11 23:59 KST**
- 자동 삭제는 구성하지 않는다. 소유자가 기한 이후 수동으로 철거한다.
- 먼저 RDS 삭제 보호와 ALB 삭제 보호를 해제한 변경을 적용한다.
- 스택 삭제 후 NAT Gateway·EIP·ALB·CloudFront·WAF·EC2·RDS가 남지 않았는지
  확인한다.
- 최종 RDS snapshot, ECR image와 배포 secret은 제출 증적 보존 필요성을 확인한
  후 별도로 삭제한다. 이 리소스들은 데이터 손실 방지를 위해 stack 삭제 시
  `Retain` 또는 snapshot 정책을 사용하므로 반드시 별도로 재확인한다.
