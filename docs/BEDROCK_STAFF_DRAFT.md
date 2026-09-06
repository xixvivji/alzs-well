# 행원 검토 초안: 선택형 Bedrock 연결

2026-09-06 개발·AWS 활성화 기록. 저장소 기본값은 비활성화이며 AWS에서는 승인된 합성 사건에 한해 Bedrock을 활성화했다. 운영 BFF를 통한 고객 응답 → 담당 행원 동일 사건 → 실제 생성 초안 호출을 확인했다. 화면 배포와 추가 품질 검증 상태는 아래 증적 범위와 구분한다.

## AWS 실제 호출 증적

- 요청 리전 서울, 추론 프로필 `apac.amazon.nova-lite-v1:0`. 사용자가 APAC 교차 리전 처리를 승인했다. 기존 NAT의 HTTPS 경로를 사용하며 Bedrock VPC Endpoint나 완전 폐쇄망으로 설명하지 않는다.
- AI EC2 역할에 해당 Nova Lite 추론 프로필만 허용한다. 컨테이너는 호스트가 5분마다 갱신하는 읽기 전용 단기 역할 자격증명을 사용한다. 장기 키·EC2 메타데이터 hop limit 완화는 사용하지 않는다.
- Spring `NETWORK_MODE=CONTROLLED_BEDROCK_SYNTHETIC`, 전송 승인 문서 `DOC-SYN-COPILOT-001`. 다른 문서·실데이터 전송을 승인한 것이 아니다.
- 2026-09-06 06:29 UTC: `demo003`의 `NOT_SURE` 응답 → `BANK_REVIEW` → 담당 `staff003`의 동일 사건 조회 → 초안 HTTP 200, 3,898ms. `generatedBy=BEDROCK_GENERATIVE_DRAFT`, `fallbackUsed=false`, `retrievalMode=INTERNAL_RAG_HYBRID`를 확인했다. 초안 trace ID: `a8ebe7da46fc4c9989c4e868de0bf774`.
- 인용은 합성 상담 문서 1건이며 외부 원문 URL이 없는 자료다. 이를 공식 법령 인용이나 법률 검토 결과로 표시하지 않는다.
- AI readiness에서 `local-arctic-ko`, `STAGED_APPROVED`, `embeddingFallbackUsed=false` 확인. 생성 모델 추가로 검색 임베딩을 교체하지 않았다.
- 최초 출력에서 행원 업무 회고형 질문이 나타나 고객에게 직접 확인할 질문 예시를 추가했다. 이 기록은 기능 연동 증적이지 독립적인 답변 품질 평가가 아니다.

## 사용자 흐름

- 고객: 합성 회원 로그인 → 금융생활 도움받기 → 의향 확인 → 변화·알림 확인 → 본인 응답. 새 생성형 단계는 없다.
- 행원: 직원 로그인 → 담당 사건 선택 → 고객 응답·근거 확인 → 검토 초안 만들기 → 원문과 초안 검토 → 기존 메모·승인·후속관리.
- 초안 생성 API는 사건 상태와 안내계획을 변경하지 않는다. 생성 문장은 자동 저장되지 않는다.
- 공개 익명 `/demo` 체험은 기존 검색·템플릿을 유지하며 Bedrock 호출 자격을 부여하지 않는다. 생성형 호출은 로그인 행원의 권한 검사를 통과한 회원 사건으로 한정한다.

## 구현 범위와 한계

`POST /api/v1/staff/cases/{caseId}/copilot-drafts`: 요청 본문 없이 인증된 사건 ID로 조회한다. `STAFF_CASE_READ`와 `STAFF_CASE_REVIEW`를 모두 요구하며 담당 고객 접근권을 별도 검사한다. 반환은 공통 `ApiResponse`의 `data`에 요약·질문·체크리스트·생성 방식·참고 인용을 담는다. 생성 중 사건이 변경되면 409로 최신 사건 확인을 요청한다. 저장용 멱등성 키는 사용하지 않으며 다시 생성하면 다른 초안과 추가 추론 비용이 발생할 수 있다.

Spring의 승인·유효일·ACL 검증을 통과한 검색 결과를 기반으로 FastAPI가 Bedrock Converse를 호출한다. Arctic-ko 검색 임베딩은 교체하지 않는다. 생성 모델은 별도 설정이다. 이번 최소 구현의 사건 입력은 사유 코드와 고객 응답 코드다. 거래 원장, 고객 이름·계좌번호·UUID·자유 메모·의향서는 전달하지 않으며 변화 수치까지 포함하는 요약은 이번 범위가 아니다.

반환 항목은 요약·확인 질문·체크리스트뿐이다. JSON 형태, 길이, 배열 크기, URL·HTML을 양쪽 서버에서 검사한다. 인용은 모델이 만들지 않으며 Spring 검색 결과에서만 붙인다. 생성 후 검색 결과와 회원 사건 접근권·상태를 다시 확인한다. 문장별 사실 일치나 환각 부재를 증명한 것은 아니므로 행원이 원문을 확인해야 한다.

FastAPI는 기본 비활성화, 내부 서비스 토큰 인증, 합성 플래그, 명시적 전송 승인, 모델·리전 설정을 요구한다. Spring에서는 외부 전송을 승인한 문서 ID 목록도 요구한다. 목록에 없는 자료가 하나라도 있으면 모델을 호출하지 않는다. 단순 식별자 패턴 검사는 완전한 개인정보 탐지기가 아니며 실데이터 처리를 허가하지 않는다.

