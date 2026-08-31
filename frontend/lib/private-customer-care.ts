import { invokeApiOperation } from "./api-operation-client";
import { grantConsent, type Consent } from "./private-customer-assets";
import { withPrivateCustomerSession } from "./private-auth-session";
import type { PrivateCustomerSession } from "./private-financial-products";

export type CustomerSummary = {
  customerId: string; displayName: string; organization: string; region: string; status: string;
  version: number; createdAt: string; updatedAt: string;
};
export type CustomerPreferences = {
  customerId: string; smsNotificationEnabled: boolean; pushNotificationEnabled: boolean;
  inAppNotificationEnabled: boolean; version: number; updatedAt: string;
};
export type AccessibilitySettings = {
  customerId: string; largeFont: boolean; highContrast: boolean; speechGuidance: boolean;
  oneHandMode: boolean; version: number; updatedAt: string;
};
export type CustomerDataSummary = {
  customerId: string; institutions: number; accounts: number; transactionsSynced: number;
  lastSyncAt: string | null; dataFreshness: { accounts: string; transactions: string; baseline: string };
  updatedAt: string;
};
export type TrustedContact = {
  contactId: string; customerId: string; consentId: string; displayName: string;
  relationshipCode: string; maskedContact: string; recipientAccepted: boolean;
  acceptanceStatus: string; status: string; scopes: string[]; validFrom: string;
  expiresAt: string; version: number; authorizedToAct: false; externalContactEnabled: false;
};
export type CustomerAlert = {
  alertId: string; signalId: string; customerId: string; state: string; severity: string;
  reasonCode: string; version: number; deferredUntil: string | null; createdAt: string; updatedAt: string;
};
export type AlertAppeal = {
  appealId: string; alertId: string; caseId: string; reasonCode: string; status: string;
  previousState: string; currentState: string; alertVersion: number; submittedAt: string;
  idempotencyReplayed: boolean; financialActionExecuted: false; externalNotificationSent: false;
};
export type CustomerCareBundle = {
  summary: CustomerSummary;
  preferences: CustomerPreferences;
  accessibility: AccessibilitySettings;
  dataSummary: CustomerDataSummary;
  contacts: TrustedContact[];
  consents: Consent[];
  alerts: CustomerAlert[];
};

export async function loadPrivateCustomerCare(session: PrivateCustomerSession): Promise<CustomerCareBundle> {
  return withPrivateCustomerSession(session, async (accessToken) => {
    const path = { customerId: session.customerId };
    const [summary, preferences, accessibility, dataSummary, contacts, consents, alerts] = await Promise.all([
    invokeApiOperation<CustomerSummary>("GET /api/v1/customers/{customerId}", { path, accessToken }),
    invokeApiOperation<CustomerPreferences>("GET /api/v1/customers/{customerId}/preferences", { path, accessToken }),
    invokeApiOperation<AccessibilitySettings>("GET /api/v1/customers/{customerId}/accessibility-settings", { path, accessToken }),
    invokeApiOperation<CustomerDataSummary>("GET /api/v1/customers/{customerId}/data-summary", { path, accessToken }),
    invokeApiOperation<{ items: TrustedContact[] }>("GET /api/v1/customers/{customerId}/trusted-contacts", { path, accessToken }),
    invokeApiOperation<{ items: Consent[] }>("GET /api/v1/customers/{customerId}/consents", { path, accessToken }),
    invokeApiOperation<{ items: CustomerAlert[] }>("GET /api/v1/customers/{customerId}/alerts", { path, accessToken }),
    ]);
    return {
      summary: required(summary.body.data, "고객 프로필"),
      preferences: required(preferences.body.data, "알림 설정"),
      accessibility: required(accessibility.body.data, "접근성 설정"),
      dataSummary: required(dataSummary.body.data, "데이터 범위"),
      contacts: contacts.body.data?.items ?? [],
      consents: consents.body.data?.items ?? [],
      alerts: alerts.body.data?.items ?? [],
    };
  });
}

export async function updateCustomerDisplayName(
  session: PrivateCustomerSession, expectedVersion: number, displayName: string,
): Promise<void> {
  const idempotencyKey = crypto.randomUUID();
  await withPrivateCustomerSession(session, (accessToken) => invokeApiOperation("PATCH /api/v1/customers/{customerId}/display-profile", {
    path: { customerId: session.customerId }, accessToken,
    idempotencyKey, body: { expectedVersion, displayName },
  }));
}

