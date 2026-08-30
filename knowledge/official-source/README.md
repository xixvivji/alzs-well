# ALZ's well 공식 원문 corpus

> 기본 수집일: 2026-08-21 KST · 통신사기피해환급법 시행령 현행본 추가: 2026-08-26 KST  
> 상태: 원문 보존본. RAG 색인 전 반입 승인·이용조건·최신성 검토 필요

이 디렉터리는 ALZ's well의 폐쇄망 RAG와 자체 정책 작성에 필요한 공개 공식문서를 보존한다. PDF·HWP는 원본 그대로 유지하고, HTML 보존본은 공개 저장소 게시 전에 내장된 지도 SDK 키·외부 링크 API 키·페이지 토큰만 `REDACTED_SOURCE_CREDENTIAL`로 치환한다. 본문은 수정하지 않으며 `SHA256SUMS.txt`로 저장소 반입본의 무결성을 확인한다.

## 분류

### `01_laws`

- 개인정보 보호법 및 시행령
- 금융소비자 보호에 관한 법률 및 시행령
- 신용정보의 이용 및 보호에 관한 법률
- 전자금융거래법
- 전자금융감독규정 공식 원문 페이지

`*_current`는 파일명이 버전을 보장한다는 뜻이 아니라 **2026-08-21 수집 당시 공식 사이트가 반환한 현행본**이라는 뜻이다. 색인 전 시행일과 개정 여부를 다시 검증한다.

### `02_elderly-financial-protection`

- 금융위원회 「고령친화 금융환경 조성방안」

### `03_privacy-ai`

- 개인정보보호위원회 「생성형 인공지능(AI) 개발·활용을 위한 개인정보 처리 안내서」
- 개인정보보호위원회 「개인정보의 안전성 확보조치 기준 안내서」
- 개인정보보호위원회 「가명정보 처리 가이드라인」

### `04_financial-ai-security`

- 금융보안원 「금융분야 AI 보안 가이드라인」
- 금융보안원 「금융분야 인공지능 보안 안내서」
- 금융위원회 「금융권 생성형 AI 활용 지원 방안」

### `05_source-pages`

- 고령층 금융상품 계약 시 지정인 알림서비스 공식 안내 페이지
- 신탁업 혁신 및 고령층 재산관리 관련 공식 안내 페이지
- 생성형 AI 개인정보 처리 안내서의 공식 게시 페이지

HTML은 해당 시점의 공식 게시 페이지 보존본이다. 법률·정책의 효력 있는 전체 원문을 대신하지 않으며, 게시기관·게시일·첨부파일·출처 확인용으로 사용한다.

### `06_fss-consumer-protection`

- 금융감독원 「반짝반짝 은빛 노후를 위한 금융가이드」 개정판 3권
  - 탄탄한 노후를 위한 금융생활설계
  - 금융사기 예방과 노후자산 정리
  - 바로 지금! 꼭 알아야 할 디지털금융
- 각 교재의 e-금융교육센터 공식 게시 페이지
- 금융소비자 정보포털 FINE 공식 메인 페이지

### `07_guardianship`

- 보건복지부 「치매공공후견 사례집」
- 경기도광역치매센터 「치매공공후견사업 실무 가이드」
- 법제처 찾기쉬운 생활법령의 성년후견·치매노인 후견 안내 페이지

법제처 생활법령 안내는 법령 원문이나 유권해석을 대신하지 않는다. 사례집과 실무 가이드 역시 현재 사업 기준과 지원대상을 색인 전에 재확인한다.

### `08_bank-public-services`

- 하나은행: 지정인 알림서비스, 금융접근성·소비자보호 안내, 관련 규정·체크리스트
- 신한은행: ISA 신청서 중 지정인 알림 관련 공개 서식
- 카카오뱅크: 지연이체·금융사기 예방 안내 및 전자금융거래 약관
- KB증권: 고령투자자 보호·금융접근성 안내 및 투자권유준칙

금융회사 공개 자료는 업계의 실제 서비스 사례를 확인하기 위한 참고자료이다. ALZ's well의 자체 정책이나 모든 금융회사에 공통 적용되는 규정으로 취급하지 않는다.

### `09_guardianship-laws`

- 국가법령정보센터 현행 민법 원문과 성년후견 관련 조문 페이지
- 국가법령정보센터 치매관리법 원문

성년후견·한정후견·특정후견의 요건과 치매공공후견 지원의 법적 근거를 확인하기 위한 최우선 법령 자료다.

