"use client";

import { useRouter } from "next/navigation";
import Link from "next/link";
import { useEffect, useState } from "react";
import { loginPrivateCustomer, restorePrivateCustomerSession } from "../lib/private-financial-products";

export function MemberLogin() {
  const router = useRouter();
  const [loginId, setLoginId] = useState("demo001");
  const [password, setPassword] = useState("local-synthetic-customer-password");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    void restorePrivateCustomerSession().then(() => router.replace("/banking")).catch(() => undefined);
  }, [router]);

  async function login() {
    setBusy(true); setError("");
    try {
      await loginPrivateCustomer(loginId.trim(), password);
      router.replace("/banking");
      router.refresh();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "로그인하지 못했습니다.");
    } finally { setBusy(false); }
  }

  return <main className="member-login-page">
    <section className="member-login-card">
      <Link className="member-login-brand" href="/"><span>A</span><div><strong>ALZ&apos;s well</strong><small>금융생활 안심 동행</small></div></Link>
      <div className="member-login-copy"><p>합성 금융서비스</p><h1>내 금융생활에<br />로그인하세요.</h1><span>실제 개인정보가 아닌 300명의 분리된 합성 회원·계좌·거래 데이터로만 동작합니다.</span></div>
      <form onSubmit={(event) => { event.preventDefault(); void login(); }}>
        <label><span>회원 ID</span><input autoComplete="username" value={loginId} pattern="demo[0-9]{3}" onChange={(event) => setLoginId(event.target.value)} /></label>
        <label><span>비밀번호</span><input type="password" autoComplete="current-password" minLength={12} maxLength={200} value={password} onChange={(event) => setPassword(event.target.value)} /></label>
        <button disabled={busy || !/^demo[0-9]{3}$/.test(loginId) || password.length < 12}>{busy ? "안전하게 확인 중…" : "금융서비스 로그인"}</button>
        {error && <p className="api-error" role="alert">{error}</p>}
      </form>
      <aside><strong>공개 합성 체험 계정</strong><p><b>demo001 ~ demo300</b> 중 하나와 공통 비밀번호 <b>local-synthetic-customer-password</b>를 이용하세요. 300명은 계좌·거래·설정이 서로 분리되어 있습니다.</p></aside>
      <Link className="member-login-help" href="/demo">로그인 없이 금융생활 도움받기 →</Link>
    </section>
  </main>;
}
