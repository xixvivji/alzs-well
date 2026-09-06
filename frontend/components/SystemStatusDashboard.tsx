"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { loadServiceAvailability, loadServiceMetadata, type ServiceAvailability, type ServiceMetadata } from "../lib/system-status";
import { dateTime } from "../lib/continuity-labels";
import { ApiClientError } from "../lib/api";

const CHECK_LABELS: Record<string, string> = {
  database: "데이터베이스", flyway: "데이터 구조", syntheticFixtures: "시연 데이터",
  policyCatalog: "안내 정책", detectionPolicy: "탐지 정책", safeGuardrails: "실행 제한",
  aiRetrieval: "AI 근거 검색", aiRequiredForCore: "금융업무의 AI 의존 설정",
};
const checkLabel = (value: string) => ({ UP: "정상", DOWN: "점검 필요", REQUIRED: "필수", OPTIONAL: "선택" } as Record<string, string>)[value] ?? value;

export function SystemStatusDashboard() {
  const [snapshot, setSnapshot] = useState<ServiceAvailability | null>(null);
  const [metadata, setMetadata] = useState<ServiceMetadata | null>(null);
  const [metadataBusy, setMetadataBusy] = useState(false);
  const [metadataError, setMetadataError] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [autoRefresh, setAutoRefresh] = useState(true);
  const pending = useRef(false);
  const mounted = useRef(false);
  const refresh = useCallback(async () => {
    if (pending.current) return;
    pending.current = true; setLoading(true); setError("");
    try { const value = await loadServiceAvailability(); if (mounted.current) setSnapshot(value); }
    catch (reason) { if (mounted.current) { if (reason instanceof ApiClientError && reason.status === 429) { setAutoRefresh(false); setError("요청이 많아 자동 확인을 멈췄습니다. 1분 후 다시 확인해 주세요."); } else setError("연결 상태를 확인하지 못했습니다. 잠시 후 다시 확인해 주세요."); } }
    finally { pending.current = false; if (mounted.current) setLoading(false); }
  }, []);
  useEffect(() => { mounted.current = true; void refresh(); return () => { mounted.current = false; }; }, [refresh]);
  useEffect(() => {
    if (!autoRefresh) return;
    const timer = window.setInterval(() => { if (document.visibilityState === "visible") void refresh(); }, 30000);
    return () => window.clearInterval(timer);
  }, [autoRefresh, refresh]);

  async function loadMetadata() {
    if (metadataBusy) return;
    setMetadataBusy(true); setMetadataError("");
    try { const value = await loadServiceMetadata(); if (mounted.current) setMetadata(value); }
    catch { if (mounted.current) setMetadataError("설정·버전 정보를 불러오지 못했습니다. 다시 확인해 주세요."); }
    finally { if (mounted.current) setMetadataBusy(false); }
  }

  return <div className="service-health-page">
    <header className="portal-section-heading"><div><h2>금융업무와 AI 설명을 사용할 수 있나요?</h2><p>연결 문제가 있을 때 이곳에서 확인하세요.</p></div><button className="btn btn-outline" disabled={loading} onClick={() => void refresh()}>{loading ? "확인 중…" : "새로고침"}</button></header>
    <div className="service-health-controls"><label><input type="checkbox" checked={autoRefresh} onChange={(event) => setAutoRefresh(event.target.checked)} />30초마다 확인</label><span>{snapshot ? `마지막 확인 ${dateTime(snapshot.checkedAt)}` : "연결 상태 확인 중"}</span></div>
    {error && <p className="api-error" role="alert">{snapshot ? "최신 상태를 확인하지 못했습니다. 아래는 마지막으로 확인한 결과입니다. " : ""}{error}</p>}
    {!snapshot ? <section className="panel" aria-busy={loading}><h3>{loading ? "서비스 연결을 확인하고 있습니다." : "연결 상태를 불러오지 못했습니다."}</h3>{!loading && <button className="btn btn-primary" onClick={() => void refresh()}>다시 확인</button>}</section> : <>
      <div className="service-health-summary">
        <section className="panel"><h3>금융업무 연결</h3><strong className={snapshot.coreReadiness.ready ? "health-ok" : "health-attention"}>{snapshot.coreReadiness.ready ? "정상" : "점검 필요"}</strong><p>{snapshot.coreReadiness.ready ? "데이터·정책 연결이 확인됐습니다." : "사건이나 금융정보를 불러오지 못한다면 아래 점검 항목을 확인해 주세요."}</p></section>
        <section className="panel"><h3>AI 설명·근거 검색</h3><strong className={snapshot.aiReadiness.ready ? "health-ok" : "health-attention"}>{snapshot.aiReadiness.ready ? "정상" : "점검 필요"}</strong><p>{snapshot.aiReadiness.ready ? "AI 보조 기능의 연결이 확인됐습니다." : "새 설명이나 검색 결과를 받지 못할 수 있습니다. 기존 근거를 확인해 주세요."}</p></section>
      </div>
      <section className="service-health-guidance"><h3>AI 설명을 불러오지 못할 때</h3><p>{snapshot.config.featureFlags.templateFallbackEnabled ? "기본 안내문으로 대신 설명하도록 설정되어 있습니다. 개별 설명의 상태는 해당 화면에서 확인해 주세요." : "기본 안내문 대체가 꺼져 있습니다. 기존 기록과 근거를 확인해 주세요."}</p></section>
      <details className="panel service-health-details"><summary>상세 점검 항목</summary><dl>{Object.entries({ ...snapshot.coreReadiness.checks, ...snapshot.aiReadiness.checks }).map(([key, value]) => <div key={key}><dt>{CHECK_LABELS[key] ?? key}</dt><dd>{checkLabel(value)}</dd></div>)}</dl></details>
      <details className="panel service-health-details" onToggle={(event) => { if (event.currentTarget.open && !metadata && !metadataBusy) void loadMetadata(); }}><summary>설정·버전 정보</summary>{metadataError && <p role="alert">{metadataError}</p>}{!metadata ? <button className="btn btn-outline" disabled={metadataBusy} onClick={() => void loadMetadata()}>{metadataBusy ? "불러오는 중…" : "다시 확인"}</button> : <><p>{dateTime(metadata.checkedAt)} 확인</p><dl>
        <div><dt>서비스</dt><dd>{metadata.health.service}</dd></div>
        <div><dt>데이터 모드</dt><dd>{snapshot.config.dataMode === "SYNTHETIC_ONLY" ? "예시 데이터" : snapshot.config.dataMode}</dd></div>
        <div><dt>금융 실행</dt><dd>{snapshot.config.externalActionsEnabled ? "활성" : "꺼짐"}</dd></div>
        <div><dt>외부 네트워크</dt><dd>{snapshot.config.externalEgressEnabled ? "허용" : "차단"}</dd></div>
        <div><dt>원격 모델</dt><dd>{snapshot.config.remoteModelEnabled ? "활성" : "꺼짐"}</dd></div>
        <div><dt>API 버전</dt><dd>{metadata.versions.apiVersion}</dd></div>
        <div><dt>데이터 구조 버전</dt><dd>{metadata.versions.schemaVersion}</dd></div>
        <div><dt>예시 데이터 버전</dt><dd>{metadata.versions.fixtureVersion}</dd></div>
        <div><dt>알고리즘 버전</dt><dd>{metadata.versions.algorithmVersion}</dd></div>
        <div><dt>정책 버전</dt><dd>{metadata.versions.policyVersion}</dd></div>
      </dl></>}</details>
    </>}
  </div>;
}
