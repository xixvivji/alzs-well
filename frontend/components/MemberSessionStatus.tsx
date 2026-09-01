"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { logoutPrivateCustomer, restorePrivateCustomerSession, type PrivateCustomerSession } from "../lib/private-financial-products";

export function MemberSessionStatus() {
  const pathname = usePathname();
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
  if (!session) return <Link className="member-session-status login" href={pathname.startsWith("/staff") ? "/staff/login" : "/login"}>{pathname.startsWith("/staff") ? "운영 채널 로그인" : "금융서비스 로그인"}</Link>;
  const operational = session.roles.some((role) => role === "PROTECTION_STAFF" || role === "DETECTION_ADMIN");
  return <div className="member-session-status active"><span>{session.displayName.slice(0, 1)}</span><Link href={operational ? "/staff/operations" : "/banking/settings"}><strong>{session.displayName}</strong><small>{operational ? "합성 운영자 로그인" : "내 정보·보호 설정"}</small></Link><button onClick={() => void logoutPrivateCustomer(session).finally(() => setSession(null))}>로그아웃</button></div>;
}
