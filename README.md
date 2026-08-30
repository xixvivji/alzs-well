# ALZ's well

금융생활 변화 조기알림 및 행원 보호업무 코파일럿 프로젝트다.

개발 브랜치와 커밋 규칙은 [`CONTRIBUTING.md`](./CONTRIBUTING.md)를 따른다.

## 현재 구현 상태

- Spring Boot 3.5·Java 21·PostgreSQL/Flyway 기반 업무 API 카탈로그 278개를 관리하며, 코드 기준 operation은 234개다.
- 합성 데이터 적재, 변화 탐지, 고객 알림, 직원 사건 검토, 동의·신뢰연락인·감사·정책 관리가 구현돼 있다.
- FastAPI RAG는 승인 문서 ingestion, pgvector hybrid 검색, Spring citation 재검증과 결정론적 폴백을 제공한다.
- Arctic-ko는 `STAGED_APPROVED`이며 AWS staging에서 승인값·revision·artifact/golden-set hash가 모두 일치할 때만 로드한다. 기본 embedding은 계속 Hash다.
- 최종 staging은 업무 EC2 + AI EC2 + Private RDS다. 로컬 개발은 단일 Docker Compose를 사용한다.
- 직원 사건 화면은 ChatGPT 로그인과 서버측 user allowlist를 통과한 사용자만 실제 합성 사건큐를 조회한다.
- Next.js 금융 포털은 고객·행원·관리자 채널을 분리하고, 구현 234개·계획 23개·외부 참고 22개 operation을 생성 카탈로그로 검증한다. 고객 안심 보호센터는 알림·AI 의향서·장기 변화·감사이력을 한 화면에 묶는다. AI 의향서 현재 상태, 사건 메모·후속관리, 시스템·AI 폴백, 카드·예금·대출·투자·외환·연금·신탁·동의관리, 고객 프로필·접근성·신뢰연락처·이의신청 화면도 실제 API에 연결했다. 공개 화면은 capability 범위의 합성데이터 API만 실행하고 운영 고객정보 조회는 사설 Bearer 인증을 요구한다.

```bash
cd backend && ./gradlew check
cd ../frontend && npm test
cd ../ai-service && uv run pytest
```

발표 전 정상·주의·오탐 3개 흐름과 AI citation·장애 폴백을 한 번에 확인한다.

```bash
python3 scripts/run_demo_rehearsal.py
```

상세 체크포인트와 정상·주의·오탐 합성 데이터 계약은 [`docs/DEMO_REHEARSAL.md`](./docs/DEMO_REHEARSAL.md)를 따른다.

AWS 구성은 [`docs/AWS_BACKEND_DEPLOYMENT.md`](./docs/AWS_BACKEND_DEPLOYMENT.md)를 따른다. 실제 고객정보·외부 금융사 호출·자동 거래차단·가족 통지는 금지한다.

## 현행 문서

프로젝트의 권위 문서와 운영 진입점은 다음과 같다.

1. 제품·기술 최상위 기준: [`ALZS_WELL_PROJECT_SSOT.md`](./ALZS_WELL_PROJECT_SSOT.md)
2. 백엔드 API 계약: [`docs/FINAL_BACKEND_API_SPEC.md`](./docs/FINAL_BACKEND_API_SPEC.md)
3. 프로젝트 진입점: [`README.md`](./README.md)
4. 백엔드 실행·검증: [`backend/README.md`](./backend/README.md)
5. CI·보안 품질 게이트: [`docs/CI_SECURITY_GUIDE.md`](./docs/CI_SECURITY_GUIDE.md)
6. 후속 백엔드 개발 인수인계: [`docs/BACKEND_DEVELOPER_HANDOFF.md`](./docs/BACKEND_DEVELOPER_HANDOFF.md)
7. GitFlow·커밋·PR 규칙: [`CONTRIBUTING.md`](./CONTRIBUTING.md)
8. AWS 배포 기준: [`docs/AWS_BACKEND_DEPLOYMENT.md`](./docs/AWS_BACKEND_DEPLOYMENT.md)
9. AI ingestion·검색: [`ai-service/README.md`](./ai-service/README.md)
10. 발표용 전체 리허설: [`docs/DEMO_REHEARSAL.md`](./docs/DEMO_REHEARSAL.md)

충돌 시 `최신 대회 공식 공지 → 최종 SSOT → 최종 API 명세 → 실제 구현과 테스트` 순서로 판단하고, 차이가 생기면 문서와 구현을 같은 변경에서 함께 갱신한다.

레거시 보고서·렌더·구 API 명세는 삭제하지 않고 `archive/legacy-docs/2026-08-14/`에 보존한다. archive 자료는 의사결정 근거가 아니라 이력 확인에만 사용한다.

## 코드

- Java 백엔드: [`backend/`](./backend/)
- 표시 이름: `ALZ's well`
- 기술 식별자: `alzs-well`, `alzs_well`, `com.alzswell`
