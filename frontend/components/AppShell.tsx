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
  const links = mode === "customer" ? customerLinks : staffLinks;
  const isActive = (href: string) => href === "/demo" ? pathname === href : pathname.startsWith(href);

  return <div className={`app-shell ${mode}`}>
    <aside>
      <Link className="app-logo" href="/"><span aria-hidden="true">A</span><div><strong>ALZ&apos;s well</strong><small>금융생활 안심 동행</small></div></Link>
      <div className="app-mode"><span>{mode === "customer" ? "고객 채널" : "행원 채널"}</span><b>{mode === "customer" ? "나의 금융생활" : "보호업무 코파일럿"}</b></div>
      <nav className="side-nav" aria-label={mode === "customer" ? "고객 서비스" : "행원 서비스"}>{links.map(([href, label, step]) => <Link className={isActive(href) ? "active" : ""} href={href} key={href}><span>{step}</span><strong>{label}</strong></Link>)}</nav>
      <div className="side-safety"><span aria-hidden="true">✓</span><div><strong>안전한 합성데이터 체험</strong><p>실제 계좌 조회나 금융 실행은 일어나지 않습니다.</p></div></div>
      <Link className="back-home" href="/">← 서비스 소개로</Link>
    </aside>
    <div className="app-content">
      <div className="mobile-app-bar"><Link className="app-logo" href="/"><span aria-hidden="true">A</span><strong>ALZ&apos;s well</strong></Link><Link href={mode === "customer" ? "/staff/cases" : "/demo"}>{mode === "customer" ? "행원 화면" : "고객 화면"}</Link></div>
      <header>
        <div><p className="app-kicker"><span>{mode === "customer" ? "CUSTOMER" : "STAFF"}</span> 2026 금융 AI Challenge</p><h1>{title}</h1><p className="app-subtitle">{mode === "customer" ? "평소와 달라진 금융생활을 쉬운 말로 확인합니다." : "고객 응답과 승인된 근거를 바탕으로 사람이 최종 결정합니다."}</p></div>
        {mode === "customer" ? <AccessibilityControls /> : <div className="service-status"><i /><span><small>데모 모드</small><strong>합성데이터 연결</strong></span></div>}
      </header>
      <div className="channel-switch" aria-label="화면 전환"><Link className={mode === "customer" ? "active" : ""} href="/demo">고객 화면</Link><Link className={mode === "staff" ? "active" : ""} href="/staff/cases">행원 화면</Link></div>
      {children}
    </div>
  </div>;
}
