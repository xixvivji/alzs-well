import { ApiClientError, apiRequest } from "./api";
import type { DemoContext } from "./demo-session";

export type IntentFields = {
  paymentContinuity: "KEEP_ESSENTIAL_PAYMENTS" | "REVIEW_BEFORE_CHANGE";
  explanationMode: "SIMPLE_TEXT" | "VOICE_AND_TEXT" | "STAFF_EXPLANATION";
  helpCondition: "ON_REPEATED_CHANGE" | "ON_CUSTOMER_REQUEST" | "NEVER_AUTOMATIC";
  shareScopes: Array<"PAYMENT_PREFERENCE" | "EXPLANATION_PREFERENCE" | "HELP_CONDITION" | "ACCESSIBILITY">;
};
export type IntentSuggestion = {
  suggestion: IntentFields; summary: string;
  evidence: Array<{ field: string; excerpt: string; confidence: number }>;
  needsClarification: boolean; clarifyingQuestions: string[];
  generatedBy: string; modelInvoked: boolean; fallbackUsed: boolean;
  healthInferenceUsed: boolean; financialActionExecuted: boolean;
};
export type FinancialIntent = IntentFields & {
  intentId: string; customerId: string; status: "DRAFT" | "APPROVED"; version: number;
  disclaimerAccepted: boolean; legallyBinding: boolean; healthInferenceUsed: boolean;
};
export type ChangeItem = {
  featureCode: string; baselineValue: number; recentValue: number; delta: number;
  direction: "INCREASE" | "DECREASE" | "STABLE"; ewmaScore: number; cusumScore: number;
  changeDetected: boolean; persistent: boolean; dataSufficient: boolean;
  method: string; explanation: string;
};
export type ChangeAnalysis = {
  baselineDays: number; recentDays: number; analysisWindowDays: number; changes: ChangeItem[];
  analysisMode: string; fallbackUsed: boolean; syntheticData: boolean;
  diagnosisInferred: boolean; financialActionExecuted: boolean;
};
export type PlainLanguage = {
  featureCode: string; title: string; text: string; speechText: string; explanationMode: string;
  generationMode: string; modelInvoked: boolean; fallbackUsed: boolean;
  diagnosisInferred: boolean; financialActionExecuted: boolean;
};

const base = (context: DemoContext) => `/api/v1/demo/sessions/${encodeURIComponent(context.sessionId)}/customers/${encodeURIComponent(context.customerId)}/ai-financial-assistance`;
const options = (context: DemoContext) => ({ capability: context.capability, demoRunId: context.demoRunId });

export async function suggestFinancialIntent(context: DemoContext, utterance: string): Promise<IntentSuggestion> {
  const response = await apiRequest<IntentSuggestion>(`${base(context)}/intent-suggestions`, {
    ...options(context), method: "POST", body: JSON.stringify({ utterance }), timeoutMs: 12_000,
  });
  if (!response.body.data) throw new Error("AI 의향서 초안 응답을 확인해 주세요.");
  return response.body.data;
}

export async function saveFinancialIntent(context: DemoContext, fields: IntentFields, expectedVersion: number): Promise<FinancialIntent> {
  const response = await apiRequest<FinancialIntent>(`${base(context)}/intent`, {
    ...options(context), method: "PUT", body: JSON.stringify({ expectedVersion, ...fields }),
  });
  if (!response.body.data) throw new Error("금융생활 의향 저장 응답을 확인해 주세요.");
  return response.body.data;
}

export async function approveFinancialIntent(context: DemoContext, expectedVersion: number): Promise<FinancialIntent> {
  const response = await apiRequest<FinancialIntent>(`${base(context)}/intent/approve`, {
    ...options(context), method: "POST", body: JSON.stringify({ expectedVersion, disclaimerAccepted: true }),
  });
  if (!response.body.data) throw new Error("금융생활 의향 승인 응답을 확인해 주세요.");
  return response.body.data;
}

export async function loadCurrentFinancialIntent(context: DemoContext): Promise<FinancialIntent | null> {
  try {
    const response = await apiRequest<FinancialIntent>(`${base(context)}/intent`, options(context));
    return response.body.data;
  } catch (error) {
    if (error instanceof ApiClientError && error.status === 404 && error.code === "DEMO_AI_INTENT_NOT_FOUND") return null;
    throw error;
  }
}

export async function loadChangeAnalysis(context: DemoContext): Promise<ChangeAnalysis> {
  const response = await apiRequest<ChangeAnalysis>(`${base(context)}/change-analysis`, {
    ...options(context), method: "POST",
  });
  if (!response.body.data) throw new Error("장기 변화 분석 응답을 확인해 주세요.");
  return response.body.data;
}

export async function generatePlainLanguage(context: DemoContext, featureCode: string): Promise<PlainLanguage> {
  const response = await apiRequest<PlainLanguage>(`${base(context)}/plain-language`, {
    ...options(context), method: "POST", body: JSON.stringify({ featureCode }),
  });
  if (!response.body.data) throw new Error("쉬운 설명 응답을 확인해 주세요.");
  return response.body.data;
}
