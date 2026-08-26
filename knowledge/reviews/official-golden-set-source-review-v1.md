# 공식 검색 골든셋 원문 검수 팩 v1

이 문서는 실제 문서 기반 검색 골든셋을 만들기 전에 사용할 5개 원문의 승인 검수 목록이다.
기술 사전검증 통과는 콘텐츠 승인이나 이용권한 승인을 의미하지 않는다. AI ingestion은
각 manifest가 `APPROVED/ACTIVE`가 되기 전까지 계속 거부한다.

## 선택 문서

`1차 권고`는 콘텐츠·최신성 검수 결과다. `APPROVE (조건부)`도 이용권한과 승인자 확인이
끝났다는 뜻은 아니며 manifest의 승인 상태를 자동 변경하지 않는다. `최종 전환 가능`이
`NO`인 문서는 계속 `IN_REVIEW/PENDING_ACTIVATION`으로 둔다.

| documentId | 기술 사전검증 | 온라인 출처·현재성 | 1차 권고 | 최종 전환 가능 |
|---|---|---|---|---|
| `DOC-FSC-SAFE-BLOCK-001` | PASS: HTML·UTF-8·SHA-256 | 2026-08-26 공식 카드뉴스·제목·게시일·핵심 신청/해제 내용 확인 | APPROVE (조건부) | NO: 개별 저작권 귀속 확인 필요 |
| `DOC-FSC-NONFACE-ACCOUNT-BLOCK-QA-001` | PASS: PDF·1쪽·비암호화·능동 콘텐츠 없음·SHA-256 | 2026-08-26 공식 게시물과 162 KB PDF 첨부 존재 확인 | APPROVE (조건부) | NO: 현재 첨부파일 해시·개별 저작권 귀속 확인 필요 |
| `DOC-FSC-DESIGNATED-PERSON-NOTICE-001` | PASS: HTML·UTF-8·SHA-256 | 2026-08-26 공식 2019-07-01 게시물 확인, 현재 운영범위는 확인되지 않음 | UPDATE_REQUIRED | NO: 현재 운영근거·효력종료 여부 확인 필요 |
| `DOC-FSC-FACE-TO-FACE-PHISHING-RELIEF-001` | PASS: HTML·UTF-8·SHA-256 | 2026-08-26 공식 2023-11-16 게시물 확인, 관련 현행법은 2026-08-04 개정 시행 | UPDATE_REQUIRED | NO: 현행법 원문 추가와 우선순위 규칙 필요 |
| `DOC-KDIC-MISTAKEN-REMITTANCE-ELIGIBILITY-001` | PASS: HTML·UTF-8·SHA-256 | 2026-08-26 동적 안내 페이지의 금액·기간·제외사유가 snapshot과 일치 | UPDATE_REQUIRED | NO: 예금보험공사 이용허락 필요 |

이번 1차 검수에서 `REJECT` 대상은 없다. 다만 `APPROVE (조건부)` 두 건도 현재 상태로는
최종 승인할 수 없다.

`DOC-KDIC-MISTAKEN-REMITTANCE-ELIGIBILITY-001`은 고정 게시물이 아니라 동적 안내 페이지의
2026-08-21 저장소 반입본이다. 승인 직전에 현재 금액·기간·제외사유가 반입본과 같은지 다시
대조하고, 다르면 새 snapshot과 새 `sourceHash`로 manifest를 갱신한다.

## 2026-08-26 1차 검수 근거

### 금융위원회 공통 이용조건

금융위원회 저작권정책은 위원회가 저작재산권 전부를 보유한 저작물은 자유이용할 수 있다고
안내하지만, 제3자와 권리를 공유한 자료는 단순 열람 외 무단 변경·복제·배포·개작을 금지한다.
따라서 발행기관이 금융위원회라는 사실만으로 `PUBLIC_REUSE_ALLOWED`를 부여하지 않는다.
각 게시물·첨부파일의 권리 귀속이나 별도 공공누리 표시를 승인자가 확인하기 전까지
`usageRights: REVIEW_REQUIRED`를 유지한다.

- 정책: https://www.fsc.go.kr/ut020104

### `DOC-FSC-SAFE-BLOCK-001`

- 공식 카드뉴스의 제목과 게시일 `2025-04-29`가 반입본과 일치한다.
- 비대면 계좌개설·여신거래 안심차단의 범위, 비대면/영업점 신청, 영업점 해제라는 핵심
  내용이 현재 공식 페이지와 일치한다.
- 저장본은 정보그림 게시판의 원 반입 URL과 내부번호 `84442`를 그대로 유지한다. 현재
  금융위원회 카드뉴스에도 같은 제목·게시일·핵심 내용이 노출되는 것을 교차 확인했다.
