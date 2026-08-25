import Link from "next/link";
import { IntroFilm } from "../components/IntroFilm";

const hosts = ["하나은행", "신한은행", "카카오뱅크", "KB증권", "생명보험협회"];

export default function Home() {
  return <main><IntroFilm />
    <header className="site-header"><Link className="brand" href="/">ALZ&apos;s well</Link><nav aria-label="주요 메뉴"><a href="#about">ALZ&apos;s well</a><a href="#flow">금융안심 서비스</a><a href="#support">고객지원</a><Link className="header-cta" href="/demo">서비스 체험</Link></nav></header>
    <section className="hero"><div className="hero-media"><video className="hero-video" autoPlay muted loop playsInline preload="metadata" aria-hidden="true"><source src="/hero-film.webm" type="video/webm"/><source src="/hero-film.mp4" type="video/mp4"/></video><div className="hero-overlay"/></div><div className="hero-copy"><p className="eyebrow">금융생활 변화 조기알림</p><h1>금융생활의 작은 변화,<br/>먼저 알아차리도록.</h1><p className="hero-description">평소와 다른 금융생활 변화를 발견하고, 고객의 맥락을 확인한 뒤 필요한 경우에만 행원의 보호업무로 연결합니다.</p><div className="hero-actions"><Link className="primary-button" href="/demo">안심 서비스 체험하기</Link><a href="#about">서비스 알아보기 →</a></div></div></section>
    <section className="section intro" id="about"><p className="section-label">WHAT WE NOTICE</p><h2>익숙한 일상에서 달라진 신호를 살핍니다.</h2><div className="feature-grid"><article><strong>01</strong><h3>정기납부 변화</h3><p>평소 이어지던 납부가 누락되었는지 확인합니다.</p></article><article><strong>02</strong><h3>반복된 금융행동</h3><p>짧은 시간 안에 반복된 송금과 확인 행동을 살핍니다.</p></article><article><strong>03</strong><h3>생활맥락 확인</h3><p>이상으로 단정하지 않고 고객에게 먼저 이유를 묻습니다.</p></article></div></section>
    <section className="section flow" id="flow"><div><p className="section-label">HOW IT WORKS</p><h2>고객의 확인에서<br/>행원의 보호업무까지</h2></div><ol><li><span>1</span><div><h3>변화 발견</h3><p>합성 금융데이터에서 평소와 다른 변화를 찾습니다.</p></div></li><li><span>2</span><div><h3>고객 맥락 확인</h3><p>고객이 알고 있는 정상적인 변화인지 확인합니다.</p></div></li><li><span>3</span><div><h3>필요한 경우 연결</h3><p>확인이 어려운 사건만 행원의 검토 화면으로 전달합니다.</p></div></li></ol></section>
    <section className="section entry-section"><Link className="entry-card customer" href="/demo"><p>FOR CUSTOMER</p><h2>고객 데모</h2><span>금융생활 요약과 변화 알림 확인 →</span></Link><Link className="entry-card staff" href="/staff/cases"><p>FOR STAFF</p><h2>행원 코파일럿</h2><span>보호업무 사건과 근거 검토 →</span></Link></section>
    <footer id="support"><p>공동개최</p><div className="host-list">{hosts.map(host=><span key={host}>{host}</span>)}</div><small>본 서비스는 합성데이터만 사용하는 데모입니다. 실제 금융 실행이나 외부 연락을 수행하지 않습니다.</small></footer>
  </main>;
}
