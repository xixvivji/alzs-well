import Link from "next/link";
import type { ReactNode } from "react";
import { AccessibilityControls } from "./AccessibilityControls";
export function AppShell({ mode, title, children }: { mode: "customer" | "staff"; title: string; children: ReactNode }) {
  return <div className={`app-shell ${mode}`}><aside><Link className="app-logo" href="/">ALZ&apos;s well</Link><p>{mode === "customer" ? "고객 안심 서비스" : "행원 코파일럿"}</p><nav className="side-nav">{mode === "customer" ? <><Link href="/demo">내 금융생활</Link><Link href="/demo/ai-assistant">AI 금융생활 도우미</Link><Link href="/demo/alerts">확인할 알림</Link></> : <><Link href="/staff/cases">보호업무 사건</Link><span>후속 일정</span></>}</nav><Link className="back-home" href="/">← 처음 화면으로</Link></aside><div className="app-content"><header><div><small>{mode === "customer" ? "안심 금융생활" : "STAFF"}</small><h1>{title}</h1></div>{mode === "customer" ? <AccessibilityControls /> : <div className="status-chip">데모 준비</div>}</header>{children}</div></div>;
}
