import { ApiClientError, apiRequest, type ApiResponse } from "./api";
import type { DemoContext } from "./demo-session";

export type StaffCaseContext = Pick<DemoContext, "sessionId" | "demoRunId">;

export type StaffCaseState =
  | "PENDING_BANK_REVIEW"
  | "IN_BANK_REVIEW"
  | "FOLLOW_UP_REQUIRED"
  | "GUIDANCE_PLAN_APPROVED"
  | "CLOSED_FALSE_POSITIVE";

export type StaffReviewPriority = "HIGH" | "MEDIUM" | "LOW";

export type StaffCaseQueueItem = {
  demoRunId: string;
  caseId: string;
  alertId: string;
  customerId: string;
  state: StaffCaseState | string;
  reviewPriority: StaffReviewPriority | string;
  reasonCodes: string[];
  customerResponseCode: string;
  summary: string;
  trustedContactGate: {
    gateEvaluated: boolean;
    consentSnapshotId: string | null;
    consentStatus: string;
    recipientAccepted: boolean;
    triggerMatched: boolean;
    fieldScopeMatched: boolean;
    validityMatched: boolean;
    deliveryEnabled: boolean;
    resultCode: string | null;
    dispatchAttempted: boolean;
    externalDeliveryRequested: boolean;
    externalDeliveryCreated: boolean;
  };
  createdAt: string;
  caseVersion: number;
  sessionResetVersion: number;
};

export type StaffCaseQueue = {
  items: StaffCaseQueueItem[];
  nextCursor: string | null;
  hasMore: boolean;
};

export type StaffCaseQueueQuery = {
  state?: StaffCaseState;
  reviewPriority?: StaffReviewPriority;
  cursor?: string;
  limit?: number;
  signal?: AbortSignal;
};

export type StaffCaseDetail = {
  caseId: string;
  caseVersion: number;
  state: string;
  reviewPriority: string;
  alert: {
    alertId: string;
    preDecision: string;
    postDecision: string;
    reasonCodes: string[];
    algorithmVersion: string;
    policyVersion: string;
  };
  customerContext: {
    responseCode: string;
    contextTypes: string[];
    confirmedItems: string[];
    unconfirmedItems: string[];
  };
  timeline: Array<{
    phase: string;
    type: string;
    title: string;
    occurredAt: string;
    evidenceIds: string[];
  }>;
  suggestedQuestions: Array<{
    questionId: string;
    text: string;
    basisReasonCodes: string[];
  }>;
  protectionCandidates: ProtectionCandidate[];
  guidancePlan: {
    status: string;
    approvedAt: string | null;
    delivered: boolean;
    deliveredAt: string | null;
  };
  capabilities: {
    externalMessage: boolean;
    transactionHold: boolean;
    limitChange: boolean;
    accountBlock: boolean;
  };
  allowedActions: Array<{
    action: string;
    enabled: boolean;
    disabledReasonCode: string | null;
  }>;
};

export type ProtectionCandidate = {
  actionCode: string;
  title: string;
  eligibilitySummary: string;
  source: {
    issuer: string;
    url: string;
    effectiveFrom: string | null;
    checkedAt: string;
  };
  executionType: string;
};

export type StaffCaseEvidence = {
  immutableT0: boolean;
  signals: Array<{
    signalId: string;
    reasonCode: string;
    observedCount: number;
    windowSeconds: number;
    algorithmVersion: string;
    detectedAt: string;
    snapshotHash: string;
    evidenceIds: string[];
  }>;
  provenance: {
    syntheticData: boolean;
    sourceProvider: string;
    externalFetchPerformed: boolean;
  };
};

export type StaffCaseBundle = {
  detail: StaffCaseDetail;
  evidence: StaffCaseEvidence;
};

export type CopilotDraftResult = {
  caseId: string;
  draftType: string;
  draft: {
    summary: string;
    suggestedQuestions: string[];
    checklist: string[];
    basisReasonCodes: string[];
    generatedBy: string;
    fallbackUsed: boolean;
    modelInvoked: boolean;
    externalEgressAttempted: boolean;
    retrievalMode: string;
    citations: Array<{
      documentId: string;
      versionLabel: string;
      passageId: string;
      citationLabel: string;
      sourceUrl: string;
      retrievalMode: string;
    }>;
  };
  safety: {
    syntheticDataOnly: boolean;
    containsDirectIdentifiers: boolean;
    externalActionCreated: boolean;
    humanReviewRequired: boolean;
  };
};

type MutationData = {
  caseId: string;
  previousState: string;
  currentState: string;
  caseVersion: number;
  externalExecutionCreated: boolean;
};

export type MutationResult = {
  message: string;
  data: MutationData;
};

