# v0.1.4 배포·검증 기록

실행일: 2026-09-05 (UTC). 코드 `3772c7992d935a16fff990525d5f93b52bd2de23`, develop 병합 `5b4d812` (PR #182).

## 배포

- 백엔드와 게이트웨이는 검증된 코드로 빌드한 불변 이미지로 배포했다. 게이트웨이는 OS 패키지 캐시 없이 재빌드했다.
- 기존 AI 서비스·Arctic-ko 모델을 유지했다. AI readiness 재조회 `UP`, 회원 분석 폴백 미사용.
- 앱 배포 명령은 성공했다. 상세 운영 식별자는 공개 문서에 포함하지 않는다.
- 기존 설정은 접근 제한 상태로 보관했다. V77 이후 구 이미지의 스키마 호환성은 별도 확인해야 하며 DB downgrade를 수행하지 않는다.

## PUBLIC v3.1

새 PUBLIC run: 고객 300명·계좌 600개·거래 216,000건, 활성 합성 회원 300명.
합성 탐지 검증은 고객 300명·기대 신호 225·실제 225·오탐 0·미탐 0으로 통과했다.
이는 고정 합성 데이터의 회귀 결과이며 실제 금융 분포의 정확도는 아니다.

## 운영 BFF 검증

- `demo003`: 로그인, 본인·권한 조회, 계좌·거래 검색·금융 요약, AI 변화 분석 200. `fallbackUsed=false`.
- `staff001`: 직원 도메인 로그인·사건 큐 200, 관리자 조회 403.
- `admin001`: 직원 도메인 로그인·규칙·정책·알고리즘·기능 플래그 조회 200.
- 세 역할 모두 토큰 갱신·로그아웃 200, 로그아웃 후 본인 조회 401.
- 고객의 직원·관리자 조회 403.
- `scripts/verify_production_scenarios.py`: 정상 `CLOSED_NORMAL` (사건 미생성), 주의 `GUIDANCE_PLAN_APPROVED` (인용 1, 폴백 없음), 오탐 `CLOSED_FALSE_POSITIVE`. 외부 실행 없음.

첫 점검의 거래 목록 URL은 `/transactions`로 잘못 지정해 404였으며, 실제 프런트 계약인 `/transactions/search`로 재실행해 통과했다. 이번 검증은 API/BFF 경유 검증이며 모든 브라우저 화면·모바일·스크린리더 수동 검증을 의미하지 않는다.

## 권한 회수

CloudFormation `UPDATE_COMPLETE`에서 `DatabaseBootstrapEnabled=false`, `AppMigrationDeploymentEnabled=false`, `ImagePublishDeploymentEnabled=false`를 확인했다. ALB·RDS 삭제 보호는 `true`로 유지했다.

PR #182의 CI·CodeQL·Secret scan·컨테이너 보안 검사는 모두 통과했다. main 릴리스와 Vercel 운영 배포는 해당 릴리스 PR·배포 결과로 별도 추적한다.
