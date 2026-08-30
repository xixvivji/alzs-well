"use client";

import { useEffect, useMemo, useState } from "react";
import {
  approveFinancialIntent, generatePlainLanguage, loadChangeAnalysis, saveFinancialIntent,
  suggestFinancialIntent, type ChangeAnalysis, type FinancialIntent, type IntentFields,
  type IntentSuggestion, type PlainLanguage,
} from "../lib/ai-financial-assistance";
import { readDemoContext, saveDemoContext, type DemoContext } from "../lib/demo-session";
import { createDemoContext } from "../lib/demo-workflow";

const EXAMPLE = "공과금은 계속 납부하고, 평소와 다른 변화가 반복되면 도움받고 싶어요. 설명은 천천히 해 주세요.";
const DEFAULT_FIELDS: IntentFields = { paymentContinuity: "KEEP_ESSENTIAL_PAYMENTS", explanationMode: "SIMPLE_TEXT", helpCondition: "ON_CUSTOMER_REQUEST", shareScopes: [] };
const LABELS: Record<string, string> = {
  MISSED_RECURRING_COUNT: "정기납부 누락", DUPLICATE_TRANSFER_COUNT: "중복송금",
  REPEATED_CONFIRMATION_COUNT: "거래결과 재확인", NEW_COUNTERPARTY_COUNT: "새 수취인 거래",
  UNUSUAL_TIME_COUNT: "평소와 다른 시간대", UNUSUAL_AMOUNT_COUNT: "평소와 다른 금액",
};
const SCOPES = [
  ["PAYMENT_PREFERENCE", "납부 방식"], ["EXPLANATION_PREFERENCE", "설명 방식"],
  ["HELP_CONDITION", "도움 요청 조건"], ["ACCESSIBILITY", "접근성 설정"],
] as const;