export async function updateCustomerPreferences(
  session: PrivateCustomerSession, preferences: CustomerPreferences,
): Promise<void> {
  const idempotencyKey = crypto.randomUUID();
  await withPrivateCustomerSession(session, (accessToken) => invokeApiOperation("PATCH /api/v1/customers/{customerId}/preferences", {
    path: { customerId: session.customerId }, accessToken,
    idempotencyKey,
    body: {
      expectedVersion: preferences.version,
      smsNotificationEnabled: preferences.smsNotificationEnabled,
      pushNotificationEnabled: preferences.pushNotificationEnabled,
      inAppNotificationEnabled: preferences.inAppNotificationEnabled,
    },
  }));
}

export async function updateAccessibilitySettings(
  session: PrivateCustomerSession, settings: AccessibilitySettings,
): Promise<void> {
  const idempotencyKey = crypto.randomUUID();
  await withPrivateCustomerSession(session, (accessToken) => invokeApiOperation("PUT /api/v1/customers/{customerId}/accessibility-settings", {
    path: { customerId: session.customerId }, accessToken,
    idempotencyKey,
    body: {
      expectedVersion: settings.version,
      largeFont: settings.largeFont,
      highContrast: settings.highContrast,
      speechGuidance: settings.speechGuidance,
      oneHandMode: settings.oneHandMode,
    },
  }));
}

export async function ensureTrustedContactConsent(session: PrivateCustomerSession, consents: Consent[]): Promise<Consent> {
  const current = consents.find((consent) => consent.purposeCode === "TRUSTED_CONTACT_DISCLOSURE"
    && consent.status === "GRANTED" && new Date(consent.expiresAt).getTime() > Date.now()
    && consent.scopes.includes("CONTACT_MINIMUM"));
  if (current) return current;
  return grantConsent(session, "TRUSTED_CONTACT_DISCLOSURE", ["CONTACT_MINIMUM"], futureIso(365));
}

export async function createTrustedContact(
  session: PrivateCustomerSession, consentId: string,
  input: { displayName: string; relationshipCode: string; maskedContact: string; scopes: string[] },
): Promise<TrustedContact> {
  const idempotencyKey = crypto.randomUUID();
  return withPrivateCustomerSession(session, async (accessToken) => {
    const response = await invokeApiOperation<TrustedContact>("POST /api/v1/customers/{customerId}/trusted-contacts", {
      path: { customerId: session.customerId }, accessToken,
      idempotencyKey, body: { consentId, ...input, expiresAt: futureIso(180) },
    });
    return required(response.body.data, "신뢰 연락처 등록");
  });
}

export async function revokeTrustedContact(
  session: PrivateCustomerSession, contact: TrustedContact, reason: string,
): Promise<TrustedContact> {
  const idempotencyKey = crypto.randomUUID();
  return withPrivateCustomerSession(session, async (accessToken) => {
    const response = await invokeApiOperation<TrustedContact>("POST /api/v1/customers/{customerId}/trusted-contacts/{contactId}/revoke", {
      path: { customerId: session.customerId, contactId: contact.contactId }, accessToken,
    idempotencyKey, body: { expectedVersion: contact.version, reason },
    });
    return required(response.body.data, "신뢰 연락처 철회");
  });
}

export async function submitAlertAppeal(
  session: PrivateCustomerSession, alert: CustomerAlert, reasonCode: string, statement: string,
): Promise<AlertAppeal> {
  const idempotencyKey = crypto.randomUUID();
  return withPrivateCustomerSession(session, async (accessToken) => {
    const response = await invokeApiOperation<AlertAppeal>("POST /api/v1/alerts/{alertId}/appeals", {
      path: { alertId: alert.alertId }, accessToken,
    idempotencyKey, body: { reasonCode, statement, expectedVersion: alert.version },
    });
    return required(response.body.data, "사람 재검토 요청");
  });
}

function futureIso(days: number): string { return new Date(Date.now() + days * 86_400_000).toISOString(); }
function required<T>(value: T | null, label: string): T {
  if (!value) throw new Error(`${label} 응답을 확인해 주세요.`);
  return value;
}