- 교차 확인: https://www.fsc.go.kr/no040101?cnId=2720
- 콘텐츠 최신성 기준으로는 승인 후보지만 개별 이미지의 권리 귀속 확인 전에는 최종 승인하지
  않는다. 카드 이미지 OCR 문장을 근거로 사용할 경우 원문 이미지와 별도 대조한다.

### `DOC-FSC-NONFACE-ACCOUNT-BLOCK-QA-001`

- 공식 Q&A 게시물의 제목·게시일과 PDF 첨부 존재를 확인했다.
- 저장된 PDF는 기술 사전검증을 통과했지만, 온라인 첨부파일이 저장본과 바이트 단위로 같은지는
  아직 검증하지 않았다. 최종 승인 전 현재 첨부파일을 다시 내려받아 SHA-256을 비교한다.
- 동일하면 콘텐츠 최신성 기준 승인 후보로 유지하고, 다르면 snapshot·`sourceHash`를 갱신한다.
- 출처: https://www.fsc.go.kr/po020201/84124

### `DOC-FSC-DESIGNATED-PERSON-NOTICE-001`

- 공식 원문은 2019년 제도 도입 계획을 알린 보도 성격의 자료다. 이를 현재 모든 금융회사에서
  동일하게 제공되는 상시 서비스 안내로 해석하면 안 된다.
- 문서 성격을 반영해 `documentType`을 `PUBLIC_NOTICE`로 수정했다. 동일 게시물은 금융소비자
  분류에서도 확인되지만, 저장본의 정확한 출처 추적을 위해 원 반입 URL은 유지했다.
- 교차 확인: https://www.fsc.go.kr/po010105/73764
- 현재 운영기관·대상상품·판매채널·연령기준이 2019년 내용과 같은지 확인하기 전까지
  `effectiveTo: null`로 활성화하지 않는다. 현재 근거를 확보하지 못하면 역사적 공지로만
  보존하고 검색 기본 결과에서 제외한다.

### `DOC-FSC-FACE-TO-FACE-PHISHING-RELIEF-001`

- 공식 원문의 게시일과 2023-11-17 시행 안내는 확인했다.
- 현재 역할 설명인 "피해구제 절차"로 사용하기에는 부족하다. 관련 특별법이 2026-08-04
  개정 시행됐으므로 2023년 보도자료가 현행 절차의 최종 권위 문서가 되어서는 안 된다.
- 2026-10-01 시행 예정 개정도 확인되므로 법령 manifest는 시행일 기준 버전 전환과 재검수를
  지원해야 한다.
- 현행 법률과 시행령을 별도 manifest로 추가하고 `LAW > PUBLIC_NOTICE` 우선순위를 적용한 뒤,
  이 문서는 2023년 대면편취형 피해구제 편입 배경을 설명하는 공지로만 사용한다.
- 공지: https://www.fsc.go.kr/po010101/81090
- 현행법: https://law.go.kr/LSW/lsInfoP.do?ancYnChk=0&lsId=011359

### `DOC-KDIC-MISTAKEN-REMITTANCE-ELIGIBILITY-001`

- 현재 공식 페이지의 기본요건은 snapshot과 일치한다: 건당 5만원 이상 1억원 이하,
  2021-07-06 이후 송금, 발생일로부터 1년 이내 신청, 금융회사 선행 반환 요청, 진행 중인
  법적절차 없음, 개인 간 분쟁·보이스피싱 등 사기송금 제외.
- 공식 페이지 자체도 이 항목들이 기본요건이며 다른 요건이 존재한다고 안내하므로, 검색 응답은
  최종 적격 판정이 아니라 확인 항목과 공식 신청 페이지 안내로 제한한다.
- 예금보험공사 저작권 보호방침은 홈페이지 자료의 무단 복제·배포를 원칙적으로 금지한다.
  내부 원문 저장·청킹·임베딩에 대한 서면 이용허락 또는 별도 개방 근거를 확보하기 전에는
  승인하지 않는다. 허락을 받지 못하면 제목·URL 등 최소 메타데이터만 유지하고 실시간 공식
  페이지로 연결하는 방식으로 대체한다.
- 안내: https://mkcs.kdic.or.kr/ir/msdrpr/selectAplyQlfcIdntyRslt.do
- 저작권 보호방침: https://www.kdic.or.kr/cm/gudn/PbcrHmpgAthPlcyGudn/selectScrn.do

## 승인자가 처리할 잔여 조치

