---
name: "ALZ's well"
description: "신뢰할 수 있는 금융생활 안심 동행"
colors:
  deep-ocean: "#163B4D"
  slate-teal: "#356675"
  quiet-sage: "#91AA9D"
  warm-ivory: "#F6F3ED"
  porcelain: "#FFFFFF"
  deep-ink: "#17242A"
  ink-soft: "#52636A"
  line: "#D9E0DE"
  signal-amber: "#B77924"
  muted-clay: "#A34F47"
  staff-navy: "#1C2D4A"
typography:
  display:
    fontFamily: "Pretendard Variable, Pretendard, Apple SD Gothic Neo, Arial, sans-serif"
    fontSize: "clamp(2.25rem, 4.5vw, 3.75rem)"
    fontWeight: 800
    lineHeight: 1.15
    letterSpacing: "-0.045em"
  headline:
    fontFamily: "Pretendard Variable, Pretendard, Apple SD Gothic Neo, Arial, sans-serif"
    fontSize: "clamp(1.75rem, 3vw, 2.5rem)"
    fontWeight: 800
    lineHeight: 1.25
    letterSpacing: "-0.035em"
  title:
    fontFamily: "Pretendard Variable, Pretendard, Apple SD Gothic Neo, Arial, sans-serif"
    fontSize: "1.25rem"
    fontWeight: 750
    lineHeight: 1.4
    letterSpacing: "-0.02em"
  body:
    fontFamily: "Pretendard Variable, Pretendard, Apple SD Gothic Neo, Arial, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.7
    letterSpacing: "-0.01em"
  label:
    fontFamily: "Pretendard Variable, Pretendard, Apple SD Gothic Neo, Arial, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 800
    lineHeight: 1.4
    letterSpacing: "0.08em"
rounded:
  control: "8px"
  card: "14px"
  feature: "20px"
  pill: "999px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "16px"
  lg: "24px"
  xl: "32px"
  section: "72px"
components:
  button-primary:
    backgroundColor: "{colors.deep-ocean}"
    textColor: "{colors.porcelain}"
    typography: "{typography.body}"
    rounded: "{rounded.control}"
    padding: "14px 20px"
    height: "48px"
  button-secondary:
    backgroundColor: "{colors.porcelain}"
    textColor: "{colors.deep-ocean}"
    typography: "{typography.body}"
    rounded: "{rounded.control}"
    padding: "13px 19px"
    height: "48px"
  card:
    backgroundColor: "{colors.porcelain}"
    textColor: "{colors.deep-ink}"
    rounded: "{rounded.card}"
    padding: "24px"
  status-caution:
    backgroundColor: "{colors.warm-ivory}"
    textColor: "{colors.signal-amber}"
    rounded: "{rounded.pill}"
    padding: "6px 10px"
---

# Design System: ALZ's well

## Overview

**Creative North Star: "차분한 금융 안내 데스크"**

ALZ's well은 고객을 불안하게 만드는 관제 화면도, 친근함을 과장한 돌봄 앱도 아니다. 필요한 정보를 정돈해 보여주고 다음 행동을 차분하게 안내하는 금융 창구처럼 느껴져야 한다. 공개 홈은 금융서비스로 들어가는 단순한 흰색 게이트웨이이며 두 번째 대시보드처럼 꾸미지 않는다. 고객 화면은 여유롭게, 행원·관리자 화면은 더 조밀하고 업무적으로 구성하되 모든 채널이 같은 신뢰의 언어를 공유한다.

시각적 표현은 신뢰감 있고 따뜻하지만 유아적이지 않으며, 금융 업무 화면답게 절제한다. 브랜드는 장식적인 효과보다 정확한 정렬, 충분한 여백, 명확한 상태 표현, 쉬운 문장과 세심한 상호작용에서 드러난다.

**Key Characteristics:**

- 흰색 금융 화면과 깊은 오션·네이비가 만드는 절제된 신뢰감
- 금융생활 도움·변화 확인 흐름에만 한정해 쓰는 저채도 세이지
- 고객 채널의 여유로운 정보 밀도와 직원 채널의 구조화된 업무 밀도
- 점수보다 변화의 사실과 다음 행동을 먼저 보여주는 위계
- 얇은 경계와 낮은 앰비언트 그림자로 구분하는 차분한 깊이
- 큰 글씨·고대비·키보드 사용을 별도 모드가 아닌 기본 품질로 취급

## Colors