호출 실패·형식 위반·기능 비활성화는 기본 안내를 유지한다. 화면은 `BEDROCK_GENERATIVE_DRAFT` 성공만 생성형 AI 초안으로 표시한다. `modelInvoked`/`externalEgressAttempted`는 호출 시도 관련 진단 필드이며 성공 판정으로 쓰지 않는다. Spring 통신 실패 시 원격 호출 여부는 확인할 수 없으므로 false를 ‘외부 전송이 절대 없었다’는 증거로 해석하지 않는다.

## 배포 전 활성화 조건

자동으로 IAM·네트워크·모델 권한을 변경하지 않는다. 기존 무외부전송/폐쇄형 설명은 기본 template 모드에 해당한다. Bedrock 모드는 관리형 외부 추론 경계를 추가한다.

1. 서울 리전에서 Converse 지원 모델·이용 조건·예상 비용을 확인하고 모델 ID를 확정한다. 교차 리전 추론 프로필은 별도 승인이 없으면 선택하지 않는다.
2. AI 실행 역할에 해당 모델만의 `bedrock:InvokeModel` 권한을 부여하고 단기 역할 자격증명을 전달한다. 장기 키를 Git이나 프런트에 넣지 않는다.
3. 사설 Bedrock Runtime VPC Endpoint 또는 승인된 제한적 egress를 검증한다. 현재 Compose 설정만으로 해당 네트워크와 IAM이 만들어지지는 않는다. 컨테이너 역할 자격증명 접근도 별도 확인한다.
4. AI 이미지를 재빌드한다. Dockerfile에는 SDK extra가 포함되어 있다. 로컬 설치는 `uv sync --extra bedrock`으로 한다.
5. Spring: `NETWORK_MODE=CONTROLLED_BEDROCK_SYNTHETIC`, `COPILOT_RAG_ENABLED=true`, `AI_ASSISTANCE_ENABLED=true`, `COPILOT_GENERATION_ENABLED=true`, `COPILOT_EGRESS_DOCUMENT_IDS=<전송 승인된 문서 ID 쉼표 목록>`.
6. FastAPI: `ALZS_COPILOT_PROVIDER=bedrock`, `ALZS_BEDROCK_SYNTHETIC_EGRESS_ALLOWED=true`, `ALZS_BEDROCK_REGION=<승인 리전>`, `ALZS_BEDROCK_MODEL_ID=<승인 모델 ID>`.
7. 합성 사건 한 건으로 실제 생성·원문 일치·권한 거절·모델 차단 시 기본 안내를 확인한 뒤에만 배포 완료로 기록한다.

추론은 프로세스당 동시 2건, SDK 연결 2초·읽기 8초, 자동 재시도 없이 900 출력 토큰으로 제한한다. Spring 생성 요청 12초, 프록시 초안 경로 18초, 브라우저 20초다. 일반 API 시간 제한은 유지한다. 이 제한은 월별 지출 상한이 아니므로 실제 활성화 전 별도 예산 경보·호출량 검토가 필요하다. SDK 자격증명 획득 시간과 검색 지연에 따라 총 응답이 초과될 수 있으며 이때 기본 안내 또는 오류 안내를 사용한다.

중단은 Spring의 `NETWORK_MODE=AIR_GAPPED_DEMO`, `COPILOT_GENERATION_ENABLED=false`와 FastAPI의 `ALZS_COPILOT_PROVIDER=template` 적용 후 재기동한다. 기존 검색·결정론적 업무 흐름은 유지한다.

## 향후 자체 호스팅

Python `DraftProvider` 인터페이스를 통해 동일한 구조화 출력 계약을 다른 추론 서버로 교체할 수 있다. 현재 구현한 제공자는 Bedrock뿐이며 자체 로컬 LLM 제공자는 미구현이다. 교체 시 성능·안전성·라이선스·하드웨어·장애 폴백을 다시 검증한다. 설정 값만 바꾸면 미구현 로컬 모델이 동작한다고 주장하지 않는다.

## 금융권 적용 설명

금융위원회는 2026-04-15 보안 위험이 낮은 단순 생성형 AI 모델 변경에 대한 혁신금융서비스 변경 절차 간소화를 발표했다. 이를 망분리의 전면 폐지나 모든 금융사의 클라우드 AI 사용 허용으로 해석하지 않는다. 적용 기관의 데이터 분류·보안·준법·예외 승인 조건을 확인한 뒤 관리형 모델 또는 자체 호스팅을 선택한다. 본 공모전 구현은 해당 금융기관 승인이나 실데이터 이용 적합성을 입증하지 않는다. [금융위원회 발표](https://www.fsc.go.kr/po010105/86712)

Converse 인터페이스는 AWS 공식 문서를 기준으로 구현했다. [Amazon Bedrock Converse](https://docs.aws.amazon.com/bedrock/latest/userguide/conversation-inference.html)

## 로컬 검증 기록

2026-09-06 `feature/bedrock-staff-draft` 작업 트리 기준:

- FastAPI 전체 351개 테스트 통과, 커버리지 90.40%. Bedrock SDK는 모킹했으며 실제 호출이 아니다.
- Java 전체 310개 테스트 통과, 라인 커버리지 92.87%, 90% 게이트와 SpotBugs 통과.
- Next.js 빌드·린트·타입 검사·`npm test` 통과(렌더링 검사 및 클라이언트 테스트 84개).
- 생성 중 검색 근거 철회 시 폐기, 사건 변경 시 거절, 고객·비인증 호출 차단, 기능 비활성화·형식 오류·시간 초과 폴백을 검사했다.
- API 카탈로그 문서 284개·구현 240개 정합성 검사와 `git diff --check` 통과.
- 원격 푸시·AWS/Vercel 재배포·실제 생성형 응답 품질·운영 UI E2E는 이번 로컬 검증에 포함하지 않았다.
