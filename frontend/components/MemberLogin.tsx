"use client";

import { useRouter } from "next/navigation";
import Link from "next/link";
import { useEffect, useState } from "react";
import { loginPrivateCustomer, restorePrivateCustomerSession } from "../lib/private-financial-products";
import { loginDestination } from "../lib/portal-access";

export function MemberLogin() {
  const router = useRouter();
  const [loginId, setLoginId] = useState("demo001");
  const [password, setPassword] = useState("local-synthetic-customer-password");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [nextPath, setNextPath] = useState("/banking");

  useEffect(() => {
    const search = new URLSearchParams(window.location.search);
    const requested = search.get("next") ?? "/banking";
    const requestedLogin = search.get("loginId");
    if (requestedLogin && /^demo\d{3}$/.test(requestedLogin)) setLoginId(requestedLogin);
    else try { const previous = sessionStorage.getItem("alzs:prototype-customer-login"); if (previous && /^demo\d{3}$/.test(previous)) setLoginId(previous); } catch { /* Optional navigation memory. */ }
    setNextPath(requested);
    let active = true;
    if (!requestedLogin && !search.has("switch")) void restorePrivateCustomerSession().then((session) => {
      if (active) router.replace(loginDestination(session.roles, requested));
    }).catch(() => undefined);
    return () => { active = false; };
  }, [router]);

  async function login() {
    if (busy) return;
    setBusy(true); setError("");
    try {
      const session = await loginPrivateCustomer(loginId.trim(), password);
      try { sessionStorage.setItem("alzs:prototype-customer-login", loginId.trim()); } catch { /* Optional navigation memory. */ }
      router.replace(loginDestination(session.roles, nextPath));
      router.refresh();
    } catch (reason) { setError(reason instanceof Error ? reason.message : "로그인하지 못했습니다. 입력한 정보를 확인해 주세요."); }
    finally { setBusy(false); }
  }

  return <main className="member-login-page">
    <section className="member-login-card">
      <Link className="member-login-brand" href="/"><span aria-hidden="true">A</span><div><strong>ALZ&apos;s well</strong><small>금융생활 안심 동행</small></div></Link>
      <nav className="login-type-tabs" aria-label="로그인 유형"><Link href="/login?switch=1" aria-current="page">개인 로그인</Link><Link href="/staff/login?switch=1">운영자 로그인</Link></nav>
      <div className="member-login-copy"><h1>개인 로그인</h1><p>내 금융현황과 확인할 변화를 살펴보세요.</p></div>
      <form aria-busy={busy} onSubmit={(event) => { event.preventDefault(); void login(); }}>
        <label><span>아이디</span><input aria-describedby="member-id-help" autoComplete="username" value={loginId} pattern="demo[0-9]{3}" required onChange={(event) => setLoginId(event.target.value)} /></label>
        <small id="member-id-help" className="field-help">체험 계정: demo001 ~ demo300</small>
        <label><span>비밀번호</span><input type="password" autoComplete="current-password" required minLength={12} maxLength={200} value={password} onChange={(event) => setPassword(event.target.value)} /></label>
        <button disabled={busy || !/^demo[0-9]{3}$/.test(loginId) || password.length < 12}>{busy ? "로그인 중…" : "개인 로그인"}</button>
        {error && <p className="api-error" role="alert">{error}</p>}
      </form>
      <details className="login-account-help"><summary>체험 계정 안내</summary><p>demo001 ~ demo300 중 선택할 수 있습니다.</p><p>공통 비밀번호: <b>local-synthetic-customer-password</b></p></details>
      <div className="login-exit-links"><Link className="member-login-help" href="/">금융서비스 홈으로</Link></div>
      <p className="login-boundary">체험 서비스 · 예시 데이터 사용 · 실제 거래·외부 연락 없음</p>
    </section>
  </main>;
}