일반 금융과 공개 진입 화면은 Porcelain, Deep Ocean, Staff Navy를 중심으로 구성한다. Quiet Sage는 ALZ's well 고유의 금융생활 도움·변화 확인 흐름에만 한정하고, 앰버와 클레이는 확인 필요와 오류를 서로 다른 의미로 전달한다.

### Primary

- **Deep Ocean** (`#163B4D`): 브랜드 마크, 일반 금융 내비게이션, 핵심 버튼과 중요한 제목에 사용한다. 흰색 텍스트를 올릴 수 있는 주 신뢰색이다.
- **Slate Teal** (`#356675`): 링크, 보조 강조, 선택 상태, 차트의 비교 계열에 사용한다.

### Secondary

- **Quiet Sage** (`#91AA9D`): 금융생활 도움의 단계, 변화 확인 표면과 그 흐름의 보조 강조에 사용한다. 일반 계좌·거래·상품 화면의 장식색이나 작은 글자의 단독 전경색으로 사용하지 않는다.
- **Staff Navy** (`#1C2D4A`): 일반 금융의 단단한 마감 영역, 공개 푸터, 행원·관리자 헤더와 최종 통제 영역에 사용한다.

### Tertiary

- **Signal Amber** (`#B77924`): 확인 필요, 유예, 주의 상태에 사용한다. 공포를 유발하는 위험색으로 확대하지 않는다.
- **Muted Clay** (`#A34F47`): 오류, 차단, 철회처럼 명확한 경계가 필요한 경우에만 사용한다.

### Neutral

- **Warm Ivory** (`#F6F3ED`): 보조 밴드와 브라우저 스크롤 트랙처럼 흰 화면을 구분하는 낮은 대비 배경에 사용한다.
- **Porcelain** (`#FFFFFF`): 공개 홈과 일반 금융의 기본 캔버스, 카드, 입력, 전면 작업 영역에 사용한다.
- **Deep Ink** (`#17242A`): 제목과 본문의 기본 전경색이다.
- **Soft Ink** (`#52636A`): 보조 설명과 메타데이터에 사용하되 명암비를 희생하지 않는다.
- **Quiet Line** (`#D9E0DE`): 카드, 입력, 표 구분선에 사용한다.

**The Calm Signal Rule.** 상태색은 의미 전달에만 사용한다. 넓은 면적을 앰버나 클레이로 채워 고객을 압박하지 않는다.

**The Service Boundary Rule.** 일반 금융과 공개 홈은 Porcelain, Deep Ocean, Staff Navy를 중심으로 유지한다. Quiet Sage 면은 금융생활 도움·변화 확인 흐름에서만 사용해 서비스의 고유 영역을 눈에 보이게 구분한다.

**The Palette Reaches the Browser Rule.** 입력 caret은 Deep Ocean, 스크롤바 thumb은 Quiet Sage, track은 Warm Ivory를 사용한다. 브라우저 기본 요소에도 임의의 별도 색을 추가하지 않는다.

## Typography

**Display Font:** Pretendard Variable (Pretendard, Apple SD Gothic Neo, Arial, sans-serif fallback)

**Body Font:** Pretendard Variable (Pretendard, Apple SD Gothic Neo, Arial, sans-serif fallback)

**Label Font:** Pretendard Variable (Pretendard, Apple SD Gothic Neo, Arial, sans-serif fallback)

**Character:** 소스로 연결한 Pretendard Variable을 기본으로 하는 단일 산세리프 체계다. 한글 가독성과 숫자 판독성을 우선하고, 과한 굵기 변화보다 크기, 여백, 문장 길이로 위계를 만들며 숫자와 상태는 빠르게 훑을 수 있어야 한다.

### Hierarchy

- **Display** (800, `clamp(2.25rem, 4.5vw, 3.75rem)`, 1.15): 홈의 핵심 약속처럼 한 화면에 하나만 필요한 메시지에 사용한다.
- **Headline** (800, `clamp(1.75rem, 3vw, 2.5rem)`, 1.25): 페이지 제목과 주요 작업의 시작점에 사용한다.
- **Title** (750, `1.25rem`, 1.4): 카드와 작업 구획의 제목에 사용한다.
- **Body** (400, `1rem`, 1.7): 설명과 안내에 사용하며 긴 문장은 가능하면 70자를 넘기지 않는다.
- **Label** (800, `0.75rem`, `0.08em`): 짧은 범주와 상태 보조 표시에 사용한다. 영문 대문자는 짧은 운영 레이블에만 허용한다.

