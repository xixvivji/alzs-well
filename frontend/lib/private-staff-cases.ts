import { invokeApiOperation } from "./api-operation-client";
import { withPrivateCustomerSession } from "./private-auth-session";
import type { PrivateCustomerSession } from "./private-financial-products";

export type OperationalCaseSummary = {
  caseId: string; alertId: string; signalId: string; customerId: string;
  reviewPriority: "HIGH" | "MEDIUM" | "LOW" | string; taskStatus: string;
  version: number; assignedTeam: string | null; assignedTo: string | null;
  createdAt: string; updatedAt: string;
};

export type OperationalCaseBundle = {
  detail: Record<string, unknown>; evidence: Record<string, unknown>;
  timeline: Record<string, unknown>[]; notes: Record<string, unknown>[];
  followUps: Record<string, unknown>[];
};

export async function loadOperationalCaseQueue(session: PrivateCustomerSession): Promise<OperationalCaseSummary[]> {
  requireStaff(session);
  return withPrivateCustomerSession(session, async (accessToken) => {
    const response = await invokeApiOperation<{ items: OperationalCaseSummary[] }>("GET /api/v1/staff/cases", {
      query: { limit: 100 }, accessToken,
    });
    return response.body.data?.items ?? [];
  });
}

export async function loadOperationalCaseBundle(session: PrivateCustomerSession, caseId: string): Promise<OperationalCaseBundle> {
  requireStaff(session);
  return withPrivateCustomerSession(session, async (accessToken) => {
    const auth = { path: { caseId }, accessToken };
    const detail = await invokeApiOperation<Record<string, unknown>>("GET /api/v1/staff/cases/{caseId}", auth);
    const evidence = await invokeApiOperation<Record<string, unknown>>("GET /api/v1/staff/cases/{caseId}/evidence", auth);
    const timeline = await invokeApiOperation<Record<string, unknown>>("GET /api/v1/staff/cases/{caseId}/timeline", auth);
    const notes = await invokeApiOperation<Record<string, unknown>>("GET /api/v1/staff/cases/{caseId}/notes", auth);
    const followUps = await invokeApiOperation<Record<string, unknown>>("GET /api/v1/staff/cases/{caseId}/follow-ups", auth);
    return {
      detail: detail.body.data ?? {}, evidence: evidence.body.data ?? {},
      timeline: items(timeline.body.data), notes: items(notes.body.data), followUps: items(followUps.body.data),
    };
  });
}

export async function startOperationalCaseReview(session: PrivateCustomerSession, item: OperationalCaseSummary): Promise<void> {
  requireStaff(session);
  let expectedVersion = item.version;
  if (!item.assignedTo) {
    await withPrivateCustomerSession(session, (accessToken) => invokeApiOperation("PUT /api/v1/staff/cases/{caseId}/assignment", {
      path: { caseId: item.caseId }, accessToken, idempotencyKey: crypto.randomUUID(),
      body: { assignedTeam: "SYNTHETIC_PROTECTION_TEAM", assignedTo: session.principalId, expectedVersion },
    }));
    expectedVersion += 1;
  } else if (item.assignedTo !== session.principalId) {
    throw new Error("다른 행원에게 배정된 사건은 검토를 시작할 수 없습니다.");
  }
  await withPrivateCustomerSession(session, (accessToken) => invokeApiOperation("POST /api/v1/staff/cases/{caseId}/reviews", {
    path: { caseId: item.caseId }, accessToken, idempotencyKey: crypto.randomUUID(),
    body: { actionCode: "START_REVIEW", note: "합성 고객의 응답과 불변 근거 검토를 시작합니다.", expectedVersion },
  }));
}

export async function addOperationalCaseNote(session: PrivateCustomerSession, caseId: string, noteText: string): Promise<void> {
  requireStaff(session);
  await withPrivateCustomerSession(session, (accessToken) => invokeApiOperation("POST /api/v1/staff/cases/{caseId}/notes", {
    path: { caseId }, accessToken, idempotencyKey: crypto.randomUUID(), body: { noteText },
  }));
}

function requireStaff(session: PrivateCustomerSession) {
  if (!session.roles.includes("PROTECTION_STAFF")) throw new Error("보호업무 행원 권한이 필요합니다.");
}

function items(value: Record<string, unknown> | null): Record<string, unknown>[] {
  const candidate = value?.items;
  return Array.isArray(candidate) ? candidate as Record<string, unknown>[] : [];
}
