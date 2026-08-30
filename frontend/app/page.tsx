import Link from "next/link";

const aiCapabilities = [
  {
    number: "01", label: "의향을 구조로", title: "AI 금융생활 의향서",
    description: "고객의 쉬운 문장을 납부·설명·도움 요청·공유 범위로 정리하고 고객이 직접 승인합니다.",
    meta: "고객 승인 전 저장되지 않음",
  },
  {
    number: "02", label: "변화를 근거로", title: "장기 변화 탐지",
    description: "최근 30일을 이전 60일 기준선과 비교해 EWMA·CUSUM 보조 신호를 이해하기 쉬운 문장으로 보여줍니다.",
    meta: "진단·사기 판정에 사용하지 않음",
  },
  {
    number: "03", label: "설명을 접근 가능하게", title: "쉬운말·음성 안내",
    description: "복잡한 알림을 짧은 문장과 큰 글씨, 느린 음성으로 바꿔 고객이 스스로 확인할 시간을 만듭니다.",
    meta: "브라우저 내 음성 읽기",
  },
  {
    number: "04", label: "행원의 판단을 지원", title: "근거 기반 AI 코파일럿",
    description: "승인된 문서만 검색해 질문과 체크리스트를 만들고, 출처를 함께 제시해 행원이 최종 판단합니다.",
    meta: "citation·장애 폴백 검증",
  },
];

const scenarios = [
  { kind: "정상", title: "알고 있는 생활 변화", result: "고객 확인 후 사건 없이 종결", tone: "normal" },
  { kind: "주의", title: "확인하기 어려운 변화", result: "행원 보호업무와 안내계획 연결", tone: "caution" },
  { kind: "오탐", title: "사실확인 결과 정상", result: "행원이 근거를 기록하고 종결", tone: "false" },
];

const organizers = [
  ["주최·주관", "금융보안원"],
  ["후원", "금융위원회"],
  ["공동개최", "하나은행 · 신한은행 · 카카오뱅크 · KB증권 · 생명보험협회"],
  ["운영", "데이콘"],
];

