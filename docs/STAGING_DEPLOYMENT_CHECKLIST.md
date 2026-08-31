# Staging 배포 체크리스트

이 문서는 `Vercel Next.js + CloudFront 기본 HTTPS 도메인 + AWS 업무 EC2 1대 + AI EC2 1대 + Private RDS`
공모전 staging의 실제 배포 상태를 기록한다. 합성데이터만 사용하며 실제
금융거래를 실행하지 않는다.

서비스 유지 기한은 **2026-09-11 23:59 KST**이며 자동 삭제하지 않는다. 기한
이후 소유자가 아래 순서로 수동 철거한다.

## 2026-08-31 사전점검

| 영역 | 상태 | 다음 게이트 |
|---|---|---|
| 코드·CI | 완료 | `main`·`develop` 동일 트리, CI·CodeQL·Gitleaks 통과 |
| Vercel 프로젝트 | 생성 | `alzs-well` 프로젝트 연결, GitHub App 저장소 권한 필요 |
| Vercel 환경변수 | 미등록 | CloudFront 기본 HTTPS origin과 공유 비밀값 확정 후 Preview·Production 분리 등록 |
| AWS 리전 | 선택 | `ap-northeast-2` |
| AWS 배포 주체 | 사전검증 완료 | 일회용 비루트 bootstrap으로 deployer AssumeRole 확인 후 사용자·키 즉시 삭제 |
| AWS IaC | change set 준비 | `alzs-well-staging-20260831-preflight` 55개 추가, 미실행 `AVAILABLE` 상태 |
| AWS ALZ's well 리소스 | 미생성 | 기존 다른 서비스의 RDS를 재사용하지 않음 |
| 배포 이미지 빌드 | 대기 | CI 스캔 통과 후 AI EC2에서 AMD64로 빌드·ECR digest 고정 |

## 1. 배포 권한 게이트

- [ ] AWS 루트 MFA·복구 수단을 확인하고 일상 배포에서 제외한다.
- [x] `alzswell-staging-deployer`와 별도 CloudFormation execution 역할을 생성한다.
- [x] CloudFormation execution 역할의 템플릿 전용 정책을 작성하고 Access Analyzer 경고 0건을 확인한다.
- [x] 고위험 IAM 변경을 승인받고 기존 렌더링·검증 execution 정책을 적용한다.
- [ ] 최초 ALB 생성용 서비스 연결 역할 권한 보완을 별도 승인 후 적용한다.
- [x] 일회용 비루트 주체로 deployer AssumeRole을 확인하고 사용자·Access Key를 즉시 삭제한다.
- [x] 배포 역할과 App·AI EC2 instance profile, Spring·AI DB secret 경계를 분리한다.
- [x] 태그가 일치하는 staging EC2에만 SSM 명령을 보낼 수 있는 별도 운영 역할을 정의한다.
- [ ] GitHub/Vercel/AWS 배포는 장기 access key 대신 OIDC 또는 단기 세션을 사용한다.

## 2. IaC 및 비용 게이트

- [x] 전용 VPC와 2개 AZ의 public·private subnet을 정의한다.
- [x] CloudFront 기본 HTTPS, ALB, WAF, 업무/AI EC2 보안 그룹, Private RDS를 정의한다.
- [x] ECR immutable tag, Secrets Manager, SSM instance profile, 백업·삭제 방지를 정의한다.
- [x] EC2 상태·RDS CPU·RDS 여유 공간 경보를 정의한다.
- [x] App·AI 컨테이너 CloudWatch 로그 그룹과 14일 보존 기간을 정의한다.
- [ ] 55개 추가 change set과 12일 안전 상한 `$90~105`를 검토하고 실제 실행을 승인한다.
- [ ] IaC 적용 전 public IP, `0.0.0.0/0`, 원본 비밀값, 데이터베이스 삭제 위험을 검토한다.

## 3. 이미지·데이터 게이트

- [x] CI에서 Spring Boot·AI runtime image 빌드와 취약점 스캔을 통과한다.
- [ ] AI EC2의 bootstrap 기간에 동일 커밋을 AMD64로 빌드·ECR에 게시하고 bootstrap 권한을 제거한다.
- [ ] ECR digest를 `compose.aws-app.yaml`·`compose.aws-ai.yaml`에 고정한다.
- [ ] App·AI mTLS 인증서를 생성해 분리된 Secrets Manager 비밀에 저장하고 각 인스턴스에만 배치한다.
- [ ] Flyway를 runtime과 분리된 migration 역할로 1회 실행한다.
- [ ] 승인 문서·Arctic-ko artifact·golden-set hash를 반입 증적과 대조한다.

## 4. Vercel 게이트

- [ ] Vercel GitHub App에 `xixvivji/alzs-well` 저장소 권한을 부여한다.
- [ ] Root Directory를 `frontend`로 설정한다.
- [ ] 프론트 주소는 Vercel 기본 `*.vercel.app` 도메인을 사용한다.
- [ ] Preview·Production 서버 환경변수를 분리하고 `NEXT_PUBLIC_` 비밀값을 금지한다.
- [ ] WAF rate rule, Bot Protection, Preview Deployment Protection을 적용한다.
- [ ] Vercel BFF에서만 AWS HTTPS origin을 호출하는지 확인한다.

## 5. 배포 후 증적

- [ ] 정상·주의·오탐 3개 시나리오를 고객→행원 폐루프로 실행한다.
- [ ] citation·문서 hash·승인 상태를 확인한다.
- [ ] AI 중단 시 core readiness와 결정론적 폴백을 확인한다.
- [ ] RDS 장애, 이전 image digest 롤백, 백업 복원을 훈련한다.
- [ ] 배포 digest·환경·시간·실행자·결과를 변조 방지 감사증적에 연결한다.

## 6. 2026-09-11 23:59 KST 이후 수동 철거

- [ ] 제출·시연 증적을 먼저 내려받는다.
- [ ] RDS와 ALB 삭제 보호를 해제하는 CloudFormation 변경을 적용한다.
- [ ] CloudFormation stack을 삭제하고 완료될 때까지 확인한다.
- [ ] CloudFront·ALB·WAF·NAT Gateway·EIP·EC2·RDS가 남지 않았는지 확인한다.
- [ ] 최종 RDS snapshot과 ECR image의 보존 필요성을 확인하고 불필요하면 삭제한다.
- [ ] Secrets Manager secret과 CloudWatch log group 잔존 여부를 확인한다.
- [ ] Vercel Production 환경변수를 제거하고 프로젝트 보존 여부를 결정한다.
