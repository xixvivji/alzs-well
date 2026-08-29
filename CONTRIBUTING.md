# 기여 및 GitFlow 규칙

이 저장소는 `main`과 `develop`을 장기 유지하는 GitFlow를 사용한다.

## 브랜치 흐름

- `main`: 배포 가능한 릴리스만 유지한다.
- `develop`: 다음 릴리스에 포함할 변경을 통합한다.
- `feature/<주제>`: `develop`에서 분기하고 PR을 통해 `develop`으로 병합한다.
- `release/<버전>`: `develop`에서 분기해 릴리스를 검증한 뒤 `main`과 `develop`에 병합한다.
- `hotfix/<주제>`: `main`에서 분기해 긴급 수정 후 `main`과 `develop`에 병합한다.

개인명이나 도구명 접두사는 사용하지 않는다. 작업 브랜치는 영문 kebab-case로 짓고, 병합된 단기 브랜치는 원격과 로컬에서 삭제한다.

## 커밋과 PR

커밋 제목과 PR 제목은 한국어로 작성하고 다음 형식을 사용한다.

```text
feat : 보안 및 검증 강화
fix : 직원 인증 오류 수정
docs : 배포 문서 보완
test : 인증 회귀 테스트 추가
refactor : 인증 필터 구조 정리
chore : 개발 환경 설정 정리
```

한 커밋에는 하나의 논리적 변경을 담는다. PR은 CI, 정적 분석, 보안 검사를 모두 통과한 뒤 병합한다.