export default function Home() {
  return <main className="landing-page">
    <header className="site-header">
      <Link className="brand-lockup" href="/" aria-label="ALZ's well 홈">
        <span className="brand-mark" aria-hidden="true">A</span>
        <span><strong>ALZ&apos;s well</strong><small>금융생활 안심 동행</small></span>
      </Link>
      <nav aria-label="주요 메뉴">
        <a href="#problem">문제와 해법</a><a href="#ai">AI 기능</a><a href="#safety">안전 원칙</a>
        <Link className="header-cta" href="/demo">MVP 체험하기</Link>
      </nav>
    </header>

    <section className="hero" aria-labelledby="hero-title">
      <div className="hero-atmosphere" aria-hidden="true"><i /><i /><i /></div>
      <div className="hero-copy">
        <p className="challenge-badge"><span>2026</span> 금융 AI Challenge 참가 프로젝트</p>
        <h1 id="hero-title">금융생활의 작은 변화,<br /><em>먼저 확인하고 함께 지킵니다.</em></h1>
        <p className="hero-description">치매머니는 고객의 금융행동을 감시하거나 질병을 진단하지 않습니다. 평소와 달라진 생활 신호를 설명하고, 고객에게 먼저 묻고, 필요한 경우에만 행원의 보호업무로 연결합니다.</p>
        <div className="hero-actions">
          <Link className="button button-primary" href="/demo">고객 흐름 체험하기 <span aria-hidden="true">→</span></Link>
          <Link className="button button-secondary" href="/staff/cases">행원 화면 보기</Link>
        </div>
        <ul className="hero-boundaries" aria-label="서비스 안전 원칙">
          <li><span aria-hidden="true">✓</span> 합성데이터만 사용</li><li><span aria-hidden="true">✓</span> 실제 금융 실행 없음</li><li><span aria-hidden="true">✓</span> 사람 최종 승인</li>
        </ul>
      </div>

      <div className="hero-product" aria-label="장기 변화 탐지 화면 예시">
        <div className="product-topline"><span>금융생활 변화 브리핑</span><b><i /> 안전 체험</b></div>
        <div className="product-heading"><div><small>최근 90일 기준선</small><h2>설명이 필요한 변화가 있어요.</h2></div><span className="product-date">오늘</span></div>
        <div className="signal-card signal-primary">
          <div><span className="signal-icon">01</span><p><small>거래결과 재확인</small><strong>평소보다 4배 증가</strong></p></div>
          <div className="mini-chart" aria-label="이전 60일 월 2회, 최근 30일 8회"><i style={{height:"22%"}} /><i style={{height:"31%"}} /><i style={{height:"28%"}} /><i className="active" style={{height:"88%"}} /></div>
        </div>
        <div className="signal-card"><div><span className="signal-icon">02</span><p><small>새로운 수취인</small><strong>최근 30일 2건</strong></p></div><span className="signal-status">맥락 확인</span></div>
        <div className="product-question"><span>AI 쉬운 설명</span><p>최근 한 달 동안 송금 결과를 다시 확인한 횟수가 이전보다 늘었어요. 알고 계신 활동인가요?</p><div><button type="button">알고 있어요</button><button type="button">잘 모르겠어요</button></div></div>
        <p className="product-caption">예시 화면 · 질병 또는 사기 여부를 자동 판단하지 않습니다.</p>
      </div>
    </section>

    <section className="proof-strip" aria-label="서비스 핵심 수치">
      <div><strong>30·60·90일</strong><span>개인 기준선 비교</span></div><div><strong>3개</strong><span>정상·주의·오탐 시나리오</span></div><div><strong>4단계</strong><span>고객 확인부터 행원 승인까지</span></div><div><strong>0건</strong><span>자동 연락·지급정지·거래 실행</span></div>
    </section>

    <section className="section problem-section" id="problem">
      <div className="section-intro"><p className="section-label">THE PROBLEM</p><h2>이상거래를 찾는 것보다,<br />고객이 이해하고 답할 수 있게 만드는 일.</h2><p>고령 금융소비자에게 필요한 것은 더 강한 경고가 아니라, 평소와 무엇이 달라졌는지 이해하고 자신의 맥락을 말할 수 있는 안전한 확인 과정입니다.</p></div>
      <div className="principle-list">
        <article><span>발견</span><div><h3>개인별 생활 기준선</h3><p>모두에게 같은 위험 점수를 적용하지 않고 본인의 이전 금융생활과 비교합니다.</p></div></article>
        <article><span>확인</span><div><h3>고객에게 먼저 질문</h3><p>이상·치매·사기로 단정하지 않고 알고 있는 변화인지 쉬운 말로 묻습니다.</p></div></article>
        <article><span>연결</span><div><h3>사람이 완성하는 보호업무</h3><p>확인이 어려운 사건만 승인된 근거와 함께 행원 검토로 전달합니다.</p></div></article>
      </div>
    </section>

    <section className="section ai-section" id="ai">
      <div className="section-heading-row"><div><p className="section-label">AI, WITH BOUNDARIES</p><h2>AI는 결정하지 않고,<br />이해와 확인을 돕습니다.</h2></div><p>생성 결과보다 근거, 자동화보다 통제, 위험 점수보다 설명 가능한 변화를 우선합니다.</p></div>
      <div className="capability-grid">{aiCapabilities.map((item) => <article key={item.number}><div className="capability-number">{item.number}</div><p className="capability-label">{item.label}</p><h3>{item.title}</h3><p>{item.description}</p><span>{item.meta}</span></article>)}</div>
    </section>

    <section className="section journey-section">
      <div className="journey-copy"><p className="section-label">ONE CLOSED LOOP</p><h2>한 번의 시연으로<br />고객에서 행원까지.</h2><p>심사 화면에서 핵심 흐름을 끊김 없이 확인할 수 있도록 합성 시나리오를 고정했습니다.</p><Link className="text-link" href="/demo">세 가지 시나리오 시작하기 →</Link></div>
      <ol className="journey-steps">
        <li><span>1</span><div><small>AI SIGNAL</small><h3>장기 변화 발견</h3><p>30·60·90일 기준선과 최근 구간을 비교합니다.</p></div></li>
        <li><span>2</span><div><small>CUSTOMER FIRST</small><h3>고객 맥락 확인</h3><p>알고 있음·도움 필요·나중에 확인 중 하나를 선택합니다.</p></div></li>
        <li><span>3</span><div><small>HUMAN REVIEW</small><h3>행원 검토 연결</h3><p>승인 문서 citation과 사실확인 질문을 함께 봅니다.</p></div></li>
        <li><span>4</span><div><small>CONTROLLED CLOSE</small><h3>안내계획 승인 또는 종결</h3><p>모든 최종 판단과 기록은 행원이 수행합니다.</p></div></li>
      </ol>
    </section>

    <section className="section scenario-section">
      <div className="section-heading-row"><div><p className="section-label">DEMO SCENARIOS</p><h2>정상도, 주의도, 오탐도<br />끝까지 보여드립니다.</h2></div><Link className="button button-dark" href="/demo">발표 리허설 열기</Link></div>
      <div className="scenario-grid">{scenarios.map((scenario, index) => <article className={scenario.tone} key={scenario.kind}><span>0{index + 1}</span><div><small>{scenario.kind} 시나리오</small><h3>{scenario.title}</h3><p>{scenario.result}</p></div><b aria-hidden="true">↗</b></article>)}</div>
    </section>

    <section className="section safety-section" id="safety">
      <div className="safety-statement"><p className="section-label">SAFETY BY DESIGN</p><h2>금융권에서 쓸 수 있는 AI는<br />멈출 줄 알아야 합니다.</h2><p>승인된 지식이 없거나 AI 서비스가 중단되면 추측하지 않고, 출처 없는 결정론적 안전 문구로 전환합니다.</p></div>
      <div className="safety-columns"><article><p>AI가 하는 일</p><ul><li>개인 기준선의 변화 설명</li><li>고객 의향 구조화 초안</li><li>승인된 문서 검색과 인용</li><li>행원 질문·체크리스트 제안</li></ul></article><article className="not"><p>AI가 하지 않는 일</p><ul><li>치매 또는 사기 판정</li><li>고객 대신 의향서 승인</li><li>지급정지·송금·외부 연락</li><li>행원 대신 최종 결정</li></ul></article></div>
    </section>

    <section className="section entry-section">
      <Link className="entry-card customer-entry" href="/demo"><span>고객용 MVP</span><div><p>큰 글씨와 쉬운 문장으로</p><h2>내 금융생활 확인하기</h2></div><b>체험 시작 <i aria-hidden="true">→</i></b></Link>
      <Link className="entry-card staff-entry" href="/staff/cases"><span>행원용 MVP</span><div><p>근거와 안전 경계를 한 화면에서</p><h2>보호업무 검토하기</h2></div><b>화면 보기 <i aria-hidden="true">→</i></b></Link>
    </section>

    <footer id="support">
      <div className="footer-brand"><span className="brand-mark" aria-hidden="true">A</span><div><strong>ALZ&apos;s well</strong><p>고령 금융소비자의 자기결정권을 지키는 금융생활 안심 동행</p></div></div>
      <div className="organizer-grid">{organizers.map(([role, names]) => <div key={role}><small>{role}</small><span>{names}</span></div>)}</div>
      <div className="footer-bottom"><p>본 화면은 2026 금융 AI Challenge 참가 프로젝트이며 각 기관의 공식 서비스가 아닙니다.</p><p>합성데이터 전용 · 실제 금융 실행 없음 · 의료 진단 아님</p></div>
    </footer>
  </main>;
}
