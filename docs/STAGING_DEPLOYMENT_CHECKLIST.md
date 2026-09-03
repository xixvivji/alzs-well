# Staging 배포 체크리스트

이 문서는 `Vercel Next.js + CloudFront 기본 HTTPS 도메인 + AWS 업무 EC2 1대 + AI EC2 1대 + Private RDS`
공모전 staging의 실제 배포 상태를 기록한다. 합성데이터만 사용하며 실제
금융거래를 실행하지 않는다.

서비스 유지 기한은 **2026-09-11 23:59 KST**이며 자동 삭제하지 않는다. 기한
이후 소유자가 아래 순서로 수동 철거한다.

## 2026-09-03 배포 현황

| 영역 | 상태 | 다음 게이트 |
|---|---|---|
| 코드·CI | 완료 | 공개 저장소 전환 후 PR #165·#166·#168의 CI·SCA·Dependency Review·CodeQL·Gitleaks 전체 통과 |
| Vercel 프로젝트 | 운영 배포 | 고객 `https://alzs-well.vercel.app`, 직원 `https://alzs-well-staff.vercel.app` |
| Vercel 환경변수 | Production 완료 | AWS origin은 Config, 공유 비밀·직원 bootstrap 토큰은 Secret. 브라우저 공개 변수에 비밀값 없음 |
| AWS 리전 | 선택 | `ap-northeast-2` |
| AWS 배포 주체 | 완료 | `alzs-well-staging-cli`의 access key 없는 `aws login` 단기 세션으로 operator·deployer 역할만 AssumeRole |
| AWS IaC | `UPDATE_COMPLETE` | `alzs-well-staging`, CloudFront·ALB·WAF·EC2 2대·Private RDS 생성 완료 |
| AWS 보호 상태 | 완료 | `DatabaseBootstrapEnabled=false`, `AppMigrationDeploymentEnabled=false`, `ImagePublishDeploymentEnabled=false`, ALB·RDS 삭제 방지 활성화 |
| 배포 이미지 | 완료 | backend·AI·gateway AMD64 이미지를 immutable ECR digest로 실행 |

## 1. 배포 권한 게이트

- [ ] AWS 루트 MFA·복구 수단을 확인하고 일상 배포에서 제외한다.
- [x] 초기 배포에 일회용 deployer 주체와 별도 CloudFormation execution 정책을 사용한다.
- [x] CloudFormation execution 역할의 템플릿 전용 정책을 작성하고 Access Analyzer 경고 0건을 확인한다.
- [x] 고위험 IAM 변경을 승인받고 기존 렌더링·검증 execution 정책을 적용한다.
- [x] 최초 ALB 생성용 서비스 연결 역할을 생성하고 배포를 완료한다.
- [x] 초기 배포에서는 일회용 비루트 주체로 deployer AssumeRole을 확인하고 사용자·Access Key를 즉시 삭제했다.
- [x] 후속 운영은 `SignInLocalDevelopmentAccess`가 있는 `alzs-well-staging-cli`로 전환하고 장기 Access Key를 생성하지 않았다.
- [x] 로컬 `default` 루트 로그인 캐시·설정을 제거하고 `alzswell-cli`를 operator·deployer 역할의 source profile로 사용한다.
- [x] 기본 사용자의 EC2 직접 조회가 거부되고 operator 역할에서만 태그 제한 staging 인스턴스 조회가 가능한지 검증했다.
- [x] 배포 역할과 App·AI EC2 instance profile, Spring·AI DB secret 경계를 분리한다.
- [x] 태그가 일치하는 staging EC2에만 SSM 명령을 보낼 수 있는 별도 운영 역할을 정의한다.
- [x] GitHub/Vercel/AWS 배포는 장기 access key 대신 Git 연동 또는 AWS CLI 단기 세션을 사용한다.

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
- [x] backend `sha256:a7d351cf6b49f05389a6fe84ad7119b4bb01179e456d740bfb2ce4c3581bbfc6`, AI `sha256:256be42ce56a874347d72799c722b83147dbef6407843b631823e3ccd0f3af4c`, gateway `sha256:53e6bfd81099eaa3ab9f01153292ef418dcdac73ba001be2879daffee1571b5d`를 고정한다.
- [x] App·AI mTLS 인증서를 분리된 Secrets Manager 비밀에 저장하고 상대 EC2의 개인키를 읽을 수 없게 배치한다.
- [x] Flyway 76개 migration을 분리된 migration 역할로 적용하고 Spring runtime은 제한 역할로 실행한다.
- [x] Arctic-ko revision·artifact·golden-set hash와 `STAGED_APPROVED`, 1024차원, `hybrid-arctic-ko-v1`을 readiness에서 대조한다.
- [x] `DOC-SYN-COPILOT-001`을 Spring governance 등록·게시·AI DB proof 검증 import하고 binding 1:1을 확인한다.

