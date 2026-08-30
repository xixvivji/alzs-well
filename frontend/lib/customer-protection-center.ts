import { loadAlertAudit, type AlertAuditItem } from "./alert-audit";
import {
  loadChangeAnalysis,
  loadCurrentFinancialIntent,
  type ChangeAnalysis,
  type FinancialIntent,
} from "./ai-financial-assistance";
import { invokeApiOperation } from "./api-operation-client";
import type { DemoContext } from "./demo-session";

export type ProtectionFinancialSummary = {
  assets: { total: { amount: string } };
  cashFlow: { monthlyIncome: { amount: string }; monthlyExpense: { amount: string } };
  changeSummary: { openAlertCount: number; summary: string };
};

export type ProtectionAlert = {
  alertId: string;
  state?: string;
  severity?: string;
  title?: string;
  summary?: string;
  explanation?: string;
  reasonCode?: string;
};

export type CustomerProtectionSnapshot = {
  financialSummary: ProtectionFinancialSummary;
  alerts: ProtectionAlert[];
  intent: FinancialIntent | null;
  analysis: ChangeAnalysis | null;
  audit: AlertAuditItem[];
  unavailable: Array<"intent" | "analysis" | "audit">;
};

export async function loadCustomerProtectionSnapshot(
  context: DemoContext,
): Promise<CustomerProtectionSnapshot> {
  const path = { sessionId: context.sessionId, customerId: context.customerId };
  const auth = { capability: context.capability, demoRunId: context.demoRunId };
  const [summaryResponse, alertsResponse] = await Promise.all([
    invokeApiOperation<ProtectionFinancialSummary>(
      "GET /api/v1/demo/sessions/{sessionId}/customers/{customerId}/financial-summary",
      { path, ...auth },
    ),
    invokeApiOperation<{ items: ProtectionAlert[] }>(
      "GET /api/v1/demo/sessions/{sessionId}/customers/{customerId}/alerts",
      { path, ...auth },
    ),
  ]);

  const financialSummary = summaryResponse.body.data;
  if (!financialSummary) throw new Error("금융생활 보호 요약을 확인하지 못했습니다.");

  const [intentResult, analysisResult, auditResult] = await Promise.allSettled([
    loadCurrentFinancialIntent(context),
    loadChangeAnalysis(context),
    loadAlertAudit(context, context.alertId),
  ]);
  const unavailable: CustomerProtectionSnapshot["unavailable"] = [];
  if (intentResult.status === "rejected") unavailable.push("intent");
  if (analysisResult.status === "rejected") unavailable.push("analysis");
  if (auditResult.status === "rejected") unavailable.push("audit");

  return {
    financialSummary,
    alerts: alertsResponse.body.data?.items ?? [],
    intent: intentResult.status === "fulfilled" ? intentResult.value : null,
    analysis: analysisResult.status === "fulfilled" ? analysisResult.value : null,
    audit: auditResult.status === "fulfilled" ? auditResult.value.items : [],
    unavailable,
  };
}
