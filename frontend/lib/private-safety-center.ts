import { invokeApiOperation } from "./api-operation-client";
import { withPrivateCustomerSession } from "./private-auth-session";
import type { PrivateCustomerSession } from "./private-financial-products";

export type Baseline = {
  baselineId: string; featureCode: string; baselineValue: string; currentValue: string;
  unit: string; readiness: string; comparisonText: string; algorithmVersion: string;
  calculatedAt: string; version: number;
};
export type BaselineFeature = {
  featureId: string; featureCode: string; value: string; unit: string;
  observedPeriod: { from: string; to: string }; sampleCount: number;
};
export type ChangeSignal = {
  signalId: string; baselineId: string; signalType: string; severity: string;
  baselineValue: string; currentValue: string; unit: string; reasonCode: string;
  status: string; algorithmVersion: string; detectedAt: string;
};
export type SignalEvidence = {
  evidenceId: string; evidenceType: string; sourceReference: string; occurredAt: string;
  amount: string; currency: string; description: string;
};
export type SafetyAlert = {
  alertId: string; signalId: string; state: string; severity: string; reasonCode: string;
  version: number; deferredUntil: string | null; createdAt: string; updatedAt: string;
};
export type AlertContextOption = { responseCode: string; label: string; description: string };
export type AlertAuditEvent = {
  auditEventId: string; eventType: string; previousState: string | null;
  resultingState: string; detail: Record<string, unknown>; integrityHash: string; createdAt: string;
};
export type SafetyCenterBundle = {
  baselines: Baseline[]; baselineFeatures: BaselineFeature[];
  signals: ChangeSignal[]; evidence: SignalEvidence[]; alerts: SafetyAlert[];
  selectedAlert: SafetyAlert | null; contextQuestion: string; contextOptions: AlertContextOption[];
  audit: AlertAuditEvent[];
};

const body = <T>(response: { body: { data: T | null } }, label: string): T => {
  if (response.body.data === null) throw new Error(`${label} 응답을 확인해 주세요.`);
  return response.body.data;
};

export async function loadSafetyCenter(
  session: PrivateCustomerSession,
  alertId?: string,
  signal?: AbortSignal,
): Promise<SafetyCenterBundle> {
  return withPrivateCustomerSession(session, async (accessToken) => {
    const path = { customerId: session.customerId };
    const [baselineResponse, signalResponse, alertResponse] = await Promise.all([
      invokeApiOperation<{ items: Baseline[] }>("GET /api/v1/customers/{customerId}/baselines", { path, accessToken, signal }),
      invokeApiOperation<{ items: ChangeSignal[] }>("GET /api/v1/customers/{customerId}/signals", { path, accessToken, signal }),
      invokeApiOperation<{ items: SafetyAlert[] }>("GET /api/v1/customers/{customerId}/alerts", { path, accessToken, signal }),
    ]);
    const baselines = body(baselineResponse, "개인 기준선").items;
    const signals = body(signalResponse, "변화신호").items;
    const alerts = body(alertResponse, "확인 알림").items;
    const selectedBaseline = baselines[0];
    const selectedSignal = signals[0];
    const selectedAlert = alerts.find((item) => item.alertId === alertId) ?? alerts[0] ?? null;
    const [featureResponse, evidenceResponse, alertDetailResponse, optionResponse, auditResponse] = await Promise.all([
      selectedBaseline ? invokeApiOperation<{ items: BaselineFeature[] }>("GET /api/v1/customers/{customerId}/baselines/{baselineId}/features", { path: { ...path, baselineId: selectedBaseline.baselineId }, accessToken, signal }) : null,
      selectedSignal ? invokeApiOperation<{ items: SignalEvidence[] }>("GET /api/v1/signals/{signalId}/evidence", { path: { signalId: selectedSignal.signalId }, accessToken, signal }) : null,
      selectedAlert ? invokeApiOperation<{ alert: SafetyAlert }>("GET /api/v1/alerts/{alertId}", { path: { alertId: selectedAlert.alertId }, accessToken, signal }) : null,
      selectedAlert ? invokeApiOperation<{ question: string; options: AlertContextOption[] }>("GET /api/v1/alerts/{alertId}/context-options", { path: { alertId: selectedAlert.alertId }, accessToken, signal }) : null,
      selectedAlert ? invokeApiOperation<{ items: AlertAuditEvent[] }>("GET /api/v1/alerts/{alertId}/audit", { path: { alertId: selectedAlert.alertId }, accessToken, signal }) : null,
    ]);
    return {
      baselines,
      baselineFeatures: featureResponse ? body(featureResponse, "기준선 특징값").items : [],
      signals,
      evidence: evidenceResponse ? body(evidenceResponse, "변화 근거").items : [],
      alerts,
      selectedAlert: alertDetailResponse ? body(alertDetailResponse, "알림 상세").alert : selectedAlert,
      contextQuestion: optionResponse ? body(optionResponse, "확인 선택지").question : "",
      contextOptions: optionResponse ? body(optionResponse, "확인 선택지").options : [],
      audit: auditResponse ? body(auditResponse, "판단 이력").items : [],
    };
  });
}

export async function respondToSafetyAlert(session: PrivateCustomerSession, alert: SafetyAlert, responseCode: string): Promise<void> {
  await withPrivateCustomerSession(session, (accessToken) => invokeApiOperation("POST /api/v1/alerts/{alertId}/context-responses", {
    path: { alertId: alert.alertId }, accessToken, idempotencyKey: crypto.randomUUID(),
    body: { responseCode, expectedVersion: alert.version },
  }));
}
