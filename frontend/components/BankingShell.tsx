"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { AccessibilityControls } from "./AccessibilityControls";
import { MemberSessionStatus } from "./MemberSessionStatus";

const links = [
  ["/banking", "금융 홈", "홈"],
  ["/banking/accounts", "내 계좌", "계좌"],
  ["/banking/transfer", "송금 전 확인", "송금"],
  ["/banking/products", "금융상품", "상품"],
  ["/banking/life", "생활금융", "생활"],
  ["/banking/safety", "안심관리", "안심"],
] as const;
const mobileLinks = [...links, ["/banking/help", "도움받기", "도움"] as const, ["/banking/settings", "내 설정", "설정"] as const];

export function BankingShell({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  const pathname = usePathname();
  const active = (href: string) => href === "/banking" ? pathname === href : pathname.startsWith(href);
  return <div className="banking-shell">
    <a className="skip-link" href="#banking-main">본문 바로가기</a>
    <header className="banking-topbar">
      <Link className="bank-brand" href="/" aria-label="ALZ's well 처음 화면"><span aria-hidden="true">A</span><div><strong>ALZ&apos;s well</strong><small>금융생활 안심 동행</small></div></Link>
      <nav aria-label="회원 금융서비스">{links.map(([href, label]) => <Link aria-current={active(href) ? "page" : undefined} className={active(href) ? "active" : ""} href={href} key={href}>{label}</Link>)}</nav>
      <div><Link aria-current={active("/banking/help") ? "page" : undefined} className={active("/banking/help") ? "banking-help-link active" : "banking-help-link"} href="/banking/help">금융생활 도움받기</Link><MemberSessionStatus /><AccessibilityControls /></div>
    </header>
    <nav className="banking-mobile-nav" aria-label="모바일 금융서비스">{mobileLinks.map(([href, label, icon]) => <Link aria-current={active(href) ? "page" : undefined} className={active(href) ? "active" : ""} href={href} key={href}><span aria-hidden="true">{icon}</span>{label}</Link>)}</nav>
    <main className="banking-main" id="banking-main" tabIndex={-1}>
      <nav className="breadcrumb" aria-label="현재 위치"><Link href="/">홈</Link><span aria-hidden="true">/</span><span>개인 금융</span><span aria-hidden="true">/</span><strong>{title}</strong></nav>
      <section className="banking-title"><div><p>개인 금융서비스</p><h1>{title}</h1><span>{description}</span></div><aside><i /> 합성 데이터로 안전하게 체험 중</aside></section>
      {children}
    </main>
    <footer className="banking-footer"><p>ALZ&apos;s well 금융생활 서비스</p><span>화면의 회원·계좌·거래는 모두 합성 데이터이며 실제 금융거래는 실행되지 않습니다.</span><Link href="/">처음 화면으로</Link></footer>
  </div>;
}