**The One Clear Sentence Rule.** 고객이 결정을 내려야 하는 영역에서는 제목 아래 핵심 안내를 한 문장으로 먼저 제공하고 전문 용어와 코드값은 보조 정보로 내린다.

**The Heading Starts With the Heading Rule.** 순서가 있는 작업의 단계 번호는 `1단계 ·`처럼 제목 문장 안에 inline으로 둔다. 범주 레이블을 제목 위에 반복해 올리는 eyebrow scaffolding으로 위계를 만들지 않는다.

## Layout

데스크톱 고객 금융 포털은 상단 내비게이션과 최대 `1440px` 작업 영역을 사용한다. 공개 데모와 직원 화면은 역할별 사이드바와 본문 작업 영역을 사용하며, 같은 페이지 안에서 탐색 구조를 혼합하지 않는다. 일반 페이지는 8px 기반 간격을 사용하고 카드 내부는 24px, 주요 섹션 사이는 48~72px의 호흡을 둔다.

공개 홈은 대시보드가 아니라 금융서비스의 방향을 고르는 게이트웨이다. 첫 화면은 서비스의 핵심 약속, 금융생활 도움을 시작하는 primary CTA, 금융 홈으로 가는 secondary CTA, 세 단계의 세이지 여정 순서로 읽혀야 한다. 계좌 요약이나 상태 카드 묶음을 첫 화면에 복제하지 않는다.

공개 셸은 최소 `100svh`를 차지하는 세로 flex 구조로 만들고 본문이 남는 공간을 채우게 해, 내용이 짧아도 네이비 푸터가 뷰포트 끝에서 화면을 닫도록 한다.

고객의 확인·설정 흐름은 한 번에 하나의 주 행동이 명확해야 한다. 금융 현황과 직원 운영 화면은 2열 또는 4열 요약을 사용할 수 있지만, 모바일에서는 단일 열과 선형 작업 순서로 바뀐다. 표는 작은 화면에서 핵심 열을 카드형 행으로 재배치하며 가로 스크롤을 기본 해법으로 삼지 않는다.

고객 화면은 넉넉한 여백과 큰 터치 영역을, 직원 화면은 정렬된 메타데이터와 빠른 스캔을 우선한다. 모든 주요 컨트롤은 최소 44px 높이를 유지한다. 모바일은 데스크톱의 목적·사실·결정·다음 행동 순서를 그대로 단일 열로 옮기고, 주요 CTA와 안전 범위의 행동을 full-width 컨트롤로 제공한다.

## Elevation & Depth

기본은 평면적이며, 배경색·얇은 선·간격으로 먼저 계층을 만든다. 그림자는 카드가 떠 보이게 장식하는 수단이 아니라 헤더, 핵심 작업 카드, 일시적 피드백처럼 실제로 앞에 놓인 요소를 구분하는 앰비언트 신호다.

### Shadow Vocabulary

- **Surface Ambient** (`0 9px 28px rgba(22, 49, 42, 0.06)`): 페이지 바탕 위의 주요 카드에만 사용한다.
- **Raised Action** (`0 14px 36px rgba(22, 59, 77, 0.16)`): 핵심 CTA 또는 일시적 결과 피드백에 제한한다.
- **Sticky Header** (`0 4px 20px rgba(15, 45, 37, 0.05)`): 고정 헤더와 내비게이션의 위치를 구분한다.

**The Layer Only When Needed Rule.** 카드 안에 또 다른 떠 있는 카드를 반복하지 않는다. 중첩 정보는 구분선, 배경 톤 또는 목록 구조로 표현한다.

## Shapes

기본 컨트롤은 8px, 일반 카드는 14px, 브랜드를 드러내는 큰 안내 영역은 최대 20px의 모서리를 사용한다. 상태 칩과 짧은 필터만 완전한 pill 형태를 사용한다. 모든 아이콘을 둥근 사각형 타일 안에 넣지 않으며, 원형은 단계 번호·상태점·사용자 아바타처럼 의미가 있는 곳에만 사용한다.

테두리는 `#D9E0DE`의 1px 선을 기본으로 하며, 선택 상태는 테두리 색과 낮은 명도 차이의 배경을 함께 사용한다. 색상만으로 상태를 구분하지 않는다.

## Components

### Buttons

