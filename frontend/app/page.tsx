import Link from "next/link";

const primaryNavigation = [
  { label: "계좌·거래", href: "/login?next=/banking/accounts" },
  { label: "송금 전 확인", href: "/login?next=/banking/transfer" },
  { label: "금융상품", href: "/login?next=/banking/products" },
  { label: "생활금융", href: "/login?next=/banking/life" },
  { label: "안심관리", href: "/login?next=/banking/safety" },
  { label: "이용안내", href: "#support" },
];
const quickServices = [
  { icon: "₩", title: "계좌조회", description: "내 계좌와 잔액을 한눈에", href: "/login?next=/banking/accounts" },
  { icon: "↗", title: "송금 전 확인", description: "보내기 전 한도와 조건 확인", href: "/login?next=/banking/transfer" },
  { icon: "▤", title: "거래내역", description: "입출금 내역을 빠르게 확인", href: "/login?next=/banking/accounts" },
  { icon: "◇", title: "금융상품", description: "예금·대출·투자 상품 확인", href: "/login?next=/banking/products" },
  { icon: "?", title: "금융생활 도움받기", description: "로그인 상태에 맞춰 도움 연결", href: "/help", featured: true },
];
const productCards = [
  { eyebrow: "목돈 마련", title: "차곡차곡 안심적금", description: "생활 계획에 맞춰 부담 없이 시작하는 저축", rate: "최고 연 4.10%", tone: "mint" },
  { eyebrow: "생활 안정", title: "든든 생활비 통장", description: "공과금과 정기지출을 한곳에서 편리하게 관리", rate: "수수료 우대", tone: "navy" },
  { eyebrow: "노후 준비", title: "함께 보는 연금관리", description: "흩어진 노후자산을 보기 쉽게 모아 확인", rate: "맞춤형 조회", tone: "sand" },
];
const notices = [
  ["안내", "금융생활 안심 서비스 이용 안내", "2026.09.01"],
  ["보안", "전자금융 이용 시 안전수칙을 확인해 주세요", "2026.08.29"],
  ["소식", "고령 고객을 위한 쉬운 금융 안내 확대", "2026.08.27"],
];

export default function Home() {
  return <main className="bank-home">
    <a className="skip-link" href="#home-main">본문 바로가기</a>
    <div className="bank-utility"><div><span>개인</span><span>기업</span></div><div><Link href="/staff/login">직원업무</Link><a href="#notices">새소식</a><a href="#support">고객센터</a><button type="button" aria-label="화면 언어 선택">한국어⌄</button></div></div>
    <header className="bank-header">
      <Link className="bank-brand" href="/" aria-label="ALZ's well 홈"><span aria-hidden="true">A</span><div><strong>ALZ&apos;s well</strong><small>금융생활 안심 동행</small></div></Link>
      <nav aria-label="주요 금융 메뉴">{primaryNavigation.map((item) => <Link href={item.href} key={item.label}>{item.label}</Link>)}</nav>
      <div className="bank-header-actions"><Link className="header-login" href="/login">로그인</Link><Link className="header-help" href="/help">도움받기</Link></div>
    </header>

    <div id="home-main" tabIndex={-1}>
    <section className="bank-hero">
      <div className="bank-hero-copy"><p>처음이어도 쉽게 쓰는 금융서비스</p><h1>내 금융생활을 한눈에,<br/><em>필요한 도움까지 바로.</em></h1><span>계좌 확인, 송금 전 점검, 금융생활 도움을 원하는 곳에서 시작하세요.</span><div><Link className="bank-button primary" href="/login">금융서비스 시작</Link><a className="bank-button ghost" href="#quick">할 수 있는 일 보기</a></div></div>
      <aside className="bank-login-card" id="login" aria-label="금융서비스 이용 안내"><div><span aria-hidden="true">◎</span><p><strong>안전한 금융서비스</strong><small>인증 후 내 합성 금융정보를 확인할 수 있어요.</small></p></div><Link href="/login">금융서비스 로그인</Link><p>회원가입 없이 제공된 합성 회원 계정으로 이용합니다.</p></aside>
      <div className="bank-hero-shape" aria-hidden="true"><i/><i/><i/></div>
    </section>

    <section className="bank-quick" id="quick" aria-labelledby="quick-title"><div className="bank-section-heading"><div><p>바로가기</p><h2 id="quick-title">무엇을 하시겠어요?</h2></div><span>가장 자주 쓰는 일을 한 번에 시작하세요.</span></div><div className="bank-quick-grid">{quickServices.map((service) => <Link className={service.featured ? "featured" : ""} href={service.href} key={service.title}><span aria-hidden="true">{service.icon}</span><div><strong>{service.title}</strong><small>{service.description}</small></div>{service.featured && <b>처음이라면 여기</b>}</Link>)}</div></section>

    <section className="bank-life" id="life"><div className="bank-life-copy"><p>생활금융 안심 서비스</p><h2>금융생활이 복잡하게 느껴질 때,<br/>쉽게 설명해 드릴게요.</h2><span>평소와 달라진 금융생활을 쉬운 말로 확인하고, 잘 모르겠을 때는 필요한 도움을 받을 수 있습니다.</span><ul><li>큰 글씨와 선명한 화면</li><li>평소와 달라진 점 설명</li><li>고객에게 먼저 확인</li></ul><Link href="/help">금융생활 도움받기 <b aria-hidden="true">→</b></Link></div><div className="bank-life-preview" aria-label="금융생활 도움 화면 미리보기"><div><small>오늘의 금융생활</small><strong>확인할 변화가 1개 있어요</strong></div><article><span>!</span><p><small>쉬운 설명</small><strong>최근 송금 결과를 다시 확인한 횟수가 늘었어요.</strong></p></article><div className="bank-life-actions"><span>알고 있어요</span><span>잘 모르겠어요</span></div><p>질병이나 사기로 단정하지 않고 고객에게 먼저 확인합니다.</p></div></section>

    <section className="bank-products" id="products"><div className="bank-section-heading"><div><p>FINANCIAL PRODUCTS</p><h2>나에게 맞는 금융상품</h2></div><Link href="/login?next=/banking/products">상품 전체보기 →</Link></div><div className="bank-product-grid">{productCards.map((product) => <article className={product.tone} key={product.title}><p>{product.eyebrow}</p><h3>{product.title}</h3><span>{product.description}</span><strong>{product.rate}</strong><Link href="/login?next=/banking/products" aria-label={`${product.title} 상세보기`}>＋</Link></article>)}</div></section>

    <section className="bank-info" id="notices"><div><div className="bank-section-heading compact"><h2>새소식</h2><a href="#notices">더보기 ＋</a></div><ul>{notices.map(([type,title,date]) => <li key={title}><span>{type}</span><strong>{title}</strong><time>{date}</time></li>)}</ul></div><aside id="support"><p>고객센터</p><strong>1588-0000</strong><span>평일 09:00~18:00</span><div><a href="#support">자주 묻는 질문</a><a href="#support">금융사고 신고</a><a href="#support">이용안내</a></div></aside></section>

    </div>
    <footer className="bank-footer"><div><Link className="bank-brand inverse" href="/"><span>A</span><strong>ALZ&apos;s well</strong></Link><p>고령 금융소비자의 자기결정권을 지키는 금융생활 안심 동행</p></div><nav aria-label="하단 서비스 메뉴"><a href="#support">개인정보처리방침</a><a href="#support">전자금융거래약관</a><a href="#support">보안센터</a><Link href="/staff/login">직원업무</Link></nav><p>합성데이터 전용 체험 서비스 · 실제 금융거래 및 외부 연락 없음</p></footer>
  </main>;
}
