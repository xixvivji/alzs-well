"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { loginPrivateCustomer, restorePrivateCustomerSession } from "../lib/private-financial-products";
import { loginDestination } from "../lib/portal-access";

export function OperationalLogin() {
  const router = useRouter();
  const [loginId, setLoginId] = useState("staff001");
  const [password, setPassword] = useState("local-synthetic-customer-password");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    const search = new URLSearchParams(window.location.search);
    try {
      const requestedLogin = search.get("loginId");
      const previous = sessionStorage.getItem("alzs:prototype-customer-login") ?? "";
      if (requestedLogin && /^(staff00[1-5]|admin00[1-2])$/.test(requestedLogin)) setLoginId(requestedLogin);
      else if (/^demo\d{3}$/.test(previous)) setLoginId(`staff${String(((Number(previous.slice(4)) - 1) % 5) + 1).padStart(3, "0")}`);
    } catch { /* The server remains the authority for access. */ }
    let active = true;
    if (!search.has("switch") && !search.has("next")) void restorePrivateCustomerSession().then((session) => {
      if (active) router.replace(loginDestination(session.roles, null));
    }).catch(() => undefined);
    return () => { active = false; };
  }, [router]);

  async function login() {
    if (busy) return;
    setBusy(true); setError("");
    try {
      const session = await loginPrivateCustomer(loginId.trim(), password);
      router.replace(loginDestination(session.roles, new URLSearchParams(window.location.search).get("next")));
      router.refresh();
    } catch (reason) { setError(reason instanceof Error ? reason.message : "로그인하지 못했습니다. 입력한 정보를 확인해 주세요."); }
    finally { setBusy(false); }
  }
  const valid = /^(staff00[1-5]|admin00[1-2])$/.test(loginId);
  return <main className="member-login-page operational-login-page"><section className="member-login-card">
    <Link className="member-login-brand" href="/"><span aria-hidden="true">A</span><div><strong>ALZ&apos;s well</strong><small>금융생활 안심 동행</small></div></Link>
    <nav className="login-type-tabs" aria-label="로그인 유형"><Link href="/login?switch=1">개인 로그인</Link><Link href="/staff/login?switch=1" aria-current="page">운영자 로그인</Link></nav>
    <div className="member-login-copy"><h1>운영자 로그인</h1><p>행원은 사건 검토로, 관리자는 관리·준법 화면으로 연결됩니다.</p></div>
    <form aria-busy={busy} onSubmit={(event) => { event.preventDefault(); void login(); }}>
      <label><span>아이디</span><input aria-describedby="operator-id-help" autoComplete="username" value={loginId} pattern="(staff00[1-5]|admin00[1-2])" required onChange={(event) => setLoginId(event.target.value)} /></label>
      <small id="operator-id-help" className="field-help">행원 staff001 ~ staff005 · 관리자 admin001 ~ admin002</small>
      <label><span>비밀번호</span><input type="password" autoComplete="current-password" required minLength={12} maxLength={200} value={password} onChange={(event) => setPassword(event.target.value)} /></label>
      <button disabled={busy || !valid || password.length < 12}>{busy ? "로그인 중…" : "운영자 로그인"}</button>
      {error && <p className="api-error" role="alert">{error}</p>}
    </form>
    <details className="login-account-help"><summary>체험 계정 안내</summary><p>공통 비밀번호: <b>local-synthetic-customer-password</b></p><p>고객 demo001·006…은 staff001, demo002·007…은 staff002가 담당합니다.</p></details>
    <div className="login-exit-links"><Link className="member-login-help" href="/">금융서비스 홈으로</Link></div>
    <p className="login-boundary">체험 서비스 · 예시 데이터 사용 · 실제 거래·외부 연락 없음</p>
  </section></main>;
}
