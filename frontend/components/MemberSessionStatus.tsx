"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { logoutPrivateCustomer, restorePrivateCustomerSession, type PrivateCustomerSession } from "../lib/private-financial-products";

export function MemberSessionStatus() {
  const [session, setSession] = useState<PrivateCustomerSession | null>(null);
  const [checking, setChecking] = useState(true);

  useEffect(() => {
    let active = true;
    void restorePrivateCustomerSession()
      .then((restored) => { if (active) setSession(restored); })
      .catch(() => undefined)
      .finally(() => { if (active) setChecking(false); });
    return () => { active = false; };
  }, []);

  if (checking) return <div className="member-session-status checking">회원 확인 중</div>;
  if (!session) return <Link className="member-session-status login" href="/login">금융서비스 로그인</Link>;
  return <div className="member-session-status active"><span>{session.displayName.slice(0, 1)}</span><p><strong>{session.displayName}</strong><small>합성 회원 로그인</small></p><button onClick={() => void logoutPrivateCustomer(session).finally(() => setSession(null))}>로그아웃</button></div>;
}
