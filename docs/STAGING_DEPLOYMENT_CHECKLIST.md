# Staging 배포 체크리스트

이 문서는 `Vercel Next.js + AWS 업무 EC2 1대 + AI EC2 1대 + Private RDS`
공모전 staging의 실제 배포 상태를 기록한다. 합성데이터만 사용하며 실제
금융거래를 실행하지 않는다.

## 2026-08-31 사전점검

| 영역 | 상태 | 다음 게이트 |
|---|---|---|
| 코드·CI | 완료 | `main`·`develop` 동일 트리, CI·CodeQL·Gitleaks 통과 |
| Vercel 프로젝트 | 생성 | `alzs-well` 프로젝트 연결, GitHub App 저장소 권한 필요 |
| Vercel 환경변수 | 미등록 | AWS HTTPS origin과 공유 비밀값 확정 후 Preview·Production 분리 등록 |
| AWS 리전 | 선택 | `ap-northeast-2` |
| AWS 배포 주체 | 차단 | 루트 세션 사용 금지, Identity Center 또는 전용 AssumeRole 필요 |
| AWS IaC | 미구현 | 실제 VPC·ALB·WAF·EC2·RDS·ECR·Secrets 생성 스택 필요 |
| AWS ALZ's well 리소스 | 미생성 | 기존 다른 서비스의 RDS를 재사용하지 않음 |
| 로컬 이미지 빌드 | 대기 | Docker 엔진 기동 후 immutable digest 생성·스캔 |

## 1. 배포 권한 게이트

- [ ] AWS 루트 MFA·복구 수단을 확인하고 일상 배포에서 제외한다.
- [ ] `alzswell-staging-deployer` 역할 또는 Identity Center permission set을 승인한다.
- [ ] 배포 역할과 EC2 instance profile, Spring·AI·DB runtime 역할을 분리한다.
- [ ] GitHub/Vercel/AWS 배포는 장기 access key 대신 OIDC 또는 단기 세션을 사용한다.

## 2. IaC 및 비용 게이트

- [ ] 전용 VPC와 2개 AZ의 public·private subnet을 정의한다.
- [ ] HTTPS ALB, WAF, 업무/AI EC2 보안 그룹, Private RDS를 정의한다.
- [ ] ECR immutable tag, Secrets Manager, SSM instance profile, 로그·백업·삭제 방지를 정의한다.
- [ ] plan/change set의 월 예상 비용과 NAT Gateway·VPC Endpoint·ALB·RDS 고정 비용을 승인한다.
- [ ] IaC 적용 전 public IP, `0.0.0.0/0`, 원본 비밀값, 데이터베이스 삭제 위험을 검토한다.

## 3. 이미지·데이터 게이트

- [ ] Spring Boot·AI runtime image를 빌드하고 취약점 스캔을 통과한다.
- [ ] ECR digest를 `compose.aws-app.yaml`·`compose.aws-ai.yaml`에 고정한다.
- [ ] Flyway를 runtime과 분리된 migration 역할로 1회 실행한다.
- [ ] 승인 문서·Arctic-ko artifact·golden-set hash를 반입 증적과 대조한다.

## 4. Vercel 게이트

- [ ] Vercel GitHub App에 `xixvivji/alzs-well` 저장소 권한을 부여한다.
- [ ] Root Directory를 `frontend`로 설정한다.
- [ ] Preview·Production 서버 환경변수를 분리하고 `NEXT_PUBLIC_` 비밀값을 금지한다.
- [ ] WAF rate rule, Bot Protection, Preview Deployment Protection을 적용한다.
- [ ] Vercel BFF에서만 AWS HTTPS origin을 호출하는지 확인한다.

## 5. 배포 후 증적

- [ ] 정상·주의·오탐 3개 시나리오를 고객→행원 폐루프로 실행한다.
- [ ] citation·문서 hash·승인 상태를 확인한다.
- [ ] AI 중단 시 core readiness와 결정론적 폴백을 확인한다.
- [ ] RDS 장애, 이전 image digest 롤백, 백업 복원을 훈련한다.
- [ ] 배포 digest·환경·시간·실행자·결과를 변조 방지 감사증적에 연결한다.
