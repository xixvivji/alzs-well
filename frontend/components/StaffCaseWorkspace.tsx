"use client";

import { useEffect, useState } from "react";
import { ApiClientError } from "../lib/api";
import { isPrivateSessionExpiredError } from "../lib/private-auth-session";
import { restorePrivateCustomerSession } from "../lib/private-financial-products";
import { PrivateStaffCaseQueue } from "./PrivateStaffCaseQueue";

export function StaffCaseWorkspace({ compact = false }: { compact?: boolean }) {
  const [mode, setMode] = useState<"loading" | "private" | "unauthenticated" | "forbidden" | "error">("loading");
  useEffect(() => {
    let cancelled = false;
    void restorePrivateCustomerSession().then((session) => {
      if (cancelled) return;
      setMode(session.roles.includes("PROTECTION_STAFF") ? "private" : "forbidden");
    }).catch((reason) => {
      if (cancelled) return;
      setMode(isPrivateSessionExpiredError(reason) || (reason instanceof ApiClientError && reason.status === 401)
        ? "unauthenticated"
        : reason instanceof ApiClientError && reason.status === 403 ? "forbidden" : "error");
    });
    return () => { cancelled = true; };
  }, []);
  if (mode === "loading") return <section className="panel"><div className="list-skeleton">사건 접근 범위를 확인하고 있습니다.</div></section>;
  if (mode === "private") return <PrivateStaffCaseQueue compact={compact} />;
  if (mode === "unauthenticated") return <section className="panel empty-state"><h2>행원 로그인이 필요합니다.</h2><p>보호업무 계정으로 다시 로그인해 주세요.</p><a className="primary-button" href="/staff/login">행원 로그인</a></section>;
  if (mode === "forbidden") return <section className="panel empty-state"><h2>이 역할에서는 보호업무 사건을 조회할 수 없습니다.</h2><p>보호업무 행원 계정으로 로그인해 주세요.</p></section>;
  return <section className="panel empty-state" role="alert"><h2>사건 정보를 불러오지 못했습니다.</h2><p>잠시 후 다시 시도해 주세요. 공개 체험 사건으로 자동 전환하지 않습니다.</p><button className="primary-button" onClick={() => window.location.reload()}>다시 시도</button></section>;
}
