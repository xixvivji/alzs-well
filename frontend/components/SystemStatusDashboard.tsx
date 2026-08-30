"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { loadSystemStatus, type SystemStatusSnapshot } from "../lib/system-status";

const CHECK_LABELS: Record<string, string> = {
  database: "Private RDS", flyway: "DB 스키마", syntheticFixtures: "합성 시나리오",
  policyCatalog: "보호정책 카탈로그", detectionPolicy: "탐지 정책", safeGuardrails: "안전 가드레일",
  aiRetrieval: "AI 승인 근거 검색",
};

export function SystemStatusDashboard() {
  const [snapshot, setSnapshot] = useState<SystemStatusSnapshot | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const refresh = useCallback(async () => {
    setLoading(true); setError("");
    try { setSnapshot(await loadSystemStatus()); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "시스템 상태를 확인하지 못했습니다."); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { void refresh(); const timer = window.setInterval(() => void refresh(), 30_000); return () => window.clearInterval(timer); }, [refresh]);

  if (!snapshot) return <section className="panel system-status-empty"><div className={loading ? "bank-spinner" : "status-outage"}>{loading ? "" : "!"}</div><h2>{loading ? "서비스 준비상태를 확인하고 있습니다." : "상태 API에 연결할 수 없습니다."}</h2><p>{error || "잠시 기다려 주세요."}</p>{!loading && <button className="primary-button" onClick={() => void refresh()}>다시 확인</button>}</section>;

  const { health, readiness, config, versions } = snapshot;
  const aiReady = readiness.checks.aiRetrieval === "UP";
  const fallbackReady = config.featureFlags.templateFallbackEnabled;
  return <div className="system-status-dashboard">
    <section className={`system-status-hero ${readiness.ready ? "ready" : "not-ready"}`}>
      <div><p><span className="live-dot" /> 30초마다 자동 확인</p><h2>{readiness.ready ? "금융 AI 데모가 준비되었습니다." : "일부 구성요소를 확인해 주세요."}</h2><span>최근 확인 {dateTime(snapshot.checkedAt)}</span></div>
      <div className="system-score"><strong>{Object.values(readiness.checks).filter((value) => value === "UP").length}/{Object.keys(readiness.checks).length}</strong><small>준비상태 통과</small></div>
    </section>
    <section className="status-check-grid">{Object.entries(readiness.checks).map(([key, value]) => <article className="panel" key={key}><span className={`check-indicator ${value === "UP" ? "up" : "down"}`}>{value === "UP" ? "✓" : "!"}</span><div><strong>{CHECK_LABELS[key] ?? key}</strong><small>{value === "UP" ? "정상 연결" : value}</small></div></article>)}</section>
    <div className="status-two-column">
      <section className="panel fallback-status-card"><div className="section-heading"><div><p className="label">AI 장애 안전망</p><h2>검색 중단 시 템플릿 폴백</h2></div><span className={`status-pill ${fallbackReady ? "safe" : "warning"}`}>{fallbackReady ? "사용 가능" : "설정 확인"}</span></div><div className="fallback-flow"><span className={aiReady ? "active" : "disabled"}>승인 근거 검색<small>{aiReady ? "정상" : "중단"}</small></span><i>→</i><span className={!aiReady && fallbackReady ? "active" : ""}>안전 템플릿<small>추측 없음</small></span><i>→</i><span>행원 검토<small>최종 승인</small></span></div><ul><li>citation이 없으면 근거가 있다고 표현하지 않습니다.</li><li>모델·검색 장애가 금융 실행으로 이어지지 않습니다.</li><li>실제 폴백 결과는 사건 코파일럿 응답의 fallbackUsed로 확인합니다.</li></ul><Link className="plain-link" href="/staff/cases">사건 코파일럿에서 확인 →</Link></section>
      <section className="panel guardrail-card"><p className="label">런타임 안전 경계</p><h2>{health.service}</h2><dl><div><dt>데이터 모드</dt><dd>{config.dataMode}</dd></div><div><dt>외부 금융 실행</dt><dd>{config.externalActionsEnabled ? "활성" : "비활성"}</dd></div><div><dt>외부 네트워크</dt><dd>{config.externalEgressEnabled ? "허용" : "차단"}</dd></div><div><dt>원격 모델</dt><dd>{config.remoteModelEnabled ? "활성" : "비활성"}</dd></div><div><dt>지원 시나리오</dt><dd>{config.supportedScenarioIds.join(", ")}</dd></div></dl></section>
    </div>
    <section className="panel version-board"><div className="section-heading"><div><p className="label">검증 기준 버전</p><h2>정책·데이터·알고리즘 일치 여부</h2></div><button className="secondary-button" disabled={loading} onClick={() => void refresh()}>{loading ? "확인 중…" : "지금 새로고침"}</button></div><div><span><small>API</small><strong>{versions.apiVersion}</strong></span><span><small>DB schema</small><strong>V{versions.schemaVersion}</strong></span><span><small>Fixture</small><strong>{versions.fixtureVersion}</strong></span><span><small>Algorithm</small><strong>{versions.algorithmVersion}</strong></span><span><small>Policy</small><strong>{versions.policyVersion}</strong></span></div></section>
    {error && <p className="api-error" role="alert">{error}</p>}
  </div>;
}

function dateTime(value: string) { return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "medium" }).format(new Date(value)); }
