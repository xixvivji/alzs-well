"use client";

import { useEffect, useState } from "react";
import { restorePrivateCustomerSession } from "../lib/private-financial-products";
import { PrivateStaffCaseQueue } from "./PrivateStaffCaseQueue";
import Link from "next/link";

export function StaffCaseWorkspace({ compact = false }: { compact?: boolean }) {
  const [mode, setMode] = useState<"loading" | "private" | "demo" | "forbidden">("loading");
  useEffect(() => {
    let cancelled = false;
    void restorePrivateCustomerSession().then((session) => {
      if (cancelled) return;
      setMode(session.roles.includes("PROTECTION_STAFF") ? "private" : "forbidden");
    }).catch(() => { if (!cancelled) setMode("demo"); });
    return () => { cancelled = true; };
  }, []);
  if (mode === "loading") return <section className="panel"><div className="list-skeleton">사건 접근 범위를 확인하고 있습니다.</div></section>;
  if (mode === "private") return <PrivateStaffCaseQueue compact={compact} />;
  if (mode === "forbidden") return <section className="panel empty-state"><h2>행원 로그인이 필요합니다.</h2><p>현재 고객·관리자 계정으로는 보호업무 사건을 볼 수 없습니다.</p><Link className="primary-button" href="/staff/login?next=/staff/cases">행원으로 로그인</Link></section>;
  return <section className="panel"><h2>운영자 로그인이 필요합니다.</h2><Link className="btn btn-primary" href="/staff/login?switch=1&next=/staff/cases">운영자 로그인</Link></section>;
}