- **Shape:** 단정한 8px 모서리와 최소 44~48px 높이를 사용한다.
- **Primary:** Deep Ocean 배경과 Porcelain 텍스트, `14px 20px` 패딩을 기본으로 한다. 한 작업 영역에는 원칙적으로 하나의 primary action만 둔다.
- **Hover / Focus:** hover는 명도를 소폭 낮추고 최대 1px만 이동한다. `:focus-visible`은 3px 외곽선으로 명확히 표시하며 제거하지 않는다.
- **Secondary:** 흰색 또는 투명 배경, Deep Ocean 텍스트와 Quiet Line 테두리를 사용한다.
- **Destructive:** Muted Clay는 철회·삭제·차단처럼 되돌리기 어렵거나 경계를 명확히 해야 하는 행동에만 사용한다.

### Chips

- **Style:** 상태의 의미색을 옅은 배경과 진한 텍스트로 함께 표현한다.
- **State:** 아이콘이나 텍스트 레이블을 반드시 포함한다. 단독 색점만으로 상태를 전달하지 않는다.

### Cards / Containers

- **Corner Style:** 일반 카드는 14px, 핵심 안내 영역은 20px까지 사용한다.
- **Background:** Porcelain을 기본으로 하고, 보조 그룹은 Warm Ivory 또는 저채도 틴트를 사용한다.
- **Shadow Strategy:** 기본 카드는 얇은 테두리와 Surface Ambient만 사용한다.
- **Border:** Quiet Line 1px.
- **Internal Padding:** 모바일 16~20px, 데스크톱 24~32px.

### Inputs / Fields

- **Style:** 흰 배경, Quiet Line 1px, 8px 모서리, 최소 48px 높이를 사용한다. 레이블은 placeholder로 대체하지 않는다.
- **Focus:** Deep Ocean 테두리와 3px 반투명 외곽선으로 표시한다.
- **Error / Disabled:** 오류는 Muted Clay 텍스트와 테두리, 설명 문장을 함께 사용한다. disabled는 낮은 대비만 주지 말고 비활성 사유를 인접 문장으로 설명한다.

### Navigation

- 공개 게이트웨이와 고객 금융 포털은 절제된 상단 내비게이션, 공개 데모와 직원 도구는 역할별 사이드 내비게이션을 사용한다.
- 공개 게이트웨이와 `/banking/**`의 고객 상단 메뉴는 `금융 홈 → 계좌·거래 → 송금 전 확인 → 금융상품 → 금융생활 도움받기`의 이름·순서·형태를 동일하게 유지한다. 로그인 전에는 인증 후 목적지로, 로그인 후에는 해당 화면으로 직접 연결한다.
- 현재 위치는 `aria-current="page"`, 텍스트 굵기, 전경색과 선 또는 배경으로 중복 표현한다.
- 모바일은 같은 다섯 목적지를 두 줄로 재배치해 `금융생활 도움받기`가 화면 밖이나 접힌 메뉴에 숨지 않게 한다.
- `글자 크게/작게`, `선명하게`, `맨 위로`는 헤더 공간과 무관한 고정 도구로 제공한다. 충분한 우측 여백이 있는 넓은 화면에서는 오른쪽 세로 레일, 그보다 좁은 화면에서는 본문을 덜 가리는 하단 가로 도구로 재배치하되 모든 스크롤 위치에서 접근 가능해야 한다.

### Detected Change Entry

로그인 회원에게 열린 변화 신호 또는 직접 확인할 알림이 있으면 상단 메뉴에는 Signal Amber의 `변화 N` 배지와 접근성 이름으로 알린다. 메뉴 전체의 앰버 배경은 `/banking/help`를 포함한 금융생활 도움 화면에 있을 때만 사용한다. 내비게이션 밑줄은 항상 하나만 보인다. 평상시에는 현재 메뉴의 Deep Ocean 밑줄을 표시하고, 다른 메뉴를 hover하면 현재 밑줄을 잠시 숨기고 hover 메뉴의 Quiet Sage 밑줄로 바꾼다. 금융 홈의 첫 작업과 도움 바로가기는 모두 하나의 `/banking/help` 허브로 연결한다. `/banking/safety`는 호환 이동 주소다. 허브의 순서는 `변화·근거 확인 → 내 상황 응답 → 은행 검토 연결 상태`이며, 의향 설정은 최초 이용·선호 변경을 위한 별도 구획이다. 의향을 매번 다시 작성하게 하지 않는다. 앰버는 변화가 있다는 사실만 알리며 질병·사기·고위험 판정으로 표현하지 않는다.

