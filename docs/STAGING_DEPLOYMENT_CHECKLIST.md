# Staging 배포 체크리스트

이 문서는 `Vercel Next.js + CloudFront 기본 HTTPS 도메인 + AWS 업무 EC2 1대 + AI EC2 1대 + Private RDS`
공모전 staging의 실제 배포 상태를 기록한다. 합성데이터만 사용하며 실제
금융거래를 실행하지 않는다.

서비스 유지 기한은 **2026-09-11 23:59 KST**이며 자동 삭제하지 않는다. 기한
이후 소유자가 아래 순서로 수동 철거한다.

## 2026-09-01 배포 현황

| 영역 | 상태 | 다음 게이트 |
|---|---|---|
| 코드·CI | 완료 | PR #130 UI head `1e666aa`, CI·CodeQL·Gitleaks 전체 통과. 보호 규칙상 독립 승인 전 병합 차단 |
| Vercel 프로젝트 | 운영 배포 | 고객 `https://alzs-well.vercel.app`, 직원 `https://alzs-well-staff.vercel.app` |
| Vercel 환경변수 | Production 완료 | AWS origin은 Config, 공유 비밀·직원 bootstrap 토큰은 Secret. 브라우저 공개 변수에 비밀값 없음 |
| AWS 리전 | 선택 | `ap-northeast-2` |
| AWS 배포 주체 | 완료 | 일회용 bootstrap 사용자로 태그 제한 operator 역할을 AssumeRole하고 사용자·키 즉시 삭제 |
| AWS IaC | `UPDATE_COMPLETE` | `alzs-well-staging`, CloudFront·ALB·WAF·EC2 2대·Private RDS 생성 완료 |
| AWS 보호 상태 | 완료 | `DatabaseBootstrapEnabled=false`, ALB·RDS 삭제 방지 활성화 |
| 배포 이미지 | 완료 | backend·AI·gateway AMD64 이미지를 immutable ECR digest로 실행 |

## 1. 배포 권한 게이트

- [ ] AWS 루트 MFA·복구 수단을 확인하고 일상 배포에서 제외한다.
- [x] 초기 배포에 일회용 deployer 주체와 별도 CloudFormation execution 정책을 사용한다.
- [x] CloudFormation execution 역할의 템플릿 전용 정책을 작성하고 Access Analyzer 경고 0건을 확인한다.
- [x] 고위험 IAM 변경을 승인받고 기존 렌더링·검증 execution 정책을 적용한다.
- [x] 최초 ALB 생성용 서비스 연결 역할을 생성하고 배포를 완료한다.
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
- [x] 12일 안전 상한 `$90~105`를 검토하고 사용자 비용 승인을 받은 뒤 change set을 실행한다.
- [x] EC2·RDS public IP 없음, ALB는 CloudFront origin-facing prefix list만 허용, 인증 API 캐시 비활성화를 확인한다.

## 3. 이미지·데이터 게이트

- [x] CI에서 Spring Boot·AI runtime image 빌드와 취약점 스캔을 통과한다.
- [x] AI EC2 bootstrap 기간에 AMD64 이미지를 ECR에 게시하고 검증 뒤 bootstrap·ingestion IAM 정책을 제거한다.
- [x] backend `sha256:d9c80af558fd7ea40610b7cd4f68d044e32db00a8e207df57d2f01438281cb79`, AI `sha256:256be42ce56a874347d72799c722b83147dbef6407843b631823e3ccd0f3af4c`, gateway `sha256:53e6bfd81099eaa3ab9f01153292ef418dcdac73ba001be2879daffee1571b5d`를 고정한다.
- [x] App·AI mTLS 인증서를 분리된 Secrets Manager 비밀에 저장하고 상대 EC2의 개인키를 읽을 수 없게 배치한다.
- [x] Flyway 74개 migration을 분리된 migration 역할로 적용하고 Spring runtime은 제한 역할로 실행한다.
- [x] Arctic-ko revision·artifact·golden-set hash와 `STAGED_APPROVED`, 1024차원, `hybrid-arctic-ko-v1`을 readiness에서 대조한다.
- [x] `DOC-SYN-COPILOT-001`을 Spring governance 등록·게시·AI DB proof 검증 import하고 binding 1:1을 확인한다.

## 4. Vercel 게이트

- [ ] Vercel GitHub App에 `xixvivji/alzs-well` 저장소 권한을 부여한다.
- [x] 같은 `frontend` 빌드를 고객·직원 프로젝트에 독립적으로 배포한다.
- [x] 고객·직원 프론트는 서로 다른 Vercel 기본 `*.vercel.app` 도메인을 사용한다.
- [x] Production의 AWS origin·서버 비밀을 등록하고 `NEXT_PUBLIC_` 비밀값을 금지한다.
- [ ] Preview 환경변수와 Preview Deployment Protection을 별도로 확정한다.
- [x] AWS WAF managed rule과 IP rate rule을 적용한다.
- [x] Vercel BFF만 CloudFront AWS HTTPS origin을 호출하며 고객 capability는 `Secure`·`HttpOnly` cookie로 보관한다.