export function AiFinancialAssistant() {
  const [context, setContext] = useState<DemoContext | null>(null);
  const [utterance, setUtterance] = useState(EXAMPLE);
  const [fields, setFields] = useState<IntentFields>(DEFAULT_FIELDS);
  const [suggestion, setSuggestion] = useState<IntentSuggestion | null>(null);
  const [intent, setIntent] = useState<FinancialIntent | null>(null);
  const [analysis, setAnalysis] = useState<ChangeAnalysis | null>(null);
  const [plain, setPlain] = useState<PlainLanguage | null>(null);
  const [busy, setBusy] = useState<"start" | "suggest" | "save" | "approve" | "analysis" | "plain" | null>(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const speechSupported = useMemo(() => typeof window !== "undefined" && "speechSynthesis" in window, []);

  useEffect(() => { const saved = readDemoContext(); if (saved) setContext(saved); }, []);

  async function start() {
    setBusy("start"); setError("");
    try { const created = await createDemoContext(); saveDemoContext(created); setContext(created); }
    catch (reason) { setError(messageOf(reason)); } finally { setBusy(null); }
  }
  async function suggest() {
    if (!context) return;
    setBusy("suggest"); setError(""); setMessage("");
    try { const value = await suggestFinancialIntent(context, utterance); setSuggestion(value); setFields(value.suggestion); }
    catch (reason) { setError(messageOf(reason)); } finally { setBusy(null); }
  }
  async function save() {
    if (!context) return;
    setBusy("save"); setError("");
    try { const value = await saveFinancialIntent(context, fields, intent?.version ?? 0); setIntent(value); setMessage("수정 가능한 초안으로 저장했습니다."); }
    catch (reason) { setError(messageOf(reason)); } finally { setBusy(null); }
  }
  async function approve() {
    if (!context || !intent) return;
    setBusy("approve"); setError("");
    try { const value = await approveFinancialIntent(context, intent.version); setIntent(value); setMessage("고객 확인과 승인이 완료되었습니다."); }
    catch (reason) { setError(messageOf(reason)); } finally { setBusy(null); }
  }
  async function analyze() {
    if (!context) return;
    setBusy("analysis"); setError(""); setPlain(null);
    try { setAnalysis(await loadChangeAnalysis(context)); }
    catch (reason) { setError(messageOf(reason)); } finally { setBusy(null); }
  }
  async function simplify(featureCode: string) {
    if (!context) return;
    setBusy("plain"); setError("");
    try { setPlain(await generatePlainLanguage(context, featureCode)); }
    catch (reason) { setError(messageOf(reason)); } finally { setBusy(null); }
  }
  function toggleScope(scope: IntentFields["shareScopes"][number]) {
    setFields((current) => ({ ...current, shareScopes: current.shareScopes.includes(scope) ? current.shareScopes.filter((item) => item !== scope) : [...current.shareScopes, scope] }));
  }
  function speak() {
    if (!plain || !speechSupported) return;
    window.speechSynthesis.cancel();
    const speech = new SpeechSynthesisUtterance(plain.speechText); speech.lang = "ko-KR"; speech.rate = 0.85;
    window.speechSynthesis.speak(speech);
  }

  if (!context) return <section className="panel ai-start"><p className="label">합성데이터 전용 체험</p><h2>AI 금융생활 도우미를 시작해 보세요.</h2><p>실제 계좌나 개인정보를 사용하지 않습니다.</p><button type="button" onClick={() => void start()} disabled={busy !== null}>{busy === "start" ? "준비 중…" : "안전 체험 시작"}</button>{error && <p className="api-error" role="alert">{error}</p>}</section>;

  return <div className="ai-assistant-flow">
    <section className="panel ai-intent-hero">
      <div><p className="label">1 · AI 금융생활 의향서</p><h2>원하는 금융생활 방식을<br/>편한 말로 알려주세요.</h2><p>AI는 초안만 정리합니다. 저장과 승인은 고객이 직접 결정합니다.</p></div>
      <span className="ai-safety-badge">법적 위임 아님 · 금융 실행 없음</span>
    </section>
    <section className="panel ai-intent-input">
      <label htmlFor="intent-utterance">나의 금융생활 의향</label>
      <textarea id="intent-utterance" maxLength={500} value={utterance} onChange={(event) => setUtterance(event.target.value)} />
      <div><small>{utterance.length}/500자</small><button type="button" disabled={busy !== null || utterance.trim().length < 4} onClick={() => void suggest()}>{busy === "suggest" ? "정리 중…" : "AI 초안 만들기"}</button></div>
    </section>
    {suggestion && <section className="panel ai-intent-review"><div className="section-heading"><div><p className="label">AI가 정리한 초안</p><h2>저장하기 전에 직접 확인해 주세요.</h2></div><span className="status-chip">{suggestion.fallbackUsed ? "안전 폴백" : "승인 모델"}</span></div><p className="ai-summary">{suggestion.summary}</p>
      {suggestion.needsClarification && <div className="ai-questions">{suggestion.clarifyingQuestions.map((question) => <p key={question}>확인 필요 · {question}</p>)}</div>}
      <div className="ai-field-grid">
        <label><span>필수 납부</span><select value={fields.paymentContinuity} onChange={(event) => setFields({ ...fields, paymentContinuity: event.target.value as IntentFields["paymentContinuity"] })}><option value="KEEP_ESSENTIAL_PAYMENTS">계속 유지</option><option value="REVIEW_BEFORE_CHANGE">변경 전 확인</option></select></label>
        <label><span>설명 방식</span><select value={fields.explanationMode} onChange={(event) => setFields({ ...fields, explanationMode: event.target.value as IntentFields["explanationMode"] })}><option value="SIMPLE_TEXT">쉬운 글</option><option value="VOICE_AND_TEXT">글과 음성</option><option value="STAFF_EXPLANATION">행원 설명</option></select></label>
        <label><span>도움 요청 조건</span><select value={fields.helpCondition} onChange={(event) => setFields({ ...fields, helpCondition: event.target.value as IntentFields["helpCondition"] })}><option value="ON_REPEATED_CHANGE">반복 변화가 있을 때</option><option value="ON_CUSTOMER_REQUEST">내가 요청할 때</option><option value="NEVER_AUTOMATIC">자동 요청 안 함</option></select></label>
      </div>
      <fieldset><legend>행원과 공유할 범위</legend><p>아무것도 선택하지 않으면 공유하지 않습니다.</p><div className="scope-options">{SCOPES.map(([value, label]) => <label key={value}><input type="checkbox" checked={fields.shareScopes.includes(value)} onChange={() => toggleScope(value)} />{label}</label>)}</div></fieldset>
      <div className="ai-approval-actions"><button className="secondary-action" type="button" disabled={busy !== null || intent?.status === "APPROVED"} onClick={() => void save()}>{busy === "save" ? "저장 중…" : intent ? "수정사항 저장" : "초안 저장"}</button><button type="button" disabled={!intent || intent.status === "APPROVED" || busy !== null} onClick={() => void approve()}>{busy === "approve" ? "승인 중…" : intent?.status === "APPROVED" ? "승인 완료" : "법적 효력 제한 확인 후 승인"}</button></div>
      <small className="legal-note">이 의향서는 법적 위임·후견·유언이 아니며 금융거래를 자동 실행하지 않습니다.</small>
    </section>}

    <section className="panel ai-change-section"><div className="section-heading"><div><p className="label">2 · 설명 가능한 장기 변화</p><h2>최근 30일과 이전 60일을 비교합니다.</h2><p>총 90일의 합성 기준선에서 EWMA·CUSUM 보조 분석을 수행합니다.</p></div><button type="button" disabled={busy !== null} onClick={() => void analyze()}>{busy === "analysis" ? "분석 중…" : analysis ? "다시 분석" : "장기 변화 분석"}</button></div>
      {analysis && <><div className="change-meta"><span>분석 범위 {analysis.analysisWindowDays}일</span><span>{analysis.fallbackUsed ? "규칙 폴백" : "FastAPI 분석"}</span><span>진단 사용 안 함</span></div><div className="change-grid">{analysis.changes.map((item) => { const maximum = Math.max(item.baselineValue, item.recentValue, 1); return <article key={item.featureCode} className={item.changeDetected ? "changed" : ""}><div><strong>{LABELS[item.featureCode] ?? item.featureCode}</strong><span>{item.changeDetected ? "변화 확인" : "기준 범위"}</span></div><div className="comparison-bars"><span>이전 60일 기준 <i style={{ width: `${Math.max(6, item.baselineValue / maximum * 100)}%` }} /></span><b>{item.baselineValue}회</b><span>최근 30일 <i style={{ width: `${Math.max(6, item.recentValue / maximum * 100)}%` }} /></span><b>{item.recentValue}회</b></div><p>{item.explanation}</p><button type="button" disabled={busy !== null} onClick={() => void simplify(item.featureCode)}>쉬운말로 확인</button></article>; })}</div></>}
    </section>

    {plain && <section className="panel plain-language-card" aria-live="polite"><div><p className="label">3 · 맞춤형 쉬운 설명</p><h2>{plain.title}</h2><p>{plain.text}</p></div><div><span>{plain.explanationMode === "VOICE_AND_TEXT" ? "글과 음성" : "쉬운 글"}</span>{speechSupported && <button type="button" onClick={speak}>🔊 천천히 읽어주기</button>}</div></section>}
    {message && <p className="workflow-result" role="status">{message}</p>}{error && <p className="api-error" role="alert">{error}</p>}
  </div>;
}

function messageOf(reason: unknown): string { return reason instanceof Error ? reason.message : "요청을 처리하지 못했습니다."; }