### `10_court-guardianship-forms`

- 가정법원 성년·한정·특정후견 절차별 공식 양식 안내 페이지
- 성년후견과 한정후견의 판단기준에 관한 대법원 2020스596 결정문 및 게시 페이지

전자민원센터 개별 양식 파일 서버는 수집 시점에 DNS 응답이 없어 절차별 공식 목록까지만 보존했다. 실제 제출용 양식은 이용 시점에 법원 전자민원센터에서 최신본을 다시 받아야 한다.

### `11_fraud-and-recovery`

- 예금보험공사 착오송금 반환지원 신청대상 확인 페이지
- 중앙치매센터 치매공공후견 담당자 교육 안내 페이지

착오송금 반환지원은 보이스피싱 등 사기 송금의 피해구제 제도가 아니므로 두 상황을 구분해 안내해야 한다.

### `12_bank-protection-services`

- KB국민은행 지연이체 및 금융사기 예방서비스
- 우리은행 보이스피싱 피해예방 서비스
- IBK기업은행 지연이체 서비스

서비스 조건·이용시간·신청 및 해지 채널은 변경될 수 있으므로 답변에는 금융회사 공식 페이지 재확인을 함께 안내한다.

### `13_common-legal-and-protection`

- 치매관리법 시행령 및 시행규칙
- 전기통신금융사기 피해 방지 및 피해금 환급에 관한 특별법 및 시행령
- 예금자보호법 중 착오송금 반환지원의 근거

PDF와 공식 게시 페이지를 함께 보존했다. 가상 금융기관인 `안심은행`·`안심증권`의 공통 안내 규칙은 개별 금융회사 자료보다 이 법령 묶음을 우선 근거로 사용한다.

### `14_public-response-guides`

- 금융위원회 비대면 계좌개설 안심차단 Q&A
- 비대면 계좌개설·여신거래 안심차단 안내
- 간편송금 및 대면편취형 보이스피싱 피해구제 안내
- 경찰청 사이버범죄 신고시스템의 피싱·파밍 신고 안내

피해가 이미 발생한 상황은 AI가 자체 판단하거나 처리하지 않고 금융기관 지급정지 요청 및 경찰 신고 등 공식 채널을 안내하는 근거로만 사용한다.

### `15_court-form-catalogs`

- 성년·한정·특정후견 개시 심판청구서 공식 HWP 양식
- 성년후견 등 재산목록보고서와 후견사무보고서 공식 HWP 양식
- 각 양식의 대한민국 법원 공식 카탈로그 페이지

양식은 사용자에게 자동 작성·제출하지 않는다. 절차와 준비서류 안내에만 활용하고 실제 제출 전 법원 최신본을 확인하도록 안내한다.

### `16_dementia-money-public-services`

- 국민연금공단 2026년 치매안심재산관리서비스 안내·보도자료
- 치매안심재산관리 상담 신청서, 본인·대리인 개인정보 동의서, 대상자 의뢰서
- 중앙치매센터 치매가이드북·치매어르신 서비스 안내서·개인정보 동의서
- 복지로 2026년 치매검사비 지원 안내
- 국민건강보험공단 노인장기요양보험 제도·인정 신청·등급 안내

금액·소득기준·시범사업 지역·신청기관은 변경될 수 있으므로 수집일과 기준연도를 함께 표시한다. RAG는 치매 여부나 서비스 수급자격을 판정하지 않고, 가능한 공공서비스와 공식 확인 채널을 근거와 함께 안내하는 용도로만 사용한다.

## 가상 금융기관명 사용 원칙

- 제품 DB와 화면에서는 실제 회사명 대신 `안심은행`, `안심증권` 등 가상 기관명을 사용한다.
- 개별 금융회사 공개자료는 가능한 서비스 유형을 확인하는 사례 자료일 뿐, 가상 기관이 실제로 동일한 조건의 서비스를 제공한다고 표현하지 않는다.
- 금액·처리시간·가입 및 해지 조건처럼 회사마다 다른 값은 자체 승인 정책 문서에서 정의한다.
- 법령·감독기관·공공기관 자료와 자체 제품정책을 검색 결과에서 구분하고 출처 유형을 표시한다.

## 원문 출처

