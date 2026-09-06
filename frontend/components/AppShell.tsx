"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { MemberSessionStatus } from "./MemberSessionStatus";
import { LoginNavigationProvider } from "./LoginNavigationContext";
import { adminLinks, canOpenStaffPage, portalHome, staffLinks, staffRoleFor } from "../lib/portal-access";
import { restorePrivateCustomerSession, type PrivateCustomerSession } from "../lib/private-financial-products";

const customerLinks = [
  ["/demo", "오늘 할 일"],
  ["/demo/alerts", "확인할 알림"],
  ["/demo/ai-assistant", "금융생활 의향 정리"],
  ["/demo/protection", "안심 보호"],
  ["/demo/finance", "내 금융 현황"],
  ["/demo/settings", "도움 설정"],
  ["/demo/services", "개발 참고"],
  ["/demo/products", "금융상품 안내"],
] as const;

export function AppShell({ mode, title, staffRole = "shared", demoStaff = false, children }: { mode: "customer" | "staff"; title: string; staffRole?: "protection" | "admin" | "shared"; demoStaff?: boolean; children: ReactNode }) {
  const pathname = usePathname();
  const [session, setSession] = useState<PrivateCustomerSession | null>(null);
  const [checking, setChecking] = useState(mode === "staff");
  useEffect(() => {
    let active = true;
    if (mode === "staff") void restorePrivateCustomerSession().then((value) => { if (active) setSession(value); })
      .catch(() => { if (active) setSession(null); }).finally(() => { if (active) setChecking(false); });
    return () => { active = false; };
  }, [mode, pathname]);

  const effectiveRole = staffRoleFor(session?.roles ?? []) ?? (staffRole === "admin" ? "admin" : "protection");
  const links = mode === "customer" ? customerLinks : demoStaff ? [["/demo/staff/cases", "시연 사건 검토"], ["/demo", "시연 처음으로"]] : effectiveRole === "admin" ? adminLinks : staffLinks;
  const navigationLabel = mode === "customer" ? "고객 서비스" : effectiveRole === "admin" ? "관리자 서비스" : "행원 서비스";
  const allowed = mode !== "staff" || demoStaff || canOpenStaffPage(session?.roles ?? [], staffRole);
  const requiresLogin = mode === "staff" && !demoStaff && staffRole !== "shared";
  const isActive = (href: string) => href === "/demo" ? pathname === href : pathname.startsWith(href);

  return <LoginNavigationProvider><div className={`app-shell ${mode}`}>
    <a className="skip-link" href="#app-main">본문 바로가기</a>
    <aside className="app-sidebar">
      <Link className="app-logo" href="/"><span aria-hidden="true">A</span><div><strong>ALZ&apos;s well</strong><small>금융생활 안심 동행</small></div></Link>
      <div className="app-mode"><b>{mode === "customer" ? "금융생활 도움받기" : demoStaff ? "시연 업무" : effectiveRole === "admin" ? "관리자 업무" : "행원 업무"}</b></div>
      <nav className="side-nav" aria-label={navigationLabel}>{links.map(([href, label]) => <Link aria-current={isActive(href) ? "page" : undefined} className={isActive(href) ? "active" : ""} href={href} key={href}><strong>{label}</strong></Link>)}</nav>
      <Link className="back-home" href="/">금융서비스 홈으로</Link>
    </aside>
    <main className="app-content" id="app-main" tabIndex={-1}>
      <div className="mobile-app-bar">
        <Link className="app-logo" href="/"><span aria-hidden="true">A</span><strong>ALZ&apos;s well</strong></Link>
        <details className="mobile-navigation"><summary>전체 메뉴</summary><nav aria-label={`모바일 ${navigationLabel}`}>
          {links.map(([href, label]) => <Link aria-current={isActive(href) ? "page" : undefined} className={isActive(href) ? "active" : ""} href={href} key={href}>{label}</Link>)}
          <Link href="/">금융서비스 홈으로</Link>
        </nav></details>
      </div>
      <header><div><h1>{title}</h1></div><div className="customer-header-actions"><MemberSessionStatus /></div></header>
      {requiresLogin && checking ? <section className="panel" aria-busy="true" role="status">로그인 권한을 확인하고 있습니다.</section>
        : allowed ? children
        : <section className="panel portal-access-required"><h2>{session ? "이 화면의 접근 권한이 없습니다." : "운영자 로그인이 필요합니다."}</h2><p>{staffRole === "admin" ? "관리자 계정으로 로그인해 주세요." : "행원 계정으로 로그인해 주세요."}</p><Link className="btn btn-primary" href={`/staff/login?switch=1&next=${encodeURIComponent(pathname)}`}>운영자 로그인</Link>{session && <Link className="btn btn-outline" href={portalHome(session.roles)}>내 업무 화면으로</Link>}</section>}
      <footer className="portal-footer">체험 서비스 · 예시 데이터 사용 · 실제 거래·외부 연락 없음</footer>
    </main>
  </div></LoginNavigationProvider>;
}