## 4. Vercel 게이트

- [x] `xixvivji/alzs-well` 저장소를 공개 전환하고 고객·직원 Vercel Production의 정상 응답을 확인한다.
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
- Vercel frontend UI 기준 코드: `341fbb9b3e30c68d12bb304365e9f4f310c36797`
- 고객 Vercel production: `dpl_5puVKAeDRKhaiXY7x8xqZznNcLxg` → `https://alzs-well.vercel.app`
- 직원 Vercel production: `dpl_CEyYJSZ5CdEK2Tua1YdMUA7G2Nkz` → `https://alzs-well-staff.vercel.app`
- 운영 UI 검증: 일반 금융 홈의 조회·이체 사전확인·금융상품 메뉴가 목적 화면을 보존한 로그인으로 연결되고, `/help`는 비로그인 사용자를 공개 시나리오로 분리함을 확인
- 합성 회원 BFF 검증: `demo001` 로그인 후 `auth/me`가 `SYN_V3_PUBLIC_4393bb3d_000001`을 반환하고 계좌·거래·수취인·한도·이체 양식과 의향·알림·기준선·신호·확인 알림 API가 모두 200 응답
- 이체 안전 경계 검증: 모의계산 `SIMULATION_ALLOWED`, 사전검증 `PREVIEW_VALID`, `transferCreated=false`, `authorizationCreated=false`
- 공개 데모 BFF 검증: Vercel→CloudFront→AWS 세션 생성·`FIN_MGMT_AB_001` 적재 성공 후 검증 세션 즉시 폐기
- 역할별 금융 포털 검증: `/`, `/banking/accounts`, `/banking/products`, `/staff/operations` HTTP 200 및 `/api/v1/system/health` `UP`
- Spring·AI image 기준 코드: `1c14ab071c35b41054e16c213d4074a2d2946e98` (`backend-1c14ab0`, `ai-1c14ab0`)
- 운영 리허설 결과: 정상 `CLOSED_NORMAL`, 주의 `GUIDANCE_PLAN_APPROVED`+citation 1, 오탐 `CLOSED_FALSE_POSITIVE`
- AI 장애 리허설 결과: core `READY`, AI `DOWN`, 주의 `DETERMINISTIC_TEMPLATE`·citation 0·`fallbackUsed=true`; 복구 후 RAG citation 1
- 두 EC2의 `/root/.docker/config.json` ECR 로그인 파일 제거 완료

### 2026-09-02 역할별 재검증

- 고객 `demo001`: 로그인·`auth/me`·계좌 2건·거래 검색 10건·기준선·변화신호·알림 조회가 모두 200이며, `demo002` 고객 범위 조회는 403으로 차단됐다.
- 행원 `staff001~staff005`: 모두 로그인과 사건 큐 조회가 200이며, 현재 배정 사건은 `staff004`에 1건 있다.
- 행원 `staff004`: 사건 상세, 불변 근거 1건, 타임라인 6건, 메모 1건, 후속관리, 금융생활 의향 요약 조회가 모두 200이다. 관리자 규칙 조회는 403으로 차단됐다.
- 관리자 `admin001`, `admin002`: 규칙·정책 버전·알고리즘 버전·기능 플래그·보존정책 조회가 모두 200이다. `AUDIT_READ_ALL` 권한이 없어 전체 감사 조회는 403이며, 행원 사건 큐도 403으로 차단됐다.
- 새 Production 배포 뒤 대표 고객·행원·관리자 흐름을 다시 실행했고, 사용한 합성 세션은 검증 직후 모두 로그아웃했다.
- AWS CloudFormation 스택은 `UPDATE_COMPLETE`, CloudFront health는 200이다. 실제 인프라 변경에는 루트 세션을 사용하지 않고 전용 운영 역할의 단기 세션만 사용한다.

