"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { logoutPrivateCustomer, restorePrivateCustomerSession, type PrivateCustomerSession } from "../lib/private-financial-products";
import { portalHome, staffRoleFor } from "../lib/portal-access";
import { accountDisplayName } from "../lib/presentation-copy";
import { LoginMenu } from "./LoginMenu";

export function MemberSessionStatus() {
  const router = useRouter();
  const [session, setSession] = useState<PrivateCustomerSession | null>(null);
  const [checking, setChecking] = useState(true);
  const [loggingOut, setLoggingOut] = useState(false);

  useEffect(() => {
    let active = true;
    void restorePrivateCustomerSession()
      .then((restored) => { if (active) setSession(restored); })
      .catch(() => undefined)
      .finally(() => { if (active) setChecking(false); });
    return () => { active = false; };
  }, []);

  if (checking) return <LoginMenu />;
  if (!session) return <LoginMenu />;
  const staffRole = staffRoleFor(session.roles);
  return <div className="portal-account"><Link className="portal-account-name" href={portalHome(session.roles)}><strong>{accountDisplayName(session.displayName)}</strong><small>{staffRole === "admin" ? "관리자" : staffRole === "protection" ? "행원" : "개인"}</small></Link><LoginMenu signedIn /><button className="portal-logout" disabled={loggingOut} onClick={() => { setLoggingOut(true); void logoutPrivateCustomer(session).finally(() => { setSession(null); setLoggingOut(false); router.replace("/"); router.refresh(); }); }}>{loggingOut ? "종료 중…" : "로그아웃"}</button></div>;
}
