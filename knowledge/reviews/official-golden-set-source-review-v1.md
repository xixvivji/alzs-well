# 공식 검색 골든셋 원문 검수 팩 v1

이 문서는 실제 문서 기반 검색 골든셋을 만들기 전에 사용할 5개 원문의 승인 검수 목록이다.
기술 사전검증 통과는 콘텐츠 승인이나 이용권한 승인을 의미하지 않는다. AI ingestion은
각 manifest가 `APPROVED/ACTIVE`가 되기 전까지 계속 거부한다.

## 선택 문서

| documentId | 원문 역할 | 기술 사전검증 | 온라인 출처 확인 | 이용조건 | 승인결정 |
|---|---|---|---|---|---|
| `DOC-FSC-SAFE-BLOCK-001` | 안심차단의 서비스 범위와 신청 안내 | PASS: HTML·UTF-8·SHA-256 | 재확인 필요 | 검토 필요 | PENDING |
| `DOC-FSC-NONFACE-ACCOUNT-BLOCK-QA-001` | 비대면 계좌개설 안심차단의 상세 Q&A | PASS: PDF·1쪽·비암호화·능동 콘텐츠 없음·SHA-256 | 2026-08-26 게시물·첨부 PDF 확인 | 검토 필요 | PENDING |
| `DOC-FSC-DESIGNATED-PERSON-NOTICE-001` | 지정인 알림서비스의 동의·대상·절차 | PASS: HTML·UTF-8·SHA-256 | 재확인 필요 | 검토 필요 | PENDING |
| `DOC-FSC-FACE-TO-FACE-PHISHING-RELIEF-001` | 대면편취형 보이스피싱 피해구제 절차 | PASS: HTML·UTF-8·SHA-256 | 2026-08-26 게시물 확인 | 검토 필요 | PENDING |
| `DOC-KDIC-MISTAKEN-REMITTANCE-ELIGIBILITY-001` | 착오송금과 사기송금의 구분·신청 기본요건 | PASS: HTML·UTF-8·SHA-256 | 2026-08-26 동적 안내 페이지 확인 | 검토 필요 | PENDING |

`DOC-KDIC-MISTAKEN-REMITTANCE-ELIGIBILITY-001`은 고정 게시물이 아니라 동적 안내 페이지의
2026-08-21 저장소 반입본이다. 승인 직전에 현재 금액·기간·제외사유가 반입본과 같은지 다시
대조하고, 다르면 새 snapshot과 새 `sourceHash`로 manifest를 갱신한다.

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
