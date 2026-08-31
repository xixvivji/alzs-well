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
   CloudFormation execution 역할은 아직 권한이 비어 있으며, 템플릿 전용
   최소권한 검토가 끝나기 전에는 스택을 실행할 수 없다.
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
```

별도 도메인과 ACM 인증서는 필요하지 않다. CloudFront 기본 인증서로 외부 HTTPS를
종단하고 ALB는 AWS 관리 CloudFront origin-facing 네트워크에서 오는 HTTP만
허용한다. EC2는 인프라 기반만 준비하며, 실제 서비스는 ECR image digest·mTLS
인증서·DB 역할 생성·Secrets 주입을 완료한 후 SSM으로 기동한다.

## 운영 종료와 수동 철거

- 서비스 유지 기한: **2026-09-11 23:59 KST**
- 자동 삭제는 구성하지 않는다. 소유자가 기한 이후 수동으로 철거한다.
- 먼저 RDS 삭제 보호와 ALB 삭제 보호를 해제한 변경을 적용한다.
- 스택 삭제 후 NAT Gateway·EIP·ALB·CloudFront·WAF·EC2·RDS가 남지 않았는지
  확인한다.
- 최종 RDS snapshot과 ECR image는 제출 증적 보존 필요성을 확인한 후 별도로
  삭제한다. 수동 snapshot과 ECR repository는 스택 상태와 별도로 재확인한다.
