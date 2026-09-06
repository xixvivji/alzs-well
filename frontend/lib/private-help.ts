import { invokeApiOperation } from "./api-operation-client";
import { withPrivateCustomerSession } from "./private-auth-session";
import type { PrivateCustomerSession } from "./private-financial-products";
import type { FinancialIntent, InboxMessage } from "./private-life-services";
import type { Baseline, ChangeSignal, SafetyAlert } from "./private-safety-center";
import type { ChangeAnalysis } from "./ai-financial-assistance";

export type PrivateHelpOverview = {
  preparation: { readiness: string; latestApproved: FinancialIntent | null; legalDisclaimerRequired: boolean };
  intents: FinancialIntent[]; inbox: InboxMessage[]; baselines: Baseline[]; signals: ChangeSignal[]; alerts: SafetyAlert[];
};
export type PrivateChangeStatus = { detectedCount: number; needsResponseCount: number };
export type PrivateChangeIndicator = { count: number; badge: "확인" | "변화"; ariaLabel: string };

export function getPrivateChangeIndicator(status: PrivateChangeStatus): PrivateChangeIndicator | null {
  if (status.needsResponseCount > 0) return { count: status.needsResponseCount, badge: "확인", ariaLabel: "직접 확인할 변화" };
  if (status.detectedCount > 0) return { count: status.detectedCount, badge: "변화", ariaLabel: "열린 금융생활 변화" };
  return null;
}

const required = <T>(response: { body: { data: T | null } }, label: string): T => {
  if (response.body.data === null) throw new Error(`${label} 응답을 확인해 주세요.`);
  return response.body.data;
};

export async function loadPrivateHelpOverview(session: PrivateCustomerSession): Promise<PrivateHelpOverview> {
  return withPrivateCustomerSession(session, async (accessToken) => {
    const path = { customerId: session.customerId }; const auth = { accessToken };
    const [preparation, intents, inbox, baselines, signals, alerts] = await Promise.all([
      invokeApiOperation<PrivateHelpOverview["preparation"]>("GET /api/v1/customers/{customerId}/continuity-preparation", { path, ...auth }),
      invokeApiOperation<{ items: FinancialIntent[] }>("GET /api/v1/customers/{customerId}/financial-intents/versions", { path, ...auth }),
      invokeApiOperation<{ items: InboxMessage[] }>("GET /api/v1/customers/{customerId}/inbox", { path, ...auth }),
      invokeApiOperation<{ items: Baseline[] }>("GET /api/v1/customers/{customerId}/baselines", { path, ...auth }),
      invokeApiOperation<{ items: ChangeSignal[] }>("GET /api/v1/customers/{customerId}/signals", { path, ...auth }),
      invokeApiOperation<{ items: SafetyAlert[] }>("GET /api/v1/customers/{customerId}/alerts", { path, ...auth }),
    ]);
    return { preparation: required(preparation, "금융생활 의향"), intents: required(intents, "의향 이력").items, inbox: required(inbox, "알림함").items, baselines: required(baselines, "개인 기준선").items, signals: required(signals, "변화 신호").items, alerts: required(alerts, "확인 알림").items };
  });
}

export async function loadPrivateChangeStatus(session: PrivateCustomerSession): Promise<PrivateChangeStatus> {
  return withPrivateCustomerSession(session, async (accessToken) => {
    const path = { customerId: session.customerId }; const auth = { accessToken };
    const [signals, alerts] = await Promise.all([
      invokeApiOperation<{ items: ChangeSignal[] }>("GET /api/v1/customers/{customerId}/signals", { path, ...auth }),
      invokeApiOperation<{ items: SafetyAlert[] }>("GET /api/v1/customers/{customerId}/alerts", { path, ...auth }),
    ]);
    return {
      detectedCount: required(signals, "변화 신호").items.filter((item) => item.status === "OPEN").length,
      needsResponseCount: required(alerts, "확인 알림").items.filter((item) => ["AWAITING_CONTEXT", "DEFERRED"].includes(item.state)).length,
    };
  });
}

export async function loadPrivateLongitudinalAnalysis(
  session: PrivateCustomerSession,
): Promise<ChangeAnalysis> {
  return withPrivateCustomerSession(session, async (accessToken) => {
    const response = await invokeApiOperation<ChangeAnalysis>(
      "POST /api/v1/customers/{customerId}/ai-financial-assistance/change-analysis",
      { path: { customerId: session.customerId }, accessToken },
    );
    return required(response, "AI 장기 변화 분석");
  });
}
