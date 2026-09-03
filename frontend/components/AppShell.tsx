"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { AccessibilityControls } from "./AccessibilityControls";
import { MemberSessionStatus } from "./MemberSessionStatus";

const customerLinks = [
  ["/demo", "오늘 할 일", "01"],
  ["/demo/alerts", "확인할 알림", "02"],
  ["/demo/ai-assistant", "금융생활 의향 정리", "03"],
  ["/demo/protection", "안심 보호", "04"],
  ["/demo/finance", "내 금융 현황", "05"],
  ["/demo/settings", "도움 설정", "06"],
  ["/demo/services", "서비스 이용 상태", "07"],
  ["/demo/products", "금융상품 안내", "08"],
] as const;
const protectionStaffLinks = [
  ["/staff/cases", "보호업무 사건", "01"],
  ["/staff/operations", "행원 업무서비스", "02"],
  ["/staff/system-status", "시스템·AI 상태", "03"],
] as const;
const adminLinks = [
  ["/staff/control-center", "관리·준법 통제", "01"],
  ["/staff/system-status", "시스템·AI 상태", "02"],
] as const;
const sharedStaffLinks = [
  ["/staff/system-status", "시스템·AI 상태", "01"],
] as const;

export function AppShell({ mode, title, staffRole = "shared", children }: { mode: "customer" | "staff"; title: string; staffRole?: "protection" | "admin" | "shared"; children: ReactNode }) {
  const pathname = usePathname();
  const links = mode === "customer" ? customerLinks : staffRole === "protection" ? protectionStaffLinks : staffRole === "admin" ? adminLinks : sharedStaffLinks;
  const staffLabel = staffRole === "admin" ? "관리자 채널" : staffRole === "protection" ? "행원 채널" : "운영 채널";
  const staffTitle = staffRole === "admin" ? "관리·준법 통제" : staffRole === "protection" ? "보호업무 코파일럿" : "시스템 안전 상태";
  const navigationLabel = mode === "customer" ? "고객 서비스" : staffRole === "admin" ? "관리자 서비스" : staffRole === "protection" ? "행원 서비스" : "운영 서비스";
  const isActive = (href: string) => href === "/demo" ? pathname === href : pathname.startsWith(href);

  return <div className={`app-shell ${mode}`}>
    <a className="skip-link" href="#app-main">본문 바로가기</a>
    <aside>
      <Link className="app-logo" href="/"><span aria-hidden="true">A</span><div><strong>ALZ&apos;s well</strong><small>금융생활 안심 동행</small></div></Link>
      <div className="app-mode"><span>{mode === "customer" ? "개인 금융" : staffLabel}</span><b>{mode === "customer" ? "금융생활 도움받기" : staffTitle}</b></div>
      <nav className="side-nav" aria-label={navigationLabel}>{links.map(([href, label, step]) => <Link aria-current={isActive(href) ? "page" : undefined} className={isActive(href) ? "active" : ""} href={href} key={href}><span>{step}</span><strong>{label}</strong></Link>)}</nav>
      <div className="side-safety"><span aria-hidden="true">✓</span><div><strong>개인정보 없는 안전한 안내</strong><p>실제 계좌 조회나 금융 실행은 일어나지 않습니다.</p></div></div>
      <Link className="back-home" href="/">← 금융서비스 홈으로</Link>
    </aside>
    <main className="app-content" id="app-main" tabIndex={-1}>
      <div className="mobile-app-bar">
        <Link className="app-logo" href="/"><span aria-hidden="true">A</span><strong>ALZ&apos;s well</strong></Link>
        <details className="mobile-navigation"><summary>전체 메뉴</summary><nav aria-label={`모바일 ${navigationLabel}`}>
          {links.map(([href, label, step]) => <Link aria-current={isActive(href) ? "page" : undefined} className={isActive(href) ? "active" : ""} href={href} key={href}><span>{step}</span>{label}</Link>)}
          {mode === "staff" && <Link className="mobile-channel-link" href="/demo">고객 안내 화면으로</Link>}
          <Link href="/">금융서비스 홈으로</Link>
        </nav></details>
      </div>
      <header>
        <div><p className="app-kicker"><span>{mode === "customer" ? "CUSTOMER" : staffRole === "admin" ? "ADMIN" : "STAFF"}</span> {mode === "customer" ? "금융생활 도움 서비스" : staffRole === "admin" ? "관리·준법 통제 채널" : "보호업무 운영 채널"}</p><h1>{title}</h1><p className="app-subtitle">{mode === "customer" ? "평소와 달라진 금융생활을 쉬운 말로 확인합니다." : staffRole === "admin" ? "정책·감사·AI 안전 경계를 관리 역할로 확인합니다." : "고객 응답과 승인된 근거를 바탕으로 사람이 최종 결정합니다."}</p></div>
        {mode === "customer" ? <div className="customer-header-actions"><MemberSessionStatus /><AccessibilityControls /></div> : <div className="customer-header-actions"><MemberSessionStatus /><div className="service-status"><i /><span><small>안전 운영</small><strong>검증 데이터 연결</strong></span></div></div>}
      </header>
      {mode === "staff" && <div className="channel-switch" aria-label="화면 전환"><Link href="/demo">고객 안내</Link><Link className="active" href={staffRole === "admin" ? "/staff/control-center" : staffRole === "protection" ? "/staff/cases" : "/staff/system-status"}>{staffRole === "admin" ? "관리자 통제" : staffRole === "protection" ? "행원 업무" : "시스템 상태"}</Link></div>}
      {children}
    </main>
  </div>;
}
