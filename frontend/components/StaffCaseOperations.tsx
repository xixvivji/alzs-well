"use client";

import { useEffect, useState } from "react";
import {
  addStaffCaseNote, loadStaffCaseOperations, scheduleStaffFollowUp, updateStaffFollowUp,
  type StaffCaseContext, type StaffCaseOperations as Operations,
} from "../lib/staff-case-workflow";

type Props = {
  context: StaffCaseContext;
  caseId: string;
  staffCapability: string;
  caseVersion: number;
  caseState: string;
  onChanged: () => Promise<void>;
};

export function StaffCaseOperations({ context, caseId, staffCapability, caseVersion, caseState, onChanged }: Props) {
  const [operations, setOperations] = useState<Operations | null>(null);
  const [note, setNote] = useState("");
  const [followUpReason, setFollowUpReason] = useState("고객이 편한 시간에 금융활동 내용을 다시 확인합니다.");
  const [scheduledAt, setScheduledAt] = useState(defaultSchedule());
  const [resultNote, setResultNote] = useState("고객과 사실관계를 다시 확인했습니다.");
  const [busy, setBusy] = useState<"load" | "note" | "schedule" | string | null>("load");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    setBusy("load");
    loadStaffCaseOperations(context, caseId, staffCapability)
      .then((value) => { if (!cancelled) setOperations(value); })
      .catch((reason) => { if (!cancelled) setError(messageOf(reason)); })
      .finally(() => { if (!cancelled) setBusy(null); });
    return () => { cancelled = true; };
  }, [context, caseId, staffCapability, caseVersion]);

  async function addNote() {
    if (!note.trim()) return;
    setBusy("note"); setError(""); setMessage("");
    try {
      const result = await addStaffCaseNote(context, caseId, staffCapability, caseVersion, note.trim());
      setNote(""); setMessage(result.message); await onChanged();
    } catch (reason) { setError(messageOf(reason)); } finally { setBusy(null); }
  }

  async function schedule() {
    setBusy("schedule"); setError(""); setMessage("");
    try {
      const result = await scheduleStaffFollowUp(context, caseId, staffCapability, caseVersion, new Date(scheduledAt).toISOString(), followUpReason.trim());
      setMessage(result.message); await onChanged();
    } catch (reason) { setError(messageOf(reason)); } finally { setBusy(null); }
  }

  async function finish(followUpId: string, status: "COMPLETED" | "CANCELLED") {
    setBusy(`${status}:${followUpId}`); setError(""); setMessage("");
    try {
      const result = await updateStaffFollowUp(context, followUpId, staffCapability, caseVersion, status, resultNote.trim());
      setMessage(result.message); await onChanged();
    } catch (reason) { setError(messageOf(reason)); } finally { setBusy(null); }
  }

  if (busy === "load" && !operations) return <section className="panel"><div className="list-skeleton">타임라인과 업무 기록을 불러오는 중입니다.</div></section>;
  if (!operations) return <section className="panel empty-state"><p>{error || "업무 기록을 불러오지 못했습니다."}</p></section>;

  return <section className="panel staff-operations-section">
    <div className="section-heading"><div><p className="label">별도 API 연결</p><h2>타임라인·내부 메모·후속관리</h2></div><span className="status-chip">사건 v{caseVersion}</span></div>
    <div className="operations-tabs" aria-label="업무 기록 요약"><span>타임라인 {operations.timeline.phases.length}</span><span>감사 {operations.timeline.auditTrail.length}</span><span>메모 {operations.notes.count}</span><span>후속 {operations.followUps.count}</span></div>

    <div className="operations-grid">
      <article className="operations-timeline"><h3>통합 타임라인</h3><ol>{operations.timeline.phases.map((event) => <li key={`${event.phase}-${event.occurredAt}`}><span /><div><strong>{event.title}</strong><small>{event.phase} · {dateTime(event.occurredAt)}</small></div></li>)}</ol></article>
      <article className="operations-notes"><h3>행원 내부 메모</h3><div className="note-feed">{operations.notes.items.length ? operations.notes.items.map((item) => <div key={item.noteId}><p>{item.noteText}</p><small>{item.createdBy} · {dateTime(item.createdAt)} · 고객 비공개</small></div>) : <p className="muted">등록된 내부 메모가 없습니다.</p>}</div><label><span>새 내부 메모</span><textarea value={note} maxLength={500} onChange={(event) => setNote(event.target.value)} placeholder="사실확인 내용만 기록하세요." /></label><button className="secondary-button" disabled={!note.trim() || busy !== null} onClick={() => void addNote()}>{busy === "note" ? "저장 중…" : "내부 메모 저장"}</button></article>
    </div>

    <div className="follow-up-workspace"><div><h3>후속 확인 일정</h3><p>외부 연락은 생성하지 않고 행원 내부 일정만 등록합니다.</p></div>{operations.followUps.items.length ? <div className="follow-up-list">{operations.followUps.items.map((item) => <article key={item.followUpId}><span className={`status-pill ${item.status === "SCHEDULED" ? "warning" : "safe"}`}>{statusLabel(item.status)}</span><div><strong>{item.reason}</strong><small>{dateTime(item.scheduledAt)} · 외부 전달 없음</small></div>{item.status === "SCHEDULED" && <div className="follow-up-actions"><button disabled={caseState !== "FOLLOW_UP_REQUIRED" || busy !== null} onClick={() => void finish(item.followUpId, "COMPLETED")}>완료</button><button disabled={caseState !== "FOLLOW_UP_REQUIRED" || busy !== null} onClick={() => void finish(item.followUpId, "CANCELLED")}>취소</button></div>}</article>)}</div> : <p className="muted">등록된 후속 일정이 없습니다.</p>}
      <div className="follow-up-form"><label><span>확인 예정 시각</span><input type="datetime-local" value={scheduledAt} onChange={(event) => setScheduledAt(event.target.value)} /></label><label><span>내부 확인 사유</span><input value={followUpReason} maxLength={500} onChange={(event) => setFollowUpReason(event.target.value)} /></label><button className="primary-button" disabled={caseState !== "IN_BANK_REVIEW" || !followUpReason.trim() || busy !== null} onClick={() => void schedule()}>{busy === "schedule" ? "등록 중…" : "내부 일정 등록"}</button></div>
      {operations.followUps.items.some((item) => item.status === "SCHEDULED") && <label className="follow-up-result"><span>완료·취소 기록</span><input value={resultNote} maxLength={500} onChange={(event) => setResultNote(event.target.value)} /></label>}
    </div>
    {message && <p className="workflow-result" role="status">{message}</p>}{error && <p className="api-error" role="alert">{error}</p>}
  </section>;
}

function defaultSchedule() {
  const value = new Date(Date.now() + 24 * 60 * 60 * 1000);
  value.setMinutes(0, 0, 0);
  const offset = value.getTimezoneOffset() * 60_000;
  return new Date(value.getTime() - offset).toISOString().slice(0, 16);
}
function dateTime(value: string) { const parsed = new Date(value); return Number.isNaN(parsed.getTime()) ? value : new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(parsed); }
function statusLabel(value: string) { return ({ SCHEDULED: "예정", COMPLETED: "완료", CANCELLED: "취소" } as Record<string, string>)[value] ?? value; }
function messageOf(reason: unknown) { return reason instanceof Error ? reason.message : "업무 요청을 처리하지 못했습니다."; }
