"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { restorePrivateCustomerSession } from "../lib/private-financial-products";

export function HelpEntry() {
  const router = useRouter();
  useEffect(() => {
    let active = true;
    void restorePrivateCustomerSession()
      .then(() => { if (active) router.replace("/banking/help"); })
      .catch(() => { if (active) router.replace("/login?next=/banking/help"); });
    return () => { active = false; };
  }, [router]);
  return <main className="help-entry-page"><section><span aria-hidden="true">?</span><p>금융생활 도움받기</p><h1>이용 중인 도움 서비스를 확인하고 있어요.</h1><p>잠시만 기다려 주세요.</p><div><Link href="/login?next=/banking/help">개인 로그인</Link></div></section></main>;
}
