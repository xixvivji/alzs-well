import { invokeApiOperation } from "./api-operation-client";
import { withPrivateCustomerSession } from "./private-auth-session";
import type { PrivateCustomerSession } from "./private-financial-products";

export type OperationalBundle = {
  cases: Record<string, unknown>[]; rules: Record<string, unknown>[]; policies: Record<string, unknown>[];
  algorithms: Record<string, unknown>[]; flags: Record<string, unknown>[]; audit: Record<string, unknown>[];
  retention: Record<string, unknown>[]; selected: Record<string, unknown> | null;
  timeline: Record<string, unknown>[]; evidence: Record<string, unknown>[]; notes: Record<string, unknown>[];
  followUps: Record<string, unknown>[]; intentSummary: Record<string, unknown> | null;
  ruleDetail: Record<string, unknown> | null; auditDetail: Record<string, unknown> | null;
  auditAuthorized: boolean;
  aiQuality: AiQualitySummary | null;
  partialFailures: OperationalPartialFailure[];
};

export type OperationalPartialFailure = {
  section: "policies" | "algorithms" | "aiQuality" | "flags" | "audit" | "retention" | "ruleDetail" | "auditDetail";
  message: string;
};

export type AiQualitySummary = {
  windowHours: number; status: "HEALTHY" | "ATTENTION" | "NO_DATA";
  searchRequests: number; groundedSearches: number; fallbackSearches: number; emptySearches: number;
  rejectedCitations: number; searchFallbackRate: number; assistanceRequests: number;
  assistanceGenerated: number; assistanceFallbacks: number; assistanceFallbackRate: number;
  syntheticDataOnly: true; externalActionsExecuted: false;
};

const emptyDetails = { selected: null, timeline: [], evidence: [], notes: [], followUps: [], intentSummary: null, ruleDetail: null, auditDetail: null };

export async function loadStaffOperations(session: PrivateCustomerSession): Promise<OperationalBundle> {
  requireRole(session, "PROTECTION_STAFF");
  return withPrivateCustomerSession(session, async (accessToken) => {
    const response = await invokeApiOperation<Record<string, unknown>>("GET /api/v1/staff/cases", { query: { limit: 50 }, accessToken });
    const cases = items(response.body.data); const first = cases[0];
    const caseId = stringValue(first, "caseId"); const customerId = stringValue(first, "customerId");
    const [selected, timeline, evidence, notes, followUps, intentSummary] = await Promise.all([
      caseId ? optional(() => invokeApiOperation<Record<string, unknown>>("GET /api/v1/staff/cases/{caseId}", { path: { caseId }, accessToken })) : null,
      caseId ? optional(() => invokeApiOperation<Record<string, unknown>>("GET /api/v1/staff/cases/{caseId}/timeline", { path: { caseId }, accessToken })) : null,
      caseId ? optional(() => invokeApiOperation<Record<string, unknown>>("GET /api/v1/staff/cases/{caseId}/evidence", { path: { caseId }, accessToken })) : null,
      caseId ? optional(() => invokeApiOperation<Record<string, unknown>>("GET /api/v1/staff/cases/{caseId}/notes", { path: { caseId }, accessToken })) : null,
      caseId ? optional(() => invokeApiOperation<Record<string, unknown>>("GET /api/v1/staff/cases/{caseId}/follow-ups", { path: { caseId }, accessToken })) : null,
      customerId ? optional(() => invokeApiOperation<Record<string, unknown>>("GET /api/v1/staff/customers/{customerId}/financial-intent-summary", { path: { customerId }, accessToken })) : null,
    ]);
    return {
      cases, rules: [], policies: [], algorithms: [], flags: [], audit: [], retention: [],
      ...emptyDetails, auditAuthorized: false, aiQuality: null, partialFailures: [], selected: selected?.body.data ?? null, timeline: items(timeline?.body.data),
      evidence: items(evidence?.body.data), notes: items(notes?.body.data), followUps: items(followUps?.body.data),
      intentSummary: intentSummary?.body.data ?? null,
    };
  });
}

