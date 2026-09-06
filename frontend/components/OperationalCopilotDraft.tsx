"use client";

import { useEffect, useRef, useState } from "react";
import { invokeApiOperation } from "../lib/api-operation-client";
import { withPrivateCustomerSession } from "../lib/private-auth-session";
import type { PrivateCustomerSession } from "../lib/private-financial-products";

type Draft = {
  summary: string;
  suggestedQuestions: string[];
  checklist: string[];
  generatedBy: string;
  fallbackUsed: boolean;
  citations: { passageId: string; citationLabel: string; sourceUrl: string }[];
};

export function OperationalCopilotDraft({ session, caseId }: {
  session: PrivateCustomerSession;
  caseId: string;
}) {
  const [draft, setDraft] = useState<Draft | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const request = useRef<AbortController | null>(null);

  useEffect(() => () => { request.current?.abort(); }, [caseId]);

  async function generate() {
    if (request.current && !request.current.signal.aborted) return;
    const controller = new AbortController();
    request.current = controller;
    setBusy(true);
    setError("");
    setDraft(null);
    try {
      const response = await withPrivateCustomerSession(session, (accessToken) => invokeApiOperation<Draft>(
        "POST /api/v1/staff/cases/{caseId}/copilot-drafts",
        { path: { caseId }, accessToken, signal: controller.signal, timeoutMs: 20_000 },
      ));
      if (!controller.signal.aborted) {
        if (!response.body.data) throw new Error("초안 응답이 비어 있습니다.");
        setDraft(response.body.data);
      }
    } catch (reason) {
      if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : "초안을 불러오지 못했습니다.");
    } finally {
      if (!controller.signal.aborted) setBusy(false);
      if (request.current === controller) request.current = null;
    }
  }

  return <section aria-label="AI 검토 지원" aria-busy={busy}>
    <h3>AI 검토 지원</h3>
    <p>사건 사유·고객 응답과 승인된 검색 근거로 검토를 돕습니다. 초안은 저장되거나 승인되지 않습니다.</p>
    <button type="button" className="secondary-button" disabled={busy} onClick={() => void generate()}>
      {busy ? "검토 초안 준비 중…" : "검토 초안 만들기"}
    </button>
    {error && <p role="alert">{error} 기존 근거를 확인하며 검토를 계속할 수 있습니다.</p>}
    {draft && <div aria-live="polite">
      <p><strong>{draft.generatedBy === "BEDROCK_GENERATIVE_DRAFT" ? "생성형 AI 초안 · Bedrock" : "기본 안내 · 생성형 AI 결과 아님"}</strong></p>
      {draft.fallbackUsed && <p>기본 안내를 사용합니다. 생성형 기능 비활성화, 근거 부족 또는 호출·검증 실패일 수 있습니다.</p>}
      <p>{draft.summary}</p>
      <h4>고객에게 확인할 질문</h4>
      <ul>{draft.suggestedQuestions.map((item, index) => <li key={index}>{item}</li>)}</ul>
      <h4>검토 체크리스트</h4>
      <ul>{draft.checklist.map((item, index) => <li key={index}>{item}</li>)}</ul>
      <h4>서버가 확인한 참고 근거</h4>
      <ul>{draft.citations.map((item) => <li key={item.passageId}>
        {safeSource(item.sourceUrl) ? <a href={item.sourceUrl} target="_blank" rel="noopener noreferrer">{item.citationLabel}</a> : item.citationLabel}
      </li>)}</ul>
      <p>근거를 제공했다는 것이 모든 생성 문장의 정확성을 보장하지는 않습니다. 행원이 원문과 사건 사실을 확인하고 최종 판단합니다.</p>
    </div>}
  </section>;
}

function safeSource(value: string) {
  try { return new URL(value).protocol === "https:"; } catch { return false; }
}