회원용 고객 응답·사건 상태는 백엔드 운영 API의 값을 사용한다. 공개 데모 상태와 혼합하지 않는다. 고객 화면은 은행 연결 여부만 표시하며, 별도 고객 조회 API가 없는 행원 진행 상태를 추정하지 않는다. 행원 화면은 고객 선택 응답과 관찰 근거를 나란히 보여주고 공유 의향은 전용 요약 API로 동의 항목만 표시한다. 자유서술 의견 원문처럼 사건 API가 제공하지 않는 정보는 임의로 만들지 않는다. 업무 본문에 API 미제공 설명을 반복하는 대신 제공된 사실을 중심으로 구성한다.

역할 전환은 상단 로그인 메뉴의 `개인 로그인 / 운영자 로그인`으로 모은다. 마우스 hover뿐 아니라 클릭·터치·키보드로 열고 Escape로 닫을 수 있어야 한다. 본문에 역할 전환 안내 박스를 추가하지 않는다. 동일 사건·알림의 식별자는 로그인 목적지로 보존하되 권한을 부여하는 값으로 사용하지 않는다. 로그인 유형 선택은 진입 화면만 정하고, 실제 이동·열람은 서버 응답의 역할로 결정한다. 고객은 `/banking`, 행원은 `/staff/cases`, 관리자는 `/staff/control-center`가 기본 시작점이다.

행원 메뉴는 `업무 현황 → 사건 검토 → 서비스 상태`, 관리자 메뉴는 `관리·준법 → 서비스 상태`로 유지한다. 업무 현황의 상태별 건수는 목록 필터 버튼이며, 선택한 상태의 사건을 바로 아래에 표시한다. 종결 사건도 열 수 있고 `전체 보기`로 필터를 해제한다. 집계는 조회된 사건 범위를 명시하며 전역 합계로 표현하지 않는다. 공용 서비스 상태에 진입해도 로그인 역할의 메뉴가 사라지지 않는다. 사이드바의 스타일은 `.app-sidebar`에만 적용하고 본문의 `aside`로 전파하지 않는다. 회원 사건과 `/demo/staff/cases`의 시연 사건은 별도 경로로 제공한다.

서비스 상태는 핵심 연결과 AI 보조 연결을 따로 표시한다. 정책 설정값(`OPTIONAL/REQUIRED`)을 장애나 실패 개수로 계산하지 않으며, 기본 안내문 사용 설정을 개별 응답의 실제 폴백 발생처럼 표현하지 않는다. 버전·데이터 모드·세부 점검은 접힌 상세에 둔다.

시연·구현 설명은 업무 본문에 반복하지 않는다. `API 계약 수`, `Bearer`, `IdP` 같은 개발 정보는 개발 참고 화면·문서에 둔다. 각 셸 하단에 `체험 서비스 · 예시 데이터 사용 · 실제 거래·외부 연락 없음`을 한 번 표시하고, 동의·승인 등 결정에 영향을 주는 실행 범위만 해당 버튼 근처에 추가한다. 고정 시드의 근거 문구는 사실을 유지한 쉬운 문장으로 표현할 수 있으나 금액·날짜·수치·원본 기록은 바꾸지 않는다.

### Public Gateway

공개 홈은 흰색 바탕의 은행형 진입면으로 구성한다. 첫 뷰포트의 thesis와 두 CTA가 주 결정을 만들고, 그 옆 또는 바로 다음에 저채도 세이지의 세 단계 여정을 배치한다. primary는 금융생활 도움 시작, secondary는 일반 금융 홈 진입을 맡으며 두 행동의 시각적 우선순위를 뒤집지 않는다.

### Browser Surface

브라우저 caret과 스크롤바도 팔레트 토큰을 사용한다. 입력 caret은 Deep Ocean으로, 스크롤 thumb과 track은 각각 Quiet Sage와 Warm Ivory로 연결해 운영체제 기본색이 제품 표면에 끼어들지 않게 한다.

### Context Choice

고객 맥락 선택지는 한 화면에 한 질문을 중심으로 최소 44px 높이의 큰 버튼으로 제공한다. 어느 선택도 질병·사기 판정이나 금융 불이익처럼 보이지 않아야 하며, `나중에 확인`과 `직원에게 문의`도 동등한 선택지로 보여준다.

### Evidence & Human Review

회원 사건의 고객 응답·공유 의향은 초록 배경(`#e7f2eb`)과 짙은 초록 제목, 행원의 메모·안내계획·종결 입력·후속 일정과 결과는 파랑 배경(`#e9eff9`)과 네이비 제목으로 **영역 자체**를 구분한다. 출처 배지를 반복하거나 설명을 늘려 구분을 대신하지 않는다. 기준선·최근값·변화 근거·자동 이력·검색 자료는 중립 배경에 남긴다. 기존의 구체적 제목으로 색을 구별하기 어려운 상황에도 의미가 유지되게 한다. 저장된 행원 기록과 승인 전 입력을 구분하고, 작성자 이름을 API에 없는 정보로 추정하지 않는다.

