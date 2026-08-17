import Link from "next/link";
import type { ReactNode } from "react";
export function AppShell({ mode, title, children }: { mode: "customer" | "staff"; title: string; children: ReactNode }) {
  return <div className={`app-shell ${mode}`}><aside><Link className="app-logo" href="/">ALZ&apos;s well</Link><p>{mode === "customer" ? "고객 데모" : "행원 코파일럿"}</p><nav className="side-nav">{mode === "customer" ? <><Link href="/demo">금융생활 요약</Link><Link href="/demo/alerts">변화 알림</Link></> : <><Link href="/staff/cases">보호업무 사건</Link><span>후속 일정</span></>}</nav><Link className="back-home" href="/">← 메인으로</Link></aside><div className="app-content"><header><div><small>{mode === "customer" ? "CUSTOMER" : "STAFF"}</small><h1>{title}</h1></div><div className="status-chip">데모 준비</div></header>{children}</div></div>;
}
