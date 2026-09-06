import { invokeApiOperation } from "./api-operation-client";
import { withPrivateCustomerSession } from "./private-auth-session";
import type { PrivateCustomerSession } from "./private-financial-products";
import { ApiClientError } from "./api";

export type SharedCaseIntent = { intentId: string; customerId: string; version: number; paymentContinuity: string | null; explanationMode: string | null; helpCondition: string | null; sharedScopes: string[]; nonConsentedFieldsExcluded: boolean };

export type OperationalCaseSummary = {
  caseId: string; alertId: string; signalId: string; customerId: string;
  reviewPriority: "HIGH" | "MEDIUM" | "LOW" | string; taskStatus: string;
  version: number; assignedTeam: string | null; assignedTo: string | null;
  createdAt: string; updatedAt: string;
};

export type OperationalCaseBundle = {
  detail: { caseSummary: OperationalCaseSummary; customerResponseCode: string | null; reasonCode: string; alertState: string; selectedActionCodes: string[]; guidancePlanId: string | null };
  evidence: { baselineValue: string; currentValue: string; unit: string; items: { evidenceId: string; description: string; occurredAt: string; amount: string | null; currency: string | null; sourceReference: string }[] };
  timeline: { eventType: string; summary: string; occurredAt: string; previousState: string | null; resultingState: string | null }[];
  notes: { noteId: string; noteText: string; createdAt: string; createdBy: string }[];
  followUps: { followUpId: string; purpose: string; status: string; scheduledAt: string; outcome: string | null; version: number }[];
};

export async function loadOperationalCaseQueue(session: PrivateCustomerSession): Promise<OperationalCaseSummary[]> {
  return (await loadOperationalCasePage(session)).items;
}

export async function loadOperationalCasePage(session: PrivateCustomerSession, cursor?: string): Promise<{ items: OperationalCaseSummary[]; nextCursor: string | null }> {
  requireStaff(session);
  return withPrivateCustomerSession(session, async (accessToken) => {
    const response = await invokeApiOperation<{ items: OperationalCaseSummary[]; nextCursor: string | null }>("GET /api/v1/staff/cases", {
      query: { limit: 100, ...(cursor ? { cursor } : {}) }, accessToken,
    });
    if (!response.body.data) throw new Error("사건 목록을 불러오지 못했습니다. 다시 확인해 주세요.");
    return { items: response.body.data.items, nextCursor: response.body.data.nextCursor ?? null };
  });
}

export async function loadOperationalCaseBundle(session: PrivateCustomerSession, caseId: string): Promise<OperationalCaseBundle> {
  requireStaff(session);
  return withPrivateCustomerSession(session, async (accessToken) => {
    const auth = { path: { caseId }, accessToken };
    const detail = await invokeApiOperation<OperationalCaseBundle["detail"]>("GET /api/v1/staff/cases/{caseId}", auth);
    const evidence = await invokeApiOperation<OperationalCaseBundle["evidence"]>("GET /api/v1/staff/cases/{caseId}/evidence", auth);
    const timeline = await invokeApiOperation<{ items: OperationalCaseBundle["timeline"] }>("GET /api/v1/staff/cases/{caseId}/timeline", auth);
    const notes = await invokeApiOperation<{ items: OperationalCaseBundle["notes"] }>("GET /api/v1/staff/cases/{caseId}/notes", auth);
    const followUps = await invokeApiOperation<{ items: OperationalCaseBundle["followUps"] }>("GET /api/v1/staff/cases/{caseId}/follow-ups", auth);
    if (!detail.body.data || !evidence.body.data || !timeline.body.data || !notes.body.data || !followUps.body.data) throw new Error("사건 상세 일부를 확인하지 못했습니다. 다시 불러와 주세요.");
    return {
      detail: detail.body.data, evidence: evidence.body.data,
      timeline: timeline.body.data.items, notes: notes.body.data.items, followUps: followUps.body.data.items,
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

export async function loadCaseCustomerIntent(session: PrivateCustomerSession, customerId: string): Promise<SharedCaseIntent | null> {
  requireStaff(session);
  return withPrivateCustomerSession(session, async (accessToken) => {
    try {
      const result = await invokeApiOperation<SharedCaseIntent>("GET /api/v1/staff/customers/{customerId}/financial-intent-summary", { path: { customerId }, accessToken });
      if (!result.body.data) throw new Error("고객 의향을 확인하지 못했습니다.");
      return result.body.data;
    } catch (reason) {
      if (reason instanceof ApiClientError && reason.code === "FINANCIAL_INTENT_NOT_FOUND") return null;
      throw reason;
    }
  });
}

export const guidanceActions = [
  ["FDS_REVIEW", "기존 금융사기 방지 절차 검토 안내"], ["DELAYED_TRANSFER_GUIDANCE", "지연이체 제도 안내"],
  ["SECURITY_SETTINGS_GUIDANCE", "보안 설정 안내"], ["BRANCH_CONSULTATION", "영업점 상담 안내"],
] as const;

export async function approveCaseGuidance(session: PrivateCustomerSession, item: OperationalCaseSummary, selectedActionCodes: string[]) {
  requireStaff(session);
  return withPrivateCustomerSession(session, (accessToken) => invokeApiOperation("POST /api/v1/staff/cases/{caseId}/guidance-plans", {
    path: { caseId: item.caseId }, accessToken, idempotencyKey: crypto.randomUUID(), body: { selectedActionCodes, expectedVersion: item.version },
  }));
}

export async function completeCaseReview(session: PrivateCustomerSession, item: OperationalCaseSummary, note: string) {
  requireStaff(session);
  return withPrivateCustomerSession(session, (accessToken) => invokeApiOperation("POST /api/v1/staff/cases/{caseId}/reviews", {
    path: { caseId: item.caseId }, accessToken, idempotencyKey: crypto.randomUUID(), body: { actionCode: "COMPLETE_REVIEW", note, expectedVersion: item.version },
  }));
}

export async function scheduleCaseFollowUp(session: PrivateCustomerSession, item: OperationalCaseSummary, scheduledAt: string, purpose: string) {
  requireStaff(session);
  return withPrivateCustomerSession(session, (accessToken) => invokeApiOperation("POST /api/v1/staff/cases/{caseId}/follow-ups", {
    path: { caseId: item.caseId }, accessToken, idempotencyKey: crypto.randomUUID(), body: { followUpType: "CUSTOMER_RECHECK", scheduledAt, purpose, expectedCaseVersion: item.version },
  }));
}

export async function finishCaseFollowUp(session: PrivateCustomerSession, followUp: OperationalCaseBundle["followUps"][number], actionCode: "COMPLETE" | "CANCEL", outcome: string) {
  requireStaff(session);
  return withPrivateCustomerSession(session, (accessToken) => invokeApiOperation("PATCH /api/v1/staff/follow-ups/{followUpId}", {
    path: { followUpId: followUp.followUpId }, accessToken, body: { actionCode, outcome, expectedVersion: followUp.version },
  }));
}
