# ALZ's well

금융생활 변화 조기알림 및 행원 보호업무 코파일럿 프로젝트다.

## 현행 문서

프로젝트에서 유지·갱신하는 문서는 다음 다섯 개뿐이다.

1. 제품·기술 최상위 기준: [`ALZS_WELL_PROJECT_SSOT.md`](./ALZS_WELL_PROJECT_SSOT.md)
2. 백엔드 API 계약: [`docs/FINAL_BACKEND_API_SPEC.md`](./docs/FINAL_BACKEND_API_SPEC.md)
3. 프로젝트 진입점: [`README.md`](./README.md)
4. 백엔드 실행·검증: [`backend/README.md`](./backend/README.md)
5. CI·보안 품질 게이트: [`docs/CI_SECURITY_GUIDE.md`](./docs/CI_SECURITY_GUIDE.md)

충돌 시 `최신 대회 공식 공지 → 최종 SSOT → 최종 API 명세 → 실제 구현과 테스트` 순서로 판단하고, 차이가 생기면 문서와 구현을 같은 변경에서 함께 갱신한다.

레거시 보고서·렌더·구 API 명세는 삭제하지 않고 `archive/legacy-docs/2026-08-14/`에 보존한다. archive 자료는 의사결정 근거가 아니라 이력 확인에만 사용한다.

## 코드

- Java 백엔드: [`backend/`](./backend/)
- 표시 이름: `ALZ's well`
- 기술 식별자: `alzs-well`, `alzs_well`, `com.alzswell`
