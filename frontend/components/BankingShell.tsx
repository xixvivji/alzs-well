"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { AccessibilityControls } from "./AccessibilityControls";
import { MemberSessionStatus } from "./MemberSessionStatus";

const links = [
  ["/banking/accounts", "조회", "₩"], ["/banking/transfer", "이체 사전확인", "↗"],
  ["/banking/products", "금융상품", "◇"], ["/banking", "자산관리", "⌂"],
  ["/banking/life", "생활금융", "▤"], ["/banking/safety", "안심관리", "✓"],
] as const;
const mobileLinks = [...links, ["/banking/settings", "내 정보·보호", "◎"] as const, ["/banking/help", "금융생활 도움받기", "?"] as const];

export function BankingShell({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  const pathname = usePathname();
  const active = (href: string) => href === "/banking" ? pathname === href : pathname.startsWith(href);
  return <div className="banking-shell">
    <header className="banking-topbar">
      <Link className="bank-brand" href="/"><span>A</span><div><strong>ALZ&apos;s well</strong><small>금융생활 안심 동행</small></div></Link>
      <nav aria-label="회원 금융서비스">{links.map(([href, label]) => <Link className={active(href) ? "active" : ""} href={href} key={href}>{label}</Link>)}</nav>
      <div><Link className={active("/banking/help") ? "banking-help-link active" : "banking-help-link"} href="/banking/help">도움받기</Link><MemberSessionStatus /><AccessibilityControls /></div>
    </header>
    <div className="banking-mobile-nav">{mobileLinks.map(([href, label, icon]) => <Link className={active(href) ? "active" : ""} href={href} key={href}><span>{icon}</span>{label}</Link>)}</div>
    <main className="banking-main">
      <section className="banking-title"><div><p>PERSONAL BANKING</p><h1>{title}</h1><span>{description}</span></div><aside><i /> 운영형 합성 데이터 · 외부 금융 실행 없음</aside></section>
      {children}
    </main>
    <footer className="banking-footer"><p>ALZ&apos;s well 금융생활 서비스</p><span>300개 합성 회원을 실제 운영과 같은 인증·권한·감사 흐름으로 분리합니다.</span><Link href="/">서비스 홈</Link></footer>
  </div>;
}
