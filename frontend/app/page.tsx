import Link from "next/link";

const financeServices = [
  { title: "금융 홈", description: "자산과 금융 일정을 한눈에 확인", href: "/login?next=/banking" },
  { title: "계좌·거래", description: "잔액, 거래내역과 정기납부 확인", href: "/login?next=/banking/accounts" },
  { title: "송금 전 확인", description: "실제 송금 없이 한도와 조건 점검", href: "/login?next=/banking/transfer" },
  { title: "금융상품", description: "예금·대출·투자·연금 정보 조회", href: "/login?next=/banking/products" },
];

const helpSteps = [
  { step: "1", title: "도움 방식 정하기", description: "유지할 납부와 편한 설명 방식을 직접 정합니다." },
  { step: "2", title: "최근 변화 살펴보기", description: "평소와 최근의 차이를 쉬운 말로 확인합니다." },
  { step: "3", title: "직접 답하거나 도움 요청", description: "내 상황을 선택하고 필요할 때 행원과 이어집니다." },
];

export default function Home() {
  return <div className="bank-home public-home">
    <a className="skip-link" href="#home-main">본문 바로가기</a>

    <div className="public-utility">
      <span>모든 화면은 합성 데이터로 안전하게 체험합니다.</span>
      <nav aria-label="보조 메뉴"><Link href="/staff/login">직원업무</Link><a href="#service-guide">이용안내</a></nav>
    </div>

    <header className="public-header">
      <div className="public-header-inner">
        <Link className="bank-brand" href="/" aria-label="ALZ's well 처음 화면">
          <span aria-hidden="true">A</span>
          <div><strong>ALZ&apos;s well</strong><small>금융생활 안심 동행</small></div>
        </Link>
        <nav className="public-primary-nav shared-customer-nav" aria-label="주요 서비스">
          <Link href="/login?next=/banking">금융 홈</Link>
          <Link href="/login?next=/banking/accounts">계좌·거래</Link>
          <Link href="/login?next=/banking/transfer">송금 전 확인</Link>
          <Link href="/login?next=/banking/products">금융상품</Link>
          <Link className="feature-link" href="/help">금융생활 도움받기</Link>
        </nav>
        <Link className="public-login" href="/login">로그인</Link>
      </div>
    </header>

    <main id="home-main" tabIndex={-1}>
      <section className="public-hero" aria-labelledby="public-hero-title">
        <div className="public-hero-copy">
          <h1 id="public-hero-title"><span className="public-heading-lead">내 금융생활을 한눈에</span>,<br/><em>도움은 내 뜻대로.</em></h1>
          <p className="public-hero-description">일반 금융업무는 편리하게 이용하고, 평소와 다른 변화가 보이면 자동 조치 없이 나에게 먼저 확인합니다.</p>
          <div className="public-hero-actions">
            <Link className="public-primary-action btn btn-primary" href="/login?next=/banking/help">내 금융생활 준비 시작</Link>
            <Link className="public-secondary-action btn btn-outline" href="/login?next=/banking">금융 홈으로</Link>
          </div>
          <p className="public-action-note">도움 방식은 언제든 바꾸거나 철회할 수 있습니다.</p>
        </div>

        <aside className="public-journey" aria-labelledby="journey-title">
          <header><h2 id="journey-title">금융생활 도움은 세 단계예요</h2><b>ALZ&apos;s well 특화</b></header>
          <ol>{helpSteps.map((item) => <li key={item.step}><span aria-hidden="true">{item.step}</span><div><strong>{item.title}</strong><p>{item.description}</p></div></li>)}</ol>
          <p className="public-human-rule">AI는 설명을 돕고, 중요한 판단은 고객과 행원이 합니다.</p>
        </aside>
      </section>

      <section className="public-service-directory" id="service-guide" aria-labelledby="service-directory-title">
        <header><div><h2 id="service-directory-title">무엇을 하시겠어요?</h2><p>메인 화면에서는 길을 찾고, 로그인 후 금융 홈에서 자세한 내용을 확인합니다.</p></div></header>
        <div>{financeServices.map((service) => <Link href={service.href} key={service.title}><strong>{service.title}</strong><span>{service.description}</span><b>바로가기</b></Link>)}</div>
      </section>

      <section className="public-safety-boundary" aria-label="서비스 안전 범위">
        <strong>고객에게 먼저 묻습니다.</strong>
        <p>이 서비스는 질병이나 사기를 진단하지 않으며 실제 송금, 지급정지, 상품 가입 또는 가족 연락을 자동으로 실행하지 않습니다.</p>
        <Link href="/help">대표 도움 흐름 체험</Link>
      </section>
    </main>

    <footer className="public-footer"><div><strong>ALZ&apos;s well</strong><span>금융생활 연속성 준비·조기알림 및 행원 보호업무 코파일럿</span></div><p>합성데이터 전용 체험 서비스 · 실제 금융거래 및 외부 연락 없음</p></footer>
  </div>;
}