export async function loadAdminOperations(session: PrivateCustomerSession): Promise<OperationalBundle> {
  requireRole(session, "DETECTION_ADMIN");
  return withPrivateCustomerSession(session, async (accessToken) => {
    const auth = { accessToken };
    const partialFailures: OperationalPartialFailure[] = [];
    // 로그인 직후 인증·상태 조회와 겹쳐 rate-limit burst가 발생하지 않도록 순차 조회한다.
    const rules = await invokeApiOperation<Record<string, unknown>>("GET /api/v1/admin/rules", auth);
    const policies = await trackedOptional("policies", partialFailures, () => invokeApiOperation<Record<string, unknown>>("GET /api/v1/admin/policies/versions", auth));
    const algorithms = await trackedOptional("algorithms", partialFailures, () => invokeApiOperation<Record<string, unknown>>("GET /api/v1/admin/algorithms/versions", auth));
    const aiQuality = await trackedOptional("aiQuality", partialFailures, () => invokeApiOperation<AiQualitySummary>("GET /api/v1/admin/ai-quality/summary", { query: { hours: 24 }, ...auth }));
    const flags = await trackedOptional("flags", partialFailures, () => invokeApiOperation<Record<string, unknown>>("GET /api/v1/admin/feature-flags", auth));
    const auditAuthorized = session.permissions.includes("AUDIT_READ_ALL");
    const audit = auditAuthorized
      ? await trackedOptional("audit", partialFailures, () => invokeApiOperation<Record<string, unknown>>("GET /api/v1/audit/events", { query: { limit: 25 }, ...auth }))
      : null;
    const retention = await trackedOptional("retention", partialFailures, () => invokeApiOperation<Record<string, unknown>>("GET /api/v1/compliance/retention-policies", auth));
    const ruleItems = items(rules.body.data); const auditItems = items(audit?.body.data);
    const ruleId = stringValue(ruleItems[0], "ruleId"); const eventId = stringValue(auditItems[0], "eventId");
    const [ruleDetail, auditDetail] = await Promise.all([
      ruleId ? trackedOptional("ruleDetail", partialFailures, () => invokeApiOperation<Record<string, unknown>>("GET /api/v1/admin/rules/{ruleId}", { path: { ruleId }, ...auth })) : null,
      eventId ? trackedOptional("auditDetail", partialFailures, () => invokeApiOperation<Record<string, unknown>>("GET /api/v1/audit/events/{eventId}", { path: { eventId }, ...auth })) : null,
    ]);
    return { cases: [], rules: ruleItems, policies: items(policies?.body.data), algorithms: items(algorithms?.body.data), flags: items(flags?.body.data), audit: auditItems, retention: items(retention?.body.data), ...emptyDetails, auditAuthorized, aiQuality: aiQuality?.body.data ?? null, ruleDetail: ruleDetail?.body.data ?? null, auditDetail: auditDetail?.body.data ?? null, partialFailures };
  });
}

function requireRole(session: PrivateCustomerSession, role: string) { if (!session.roles.includes(role)) throw new Error("이 운영 화면을 사용할 역할이 없습니다."); }
function items(value: Record<string, unknown> | null | undefined): Record<string, unknown>[] { if (!value) return []; const candidate = value.items ?? value.rules ?? value.versions ?? value.flags ?? value.events ?? value.policies ?? value.phases ?? value.signals ?? value.followUps; return Array.isArray(candidate) ? candidate as Record<string, unknown>[] : []; }
function stringValue(value: Record<string, unknown> | undefined, key: string) { const candidate = value?.[key]; return typeof candidate === "string" && candidate ? candidate : null; }
async function optional<T>(task: () => Promise<T>): Promise<T | null> { try { return await task(); } catch { return null; } }
async function trackedOptional<T>(section: OperationalPartialFailure["section"], failures: OperationalPartialFailure[], task: () => Promise<T>): Promise<T | null> {
  try { return await task(); }
  catch (reason) {
    failures.push({ section, message: reason instanceof Error ? reason.message : "조회 요청이 실패했습니다." });
    return null;
  }
}
