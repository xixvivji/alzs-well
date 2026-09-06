"use client";

import { useRef, useState, type KeyboardEvent } from "react";
import type { PrivateCustomerSession } from "../lib/private-financial-products";
import { searchKnowledge, type KnowledgeHit } from "../lib/private-life-services";
import { addOperationalCaseNote, approveCaseGuidance, completeCaseReview, finishCaseFollowUp, guidanceActions, loadCaseCustomerIntent, scheduleCaseFollowUp, startOperationalCaseReview, type OperationalCaseBundle, type SharedCaseIntent } from "../lib/private-staff-cases";
import { customerLabel, evidenceDescription } from "../lib/presentation-copy";
import { alertStateLabel, caseStateLabel, changeLabel, dateTime, intentValueLabel, metric, responseLabel } from "../lib/continuity-labels";
import { reviewEventLabel, reviewNextTask, reviewPrompt } from "../lib/staff-review-presentation";
import { OperationalCopilotDraft } from "./OperationalCopilotDraft";

type Props = { session: PrivateCustomerSession; bundle: OperationalCaseBundle; busy: boolean; runCommand: (command: () => Promise<unknown>, success: string) => Promise<boolean> };
const workAreas = [["facts", "고객·근거 확인"], ["decision", "검토·결정"], ["followup", "후속관리·이력"]] as const;
type WorkArea = typeof workAreas[number][0];

