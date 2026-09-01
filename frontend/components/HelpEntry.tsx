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
      .catch(() => { if (active) router.replace("/demo"); });
    return () => { active = false; };
  }, [router]);
  return <main className="help-entry-page"><section><span aria-hidden="true">?</span><p>금융생활 도움받기</p><h1>이용 중인 도움 서비스를 확인하고 있어요.</h1><p>로그인한 회원은 본인의 합성 금융데이터로, 비로그인 사용자는 공개 시나리오로 안전하게 연결합니다.</p><div><Link href="/login?next=/banking/help">합성 회원으로 로그인</Link><Link href="/demo">공개 시나리오 체험</Link></div></section></main>;
}
