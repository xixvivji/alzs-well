"use client";

import { useEffect, useState } from "react";
import { readDemoContext } from "../lib/demo-session";
import {
  approveStaffGuidancePlan,
  generateStaffCopilotDraft,
  issueStaffCapability,
  loadStaffCase,
  startStaffCaseReview,
  type CopilotDraftResult,
  type StaffCaseBundle,
  type StaffCaseContext,
} from "../lib/staff-case-workflow";

type BusyAction = "loading" | "copilot" | "review" | "guidance" | null;

const STATE_LABELS: Record<string, string> = {
  PENDING_BANK_REVIEW: "행원 검토 대기",
  IN_BANK_REVIEW: "행원 검토 중",
  FOLLOW_UP_REQUIRED: "후속 확인 필요",
  GUIDANCE_PLAN_APPROVED: "안내계획 승인",
  CLOSED_FALSE_POSITIVE: "오탐으로 종결",
};

const REASON_LABELS: Record<string, string> = {
  MISSED_RECURRING: "정기납부 누락 증가",
  DUPLICATE_TRANSFER: "짧은 시간 내 중복송금",
  REPEATED_CONFIRMATION: "거래결과 반복 확인",
};

export function StaffCaseDetail({ caseId }: { caseId: string }) {
  const [context, setContext] = useState<StaffCaseContext | null>(null);
  const [staffCapability, setStaffCapability] = useState("");
  const [bundle, setBundle] = useState<StaffCaseBundle | null>(null);
  const [copilot, setCopilot] = useState<CopilotDraftResult | null>(null);
  const [selectedActionCodes, setSelectedActionCodes] = useState<string[]>([]);
  const [staffNote, setStaffNote] = useState("공식 적용조건을 확인한 뒤 고객에게 안내할 계획입니다.");
  const [busy, setBusy] = useState<BusyAction>("loading");
  const [error, setError] = useState("");
  const [result, setResult] = useState("");

  useEffect(() => {
    const demo = readDemoContext();
    if (!demo?.sessionId || !demo.demoRunId) {
      setError("먼저 고객 데모에서 도움 요청 시나리오를 실행해 주세요.");
      setBusy(null);
      return;
    }
    const activeContext = { sessionId: demo.sessionId, demoRunId: demo.demoRunId };
    let cancelled = false;
    void (async () => {
      try {
        const capability = await issueStaffCapability(activeContext.sessionId);
        const loaded = await loadStaffCase(activeContext, caseId, capability);
        if (cancelled) return;
        setContext(activeContext);
        setStaffCapability(capability);
        setBundle(loaded);
      } catch (reason) {
        if (!cancelled) setError(messageOf(reason, "사건 상세를 불러오지 못했습니다."));
      } finally {
        if (!cancelled) setBusy(null);
      }
    })();
    return () => { cancelled = true; };
  }, [caseId]);

  async function refreshCase() {
    if (!context || !staffCapability) return;
    setBundle(await loadStaffCase(context, caseId, staffCapability));
  }

  async function generateCopilot() {
    if (!context || !staffCapability) return;
    setBusy("copilot"); setError(""); setResult("");
    try {
      setCopilot(await generateStaffCopilotDraft(context, caseId, staffCapability));
    } catch (reason) {
      setError(messageOf(reason, "AI 검토 초안을 생성하지 못했습니다."));
    } finally { setBusy(null); }
  }

  async function startReview() {
    if (!context || !staffCapability || !bundle) return;
    setBusy("review"); setError(""); setResult("");
    try {
      const mutation = await startStaffCaseReview(
        context, caseId, staffCapability, bundle.detail.caseVersion,
      );
      setResult(mutation.message);
      await refreshCase();
    } catch (reason) {
      setError(messageOf(reason, "검토 상태를 변경하지 못했습니다."));
    } finally { setBusy(null); }
  }

  async function approveGuidance() {
    if (!context || !staffCapability || !bundle) return;
    if (!selectedActionCodes.length) {
      setError("안내할 보호수단을 하나 이상 선택해 주세요.");
      return;
    }
    setBusy("guidance"); setError(""); setResult("");
    try {
      const mutation = await approveStaffGuidancePlan(
        context,
        caseId,
        staffCapability,
        bundle.detail.caseVersion,
        selectedActionCodes,
        staffNote.trim(),
      );
      setResult(mutation.message);
      await refreshCase();
    } catch (reason) {
      setError(messageOf(reason, "안내계획을 승인하지 못했습니다."));
    } finally { setBusy(null); }
  }

  if (busy === "loading") {
    return <section className="panel"><div className="list-skeleton">사건 상세와 근거를 불러오는 중입니다.</div></section>;
  }
  if (!bundle) {
    return <section className="panel empty-state"><h2>사건을 열 수 없습니다.</h2><p>{error}</p></section>;
  }

  const { detail, evidence } = bundle;
  const canStartReview = isActionEnabled(detail.allowedActions, "START_REVIEW");
  const canApproveGuidance = isActionEnabled(detail.allowedActions, "APPROVE_GUIDANCE_PLAN");
  const guidanceApproved = detail.guidancePlan.status === "APPROVED";

  return <div className="staff-case-detail">
    <section className="case-summary-grid">
      <article className="panel case-identity">
        <div><p className="label">사건 상태</p><h2>{stateLabel(detail.state)}</h2></div>
        <dl><div><dt>사건 번호</dt><dd>{detail.caseId}</dd></div><div><dt>업무 우선순위</dt><dd>{detail.reviewPriority}</dd></div><div><dt>사건 버전</dt><dd>{detail.caseVersion}</dd></div></dl>
      </article>
      <article className="panel safety-boundary">
        <p className="label">안전 경계</p>
        <h2>행원이 확인하고 최종 결정합니다.</h2>
        <p>합성데이터만 사용하며 AI는 검토 초안과 승인된 근거를 제시할 뿐, 연락·지급정지·계좌조치를 실행하지 않습니다.</p>
        <div className="boundary-chips"><span>외부 연락 없음</span><span>금융 실행 없음</span><span>사람 최종 승인</span></div>
      </article>
    </section>

    <section className="case-content-grid">
      <article className="panel">
        <p className="label">고객 맥락 확인 결과</p>
        <h2>고객이 금융활동을 확인하기 어렵다고 응답했습니다.</h2>
        <ul className="plain-list">{detail.customerContext.unconfirmedItems.map((item) => <li key={item}>{item}</li>)}</ul>
      </article>
      <article className="panel">
        <p className="label">행원에게 추천하는 첫 질문</p>
        <blockquote>{detail.suggestedQuestions[0]?.text ?? "고객의 금융생활 변화를 함께 확인해 주세요."}</blockquote>
        <p className="muted">질병이나 사기를 단정하지 않고 사실과 생활맥락을 먼저 확인합니다.</p>
      </article>
    </section>

    <section className="panel evidence-section">
      <div className="section-heading"><div><p className="label">불변 합성 근거</p><h2>변화를 만든 신호</h2></div><span className="status-chip">T0 snapshot 고정</span></div>
      <div className="evidence-grid">{evidence.signals.map((signal) => <article key={signal.signalId}>
        <strong>{reasonLabel(signal.reasonCode)}</strong>
        <p><b>{signal.observedCount}건</b> · {windowLabel(signal.windowSeconds)}</p>
        <small>근거 {signal.evidenceIds.length}개 · {signal.algorithmVersion}</small>
      </article>)}</div>
      <p className="provenance-note">출처: {evidence.provenance.sourceProvider} · 합성데이터 {evidence.provenance.syntheticData ? "확인" : "미확인"} · 외부조회 {evidence.provenance.externalFetchPerformed ? "발생" : "없음"}</p>
    </section>

    <section className="panel timeline-section">
      <div className="section-heading"><div><p className="label">사건 타임라인</p><h2>변화 발견부터 고객 확인까지</h2></div></div>
      <ol>{detail.timeline.map((event) => <li key={`${event.phase}-${event.occurredAt}`}><span>{event.phase}</span><div><strong>{event.title}</strong><small>{dateTimeLabel(event.occurredAt)}</small></div></li>)}</ol>
    </section>

    <section className="panel copilot-section">
      <div className="section-heading"><div><p className="label">근거 기반 AI 지원</p><h2>행원 검토 초안</h2></div><button className="secondary-button" onClick={() => void generateCopilot()} disabled={busy !== null}>{busy === "copilot" ? "생성 중…" : copilot ? "초안 다시 생성" : "AI 검토 초안 생성"}</button></div>
      {!copilot ? <div className="empty-block">행원이 요청할 때만 승인된 근거를 검색해 초안을 만듭니다.</div> : <div className="copilot-result">
        <div className="copilot-meta"><span>{copilot.draft.generatedBy}</span><span>검색: {copilot.draft.retrievalMode}</span><span>{copilot.draft.fallbackUsed ? "안전 폴백 사용" : "승인 근거 연결"}</span></div>
        <p className="copilot-summary">{copilot.draft.summary}</p>
        <div className="copilot-columns"><div><h3>추천 질문</h3><ul className="plain-list">{copilot.draft.suggestedQuestions.map((question) => <li key={question}>{question}</li>)}</ul></div><div><h3>확인 체크리스트</h3><ul className="plain-list">{copilot.draft.checklist.map((item) => <li key={item}>{item}</li>)}</ul></div></div>
        <div className="citation-list"><h3>인용 근거</h3>{copilot.draft.citations.length ? copilot.draft.citations.map((citation) => {
          const sourceUrl = safeHttpsUrl(citation.sourceUrl);
          return <article key={citation.passageId}><div><strong>{citation.citationLabel}</strong><small>{citation.documentId} · {citation.versionLabel}</small></div>{sourceUrl ? <a href={sourceUrl} target="_blank" rel="noreferrer">원문 확인</a> : <span>내부 승인 근거</span>}</article>;
        }) : <p className="muted">검색 근거가 없어 결정론적 안전 템플릿으로 생성했습니다.</p>}</div>
        <p className="human-review-notice">사람 검토 필수 · 모델 직접 판단 {copilot.draft.modelInvoked ? "사용" : "미사용"} · 외부 전송 {copilot.draft.externalEgressAttempted ? "발생" : "없음"}</p>
      </div>}
    </section>

    <section className="panel decision-section">
      <div className="section-heading"><div><p className="label">행원 최종 통제</p><h2>검토와 안내계획</h2></div><span className="status-chip">{stateLabel(detail.state)}</span></div>
      <div className="decision-step"><div><span className="step-number">1</span><div><h3>사실확인 검토 시작</h3><p>고객 응답과 불변 근거를 확인한 뒤 검토 상태를 시작합니다.</p></div></div><button className="primary-button" disabled={!canStartReview || busy !== null} onClick={() => void startReview()}>{busy === "review" ? "처리 중…" : canStartReview ? "검토 시작" : "검토 시작 완료"}</button></div>
      <div className="decision-step guidance-step"><div><span className="step-number">2</span><div><h3>안내할 보호수단 선택</h3><p>아래 선택은 상담 안내계획일 뿐 실제 금융조치를 실행하지 않습니다.</p></div></div></div>
      <div className="protection-list">{detail.protectionCandidates.map((candidate) => {
        const checked = selectedActionCodes.includes(candidate.actionCode);
        const sourceUrl = safeHttpsUrl(candidate.source.url);
        return <label key={candidate.actionCode} className={checked ? "selected" : ""}><input type="checkbox" checked={checked} disabled={!canApproveGuidance || guidanceApproved || busy !== null} onChange={() => setSelectedActionCodes((current) => checked ? current.filter((code) => code !== candidate.actionCode) : [...current, candidate.actionCode])}/><span><strong>{candidate.title}</strong><small>{candidate.eligibilitySummary}</small><em>{candidate.executionType === "GUIDANCE_ONLY" ? "안내만 제공" : candidate.executionType}</em>{sourceUrl && <a href={sourceUrl} target="_blank" rel="noreferrer" onClick={(event) => event.stopPropagation()}>{candidate.source.issuer} 원문</a>}</span></label>;
      })}</div>
      <label className="staff-note"><span>행원 내부 메모</span><textarea value={staffNote} maxLength={500} disabled={!canApproveGuidance || guidanceApproved || busy !== null} onChange={(event) => setStaffNote(event.target.value)} /></label>
      <div className="approval-row"><p>승인 후에도 <strong>guidanceDelivered=false</strong>이며 외부 실행은 생성되지 않습니다.</p><button className="primary-button" disabled={!canApproveGuidance || guidanceApproved || !selectedActionCodes.length || !staffNote.trim() || busy !== null} onClick={() => void approveGuidance()}>{busy === "guidance" ? "승인 중…" : guidanceApproved ? "안내계획 승인 완료" : "안내계획 승인"}</button></div>
    </section>

    {result && <p className="workflow-result" role="status">{result}</p>}
    {error && <p className="api-error" role="alert">{error}</p>}
  </div>;
}

function isActionEnabled(actions: StaffCaseBundle["detail"]["allowedActions"], action: string): boolean {
  return actions.some((item) => item.action === action && item.enabled);
}

function stateLabel(state: string): string { return STATE_LABELS[state] ?? state; }
function reasonLabel(reason: string): string { return REASON_LABELS[reason] ?? reason; }
function windowLabel(seconds: number): string {
  if (seconds >= 86_400) return `최근 ${Math.round(seconds / 86_400)}일`;
  if (seconds >= 60) return `최근 ${Math.round(seconds / 60)}분`;
  return `최근 ${seconds}초`;
}
function dateTimeLabel(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat("ko-KR", {
    month: "short", day: "numeric", hour: "2-digit", minute: "2-digit",
  }).format(date);
}
function safeHttpsUrl(value: string): string | null {
  try { const url = new URL(value); return url.protocol === "https:" ? url.href : null; }
  catch { return null; }
}
function messageOf(reason: unknown, fallback: string): string {
  return reason instanceof Error && reason.message ? reason.message : fallback;
}
