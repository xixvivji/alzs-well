import { invokeApiOperation } from "./api-operation-client";
import { withPrivateCustomerSession } from "./private-auth-session";
import type { PrivateCustomerSession } from "./private-financial-products";

export type FinancialIntent = { intentId: string; status: string; version: number; paymentContinuity: string; explanationMode: string; helpCondition: string; shareScopes: string[]; disclaimerAccepted: boolean; updatedAt: string; legallyBinding: false };
export type InboxMessage = { messageId: string; messageType: string; title: string; body: string; relatedResourceType: string; read: boolean; version: number; createdAt: string; externalDeliveryExecuted: false };
export type NotificationPreference = { changeAlertEnabled: boolean; followUpEnabled: boolean; serviceNoticeEnabled: boolean; version: number; externalDeliveryEnabled: false };
export type Institution = { institutionId: string; displayName: string; institutionType: string; providerMode: string; connectionAvailable: boolean; dataAsOf: string };
export type Connection = { connectionId: string; institution: Institution; connectionStatus: string; consentedAt: string; consentExpiresAt: string; lastSyncedAt: string; providerMode: string; version: number };
export type ProtectionAction = { actionCode: string; title: string; actionStatus: string; executionType: string; eligibilitySummary: string; issuer: string; effectiveFrom: string; checkedAt: string; externalExecutionAvailable: false };
export type Enrollment = { enrollmentId: string; actionCode: string; actionTitle: string; institutionName: string; enrollmentStatus: string; observedAsOf: string; readOnly: true };
export type Faq = { faqId: string; categoryCode: string; question: string; answer: string };
export type Notice = { noticeId: string; institutionName: string; categoryCode: string; title: string; body: string; important: boolean; publishedAt: string };
export type Document = { documentId: string; title: string; sourceType: string; issuer: string; audience: string; status: string; effectiveFrom: string; checkedAt: string; currentVersion: string };
export type AuthSession = { sessionId: string; status: string; currentSession: boolean; createdAt: string; lastRotatedAt: string; accessExpiresAt: string; absoluteExpiresAt: string };
export type KnowledgeHit = { passage: { passageId: string; documentId: string; heading: string; content: string; citationLabel: string; sourceUrl: string }; matchedKeywordCount: number; retrievalMode: string };

export type LifeServiceBundle = {
  preparation: { readiness: string; latestApproved: FinancialIntent | null; legalDisclaimerRequired: boolean };
  intents: FinancialIntent[]; inbox: InboxMessage[]; preference: NotificationPreference;
  institutions: Institution[]; connections: Connection[]; actions: ProtectionAction[];
  enrollments: Enrollment[]; faqs: Faq[]; notices: Notice[]; documents: Document[];
  sessions: AuthSession[];
};

const required = <T>(response: { body: { data: T | null } }, label: string): T => {
  if (response.body.data === null) throw new Error(`${label} 응답을 확인해 주세요.`); return response.body.data;
};

export async function loadLifeServices(session: PrivateCustomerSession): Promise<LifeServiceBundle> {
  return withPrivateCustomerSession(session, async (accessToken) => {
    const customer = { customerId: session.customerId }; const auth = { accessToken };
    const [preparation, intents, inbox, preference, institutions, connections, actions, enrollments, faqs, notices, documents, sessions] = await Promise.all([
      invokeApiOperation<LifeServiceBundle["preparation"]>("GET /api/v1/customers/{customerId}/continuity-preparation", { path: customer, ...auth }),
      invokeApiOperation<{ items: FinancialIntent[] }>("GET /api/v1/customers/{customerId}/financial-intents/versions", { path: customer, ...auth }),
      invokeApiOperation<{ items: InboxMessage[] }>("GET /api/v1/customers/{customerId}/inbox", { path: customer, ...auth }),
      invokeApiOperation<NotificationPreference>("GET /api/v1/customers/{customerId}/notification-preferences", { path: customer, ...auth }),
      invokeApiOperation<{ items: Institution[] }>("GET /api/v1/financial-institutions", auth),
      invokeApiOperation<{ items: Connection[] }>("GET /api/v1/customers/{customerId}/connections", { path: customer, ...auth }),
      invokeApiOperation<{ items: ProtectionAction[] }>("GET /api/v1/protection-actions", auth),
      invokeApiOperation<{ items: Enrollment[] }>("GET /api/v1/customers/{customerId}/protection-enrollments", { path: customer, ...auth }),
      invokeApiOperation<{ items: Faq[] }>("GET /api/v1/support/faqs", auth),
      invokeApiOperation<{ items: Notice[] }>("GET /api/v1/support/notices", auth),
      invokeApiOperation<{ items: Document[] }>("GET /api/v1/knowledge/documents", { query: { audience: "CUSTOMER" }, ...auth }),
      invokeApiOperation<{ items: AuthSession[] }>("GET /api/v1/auth/sessions", auth),
    ]);
    return {
      preparation: required(preparation, "금융생활 준비"), intents: required(intents, "의향 이력").items,
      inbox: required(inbox, "알림함").items, preference: required(preference, "알림 설정"),
      institutions: required(institutions, "금융기관").items, connections: required(connections, "기관 연결").items,
      actions: required(actions, "보호수단").items, enrollments: required(enrollments, "보호 가입").items,
      faqs: required(faqs, "FAQ").items, notices: required(notices, "공지").items,
      documents: required(documents, "공식 근거").items, sessions: required(sessions, "로그인 세션").items,
    };
  });
}

