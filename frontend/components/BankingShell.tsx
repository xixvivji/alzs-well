"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { getPrivateChangeIndicator, loadPrivateChangeStatus, type PrivateChangeStatus } from "../lib/private-help";
import { restorePrivateCustomerSession } from "../lib/private-financial-products";
import { MemberSessionStatus } from "./MemberSessionStatus";

const links = [
  ["/banking", "금융 홈"],
  ["/banking/accounts", "계좌·거래"],
  ["/banking/transfer", "송금 전 확인"],
  ["/banking/products", "금융상품"],
] as const;
const continuityPaths = ["/banking/help", "/banking/life", "/banking/safety", "/banking/settings"];
const BankingChangeStatusContext = createContext<PrivateChangeStatus | null | undefined>(undefined);

export function useBankingChangeStatus() {
  return useContext(BankingChangeStatusContext);
}

export function BankingShell({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  const pathname = usePathname();
  const [changeStatus, setChangeStatus] = useState<PrivateChangeStatus | null | undefined>(undefined);
  const active = (href: string) => href === "/banking" ? pathname === href : pathname.startsWith(href);
  const continuityFeature = continuityPaths.some((href) => pathname.startsWith(href));
  const shellClassName = continuityFeature ? "banking-shell banking-shell--continuity" : "banking-shell banking-shell--standard";

  useEffect(() => {
    let mounted = true;
    let generation = 0;
    const refreshChangeStatus = () => {
      const currentGeneration = ++generation;
      setChangeStatus(undefined);
      void restorePrivateCustomerSession()
        .then((session) => loadPrivateChangeStatus(session))
        .then((status) => { if (mounted && generation === currentGeneration) setChangeStatus(status); })
        .catch(() => { if (mounted && generation === currentGeneration) setChangeStatus(null); });
    };
    refreshChangeStatus();
    window.addEventListener("alzs:change-status-updated", refreshChangeStatus);
    return () => {
      mounted = false;
      window.removeEventListener("alzs:change-status-updated", refreshChangeStatus);
    };
  }, [pathname]);

  const changeIndicator = changeStatus ? getPrivateChangeIndicator(changeStatus) : null;
  const helpActive = continuityFeature;
  return <BankingChangeStatusContext.Provider value={changeStatus}><div className={shellClassName} data-service-kind={continuityFeature ? "continuity" : "standard"}>
    <a className="skip-link" href="#banking-main">본문 바로가기</a>
    <header className="banking-topbar">
      <Link className="bank-brand" href="/" aria-label="ALZ's well 처음 화면"><span aria-hidden="true">A</span><div><strong>ALZ&apos;s well</strong><small>금융생활 안심 동행</small></div></Link>
      <nav className="public-primary-nav shared-customer-nav" aria-label="회원 금융서비스">
        {links.map(([href, label]) => <Link aria-current={active(href) ? "page" : undefined} className={active(href) ? "active" : ""} href={href} key={href}>{label}</Link>)}
        <Link aria-current={helpActive ? "page" : undefined} aria-label={changeIndicator ? `금융생활 도움받기, ${changeIndicator.ariaLabel} ${changeIndicator.count}건` : "금융생활 도움받기"} className={`feature-link${helpActive ? " active" : ""}${changeIndicator ? " has-change" : ""}`} href="/banking/help">금융생활 도움받기{changeIndicator && <span aria-hidden="true">{changeIndicator.badge} {changeIndicator.count}</span>}</Link>
      </nav>
      <div><MemberSessionStatus /></div>
    </header>
    <main className="banking-main" id="banking-main" tabIndex={-1}>
      <nav className="breadcrumb" aria-label="현재 위치"><Link href="/">홈</Link><span aria-hidden="true">/</span><span>개인 금융</span><span aria-hidden="true">/</span><strong>{title}</strong></nav>
      <section className="banking-title"><div><h1>{title}</h1><span>{description}</span></div><aside><i /> {continuityFeature ? "금융생활 변화 확인 · 합성 데이터" : "합성 데이터로 안전하게 체험 중"}</aside></section>
      {children}
    </main>
    <footer className="banking-footer"><p>ALZ&apos;s well 금융생활 서비스</p><span>화면의 회원·계좌·거래는 모두 합성 데이터이며 실제 금융거래는 실행되지 않습니다.</span><Link href="/">처음 화면으로</Link></footer>
  </div></BankingChangeStatusContext.Provider>;
}
