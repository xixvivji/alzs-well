import { invokeApiOperation } from "./api-operation-client";
import { withPrivateCustomerSession } from "./private-auth-session";
import type { PrivateCustomerSession } from "./private-financial-products";

export type OperationalBundle = { cases: Record<string, unknown>[]; rules: Record<string, unknown>[]; policies: Record<string, unknown>[]; algorithms: Record<string, unknown>[]; flags: Record<string, unknown>[]; audit: Record<string, unknown>[]; retention: Record<string, unknown>[] };

export async function loadStaffOperations(session: PrivateCustomerSession): Promise<OperationalBundle> {
  requireRole(session, "PROTECTION_STAFF");
  return withPrivateCustomerSession(session, async (accessToken) => {
    const response = await invokeApiOperation<Record<string, unknown>>("GET /api/v1/staff/cases", { query: { limit: 50 }, accessToken });
    return { cases: items(response.body.data), rules: [], policies: [], algorithms: [], flags: [], audit: [], retention: [] };
  });
}

export async function loadAdminOperations(session: PrivateCustomerSession): Promise<OperationalBundle> {
  requireRole(session, "DETECTION_ADMIN");
  return withPrivateCustomerSession(session, async (accessToken) => {
    const auth = { accessToken };
    const [rules, policies, algorithms, flags, audit, retention] = await Promise.all([
      invokeApiOperation<Record<string, unknown>>("GET /api/v1/admin/rules", auth),
      invokeApiOperation<Record<string, unknown>>("GET /api/v1/admin/policies/versions", auth),
      invokeApiOperation<Record<string, unknown>>("GET /api/v1/admin/algorithms/versions", auth),
      invokeApiOperation<Record<string, unknown>>("GET /api/v1/admin/feature-flags", auth),
      invokeApiOperation<Record<string, unknown>>("GET /api/v1/audit/events", { query: { limit: 25 }, ...auth }),
      invokeApiOperation<Record<string, unknown>>("GET /api/v1/compliance/retention-policies", auth),
    ]);
    return { cases: [], rules: items(rules.body.data), policies: items(policies.body.data), algorithms: items(algorithms.body.data), flags: items(flags.body.data), audit: items(audit.body.data), retention: items(retention.body.data) };
  });
}

function requireRole(session: PrivateCustomerSession, role: string) { if (!session.roles.includes(role)) throw new Error("이 운영 화면을 사용할 역할이 없습니다."); }
function items(value: Record<string, unknown> | null): Record<string, unknown>[] { if (!value) return []; const candidate = value.items ?? value.rules ?? value.versions ?? value.flags ?? value.events ?? value.policies; return Array.isArray(candidate) ? candidate as Record<string, unknown>[] : []; }
