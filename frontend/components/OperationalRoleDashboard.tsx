"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { loadAdminOperations, loadStaffOperations, type OperationalBundle } from "../lib/operational-portal";
import { restorePrivateCustomerSession, type PrivateCustomerSession } from "../lib/private-financial-products";

export function OperationalRoleDashboard({ mode }: { mode: "staff" | "admin" }) {
  const [session, setSession] = useState<PrivateCustomerSession | null>(null); const [bundle, setBundle] = useState<OperationalBundle | null>(null); const [error, setError] = useState("");
  useEffect(() => { void restorePrivateCustomerSession().then(async (active) => { setSession(active); setBundle(await (mode === "admin" ? loadAdminOperations(active) : loadStaffOperations(active))); }).catch((reason) => setError(reason instanceof Error ? reason.message : "운영정보를 불러오지 못했습니다.")); }, [mode]);
  if (!session && !error) return <section className="panel operational-role-gate" aria-busy="true" aria-live="polite"><p>운영자 권한과 합성데이터를 확인하고 있습니다.</p></section>;
  if (!session) return <section className="panel operational-role-gate"><div><p className="label">LOGIN REQUIRED</p><h2>운영자 로그인이 필요합니다.</h2><p>{error || "합성 운영 계정으로 로그인해 주세요."}</p></div><Link href="/staff/login">운영 채널 로그인</Link></section>;
  if (error) return <section className="panel operational-role-gate"><div><p className="label">ROLE REQUIRED</p><h2>이 역할에서는 해당 운영 화면을 사용할 수 없습니다.</h2><p>{error}</p></div><Link href={session.roles.includes("DETECTION_ADMIN") ? "/staff/control-center" : "/staff/operations"}>내 역할 화면으로 이동</Link></section>;
  if (!bundle) return <section className="panel operational-role-gate" aria-busy="true" aria-live="polite"><p>역할에 맞는 운영 현황을 불러오고 있습니다.</p></section>;
  const cards: Array<[string, string | number]> = mode === "admin" ? [["탐지 규칙",bundle.rules.length],["정책 버전",bundle.policies.length],["알고리즘",bundle.algorithms.length],["기능 플래그",bundle.flags.length],["감사 이벤트",bundle.auditAuthorized ? bundle.audit.length : "별도 권한"],["보존 정책",bundle.retention.length]] : [["보호 사건",bundle.cases.length],["처리 권한",session.permissions.length],["자동 금융조치",0]];
  return <section className="operational-role-dashboard"><header><div><p>{mode === "admin" ? "DETECTION ADMIN" : "PROTECTION STAFF"}</p><h2>{session.displayName}님 운영 현황</h2></div><span>{session.roles.join(" · ")}</span></header><div>{cards.map(([label,count]) => <article key={String(label)}><span>{label}</span><strong>{count}</strong><small>합성 운영 데이터</small></article>)}</div><p>조회 결과는 현재 로그인 역할의 Bearer 권한으로 직접 확인했습니다. 변경 작업은 목적·승인·감사값을 갖춘 개별 화면에서만 제공합니다.</p>
    {mode === "staff" && <div className="operational-live-board"><section><h3>우선 검토 사건</h3>{bundle.cases.slice(0, 5).map((item, index) => <article key={textValue(item, "caseId", index)}><span>{textValue(item, "reviewPriority", "확인")}</span><div><strong>{textValue(item, "summary", "고객 확인 요청")}</strong><small>{textValue(item, "customerId", "-")} · {textValue(item, "state", "-")}</small></div></article>)}</section><section><h3>선택 사건 연결 범위</h3><dl><div><dt>타임라인</dt><dd>{bundle.timeline.length}건</dd></div><div><dt>근거</dt><dd>{bundle.evidence.length}건</dd></div><div><dt>내부 메모</dt><dd>{bundle.notes.length}건</dd></div><div><dt>후속관리</dt><dd>{bundle.followUps.length}건</dd></div><div><dt>금융생활 의향</dt><dd>{bundle.intentSummary ? "확인됨" : "없음"}</dd></div></dl></section></div>}
    {mode === "admin" && <>{bundle.partialFailures.length > 0 && <section className="panel api-error" role="alert"><h3>일부 운영정보를 불러오지 못했습니다.</h3><ul>{bundle.partialFailures.map((failure) => <li key={failure.section}><strong>{sectionLabel(failure.section)}</strong>: {failure.message}</li>)}</ul><p>표시되지 않은 항목을 빈 데이터로 판단하지 말고 잠시 후 다시 조회해 주세요.</p></section>}<div className="operational-live-board"><section><h3>현재 탐지 규칙</h3>{bundle.rules.slice(0, 5).map((item, index) => <article key={textValue(item, "ruleId", index)}><span>{textValue(item, "status", "규칙")}</span><div><strong>{textValue(item, "name", textValue(item, "ruleCode", "탐지 규칙"))}</strong><small>버전 {textValue(item, "version", "-")}</small></div></article>)}</section><section><h3>통제 조회 상태</h3><dl><div><dt>규칙 상세</dt><dd>{statusLabel(bundle, "ruleDetail", Boolean(bundle.ruleDetail))}</dd></div><div><dt>감사 이벤트 상세</dt><dd>{bundle.auditAuthorized ? statusLabel(bundle, "auditDetail", Boolean(bundle.auditDetail)) : "별도 감사권한 필요"}</dd></div><div><dt>정책 버전</dt><dd>{statusLabel(bundle, "policies", bundle.policies.length)}</dd></div><div><dt>보존 정책</dt><dd>{statusLabel(bundle, "retention", bundle.retention.length)}</dd></div></dl></section></div><AiQualityPanel quality={bundle.aiQuality} failed={bundle.partialFailures.some((failure) => failure.section === "aiQuality")} /></>}
  </section>;
}