- 국가법령정보센터: <https://www.law.go.kr>
- 금융위원회 고령친화 금융환경 조성방안: <https://www.fsc.go.kr/po010101/74512>
- 금융위원회 지정인 알림서비스: <https://www.fsc.go.kr/po010106/73764>
- 금융위원회 신탁업 혁신 방안: <https://www.fsc.go.kr/po010104/78704>
- 금융위원회 금융권 생성형 AI 활용 지원 방안: <https://www.fsc.go.kr/po010101/83594>
- 개인정보보호위원회 생성형 AI 개인정보 처리 안내서: <https://www.pipc.go.kr/np/cop/bbs/selectBoardArticle.do?bbsId=BS217&mCode=G010030030&nttId=11439>
- 개인정보보호위원회 가명정보 처리 가이드라인: <https://www.pipc.go.kr/np/cop/bbs/selectBoardArticle.do?bbsId=BS217&mCode=G010030030&nttId=11931>
- 개인정보보호위원회 안전성 확보조치 기준 안내서: <https://www.pipc.go.kr/np/cop/bbs/selectBoardArticle.do?bbsId=BS217&mCode=G010030030&nttId=11641>
- 금융보안원 금융분야 AI 보안 가이드라인: <https://www.fsec.or.kr/bbs/detail?bbsNo=11240&menuNo=69>
- 금융보안원 금융분야 인공지능 보안 안내서: <https://www.fsec.or.kr/bbs/detail?bbsNo=11977&menuNo=222>
- 금융감독원 고령층 금융가이드 1권: <https://www.fss.or.kr/edu/fec/contMng/view.do?contentsSlno=518&menuNo=300017>
- 금융감독원 고령층 금융가이드 2권: <https://www.fss.or.kr/edu/fec/contMng/view.do?contentsSlno=519&menuNo=300017>
- 금융감독원 고령층 금융가이드 3권: <https://www.fss.or.kr/edu/fec/contMng/view.do?contentsSlno=520&menuNo=300017>
- 금융감독원 금융소비자 정보포털 FINE: <https://fine.fss.or.kr/>
- 보건복지부 치매공공후견 사례집: <https://www.mohw.go.kr/upload/140/202008/1598832118278_20200831090305.pdf>
- 법제처 성년후견 안내: <https://www.easylaw.go.kr/CSP/CnpClsMain.laf?ccfNo=3&cciNo=1&cnpClsNo=1&csmSeq=694>
- 법제처 치매노인 성년후견 안내: <https://m.easylaw.go.kr/MOB/CsmInfoRetrieve.laf?ccfNo=3&cciNo=1&cnpClsNo=2&csmSeq=854>
- 경기도광역치매센터 치매공공후견 실무 가이드: <https://gyeonggi.nid.or.kr/download/download.aspx?filename=%EA%B2%BD%EA%B8%B0%EB%8F%84+%EC%B9%98%EB%A7%A4%EA%B3%B5%EA%B3%B5%ED%9B%84%EA%B2%AC%EC%82%AC%EC%97%85+%EC%8B%A4%EB%AC%B4+%EA%B0%80%EC%9D%B4%EB%93%9C.pdf&path=%2Fcenter_publication_file%2F2908%2F%EA%B2%BD%EA%B8%B0%EB%8F%84+%EC%B9%98%EB%A7%A4%EA%B3%B5%EA%B3%B5%ED%9B%84%EA%B2%AC%EC%82%AC%EC%97%85+%EC%8B%A4%EB%AC%B4+%EA%B0%80%EC%9D%B4%EB%93%9C.pdf>
- 하나은행 지정인 알림서비스: <https://www.kebhana.com/cont/mall/mall12/mall1201/1468060_115454.jsp>
- 카카오뱅크 금융사기 예방 시스템: <https://www.kakaobank.com/Help/FinanceFraud/Prevention/System>
- KB증권 고령투자자 보호 안내: <https://www.kbsec.com/go.able?linkcd=s500231060000>
- 국가법령정보센터 민법: <https://www.law.go.kr/LSW/lsInfoP.do?lsiSeq=284415>
- 국가법령정보센터 치매관리법: <https://law.go.kr/LSW/lsInfoP.do?ancYnChk=0&chrClsCd=010202&efYd=20240703&lsiSeq=257869&urlMode=lsInfoP>
- 가정법원 후견 절차별 양식 안내: <https://suwon.scourt.go.kr/slfamily/civil_complaint/civil_06/index_03.html>
- 대법원 2020스596 결정: <https://www.scourt.go.kr/supreme/news/NewsViewAction2.work?gubun=4&searchOption=&searchWord=&seqnum=7789>
- 예금보험공사 착오송금 반환지원: <https://mkcs.kdic.or.kr/ir/msdrpr/selectAplyQlfcIdntyRslt.do>
- 중앙치매센터 치매공공후견 교육 안내: <https://edu.nid.or.kr/common/menu/html/900001001/detail.do>
- KB국민은행 지연이체서비스: <https://obank.kbstar.com/quics?page=C039265>
- 우리은행 보이스피싱 피해예방 서비스: <https://spot.wooribank.com/pot/Dream?ARTICLE_ID=40080&BOARD_ID=B00445&bbsMode=view&withyou=CQCNT0009>
- IBK기업은행 지연이체서비스: <https://blog.ibk.co.kr/2712>
- 국가법령정보센터 치매관리법 시행령: <https://www.law.go.kr/법령/치매관리법시행령>
- 국가법령정보센터 치매관리법 시행규칙: <https://www.law.go.kr/법령/치매관리법시행규칙>
- 국가법령정보센터 통신사기피해환급법: <https://www.law.go.kr/법령/전기통신금융사기피해방지및피해금환급에관한특별법>
- 국가법령정보센터 통신사기피해환급법 시행령: <https://www.law.go.kr/법령/전기통신금융사기피해방지및피해금환급에관한특별법시행령>
- 국가법령정보센터 예금자보호법: <https://www.law.go.kr/법령/예금자보호법>
- 금융위원회 비대면 계좌개설 안심차단 Q&A: <https://www.fsc.go.kr/po020201/84124>
- 금융위원회 비대면 계좌개설·여신거래 안심차단: <https://www.fsc.go.kr/no040103/84442>
- 금융위원회 간편송금 보이스피싱 피해차단: <https://www.fsc.go.kr/po010101/82912>
- 금융위원회 대면편취형 보이스피싱 피해구제: <https://www.fsc.go.kr/po010101/81090>
- 경찰청 사이버범죄 신고시스템: <https://ecrm.police.go.kr/minwon/crs/quick/cyber1>
- 대한민국 법원 후견 양식 자료실: <https://jifi.scourt.go.kr/foreigner/doc/KoFgnDocListAction.work>
- 국민연금공단 치매안심재산관리서비스 신청 안내: <https://www.nps.or.kr/pnsinfo/databbs/getOHAF0279M1.do?menuId=MN24000998&tmpltDataSn=5912>
- 국민연금공단 치매안심재산관리서비스 보도자료: <https://www.nps.or.kr/pnsgdnc/nscvrgdata/getOHAE0002M1.do?hmpgBbsCd=BS20240145&hmpgCd=01&menuId=MN24000898&pageIndex=1&pstId=ZZ202600000000000453>
- 중앙치매센터 자료실: <https://www.nid.or.kr>
- 복지로 2026년 치매검사비 지원: <https://www.bokjiro.go.kr/ssis-tbu/twataa/wlfareInfo/moveTWAT52011M.do?wlfareInfoId=WLF00005004&wlfareInfoReldBztpCd=01>
- 국민건강보험공단 노인장기요양보험 제도 안내: <https://www.nhis.or.kr/static/html/wbda/c/wbdac01.html>
- 국민건강보험공단 노인장기요양보험 인정 신청: <https://www.nhis.or.kr/static/html/wbda/c/wbdac02.html>
- 국민건강보험공단 노인장기요양보험 등급 안내: <https://www.nhis.or.kr/static/html/wbda/c/wbdac03.html>

## 반입 및 색인 전 필수 절차

1. 공식 게시 페이지에서 최신본·시행일·폐지 여부를 재확인한다.
2. 저작권정책·공공누리·내부 이용조건을 문서별로 기록한다.
3. 악성파일 검사와 PDF JavaScript·첨부객체 검사를 수행한다.
4. `SHA256SUMS.txt`와 실제 파일 해시가 일치하는지 확인한다.
5. 승인자·승인일·접근등급·효력기간을 문서 카탈로그에 등록한다.
6. 원문 보존본은 수정하지 않고 별도의 파생 텍스트와 chunk를 생성한다.
7. 법령과 가이드는 동일한 권위로 취급하지 않고 문서유형과 우선순위를 구분한다.
8. HTML 자격증명 마스킹 여부를 manifest에 기록하고, 마스킹 값을 검색 근거로 사용하지 않는다.

이 corpus는 법률자문이나 금융회사 내부규정을 대신하지 않는다. 실제 도입 시 해당 금융회사가 제공하고 승인한 내부통제·행원업무 문서를 별도 권한영역에 적재해야 한다.
