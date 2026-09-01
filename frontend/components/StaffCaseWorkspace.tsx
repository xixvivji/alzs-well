"use client";

import { useEffect, useState } from "react";
import { restorePrivateCustomerSession } from "../lib/private-financial-products";
import { PrivateStaffCaseQueue } from "./PrivateStaffCaseQueue";
import { StaffCaseQueue } from "./StaffCaseQueue";

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
  if (mode === "forbidden") return <section className="panel empty-state"><h2>이 역할에서는 보호업무 사건을 조회할 수 없습니다.</h2><p>보호업무 행원 계정으로 로그인해 주세요.</p></section>;
  return <StaffCaseQueue compact={compact} />;
}
