import { apiRequest } from "./api";
import type { DemoContext } from "./demo-session";

export type AlertAuditItem = {
  auditId: string;
  eventType: string;
  actorType: string;
  fromState: string | null;
  toState: string | null;
  resultCode: string | null;
  evidenceIds: string[];
  algorithmVersion: string;
  policyVersion: string;
  traceId: string;
  occurredAt: string;
};
export type AlertAuditPage = { items: AlertAuditItem[]; nextCursor: string | null; hasMore: boolean };

export async function loadAlertAudit(context: DemoContext, alertId: string, cursor?: string): Promise<AlertAuditPage> {
  const query = new URLSearchParams({ limit: "20" });
  if (cursor) query.set("cursor", cursor);
  const response = await apiRequest<AlertAuditPage>(
    `/api/v1/demo/sessions/${encodeURIComponent(context.sessionId)}/alerts/${encodeURIComponent(alertId)}/audit?${query}`,
    { capability: context.capability, demoRunId: context.demoRunId },
  );
  if (!response.body.data) throw new Error("알림 감사이력 응답을 확인해 주세요.");
  return response.body.data;
}
