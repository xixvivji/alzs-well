import { DEMO_REHEARSAL_SCENARIOS, REHEARSAL_FIXTURE } from "../lib/demo-rehearsal";

const SIGNAL_LABELS: Record<string, string> = {
  MISSED_RECURRING: "정기납부 누락",
  DUPLICATE_TRANSFER: "중복송금",
  REPEATED_CONFIRMATION: "거래결과 반복 확인",
};

export function ScenarioDatasetSummary() {
  return <section className="panel scenario-dataset-summary">
    <div className="section-heading"><div><p className="label">발표용 합성 데이터 계약</p><h2>같은 신호, 다른 생활맥락</h2></div><span className="status-chip">{REHEARSAL_FIXTURE.fixtureVersion}</span></div>
    <p className="scenario-principle">{REHEARSAL_FIXTURE.principle}</p>
    <div className="scenario-signal-strip">{REHEARSAL_FIXTURE.signals.map((signal) => <article key={signal.reasonCode}><span>{signal.windowLabel}</span><strong>{SIGNAL_LABELS[signal.reasonCode] ?? signal.reasonCode}</strong><b>{signal.observedCount}건</b></article>)}</div>
    <div className="scenario-outcome-grid">{DEMO_REHEARSAL_SCENARIOS.map((scenario) => <article key={scenario.id}><span>{scenario.label}</span><strong>{scenario.expectedState}</strong><p>{scenario.staffDecision}</p><small>{scenario.requiresCitation ? "승인 근거 citation 필수" : "사람·규칙 근거로 종결"}</small></article>)}</div>
    <p className="scenario-safety">기준선 {REHEARSAL_FIXTURE.baselineMonths}개월 · 관측 {REHEARSAL_FIXTURE.observationMonths}개월 · 실제 고객정보 없음 · 외부 금융 실행 없음</p>
  </section>;
}
