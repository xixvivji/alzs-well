# ALZ's well 지식 코퍼스

이 디렉터리는 폐쇄망 RAG와 서비스 정책 구현에 사용하는 원문 및 자체 정책을 버전 관리한다. 원문과 정책은 함께 검색할 수 있지만, 답변과 인용에서는 권위 수준을 반드시 구분한다.

## 구성

- `official-source/`: 외부 기관이 공개한 공식 원문 보존본 93개와 SHA-256 목록
- `internal-policy/`: ALZ's well이 자체 작성한 서비스·보안·RAG 정책 13종
- `manifests/`: 문서별 승인·버전·출처·효력·이용조건 메타데이터
- `evaluation/`: 개인정보가 없는 합성 RAG 평가 데이터(후속 구축)
- `derived/`: 추출 텍스트·청크·임베딩·검색 인덱스·실행 리포트(버전 관리 제외)

## 권위 구분

- 공식 원문은 `OFFICIAL_EXTERNAL`로 분류한다.
- 자체 정책은 `INTERNAL_POLICY`로 분류하며 법령·감독규정·공식 안내로 표현하지 않는다.
- 공식 원문도 승인·최신성·효력기간 검토 전에는 RAG 검색 대상으로 사용하지 않는다.
- FastAPI는 승인 상태를 변경하지 않고 승인된 manifest만 소비한다. 최종 ACL과 효력 판정은 Spring Boot가 담당한다.
- manifest와 citation의 공용 규칙은 `contracts/knowledge/`를 단일 기준으로 사용한다.

## 원본 보존과 Git LFS

`official-source`의 PDF·HWP·HWPX는 Git LFS로 관리한다. 저장소를 처음 받은 팀원은 Git LFS 설치 후 다음 명령으로 원본을 내려받는다.

```bash
git lfs install
git lfs pull
```

HTML·Markdown·체크섬·manifest·합성 평가 데이터는 일반 Git으로 관리한다. 공식 HTML에 포함된 공개 웹 자격증명은 `REDACTED_SOURCE_CREDENTIAL`로 치환하고 체크섬을 다시 생성한다. 모델 파일, 벡터, DB 파일, 실행 로그와 `derived/` 산출물은 커밋하지 않는다.

## 무결성 확인

공식 원문 93개의 체크섬은 다음 명령으로 검증한다.

```bash
cd knowledge/official-source
shasum -a 256 -c SHA256SUMS.txt
```

원본 파일은 직접 수정하지 않는다. 갱신본은 새 버전과 해시로 추가하고, 기존 버전의 대체 관계는 manifest에 기록한다.