export type StaffCaseTimeline = {
  caseId: string;
  currentState: string;
  caseVersion: number;
  phases: StaffCaseDetail["timeline"];
  auditTrail: Array<{ auditId: string; eventType: string; actorType: string; fromState: string | null; toState: string | null; occurredAt: string }>;
  hasMore: boolean;
  externalActionCreated: false;
};
export type StaffCaseNote = { noteId: string; caseVersion: number; noteText: string; createdBy: string; createdAt: string; isVisibleToCustomer: false };
export type StaffCaseNotes = { items: StaffCaseNote[]; count: number; externalDeliveryCreated: false };
export type StaffFollowUp = { followUpId: string; status: "SCHEDULED" | "COMPLETED" | "CANCELLED"; reason: string; scheduledAt: string; resultNote: string | null; completedAt: string | null; createdAt: string; updatedAt: string; createdBy: string; externalDeliveryCreated: false };
export type StaffFollowUps = { items: StaffFollowUp[]; count: number; nextFollowUpAt: string | null };
export type StaffCaseOperations = { timeline: StaffCaseTimeline; notes: StaffCaseNotes; followUps: StaffFollowUps };

export async function issueStaffCapability(sessionId: string): Promise<string> {
  const response = await fetch(`/api/internal/staff-capability/${encodeURIComponent(sessionId)}`, {
    method: "POST",
    headers: { Accept: "application/json" },
  });
  if (!response.ok) {
    let message = "직원 접근 권한을 확인할 수 없습니다.";
    try {
      const body = await response.json() as Partial<ApiResponse<unknown>>;
      if (body.message) message = body.message;
    } catch {
      // 서버의 안정적인 오류 문구만 사용하고 HTML·내부 오류 내용은 노출하지 않는다.
    }
    throw new ApiClientError("http", message, response.status, undefined,
      response.headers.get("X-Trace-Id") ?? undefined, response.status >= 500);
  }
  const capability = response.headers.get("X-Demo-Staff-Capability");
  if (!capability) throw new ApiClientError("parse", "직원 capability 응답이 올바르지 않습니다.");
  return capability;
}

export async function loadStaffCaseQueue(
  context: StaffCaseContext,
  staffCapability: string,
  query: StaffCaseQueueQuery = {},
): Promise<StaffCaseQueue> {
  const parameters = new URLSearchParams();
  if (query.state) parameters.set("state", query.state);
  if (query.reviewPriority) parameters.set("reviewPriority", query.reviewPriority);
  if (query.cursor) parameters.set("cursor", query.cursor);
  parameters.set("limit", String(query.limit ?? 20));
  const response = await apiRequest<StaffCaseQueue>(
    `/api/v1/demo/sessions/${encodeURIComponent(context.sessionId)}/staff/cases?${parameters.toString()}`,
    { staffCapability, demoRunId: context.demoRunId, signal: query.signal },
  );
  return requireData(response.body, "행원 사건큐 응답을 확인할 수 없습니다.");
}

export async function loadStaffCase(
  context: StaffCaseContext,
  caseId: string,
  staffCapability: string,
): Promise<StaffCaseBundle> {
  const path = casePath(context.sessionId, caseId);
  const options = { staffCapability, demoRunId: context.demoRunId };
  const [detail, evidence] = await Promise.all([
    apiRequest<StaffCaseDetail>(path, options),
    apiRequest<StaffCaseEvidence>(`${path}/evidence`, options),
  ]);
  return {
    detail: requireData(detail.body, "사건 상세 응답을 확인할 수 없습니다."),
    evidence: requireData(evidence.body, "사건 근거 응답을 확인할 수 없습니다."),
  };
}

export async function loadStaffCaseOperations(
  context: StaffCaseContext, caseId: string, staffCapability: string,
): Promise<StaffCaseOperations> {
  const path = casePath(context.sessionId, caseId);
  const options = { staffCapability, demoRunId: context.demoRunId };
  const [timeline, notes, followUps] = await Promise.all([
    apiRequest<StaffCaseTimeline>(`${path}/timeline`, options),
    apiRequest<StaffCaseNotes>(`${path}/notes`, options),
    apiRequest<StaffFollowUps>(`${path}/follow-ups`, options),
  ]);
  return {
    timeline: requireData(timeline.body, "사건 타임라인 응답을 확인할 수 없습니다."),
    notes: requireData(notes.body, "사건 메모 응답을 확인할 수 없습니다."),
    followUps: requireData(followUps.body, "후속 일정 응답을 확인할 수 없습니다."),
  };
}

export async function addStaffCaseNote(
  context: StaffCaseContext, caseId: string, staffCapability: string, caseVersion: number, note: string,
): Promise<MutationResult> {
  return mutate(`${casePath(context.sessionId, caseId)}/notes`, "POST", context, staffCapability, { caseVersion, note });
}

