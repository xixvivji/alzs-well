"use client";

import { useState } from "react";
import { suggestPrivateFinancialIntent } from "../lib/private-ai-intent";
import { approveIntent, createIntent, type FinancialIntent } from "../lib/private-life-services";
import type { PrivateCustomerSession } from "../lib/private-financial-products";

const DEFAULT_TEXT = "공과금은 계속 납부하고, 평소와 다른 변화가 반복되면 천천히 설명해 주세요.";

export function MemberAiIntentAssistant({ session, initialIntent }: { session: PrivateCustomerSession; initialIntent: FinancialIntent | null }) {
  const [utterance, setUtterance] = useState(DEFAULT_TEXT);
  const [paymentContinuity, setPaymentContinuity] = useState(initialIntent?.paymentContinuity ?? "KEEP_ESSENTIAL_PAYMENTS");
  const [explanationMode, setExplanationMode] = useState(initialIntent?.explanationMode ?? "SIMPLE_TEXT");
  const [helpCondition, setHelpCondition] = useState(initialIntent?.helpCondition ?? "ON_CUSTOMER_REQUEST");
  const [intent, setIntent] = useState(initialIntent);
  const [summary, setSummary] = useState(""); const [busy, setBusy] = useState(""); const [notice, setNotice] = useState(""); const [error, setError] = useState("");

  async function run(key: string, task: () => Promise<void>) {
    setBusy(key); setError(""); setNotice("");
    try { await task(); } catch (reason) { setError(reason instanceof Error ? reason.message : "요청을 처리하지 못했습니다."); }
    finally { setBusy(""); }
  }

  return <section className="member-ai-intent bank-panel">
    <header><div><p>AI 금융생활 의향서</p><h3>편한 말로 입력하고, 직접 확인해 저장하세요.</h3></div><span>회원 로그인 연결</span></header>
    <p className="member-ai-boundary">입력 문장만 AI 구조화 기능에 전달합니다. 회원 ID·계좌·거래는 전달하지 않으며 금융 실행도 일어나지 않습니다.</p>
    <label><span>나의 금융생활 의향</span><textarea value={utterance} maxLength={500} onChange={(event) => setUtterance(event.target.value)} /></label>
    <button className="secondary-button" disabled={busy !== "" || utterance.trim().length < 4} onClick={() => void run("suggest", async () => { const result = await suggestPrivateFinancialIntent(utterance); setPaymentContinuity(result.suggestion.paymentContinuity); setExplanationMode(result.suggestion.explanationMode); setHelpCondition(result.suggestion.helpCondition); setSummary(result.summary); setNotice(result.fallbackUsed ? "검증된 안전 템플릿으로 초안을 정리했습니다." : "AI가 문장을 구조화했습니다. 아래 항목을 확인해 주세요."); })}>{busy === "suggest" ? "AI가 정리 중…" : "AI로 초안 정리"}</button>
    {summary && <p className="ai-summary">{summary}</p>}
    <div className="member-ai-fields"><label><span>필수 납부</span><select value={paymentContinuity} onChange={(event) => setPaymentContinuity(event.target.value)}><option value="KEEP_ESSENTIAL_PAYMENTS">공과금·생활비 납부 유지</option><option value="REVIEW_BEFORE_CHANGE">변경 전에 다시 확인</option></select></label><label><span>설명 방식</span><select value={explanationMode} onChange={(event) => setExplanationMode(event.target.value)}><option value="SIMPLE_TEXT">짧고 쉬운 글</option><option value="VOICE_AND_TEXT">음성과 글 함께</option><option value="STAFF_EXPLANATION">행원이 천천히 설명</option></select></label><label><span>도움 요청 조건</span><select value={helpCondition} onChange={(event) => setHelpCondition(event.target.value)}><option value="ON_REPEATED_CHANGE">반복 변화가 있을 때</option><option value="ON_CUSTOMER_REQUEST">내가 요청했을 때</option><option value="NEVER_AUTOMATIC">자동 요청하지 않기</option></select></label></div>
    <div className="member-ai-actions">{!intent || intent.status === "REVOKED" ? <button disabled={busy !== ""} onClick={() => void run("save", async () => { const saved = await createIntent(session, { paymentContinuity, explanationMode, helpCondition, shareScopes: [] }); setIntent(saved); setNotice("로그인 회원의 수정 가능한 초안으로 저장했습니다."); })}>{busy === "save" ? "저장 중…" : "회원 의향서 초안 저장"}</button> : intent.status === "DRAFT" ? <button disabled={busy !== ""} onClick={() => void run("approve", async () => { setIntent(await approveIntent(session, intent)); setNotice("법적 효력 제한을 확인하고 본인이 승인했습니다."); })}>{busy === "approve" ? "승인 중…" : "확인 후 본인 승인"}</button> : <span className="status-pill safe">승인 완료 · v{intent.version}</span>}</div>
    <small className="legal-note">행원 공유 범위는 기본 ‘공유 안 함’입니다. 생활금융 화면에서 별도로 변경할 수 있습니다.</small>
    {notice && <p className="workflow-result" role="status">{notice}</p>}{error && <p className="api-error" role="alert">{error}</p>}
  </section>;
}
