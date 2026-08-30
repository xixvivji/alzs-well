# CI·보안 품질 게이트 운영 가이드

## 자동 검증

`develop` push와 `develop`·`main` 대상 PR에서 다음 검증을 실행한다.

- 백엔드 Java 21 테스트와 JaCoCo 리포트
- SpotBugs와 FindSecBugs 기반 Java 정적·보안 분석
- 전체 라인 커버리지 90% 이상
- Bearer 인증 필터·인증 세션·로컬 인증 어댑터 라인 커버리지 80% 이상
- 프론트 Node.js 22 기준 `npm ci`, high 이상 `npm audit`, lint, build, test
- `uv audit` 기반 Python 기본·개발·모델 런타임 잠금 의존성 검사
- 오프라인 AI model-runtime 이미지 빌드와 Trivy OS·Python high/critical 검사
- Docker Compose 설정 렌더링
- PR에서 새로 도입된 high severity 의존성 검토

테스트와 JaCoCo HTML/XML 결과는 GitHub Actions artifact로 14일 보관한다. PR에서는 전체 90%, 변경 파일 80% 기준의 커버리지 댓글을 갱신한다.
Maven 저장소가 일시적으로 `429 Too Many Requests`를 반환한 경우에만 백엔드 검증을 최대 3회 재시도한다. 코드·테스트·커버리지 실패는 즉시 실패시키며, 선행 Gradle 실패로 리포트가 생성되지 않은 경우 artifact 업로드는 원래 실패 원인을 덮지 않고 경고만 남긴다.

## 의존성 업데이트

GitHub Dependabot vulnerability alerts와 security updates를 활성화했다.

Dependabot은 매주 월요일 다음 생태계를 `develop` 대상으로 확인한다.

- Gradle: `backend/`
- npm: `frontend/`
- pip/uv: `ai-service/`
- GitHub Actions: 저장소 루트

자동 PR 한도는 생태계별 5개이며 커밋 prefix는 `chore`다.

## 보안 분석

- Gitleaks는 PR, `develop`·`main` push, 매주 정기 실행에서 전체 Git 이력을 검사한다.
- SpotBugs/FindSecBugs는 기존 네 개의 필수 상태검사 중 `Backend test and coverage` 안에서 실행되며, 검토된 내부 상수 SQL 조립 등 오탐만 `backend/config/spotbugs-exclude.xml`에 좁게 기록한다.
- npm audit은 high 이상이면 프론트 필수 상태검사를 실패시킨다. 현재 남은 moderate 항목은 배포 런타임이 아닌 Drizzle 개발 도구의 구형 esbuild 경로이며, 무리한 major downgrade 대신 상위 패치를 추적한다.
- `uv audit --locked`는 Linux Python 3.12 기준으로 model-runtime 그룹까지 검사한다. 이어서 `Dockerfile.model-runtime`을 실제 빌드하고 Trivy가 이미지의 OS·Python high/critical 취약점을 차단한다.
- CodeQL은 Java와 JavaScript/TypeScript를 대상으로 실행하며 저장소 변수 `CODEQL_ENABLED=true`로 활성화했다.
- `knowledge/official-source/**`는 RAG 근거 보존용 제3자 공개 원문 사본이며 애플리케이션에서 제공하거나 실행하지 않으므로 JavaScript CodeQL 분석 대상에서 제외한다. 애플리케이션·프록시·테스트 코드는 제외하지 않는다.
- CodeQL 경고 억제·오탐 분류는 근거를 소스와 보안 경고 이력에 함께 기록한 경우만 허용한다. 현재 CSRF 오탐 분류는 쿠키·세션 없이 명시적 Bearer/Capability 헤더로 인증하고, GET의 유일한 쓰기가 append-only 접근 감사인 핸들러에만 적용한다.
- Dependency Review는 저장소 변수 `DEPENDENCY_REVIEW_ENABLED=true`로 활성화했다.
- 공개 저장소의 GitHub Secret Scanning과 Push Protection을 활성화했으며 Gitleaks 전체 이력 검사도 독립된 방어선으로 유지한다.

민감정보는 `.env.example`에도 실제 값을 넣지 않는다. 비밀이 발견되면 파일 삭제만으로 끝내지 말고 해당 자격증명을 먼저 폐기·회전한 뒤 Git 이력 정리 여부를 판단한다.