export async function scheduleStaffFollowUp(
  context: StaffCaseContext, caseId: string, staffCapability: string, caseVersion: number,
  scheduledAt: string, reason: string,
): Promise<MutationResult> {
  return mutate(`${casePath(context.sessionId, caseId)}/follow-ups`, "POST", context, staffCapability, { caseVersion, scheduledAt, reason });
}

export async function updateStaffFollowUp(
  context: StaffCaseContext, followUpId: string, staffCapability: string, caseVersion: number,
  status: "COMPLETED" | "CANCELLED", resultNote: string,
): Promise<MutationResult> {
  return mutate(`/api/v1/demo/sessions/${encodeURIComponent(context.sessionId)}/staff/follow-ups/${encodeURIComponent(followUpId)}`,
    "PATCH", context, staffCapability, { caseVersion, status, resultNote, completedAt: null });
}

export async function generateStaffCopilotDraft(
  context: StaffCaseContext,
  caseId: string,
  staffCapability: string,
): Promise<CopilotDraftResult> {
  const response = await apiRequest<CopilotDraftResult>(`${casePath(context.sessionId, caseId)}/copilot-drafts`, {
    method: "POST",
    body: JSON.stringify({ draftType: "CONSULTATION_NOTE" }),
    staffCapability,
    demoRunId: context.demoRunId,
  });
  return requireData(response.body, "코파일럿 초안 응답을 확인할 수 없습니다.");
}

export async function startStaffCaseReview(
  context: StaffCaseContext,
  caseId: string,
  staffCapability: string,
  caseVersion: number,
): Promise<MutationResult> {
  const response = await apiRequest<MutationData>(`${casePath(context.sessionId, caseId)}/review`, {
    method: "POST",
    body: JSON.stringify({
      action: "START_REVIEW",
      caseVersion,
      note: "고객 응답과 합성 근거를 확인합니다.",
      followUpAt: null,
    }),
    staffCapability,
    demoRunId: context.demoRunId,
    idempotencyKey: crypto.randomUUID(),
  });
  return {
    message: response.body.message,
    data: requireData(response.body, "검토 상태 응답을 확인할 수 없습니다."),
  };
}

export async function closeStaffCaseAsFalsePositive(
  context: StaffCaseContext,
  caseId: string,
  staffCapability: string,
  caseVersion: number,
  note: string,
): Promise<MutationResult> {
  const response = await apiRequest<MutationData>(`${casePath(context.sessionId, caseId)}/review`, {
    method: "POST",
    body: JSON.stringify({
      action: "CLOSE_FALSE_POSITIVE",
      caseVersion,
      note,
      followUpAt: null,
    }),
    staffCapability,
    demoRunId: context.demoRunId,
    idempotencyKey: crypto.randomUUID(),
  });
  return {
    message: response.body.message,
    data: requireData(response.body, "오탐 종결 응답을 확인할 수 없습니다."),
  };
}

export async function approveStaffGuidancePlan(
  context: StaffCaseContext,
  caseId: string,
  staffCapability: string,
  caseVersion: number,
  selectedActionCodes: string[],
  staffNote: string,
): Promise<MutationResult> {
  const response = await apiRequest<MutationData>(`${casePath(context.sessionId, caseId)}/guidance-plan`, {
    method: "POST",
    body: JSON.stringify({
      caseVersion,
      decision: "APPROVE_GUIDANCE_PLAN",
      selectedActionCodes,
      staffNote,
    }),
    staffCapability,
    demoRunId: context.demoRunId,
    idempotencyKey: crypto.randomUUID(),
  });
  return {
    message: response.body.message,
    data: requireData(response.body, "안내계획 응답을 확인할 수 없습니다."),
  };
}

function casePath(sessionId: string, caseId: string): string {
  return `/api/v1/demo/sessions/${encodeURIComponent(sessionId)}/cases/${encodeURIComponent(caseId)}`;
}

async function mutate(
  path: string, method: "POST" | "PATCH", context: StaffCaseContext, staffCapability: string,
  body: Record<string, unknown>,
): Promise<MutationResult> {
  const response = await apiRequest<MutationData>(path, {
    method, body: JSON.stringify(body), staffCapability, demoRunId: context.demoRunId,
    idempotencyKey: crypto.randomUUID(),
  });
  return { message: response.body.message, data: requireData(response.body, "업무 변경 응답을 확인할 수 없습니다.") };
}

function requireData<T>(body: ApiResponse<T>, message: string): T {
  if (body.data === null) throw new ApiClientError("parse", message, body.status, body.code, body.traceId);
  return body.data;
}
