# AWS staging IaC

`foundation.yaml`은 합성데이터 공모전 staging을 위한 비용절감형 CloudFormation
기반이다. 실제 생성 전까지는 요금이 발생하지 않는 템플릿·change set
검증만 수행한다.

## 생성 예정 리소스

- 전용 VPC, 2개 AZ의 public·application·database subnet
- public ALB, AWS WAF managed rule, IP rate rule
- private 업무 EC2 1대·AI EC2 1대, SSM 전용 접근, IMDSv2
- 단일 NAT Gateway 1개와 무료 S3 gateway endpoint
- Single-AZ PostgreSQL 17 RDS, TLS 강제, 암호화, 7일 백업, 삭제 방지
- immutable ECR repository 2개·Secrets Manager 3개

NAT Gateway, ALB, WAF, EC2 2대, RDS, EBS, Secrets Manager는 스택 실행 즉시
고정 또는 사용량 비용이 발생한다. 실행 전 AWS Pricing Calculator 결과와 삭제
순서를 별도 승인한다.

## 배포 게이트

1. AWS 루트가 아닌 `alzswell-staging-deployer` 세션을 사용한다.
   CloudFormation execution 역할은 아직 권한이 비어 있으며, 템플릿 전용
   최소권한 검토가 끝나기 전에는 스택을 실행할 수 없다.
2. ACM 인증서와 소유 도메인을 준비한다. ALB DNS에 직접 발급한 인증서는
   사용할 수 없다.
3. `cfn-lint` 및 AWS `validate-template`을 통과한다.
4. change set은 생성만 하고 자동 실행하지 않는다.
5. 생성·교체·삭제 리소스와 월 예상 비용을 검토한 후에만 실행한다.

## 검증 명령

```bash
uvx cfn-lint infra/aws-staging/foundation.yaml
aws cloudformation validate-template \
  --region ap-northeast-2 \
  --template-body file://infra/aws-staging/foundation.yaml
```

`CertificateArn`은 서울 리전에서 발급·검증된 ACM 인증서 ARN을 사용한다.
EC2는 인프라 기반만 준비하며, 실제 서비스는 ECR image digest·mTLS 인증서·DB
역할 생성·Secrets 주입을 완료한 후 SSM으로 기동한다.
