"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { AccessibilityControls } from "./AccessibilityControls";
import { MemberSessionStatus } from "./MemberSessionStatus";

const links = [
  ["/banking", "MY 금융", "⌂"], ["/banking/accounts", "계좌·거래", "₩"],
  ["/banking/transfer", "이체", "↗"], ["/banking/products", "금융상품·자산", "◇"],
  ["/banking/life", "생활금융·고객센터", "▤"], ["/banking/safety", "금융생활 안심관리", "✓"],
  ["/banking/settings", "내 정보·보호", "◎"],
  ["/demo", "금융생활 도움받기", "?"],
] as const;

export function BankingShell({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  const pathname = usePathname();
  const active = (href: string) => href === "/banking" ? pathname === href : pathname.startsWith(href);
  return <div className="banking-shell">
    <header className="banking-topbar">
      <Link className="bank-brand" href="/"><span>A</span><div><strong>ALZ&apos;s well</strong><small>금융생활 안심 동행</small></div></Link>
      <nav aria-label="회원 금융서비스">{links.map(([href, label]) => <Link className={active(href) ? "active" : ""} href={href} key={href}>{label}</Link>)}</nav>
      <div><MemberSessionStatus /><AccessibilityControls /></div>
    </header>
    <div className="banking-mobile-nav">{links.map(([href, label, icon]) => <Link className={active(href) ? "active" : ""} href={href} key={href}><span>{icon}</span>{label}</Link>)}</div>
    <main className="banking-main">
      <section className="banking-title"><div><p>SYNTHETIC PERSONAL BANKING</p><h1>{title}</h1><span>{description}</span></div><aside><i /> 합성데이터 전용 · 실제 금융 실행 없음</aside></section>
      {children}
    </main>
    <footer className="banking-footer"><p>ALZ&apos;s well 합성 금융서비스</p><span>회원가입 없이 제공된 300개 합성 회원 계정으로만 이용합니다.</span><Link href="/">서비스 홈</Link></footer>
  </div>;
}