AI 초안, 승인 근거, 폴백 상태, 행원 최종 결정은 서로 다른 구획과 명시적 레이블로 구분한다. 기술 코드와 hash는 기본 작업을 방해하지 않는 보조 계층에 두되 감사 화면에서는 확인 가능해야 한다.

회원 사건 상세는 `고객·근거 확인 → 검토·결정 → 후속관리·이력`의 세 작업 탭을 사용한다. 이 탭은 열람 위치이며 업무 완료를 의미하는 진행 표시가 아니다. 실제 사건 상태와 지금 할 일을 별도로 표시한다. 고객 응답·평소값·최근값은 공통 요약으로 유지하고, 공유 의향은 별도 권한 확인 조회로 제공한다. 선택 사건을 검토하는 동안 긴 사건 목록은 접되 `사건 목록으로`로 돌아갈 수 있고 같은 사건의 미저장 입력은 유지한다.

안내계획과 종결은 다른 결과이므로 선택을 분리한다. 안내는 항목 선택 후 영향 검토·명시적 승인 2단계를 유지한다. 안내 승인 후에는 종결 사유를 작성하고, 종결 후에도 후속 일정을 관리할 수 있다. 후속 결과 입력은 해당 일정 바로 아래에 둔다. 기본 질문은 현재 사건 사유에 해당하는 백엔드 템플릿만 제시하며 AI 생성 결과처럼 표시하지 않는다. 기록 출처, 검색 도구, 내부 메모 입력과 처리 이력은 명확한 이름의 펼침 영역에 둔다.

일반 고객·행원 동선에는 `/demo` 진입 버튼이나 로그인 실패 시 데모 자동 이동을 두지 않는다. 기존 데모 경로는 호환을 위해 유지하지만 로그인·도움 진입은 회원 화면으로 연결한다.

## Do's and Don'ts

### Do:

- **Do** 고객이 지금 이해해야 할 변화와 다음 선택을 화면의 첫 번째 위계로 둔다.
- **Do** 합성 데이터, 외부 실행 없음, 사람의 최종 판단이라는 경계를 필요한 맥락 가까이에 표시한다.
- **Do** 정상·확인 필요·오류·비활성 상태를 색상과 텍스트로 함께 표현한다.
- **Do** 고객 채널과 직원 채널의 정보 밀도는 다르게 하되 핵심 상태 용어와 의미는 일치시킨다.
- **Do** 큰 글씨에서도 줄바꿈, 버튼 높이, 표와 카드 레이아웃이 유지되도록 설계한다.
- **Do** 공개 홈에서는 핵심 약속과 두 진입 행동, 세 단계 도움 여정만으로 첫 화면의 결정을 완성한다.
- **Do** 모바일에서도 같은 결정 순서를 유지하고 주요 행동을 전체 너비로 제공한다.

### Don't:

- **Don't** `치매 의심`, `인지저하 확률`, `고위험 고객` 같은 낙인 표현이나 위험 점수를 시각적 중심에 둔다.
- **Don't** 임시 팔레트의 초록색을 모든 제목·아이콘·버튼·상태에 반복해 의미를 흐리지 않는다.
- **Don't** 모든 정보를 카드 안의 카드로 감싸거나 모든 아이콘을 둥근 타일에 넣지 않는다.
- **Don't** 공개 홈에 계좌 요약·상태 지표·업무 카드를 쌓아 또 하나의 대시보드를 만들지 않는다.
- **Don't** 일반 금융 화면 전체를 세이지로 물들이거나 금융생활 도움 흐름의 고유 신호를 장식색으로 소비하지 않는다.
- **Don't** 단계 번호를 제목 위의 작은 eyebrow 레이블로 분리해 제목의 시작점을 늦추지 않는다.
- **Don't** AI 초안이나 안내계획 승인을 실제 금융조치처럼 표현하지 않는다.
- **Don't** 작은 회색 글자, 색상만 있는 상태, 44px 미만 조작 영역, 보이지 않는 키보드 포커스를 사용하지 않는다.
- **Don't** 실제 고객 성과, 임상 효과, 금융회사 도입 사례나 검증되지 않은 신뢰 지표를 만들어내지 않는다.