export function OperationalCaseReview({ session, bundle, busy, runCommand }: Props) {
  const item = bundle.detail.caseSummary;
  const [area, setArea] = useState<WorkArea>("facts");
  const [decision, setDecision] = useState<"guidance" | "close">("guidance");
  const [note, setNote] = useState("");
  const [reviewNote, setReviewNote] = useState("");
  const [actions, setActions] = useState<string[]>([]);
  const [confirmPlan, setConfirmPlan] = useState(false);
  const [purpose, setPurpose] = useState("");
  const [scheduledAt, setScheduledAt] = useState("");
  const [outcomes, setOutcomes] = useState<Record<string, string>>({});
  const [intent, setIntent] = useState<SharedCaseIntent | null | undefined>(undefined);
  const [query, setQuery] = useState(changeLabel(bundle.detail.reasonCode));
  const [hits, setHits] = useState<KnowledgeHit[] | null>(null);
  const [auxBusy, setAuxBusy] = useState("");
  const [auxError, setAuxError] = useState({ intent: "", knowledge: "" });
  const tabButtons = useRef<Array<HTMLButtonElement | null>>([]);
  const confirmation = useRef<HTMLDivElement | null>(null);
  const auxiliaryGeneration = useRef(0);
  const assignedToMe = item.assignedTo === session.principalId;
  const reviewing = item.taskStatus === "IN_REVIEW";
  const canWork = assignedToMe && ["IN_REVIEW", "GUIDANCE_APPROVED", "COMPLETED"].includes(item.taskStatus);
  const canDecide = assignedToMe && ["IN_REVIEW", "GUIDANCE_APPROVED"].includes(item.taskStatus);
  const canApprove = reviewing && assignedToMe && !bundle.detail.guidancePlanId;
  const showGuidance = canApprove && decision === "guidance";
  const disabled = busy || Boolean(auxBusy);
  const prompt = reviewPrompt(bundle.detail.reasonCode);
  const pendingFollowUps = bundle.followUps.filter((entry) => entry.status === "SCHEDULED");

  function switchArea(next: WorkArea, focus = false) {
    setArea(next);
    if (focus) tabButtons.current[workAreas.findIndex(([key]) => key === next)]?.focus();
  }

  function navigateTabs(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    const target = event.key === "Home" ? 0 : event.key === "End" ? workAreas.length - 1
      : event.key === "ArrowRight" ? (index + 1) % workAreas.length
      : event.key === "ArrowLeft" ? (index + workAreas.length - 1) % workAreas.length : -1;
    if (target < 0) return;
    event.preventDefault(); switchArea(workAreas[target][0], true);
  }

  async function loadAuxiliary(kind: "intent" | "knowledge") {
    const current = ++auxiliaryGeneration.current;
    setAuxBusy(kind); setAuxError((errors) => ({ ...errors, [kind]: "" }));
    try {
      if (kind === "intent") {
        setIntent(undefined);
        const value = await loadCaseCustomerIntent(session, item.customerId);
        if (current === auxiliaryGeneration.current) setIntent(value);
      } else {
        setHits(null);
        const value = await searchKnowledge(session, query.trim());
        if (current === auxiliaryGeneration.current) setHits(value);
      }
    } catch (reason) {
      if (current === auxiliaryGeneration.current) setAuxError((errors) => ({ ...errors, [kind]: `${kind === "intent" ? "공유 의향을 열지 못했습니다. 고객별 열람 권한을 확인해 주세요. " : "근거 검색을 완료하지 못했습니다. "}${reason instanceof Error ? reason.message : "다시 확인해 주세요."}` }));
    } finally { if (current === auxiliaryGeneration.current) setAuxBusy(""); }
  }

  return <section className="case-workbench" id="staff-case-detail" tabIndex={-1} aria-labelledby="case-review-title">
    <header className="case-workbench-heading">
      <div><h2 id="case-review-title" tabIndex={-1}>{changeLabel(bundle.detail.reasonCode)}</h2><p>{customerLabel(item.customerId)} · 접수 {dateTime(item.createdAt)}</p></div>
      <span className={`case-task-status status-${item.taskStatus.toLowerCase()}`}>{caseStateLabel(item.taskStatus)}</span>
    </header>
    <div className="case-next-task"><p>{item.assignedTo && !assignedToMe ? "다른 행원이 담당하고 있습니다. 응답과 처리 기록을 조회할 수 있습니다." : reviewNextTask(item.taskStatus)}</p>{item.taskStatus === "PENDING" && <button className="btn btn-primary" disabled={disabled || Boolean(item.assignedTo && !assignedToMe)} onClick={() => void runCommand(() => startOperationalCaseReview(session, item), "검토를 시작했습니다.")}>검토 시작</button>}</div>
    <div className="case-source-summary" aria-label="고객 응답과 시스템 관찰 구분">
      <section className="case-customer-source" aria-labelledby="case-response-title" data-origin="customer">
        <header className="case-source-heading"><h3 id="case-response-title">고객이 남긴 응답</h3></header>
        <p className="case-response-value">{responseLabel(bundle.detail.customerResponseCode)}</p>
        
      </section>
      <section className="case-system-source" aria-labelledby="case-observation-title" data-origin="system">
        <header className="case-source-heading"><h3 id="case-observation-title">금융활동 변화</h3></header>
        <dl className="case-observation-values"><div><dt>평소 기준</dt><dd>{metric(bundle.evidence.baselineValue, bundle.evidence.unit)}</dd></div><div><dt>최근 관찰</dt><dd>{metric(bundle.evidence.currentValue, bundle.evidence.unit)}</dd></div></dl>
      </section>
    </div>
    <div className="case-work-tabs" role="tablist" aria-label="사건 검토 작업">
      {workAreas.map(([key, label], index) => <button key={key} ref={(element) => { tabButtons.current[index] = element; }} type="button" role="tab" id={`case-tab-${key}`} aria-controls={`case-area-${key}`} aria-selected={area === key} tabIndex={area === key ? 0 : -1} onKeyDown={(event) => navigateTabs(event, index)} onClick={() => switchArea(key)}>{label}</button>)}
    </div>

    <div className="case-work-area" id="case-area-facts" role="tabpanel" aria-labelledby="case-tab-facts" hidden={area !== "facts"} tabIndex={0}>
      <section className="case-section" aria-labelledby="case-evidence-title" data-origin="system">
        <header className="case-section-heading"><h3 id="case-evidence-title">무엇이 달라졌나요?</h3><div className="case-source-meta"><span>근거 {bundle.evidence.items.length}건</span></div></header>
        <p className="case-supporting">고객의 평소 기록과 비교한 변화입니다. 원인은 고객 응답과 함께 확인합니다.</p>
        {bundle.evidence.items.length ? <ul className="case-record-list">{bundle.evidence.items.map((evidence) => <li key={evidence.evidenceId}><p>{evidenceDescription(evidence.description)}</p><span>{dateTime(evidence.occurredAt)}{evidence.amount != null ? ` · ${metric(evidence.amount, evidence.currency ?? "")}` : ""}</span><details className="case-disclosure"><summary>기록 출처</summary><p>{evidence.sourceReference}</p></details></li>)}</ul> : <p className="case-empty">연결된 근거가 없습니다. 확인되지 않은 원인을 단정하지 말고 추가 확인 내용을 메모해 주세요.</p>}
      </section>
      <section className="case-section case-customer-source" aria-labelledby="case-intent-title" data-origin="customer">
        <header className="case-section-heading"><h3 id="case-intent-title">고객이 미리 정한 도움 방식</h3><button className="btn btn-outline" disabled={disabled} onClick={() => void loadAuxiliary("intent")}>{auxBusy === "intent" ? "확인 중…" : intent === undefined ? "공유 의향 확인" : "의향 새로고침"}</button></header>
        <p className="case-supporting">공유에 동의한 의향을 현재 고객 응답과 함께 참고하세요.</p>
        {auxError.intent && <p className="api-error" role="alert">{auxError.intent}</p>}
        {intent === null ? <p className="case-empty">조회 가능한 승인 의향이 없습니다. 상담에서 현재 원하는 도움 방식을 확인해 주세요.</p> : intent && <dl className="case-intent-facts"><div><dt>유지할 납부</dt><dd>{intent.paymentContinuity ? intentValueLabel(intent.paymentContinuity) : "공유 동의 안 함"}</dd></div><div><dt>설명 방식</dt><dd>{intent.explanationMode ? intentValueLabel(intent.explanationMode) : "공유 동의 안 함"}</dd></div><div><dt>도움 조건</dt><dd>{intent.helpCondition ? intentValueLabel(intent.helpCondition) : "공유 동의 안 함"}</dd></div></dl>}
      </section>
      <div className="case-area-footer"><button className={`btn ${item.taskStatus === "PENDING" ? "btn-outline" : "btn-primary"}`} onClick={() => switchArea("decision", true)}>검토·결정으로 이동</button></div>
    </div>

    <div className="case-work-area" id="case-area-decision" role="tabpanel" aria-labelledby="case-tab-decision" hidden={area !== "decision"} tabIndex={0}>
      <OperationalCopilotDraft key={`${item.caseId}:${item.version}`} session={session} caseId={item.caseId} />
      <section className="case-section" aria-labelledby="case-consultation-title" data-origin="reference">
        <header className="case-section-heading"><h3 id="case-consultation-title">상담에서 확인할 내용</h3></header>
        
        {prompt ? <div className="case-consultation"><p>{prompt.question}</p><p className="case-supporting">확인 항목: {prompt.check}</p></div> : <p className="case-supporting">변화 근거와 고객 응답을 대조하고 확인할 내용을 내부 메모에 정리하세요.</p>}
        <details className="case-disclosure case-search-disclosure"><summary>관련 안내·공식 근거 검색</summary>
          <div className="continuity-search"><label className="continuity-field"><span>검색어</span><input className="input" value={query} maxLength={100} onChange={(event) => setQuery(event.target.value)} /></label><button className="btn btn-outline" disabled={disabled || query.trim().length < 2} onClick={() => void loadAuxiliary("knowledge")}>{auxBusy === "knowledge" ? "검색 중…" : "근거 검색"}</button></div>
          {auxError.knowledge && <p className="api-error" role="alert">{auxError.knowledge}</p>}
          {hits && (hits.length ? <ul className="case-record-list">{hits.map((hit) => <li key={hit.passage.passageId}><strong>{hit.passage.heading}</strong><p>{hit.passage.content}</p><span>{hit.passage.citationLabel}</span>{/^https?:\/\//.test(hit.passage.sourceUrl) && <a href={hit.passage.sourceUrl} target="_blank" rel="noreferrer">원문 보기 (새 창)</a>}</li>)}</ul> : <p role="status" className="case-empty">검색된 근거가 없습니다. 다른 검색어로 확인해 주세요.</p>)}
        </details>
      </section>
      <section className="case-section case-staff-source" aria-label="행원이 작성한 내부 메모" data-origin="staff">
        <details className="case-disclosure case-staff-notes"><summary>행원 내부 메모 · {bundle.notes.length}건</summary>
          {bundle.notes.length ? <ul className="case-record-list">{bundle.notes.map((entry) => <li key={entry.noteId}><div className="case-source-meta"><span>{entry.createdBy === session.principalId ? "내가 작성" : "행원 작성"} · 저장됨</span><time dateTime={entry.createdAt}>{dateTime(entry.createdAt)}</time></div><p>{entry.noteText}</p></li>)}</ul> : <p className="case-supporting">상담 중 확인한 사실과 추가 확인할 내용을 남기세요.</p>}
          {assignedToMe && <><label className="continuity-field"><span>새 내부 메모 {note.trim() && <em className="case-draft-label">{busy ? "상태 확인 중" : "작성 중 · 미저장"}</em>}</span><textarea className="textarea" aria-describedby="staff-note-help" value={note} onChange={(event) => setNote(event.target.value)} maxLength={500} /></label><p className="case-supporting" id="staff-note-help">최대 500자 · 저장 후 수정·삭제할 수 없는 행원용 기록입니다.</p><button className="btn btn-outline" disabled={disabled || !note.trim()} onClick={() => void runCommand(() => addOperationalCaseNote(session, item.caseId, note.trim()), "내부 메모를 저장했습니다.").then((ok) => { if (ok) setNote(""); })}>내부 메모 저장</button></>}
        </details>
      </section>

      <section className="case-section case-staff-source" aria-labelledby="case-decision-title" data-origin="staff">
        <header className="case-section-heading"><h3 id="case-decision-title">검토 결과 결정</h3></header>
        {bundle.detail.selectedActionCodes.length > 0 && <div className="case-approved"><header className="case-source-heading"><h4>승인된 안내계획</h4><span className="case-record-state">행원 승인 · 저장됨</span></header><ul>{bundle.detail.selectedActionCodes.map((code) => <li key={code}>{guidanceLabel(code)}</li>)}</ul><p className="case-supporting">계획 승인 기록입니다. 고객 전달·금융 실행은 하지 않았습니다.</p></div>}
        {item.taskStatus === "PENDING" && <p className="case-empty">상단에서 검토를 시작하면 안내계획 작성과 종결 기록을 진행할 수 있습니다.</p>}
        {item.taskStatus === "COMPLETED" && <p className="case-empty">검토가 종결된 사건입니다. 후속관리·이력에서 남은 일정과 처리 기록을 확인하세요.</p>}
        {canApprove && <fieldset className="case-decision-choice" disabled={disabled}><legend>어떻게 처리할까요?</legend><label><input type="radio" name="case-decision" checked={decision === "guidance"} onChange={() => { setDecision("guidance"); setConfirmPlan(false); }} /><span>안내계획 작성<small>고객에게 안내할 내용을 선택합니다.</small></span></label><label><input type="radio" name="case-decision" checked={decision === "close"} onChange={() => { setDecision("close"); setConfirmPlan(false); }} /><span>검토 종결<small>검토 결과와 종결 사유를 남깁니다.</small></span></label></fieldset>}
        {showGuidance && <fieldset className="case-guidance-form" disabled={disabled}>
          <legend>안내할 내용 선택 <span className="case-draft-label">{busy ? "상태 확인 중" : "승인 전 · 미저장"}</span></legend>
          <p className="case-supporting">고객의 현재 의사를 확인하고 필요한 항목만 선택하세요.</p>
          {guidanceActions.map(([code, label]) => <label className="continuity-checkbox" key={code}><input type="checkbox" checked={actions.includes(code)} onChange={(event) => { setActions((current) => event.target.checked ? [...current, code] : current.filter((value) => value !== code)); setConfirmPlan(false); }} />{label}</label>)}
          {!confirmPlan ? <><p className="case-supporting">안내계획만 기록하며 실제 금융 조치나 연락은 실행하지 않습니다.</p><button className="btn btn-primary" disabled={!actions.length} onClick={() => { setConfirmPlan(true); requestAnimationFrame(() => confirmation.current?.focus()); }}>선택한 안내계획 검토</button></> : <div className="case-confirm-plan" ref={confirmation} tabIndex={-1}><h4>이 내용으로 안내계획을 승인할까요?</h4><ul>{actions.map((code) => <li key={code}>{guidanceLabel(code)}</li>)}</ul><p>선택한 내용만 승인 기록으로 남깁니다. 고객 전달·금융 실행은 하지 않습니다.</p><div className="case-actions"><button className="btn btn-primary" onClick={() => void runCommand(() => approveCaseGuidance(session, item, actions), "안내계획을 승인했습니다. 검토를 마치면 종결 사유를 남겨 주세요.").then((ok) => { if (ok) { setConfirmPlan(false); setDecision("close"); } })}>안내계획 승인</button><button className="btn btn-outline" onClick={() => setConfirmPlan(false)}>다시 선택</button></div></div>}
        </fieldset>}
        {canDecide && !showGuidance && <div className="case-close-form"><label className="continuity-field"><span>검토 결과·종결 사유</span><textarea className="textarea" value={reviewNote} maxLength={500} aria-describedby="case-close-help" onChange={(event) => setReviewNote(event.target.value)} /></label><p className="case-supporting" id="case-close-help">최대 500자 · 사건을 완료 상태로 기록합니다. 후속 일정은 종결 후에도 관리할 수 있습니다.</p><button className="btn btn-primary" disabled={disabled || !reviewNote.trim()} onClick={() => void runCommand(() => completeCaseReview(session, item, reviewNote.trim()), "검토를 종결했습니다. 남은 후속 일정을 확인해 주세요.").then((ok) => { if (ok) switchArea("followup", true); })}>검토 종결</button></div>}
      </section>
      <div className="case-area-footer"><button className="btn btn-outline" onClick={() => switchArea("facts", true)}>고객·근거 다시 확인</button><button className="btn btn-outline" onClick={() => switchArea("followup", true)}>후속관리·이력으로 이동</button></div>
    </div>

    <div className="case-work-area" id="case-area-followup" role="tabpanel" aria-labelledby="case-tab-followup" hidden={area !== "followup"} tabIndex={0}>
      <section className="case-section case-staff-source" aria-labelledby="case-followup-title" data-origin="staff">
        <header className="case-section-heading"><h3 id="case-followup-title">후속 확인 일정</h3><div className="case-source-meta"><span>예정 {pendingFollowUps.length}건</span></div></header>
        {!bundle.followUps.length ? <p className="case-empty">등록된 일정이 없습니다. 추가 확인이 필요하면 일정과 목적을 남기세요.</p> : <ul className="case-record-list case-followups">{bundle.followUps.map((entry) => <li key={entry.followUpId}>
          <div className="case-section-heading"><strong>{entry.purpose}</strong><span>{followUpLabel(entry.status)} · 저장됨</span></div><p>{dateTime(entry.scheduledAt)}</p>{entry.outcome && <p>행원이 기록한 결과: {entry.outcome}</p>}
          {canWork && entry.status === "SCHEDULED" && <details className="case-disclosure"><summary>결과 기록·일정 취소</summary><label className="continuity-field"><span>확인 결과 또는 취소 사유 (500자 이내)</span><textarea className="textarea" value={outcomes[entry.followUpId] ?? ""} maxLength={500} onChange={(event) => setOutcomes((values) => ({ ...values, [entry.followUpId]: event.target.value }))} /></label><div className="case-actions"><button className="btn btn-outline" disabled={disabled || !outcomes[entry.followUpId]?.trim()} onClick={() => void runCommand(() => finishCaseFollowUp(session, entry, "COMPLETE", outcomes[entry.followUpId].trim()), "후속 확인 결과를 기록했습니다.")}>완료 기록</button><button className="btn btn-outline" disabled={disabled || !outcomes[entry.followUpId]?.trim()} onClick={() => void runCommand(() => finishCaseFollowUp(session, entry, "CANCEL", outcomes[entry.followUpId].trim()), "후속 일정의 취소 사유를 기록했습니다.")}>일정 취소</button></div></details>}
        </li>)}</ul>}
        {canWork ? <details className="case-disclosure case-new-schedule" open={bundle.followUps.length === 0 ? true : undefined}><summary>새 후속 일정 등록</summary><div className="case-schedule-fields"><label className="continuity-field"><span>확인 예정 시각</span><input className="input" type="datetime-local" value={scheduledAt} aria-describedby="case-schedule-help" onChange={(event) => setScheduledAt(event.target.value)} /></label><label className="continuity-field"><span>확인 목적 (300자 이내)</span><input className="input" value={purpose} maxLength={300} onChange={(event) => setPurpose(event.target.value)} /></label></div><p className="case-supporting" id="case-schedule-help">90일 이내의 미래 시각을 선택하세요. 내부 일정만 기록하며 고객에게 연락하지 않습니다.</p>{scheduledAt && !validSchedule(scheduledAt) && <p className="api-error">현재 이후부터 90일 이내의 시각을 선택해 주세요.</p>}<button className="btn btn-primary" disabled={disabled || !purpose.trim() || !validSchedule(scheduledAt)} onClick={() => void runCommand(() => scheduleCaseFollowUp(session, item, new Date(scheduledAt).toISOString(), purpose.trim()), "후속 일정을 등록했습니다.").then((ok) => { if (ok) { setPurpose(""); setScheduledAt(""); } })}>후속 일정 등록</button></details> : item.taskStatus === "PENDING" && <p className="case-supporting">검토 시작 후 후속 일정을 등록할 수 있습니다.</p>}
      </section>
      <section className="case-section" aria-labelledby="case-history-title" data-origin="system"><header className="case-section-heading"><h3 id="case-history-title">고객 확인부터 처리까지</h3></header><details className="case-disclosure"><summary>처리 이력 {bundle.timeline.length}건 보기</summary>{bundle.timeline.length ? <ol className="case-timeline">{bundle.timeline.map((entry, index) => <li key={`${entry.eventType}-${entry.occurredAt}-${index}`}><strong>{reviewEventLabel(entry.eventType, entry.summary)}</strong><span>{dateTime(entry.occurredAt)}{entry.resultingState ? ` · ${followUpLabel(alertStateLabel(caseStateLabel(entry.resultingState)))}` : ""}</span></li>)}</ol> : <p className="case-empty">등록된 처리 이력이 없습니다.</p>}</details></section>
      <div className="case-area-footer"><button className="btn btn-outline" onClick={() => switchArea("decision", true)}>검토·결정으로 돌아가기</button></div>
    </div>
    <details className="case-disclosure case-identifiers"><summary>사건 식별정보</summary><p>고객 {item.customerId}<br />사건 {item.caseId}<br />알림 {item.alertId} · 버전 {item.version}</p></details>
  </section>;
}

function guidanceLabel(code: string) { return guidanceActions.find(([value]) => value === code)?.[1] ?? code; }
function followUpLabel(status: string) { return ({ SCHEDULED: "예정", COMPLETED: "완료", CANCELLED: "취소" } as Record<string, string>)[status] ?? status; }
function validSchedule(value: string) { const time = new Date(value).getTime(); return Number.isFinite(time) && time > Date.now() && time <= Date.now() + 90 * 86400000; }