export async function createIntent(session: PrivateCustomerSession, values: Omit<FinancialIntent, "intentId" | "status" | "version" | "disclaimerAccepted" | "updatedAt" | "legallyBinding">): Promise<FinancialIntent> {
  return withPrivateCustomerSession(session, async (accessToken) => required(await invokeApiOperation<FinancialIntent>("POST /api/v1/customers/{customerId}/financial-intents/drafts", {
    path: { customerId: session.customerId }, accessToken, idempotencyKey: crypto.randomUUID(), body: values,
  }), "의향 초안"));
}
export async function approveIntent(session: PrivateCustomerSession, intent: FinancialIntent): Promise<FinancialIntent> {
  return withPrivateCustomerSession(session, async (accessToken) => required(await invokeApiOperation<FinancialIntent>("POST /api/v1/customers/{customerId}/financial-intents/{intentId}/approve", {
    path: { customerId: session.customerId, intentId: intent.intentId }, accessToken, idempotencyKey: crypto.randomUUID(), body: { expectedVersion: intent.version, disclaimerAccepted: true },
  }), "의향 승인"));
}
export async function revokeIntent(session: PrivateCustomerSession, intent: FinancialIntent): Promise<void> {
  await withPrivateCustomerSession(session, (accessToken) => invokeApiOperation("POST /api/v1/customers/{customerId}/financial-intents/{intentId}/revoke", {
    path: { customerId: session.customerId, intentId: intent.intentId }, accessToken, idempotencyKey: crypto.randomUUID(), body: { expectedVersion: intent.version, reason: "고객이 금융생활 의향을 다시 검토하기 위해 철회합니다." },
  }));
}
export async function markInboxRead(session: PrivateCustomerSession, item: InboxMessage): Promise<void> { await withPrivateCustomerSession(session, (accessToken) => invokeApiOperation("POST /api/v1/customers/{customerId}/inbox/{messageId}/read", { path: { customerId: session.customerId, messageId: item.messageId }, accessToken, body: { expectedVersion: item.version } })); }
export async function updateNotificationPreference(session: PrivateCustomerSession, preference: NotificationPreference): Promise<void> { await withPrivateCustomerSession(session, (accessToken) => invokeApiOperation("PUT /api/v1/customers/{customerId}/notification-preferences", { path: { customerId: session.customerId }, accessToken, body: { expectedVersion: preference.version, changeAlertEnabled: preference.changeAlertEnabled, followUpEnabled: preference.followUpEnabled, serviceNoticeEnabled: preference.serviceNoticeEnabled } })); }
export async function evaluateProtection(session: PrivateCustomerSession, actionCode: string, reasonCode: string): Promise<Record<string, unknown>> { return withPrivateCustomerSession(session, async (accessToken) => required(await invokeApiOperation<Record<string, unknown>>("POST /api/v1/protection-actions/{actionCode}/eligibility-evaluations", { path: { actionCode }, accessToken, body: { customerId: session.customerId, reasonCode } }), "보호수단 평가")); }
export async function searchKnowledge(session: PrivateCustomerSession, query: string): Promise<KnowledgeHit[]> { return withPrivateCustomerSession(session, async (accessToken) => required(await invokeApiOperation<{ items: KnowledgeHit[] }>("POST /api/v1/knowledge/search", { accessToken, body: { query, audience: "CUSTOMER", limit: 5 } }), "공식 근거 검색").items); }
export async function requestPrivacyCorrection(session: PrivateCustomerSession, correctedValue: string): Promise<void> { await withPrivateCustomerSession(session, (accessToken) => invokeApiOperation("POST /api/v1/customers/{customerId}/privacy/correction-requests", { path: { customerId: session.customerId }, accessToken, idempotencyKey: crypto.randomUUID(), body: { targetType: "CUSTOMER_PROFILE", targetReference: session.customerId, reasonCode: "CUSTOMER_REQUEST", correctedValue } })); }
export async function revokeAuthSession(session: PrivateCustomerSession, authSessionId: string): Promise<void> { await withPrivateCustomerSession(session, (accessToken) => invokeApiOperation("DELETE /api/v1/auth/sessions/{authSessionId}", { path: { authSessionId }, accessToken })); }
