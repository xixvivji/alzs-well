# AWS 장애·롤백 런북

## AI 장애

업무 API는 결정론적 템플릿으로 폴백하지만 AWS AI staging strict readiness는 신규 트래픽을 중단한다. AI 재기동 후 `/health`의 status, revision, artifact/golden hash, index를 배포 manifest와 대조한다. 불일치하면 자동 다운로드하지 않고 직전 승인 digest와 artifact로 롤백한다.

## RDS 장애

Spring readiness를 실패시키고 쓰기·DB 검색을 중단한다. RDS 이벤트, 연결 수, 저장공간, 인증서, security group을 점검한다. 복구/PITR 후 Flyway version, append-only trigger, 활성 탐지정책, 합성 fixture와 SMOKE E2E를 확인한다.

## 업무 EC2 장애

승인 AMI에서 직전 immutable gateway/backend digest로 교체한다. Secrets Manager/SSM 설정을 다시 주입하고 readiness 통과 후 ALB에 등록한다. SSH로 직접 수정하지 않는다.

공통 순서는 `ALB target 제외 → 안전한 지표 확보 → 변경 중지 → 직전 승인본 롤백 → readiness/SMOKE/DEMO/citation·fallback 확인 → 감사기록`이다.
