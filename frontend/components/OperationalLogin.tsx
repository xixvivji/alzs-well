"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { loginPrivateCustomer, restorePrivateCustomerSession } from "../lib/private-financial-products";

export function OperationalLogin() {
  const router = useRouter();
  const [loginId, setLoginId] = useState("staff001");
  const [password, setPassword] = useState("local-synthetic-customer-password");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const route = useCallback((session: { roles: string[] }) => {
    if (session.roles.includes("DETECTION_ADMIN")) router.replace("/staff/control-center");
    else if (session.roles.includes("PROTECTION_STAFF")) router.replace("/staff/operations");
  }, [router]);
  useEffect(() => { void restorePrivateCustomerSession().then(route).catch(() => undefined); }, [route]);
  async function login() {
    setBusy(true); setError("");
    try {
      const session = await loginPrivateCustomer(loginId.trim(), password);
      if (!session.roles.some((role) => role === "PROTECTION_STAFF" || role === "DETECTION_ADMIN")) throw new Error("직원 또는 관리자 역할이 있는 합성 계정만 이용할 수 있습니다.");
      route(session); router.refresh();
    } catch (reason) { setError(reason instanceof Error ? reason.message : "운영 채널에 로그인하지 못했습니다."); }
    finally { setBusy(false); }
  }
  const valid = /^(staff00[1-5]|admin00[1-2])$/.test(loginId);
  return <main className="member-login-page operational-login-page"><section className="member-login-card">
    <Link className="member-login-brand" href="/"><span>A</span><div><strong>ALZ&apos;s well</strong><small>금융생활 보호업무</small></div></Link>
    <div className="member-login-copy"><p>합성 운영 채널</p><h1>보호업무·관리자<br />로그인</h1><span>고객과 분리된 최소권한 역할로 합성 사건·정책·감사정보만 처리합니다.</span></div>
    <form onSubmit={(event) => { event.preventDefault(); void login(); }}>
      <label><span>운영자 ID</span><input autoComplete="username" value={loginId} pattern="(staff00[1-5]|admin00[1-2])" onChange={(event) => setLoginId(event.target.value)} /></label>
      <label><span>비밀번호</span><input type="password" autoComplete="current-password" minLength={12} maxLength={200} value={password} onChange={(event) => setPassword(event.target.value)} /></label>
      <button disabled={busy || !valid || password.length < 12}>{busy ? "권한 확인 중…" : "운영 채널 로그인"}</button>
      {error && <p className="api-error" role="alert">{error}</p>}
    </form>
    <aside><strong>공개 합성 운영 계정</strong><p><b>staff001~staff005</b>는 보호업무, <b>admin001~admin002</b>는 탐지·정책 관리 역할입니다. 공통 비밀번호는 <b>local-synthetic-customer-password</b>이며 실제 금융 조치는 실행하지 않습니다.</p></aside>
    <Link className="member-login-help" href="/login">고객 금융서비스 로그인 →</Link>
  </section></main>;
}
