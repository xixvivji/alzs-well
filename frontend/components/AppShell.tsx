"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { AccessibilityControls } from "./AccessibilityControls";

const customerLinks = [
  ["/demo", "내 금융생활", "01"],
  ["/demo/protection", "안심 보호센터", "02"],
  ["/demo/finance", "통합 금융생활", "03"],
  ["/demo/ai-assistant", "AI 금융생활 도우미", "04"],
  ["/demo/alerts", "확인할 알림", "05"],
  ["/demo/products", "금융상품·자산", "06"],
  ["/demo/settings", "내 정보·도움 설정", "07"],
  ["/demo/services", "전체 금융서비스", "08"],
] as const;
const staffLinks = [
  ["/staff/cases", "보호업무 사건", "01"],
  ["/staff/operations", "행원 업무서비스", "02"],
  ["/staff/control-center", "관리·준법 통제", "03"],
  ["/staff/system-status", "시스템·AI 상태", "04"],
] as const;

export function AppShell({ mode, title, children }: { mode: "customer" | "staff"; title: string; children: ReactNode }) {
  const pathname = usePathname();
  const privatePocEnabled = process.env.NEXT_PUBLIC_PRIVATE_POC_ENABLED === "true";
  const links = (mode === "customer" ? customerLinks : staffLinks).filter(([href]) =>
    privatePocEnabled || (href !== "/demo/products" && href !== "/demo/settings"));
  const isActive = (href: string) => href === "/demo" ? pathname === href : pathname.startsWith(href);

  return <div className={`app-shell ${mode}`}>
    <aside>
      <Link className="app-logo" href="/"><span aria-hidden="true">A</span><div><strong>ALZ&apos;s well</strong><small>금융생활 안심 동행</small></div></Link>
      <div className="app-mode"><span>{mode === "customer" ? "개인 금융" : "행원 채널"}</span><b>{mode === "customer" ? "금융생활 도움받기" : "보호업무 코파일럿"}</b></div>
      <nav className="side-nav" aria-label={mode === "customer" ? "고객 서비스" : "행원 서비스"}>{links.map(([href, label, step]) => <Link className={isActive(href) ? "active" : ""} href={href} key={href}><span>{step}</span><strong>{label}</strong></Link>)}</nav>
      <div className="side-safety"><span aria-hidden="true">✓</span><div><strong>개인정보 없는 안전한 안내</strong><p>실제 계좌 조회나 금융 실행은 일어나지 않습니다.</p></div></div>
      <Link className="back-home" href="/">← 금융서비스 홈으로</Link>
    </aside>
    <div className="app-content">
      <div className="mobile-app-bar">
        <Link className="app-logo" href="/"><span aria-hidden="true">A</span><strong>ALZ&apos;s well</strong></Link>
        <details className="mobile-navigation"><summary>전체 메뉴</summary><nav aria-label={mode === "customer" ? "모바일 고객 서비스" : "모바일 행원 서비스"}>
          {links.map(([href, label, step]) => <Link className={isActive(href) ? "active" : ""} href={href} key={href}><span>{step}</span>{label}</Link>)}
          {mode === "staff" && <Link className="mobile-channel-link" href="/demo">고객 안내 화면으로</Link>}
          <Link href="/">금융서비스 홈으로</Link>
        </nav></details>
      </div>
      <header>
        <div><p className="app-kicker"><span>{mode === "customer" ? "CUSTOMER" : "STAFF"}</span> {mode === "customer" ? "금융생활 도움 서비스" : "보호업무 운영 채널"}</p><h1>{title}</h1><p className="app-subtitle">{mode === "customer" ? "평소와 달라진 금융생활을 쉬운 말로 확인합니다." : "고객 응답과 승인된 근거를 바탕으로 사람이 최종 결정합니다."}</p></div>
        {mode === "customer" ? <AccessibilityControls /> : <div className="service-status"><i /><span><small>안전 운영</small><strong>검증 데이터 연결</strong></span></div>}
      </header>
      {mode === "staff" && <div className="channel-switch" aria-label="화면 전환"><Link href="/demo">고객 안내</Link><Link className="active" href="/staff/cases">행원 업무</Link></div>}
      {children}
    </div>
  </div>;
}