### 2026-09-02 비루트 AWS CLI 전환

- `default` 프로필의 루트 `login_session`과 캐시를 제거했으며, 호출은 `NoCredentials`로 차단된다.
- IAM 사용자 `alzs-well-staging-cli`에는 `SignInLocalDevelopmentAccess`, 암호 변경, 두 staging 역할의 `sts:AssumeRole`만 허용한다.
- `alzswell-cli`는 `aws login` 단기 자격만 사용하며 IAM Access Key는 0개다.
- `alzswell-operator`는 `alzs-well-staging-operator`, `alzswell-staging`은 `alzswell-staging-deployer` 역할로 전환된다.
- 실제 STS에서 기본 사용자·operator·deployer ARN을 확인했고, 기본 사용자의 `ec2:DescribeInstances`는 `UnauthorizedOperation`으로 거부됐다.
- operator로 `alzs-well-staging` 스택 `UPDATE_COMPLETE`와 app·AI EC2 두 대의 `running` 상태를 확인했다.

### 2026-09-03 회원 장기 변화 AI 배포 검증

- PR #165에서 로그인 회원의 합성 거래·기준선으로 30·60·90일 변화 분석을 수행하는 고객 API와 화면을 추가했다.
- PR #166에서 이미지 게시 권한을 DB bootstrap과 분리했고, 배포 완료 후 세 임시 권한을 모두 회수했다.
- PR #168에서 PostgreSQL `numeric` 건수값의 소수 표기(`0.0000`)를 정규화해 장기 변화 분석의 500 오류를 수정했다.
- backend는 코드 `a269168f3c23c3e459b5d49e3714a9128a5e2052`, image `backend-a269168`, digest `sha256:a7d351cf6b49f05389a6fe84ad7119b4bb01179e456d740bfb2ce4c3581bbfc6`로 교체했다.
- Flyway 76개 migration을 검증했고, 공개 합성 fixture는 고객 300명·계좌 600건·거래 72,000건을 재현했다.
- Vercel 고객 BFF에서 `demo001` 로그인·본인 확인·AI 분석·로그아웃이 모두 200이고, `demo002` 분석 접근은 403으로 차단됐다.
- 회원 AI 응답은 `FASTAPI_EWMA_CUSUM`, `fallbackUsed=false`이며 30·60·90일 비교 결과를 모두 반환했다.
- 응답은 `syntheticData=true`, `diagnosisInferred=false`, `financialActionExecuted=false` 안전 경계를 유지한다.
- CloudFront `/api/v1/system/health`는 200·`UP`, 고객 Vercel `/banking/help`는 200을 반환한다.
- CloudFormation은 `UPDATE_COMPLETE`이며 `DatabaseBootstrapEnabled`, `AppMigrationDeploymentEnabled`, `ImagePublishDeploymentEnabled`가 모두 `false`다.
- 권한 회수 후 EC2 SSM 재조회가 `AccessDenied`로 거부되는 것을 확인했다. 실행 digest는 배포 명령 성공 기록으로 보존하며 확인만을 위해 권한을 다시 열지 않는다.

## 6. 2026-09-11 23:59 KST 이후 수동 철거

- [ ] 제출·시연 증적을 먼저 내려받는다.
- [ ] RDS와 ALB 삭제 보호를 해제하는 CloudFormation 변경을 적용한다.
- [ ] CloudFormation stack을 삭제하고 완료될 때까지 확인한다.
- [ ] CloudFront·ALB·WAF·NAT Gateway·EIP·EC2·RDS가 남지 않았는지 확인한다.
- [ ] 최종 RDS snapshot과 ECR image의 보존 필요성을 확인하고 불필요하면 삭제한다.
- [ ] Secrets Manager secret과 CloudWatch log group 잔존 여부를 확인한다.
- [ ] Vercel Production 환경변수를 제거하고 프로젝트 보존 여부를 결정한다.