function AiQualityPanel({ quality, failed }: { quality: OperationalBundle["aiQuality"]; failed: boolean }) {
  if (!quality) return <section className="panel"><p className="label">AI QUALITY</p><h2>{failed ? "운영 품질 조회 실패" : "운영 품질 집계 대기"}</h2><p>{failed ? "현재 값을 빈 집계로 판단하지 말고 다시 조회해 주세요." : "아직 집계된 운영 데이터가 없습니다."}</p></section>;
  const percent = (value: number) => `${(value * 100).toFixed(1)}%`;
  return <section className="panel"><div className="section-heading"><div><p className="label">AI QUALITY · 최근 {quality.windowHours}시간</p><h2>검색과 안전 폴백 품질</h2></div><span className={`status-chip ${quality.status === "ATTENTION" ? "warning" : ""}`}>{quality.status === "HEALTHY" ? "정상" : quality.status === "ATTENTION" ? "확인 필요" : "데이터 없음"}</span></div><div className="metric-grid"><article><span>검색 요청</span><strong>{quality.searchRequests}</strong><small>근거 연결 {quality.groundedSearches}</small></article><article><span>검색 폴백률</span><strong>{percent(quality.searchFallbackRate)}</strong><small>폴백 {quality.fallbackSearches}건</small></article><article><span>인용 거부</span><strong>{quality.rejectedCitations}</strong><small>Spring 재검증 결과</small></article><article><span>AI 도움 폴백률</span><strong>{percent(quality.assistanceFallbackRate)}</strong><small>요청 {quality.assistanceRequests}건</small></article></div><p>합성 운영 데이터만 집계하며 질문 원문과 개인식별정보는 저장하거나 표시하지 않습니다.</p></section>;
}

function sectionLabel(section: OperationalBundle["partialFailures"][number]["section"]) {
  return ({ policies: "정책 버전", algorithms: "알고리즘", aiQuality: "AI 품질", flags: "기능 플래그", audit: "감사 이벤트", retention: "보존 정책", ruleDetail: "규칙 상세", auditDetail: "감사 상세" })[section];
}

function statusLabel(bundle: OperationalBundle, section: OperationalBundle["partialFailures"][number]["section"], value: boolean | number) {
  if (bundle.partialFailures.some((failure) => failure.section === section)) return "조회 실패";
  return typeof value === "number" ? `${value}개` : value ? "연결" : "대상 없음";
}

function textValue(record: Record<string, unknown>, key: string, fallback: string | number) {
  const value = record[key]; return typeof value === "string" || typeof value === "number" ? String(value) : String(fallback);
}