## 5. 배포 후 증적

- [x] 운영 Vercel→AWS 경로에서 정상·주의·오탐 3개 시나리오를 고객→행원 폐루프로 실행한다.
- [x] 주의 흐름 `RAG_GROUNDED_TEMPLATE`, citation 1건, `fallbackUsed=false`와 문서 proof를 확인한다.
- [x] AI 컨테이너를 실제 중단해 core `READY`·`aiRetrieval=DOWN`·`DETERMINISTIC_TEMPLATE` 폴백을 확인하고, 재기동 뒤 citation 복귀를 확인한다.
- [ ] RDS 장애, 이전 image digest 롤백, 백업 복원을 훈련한다.
- [x] 정상은 사건 미생성·`CLOSED_NORMAL`, 주의는 `GUIDANCE_PLAN_APPROVED`, 오탐은 `CLOSED_FALSE_POSITIVE`이며 외부 실행 0건임을 확인한다.
- [x] 배포 digest·모델 hash·ingestion run·Spring proof·실행 결과를 CloudWatch와 추가 전용 감사 테이블에 남긴다.

### 2026-09-01 운영 증적

- CloudFront: `https://dastgcm2hgbnl.cloudfront.net`
- App EC2: `i-0a156c169fe294152`, private `10.42.10.110`, `t3.small`
- AI EC2: `i-0663b523093db2d19`, private `10.42.11.12`, `m7i-flex.large`
- RDS: `alzs-well-staging-postgres`, PostgreSQL 17, private, 삭제 방지 활성화
- Vercel frontend UI 기준 코드: `07c72e69307429f11d2e8f924bda055a599fc4f4`
- 고객 Vercel production: `dpl_EJSDmxZdF8P1bQFrwNJJvtL9kW6A` → `https://alzs-well.vercel.app`
- 직원 Vercel production: `dpl_7psw8Qq126NCrVhXW2k8ULvHYbRz` → `https://alzs-well-staff.vercel.app`
- 운영 UI 검증: 일반 금융 홈의 조회·이체 사전확인·금융상품 메뉴가 목적 화면을 보존한 로그인으로 연결되고, `/help`는 비로그인 사용자를 공개 시나리오로 분리함을 확인
- 합성 회원 BFF 검증: `demo001` 로그인 후 `auth/me`가 `SYN_V3_PUBLIC_4393bb3d_000001`을 반환하고 계좌·거래·수취인·한도·이체 양식과 의향·알림·기준선·신호·확인 알림 API가 모두 200 응답
- 이체 안전 경계 검증: 모의계산 `SIMULATION_ALLOWED`, 사전검증 `PREVIEW_VALID`, `transferCreated=false`, `authorizationCreated=false`
- 공개 데모 BFF 검증: Vercel→CloudFront→AWS 세션 생성·`FIN_MGMT_AB_001` 적재 성공 후 검증 세션 즉시 폐기
- Spring·AI image 기준 코드: `1c14ab071c35b41054e16c213d4074a2d2946e98` (`backend-1c14ab0`, `ai-1c14ab0`)
- 운영 리허설 결과: 정상 `CLOSED_NORMAL`, 주의 `GUIDANCE_PLAN_APPROVED`+citation 1, 오탐 `CLOSED_FALSE_POSITIVE`
- AI 장애 리허설 결과: core `READY`, AI `DOWN`, 주의 `DETERMINISTIC_TEMPLATE`·citation 0·`fallbackUsed=true`; 복구 후 RAG citation 1
- 두 EC2의 `/root/.docker/config.json` ECR 로그인 파일 제거 완료

## 6. 2026-09-11 23:59 KST 이후 수동 철거

- [ ] 제출·시연 증적을 먼저 내려받는다.
- [ ] RDS와 ALB 삭제 보호를 해제하는 CloudFormation 변경을 적용한다.
- [ ] CloudFormation stack을 삭제하고 완료될 때까지 확인한다.
- [ ] CloudFront·ALB·WAF·NAT Gateway·EIP·EC2·RDS가 남지 않았는지 확인한다.
- [ ] 최종 RDS snapshot과 ECR image의 보존 필요성을 확인하고 불필요하면 삭제한다.
- [ ] Secrets Manager secret과 CloudWatch log group 잔존 여부를 확인한다.
- [ ] Vercel Production 환경변수를 제거하고 프로젝트 보존 여부를 결정한다.
