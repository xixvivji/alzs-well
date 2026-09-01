"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { loadAdminOperations, loadStaffOperations, type OperationalBundle } from "../lib/operational-portal";
import { restorePrivateCustomerSession, type PrivateCustomerSession } from "../lib/private-financial-products";

export function OperationalRoleDashboard({ mode }: { mode: "staff" | "admin" }) {
  const [session, setSession] = useState<PrivateCustomerSession | null>(null); const [bundle, setBundle] = useState<OperationalBundle | null>(null); const [error, setError] = useState("");
  useEffect(() => { void restorePrivateCustomerSession().then(async (active) => { setSession(active); setBundle(await (mode === "admin" ? loadAdminOperations(active) : loadStaffOperations(active))); }).catch((reason) => setError(reason instanceof Error ? reason.message : "운영정보를 불러오지 못했습니다.")); }, [mode]);
  if (!session && !error) return <section className="panel operational-role-gate"><p>운영자 권한과 합성데이터를 확인하고 있습니다.</p></section>;
  if (!session || error) return <section className="panel operational-role-gate"><div><p className="label">ROLE REQUIRED</p><h2>운영자 로그인이 필요합니다.</h2><p>{error || "합성 운영 계정으로 로그인해 주세요."}</p></div><Link href="/staff/login">운영 채널 로그인</Link></section>;
  if (!bundle) return null;
  const cards = mode === "admin" ? [["탐지 규칙",bundle.rules.length],["정책 버전",bundle.policies.length],["알고리즘",bundle.algorithms.length],["기능 플래그",bundle.flags.length],["감사 이벤트",bundle.audit.length],["보존 정책",bundle.retention.length]] : [["보호 사건",bundle.cases.length],["처리 권한",session.permissions.length],["자동 금융조치",0]];
  return <section className="operational-role-dashboard"><header><div><p>{mode === "admin" ? "DETECTION ADMIN" : "PROTECTION STAFF"}</p><h2>{session.displayName}님 운영 현황</h2></div><span>{session.roles.join(" · ")}</span></header><div>{cards.map(([label,count]) => <article key={String(label)}><span>{label}</span><strong>{count}</strong><small>합성 운영 데이터</small></article>)}</div><p>조회 결과는 현재 로그인 역할의 Bearer 권한으로 직접 확인했습니다. 변경 작업은 목적·승인·감사값을 갖춘 개별 화면에서만 제공합니다.</p></section>;
}