1. 금융위원회 2개 승인 후보의 게시물·첨부파일 권리 귀속 또는 공공누리 표시를 확인한다.
2. Q&A PDF의 현재 첨부파일 SHA-256을 저장본과 비교한다.
3. 지정인 알림서비스의 현재 운영근거를 확보하고 `effectiveTo` 또는 대체 manifest를 결정한다.
4. 현행 통신사기피해환급법·시행령 manifest를 추가하고 법령 우선 검색 규칙을 적용한다.
5. 예금보험공사에 내부 RAG 이용허락을 확인하거나 metadata-only 방식으로 전환한다.
6. 조치가 끝난 문서만 별도 승인자가 `usageRights/approvalStatus/lifecycleStatus`를 변경하고
   `approvedBy/approvedAt`을 기록한다.

## 문서별 필수 검수

각 문서에서 아래 항목을 모두 확인한다. 하나라도 확인할 수 없으면 `APPROVED/ACTIVE`로
전환하지 않는다.

- [ ] `sourceUrl`이 발행기관의 공식 페이지이며 현재 접근 가능하다.
- [ ] 제목·발행기관·게시일 또는 snapshot 기준일이 manifest와 일치한다.
- [ ] 최신 문서이며 폐지·대체·정정된 자료가 아니다.
- [ ] `effectiveFrom`과 필요 시 `effectiveTo`가 검색 기준일에 맞다.
- [ ] 내부 검색·인용·평가 데이터 사용이 저작권정책과 이용조건에 부합한다.
- [ ] `classification`, `audience`, `allowedRoles`가 실제 노출정책과 맞다.
- [ ] 자격증명 마스킹 이력이 실제 반입본과 일치하고 마스킹 문자열이 근거가 되지 않는다.
- [ ] 원문이 자체 정책이나 자동 금융판단으로 오인되지 않도록 문서유형이 적절하다.
- [ ] 검수자와 승인시각을 감사 가능한 식별자로 기록한다.

## 승인 전환 규칙

승인자가 위 항목을 확인한 경우에만 해당 manifest를 다음과 같이 변경한다.

```yaml
usageRights: INTERNAL_USE_APPROVED
approvalStatus: APPROVED
lifecycleStatus: ACTIVE
approvedBy: <감사 가능한 검수자 식별자>
approvedAt: <UTC ISO 8601 date-time>
```

공개 재이용 근거까지 확인된 경우에만 `PUBLIC_REUSE_ALLOWED`를 사용한다. AI나 ingestion
도구가 승인 필드를 자동 변경해서는 안 된다.

## 사전검증 명령

저장소 루트를 명시하고 각 manifest에 대해 실행한다.

```bash
cd ai-service
uv run python -m app.cli validate-manifest \
  --repo-root .. \
  --manifest knowledge/manifests/DOC-FSC-SAFE-BLOCK-001.yaml
```

성공 결과에서도 `approvalAndLifecycleEligible`은 현재 `false`이고
`governanceBlockingCodes`에는 `DOCUMENT_NOT_APPROVED`, `DOCUMENT_NOT_ACTIVE`가 남아야 한다.

## 승인 이후 골든셋 생성 순서

1. 승인된 5개 manifest를 각각 ingestion하여 결정론적 chunk JSONL을 만든다.
2. 다음 명령으로 검증된 chunk를 합쳐 실제 문서 평가 corpus를 만든다.

```bash
cd ai-service
uv run python -m app.evaluation.corpus_cli \
  --repo-root .. \
  --as-of 2026-08-26 \
  --manifest knowledge/manifests/DOC-FSC-SAFE-BLOCK-001.yaml \
  --manifest knowledge/manifests/DOC-FSC-NONFACE-ACCOUNT-BLOCK-QA-001.yaml \
  --manifest knowledge/manifests/DOC-FSC-DESIGNATED-PERSON-NOTICE-001.yaml \
  --manifest knowledge/manifests/DOC-FSC-FACE-TO-FACE-PHISHING-RELIEF-001.yaml \
  --manifest knowledge/manifests/DOC-KDIC-MISTAKEN-REMITTANCE-ELIGIBILITY-001.yaml
```

3. corpus builder가 각 문서의 승인·활성·효력기간, `sourceHash`, `textHash`, 결정론적
   `chunkId`와 순서를 재검증한다.
4. 의미 바꿔 말하기, 정확 용어, 서비스 구분, 피해구제, 무응답·권한 차단 질문 후보를 만든다.
5. 질문과 정답 `chunkId`를 최소 2명이 독립 검수한다.
6. `ACCEPTED` 후보만 버전이 고정된 골든셋으로 승격한다.
7. E5-small, Arctic-ko와 선택적 Granite 107M을 동일 corpus와 질의로 평가한다.
