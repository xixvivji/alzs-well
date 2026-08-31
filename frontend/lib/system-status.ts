import { ApiClientError, apiRequest } from "./api";

export type SystemHealth = { status: string; service: string; syntheticDataOnly: boolean; externalActionsEnabled: boolean };
export type SystemReadiness = { ready: boolean; status: string; checks: Record<string, string> };
export type PublicConfig = {
  apiVersion: string; dataMode: string; syntheticDataOnly: boolean; externalActionsEnabled: boolean;
  networkMode: string; externalEgressEnabled: boolean; remoteModelEnabled: boolean; syntheticProviderOnly: boolean;
  supportedScenarioIds: string[]; defaultLocale: string; demoSessionTtlSeconds: number;
  featureFlags: { optionalLlmEnabled: boolean; templateFallbackEnabled: boolean; trustedContactDeliveryEnabled: boolean };
};
export type SystemVersions = {
  applicationVersion: string; apiVersion: string; schemaVersion: string; fixtureVersion: string;
  algorithmVersion: string; policyVersion: string; sourceCatalogCheckedAt: string;
};
export type SystemStatusSnapshot = { health: SystemHealth; readiness: SystemReadiness; config: PublicConfig; versions: SystemVersions; checkedAt: string };

export async function loadSystemStatus(): Promise<SystemStatusSnapshot> {
  const [health, readiness, config, versions] = await Promise.all([
    apiRequest<SystemHealth>("/api/v1/system/health", { timeoutMs: 5_000 }),
    loadReadiness(),
    apiRequest<PublicConfig>("/api/v1/system/public-config", { timeoutMs: 5_000 }),
    apiRequest<SystemVersions>("/api/v1/system/versions", { timeoutMs: 5_000 }),
  ]);
  return {
    health: required(health.body.data, "상태"), readiness,
    config: required(config.body.data, "공개 설정"), versions: required(versions.body.data, "버전"),
    checkedAt: new Date().toISOString(),
  };
}

async function loadReadiness(): Promise<SystemReadiness> {
  try {
    const response = await apiRequest<SystemReadiness>("/api/v1/system/readiness", { timeoutMs: 8_000 });
    return required(response.body.data, "준비상태");
  } catch (error) {
    // readiness는 장애를 설명하는 data와 함께 503을 반환한다. 화면에서 그 상세를 숨기지 않는다.
    if (error instanceof ApiClientError && error.status === 503 && error.data) {
      return error.data as SystemReadiness;
    }
    throw error;
  }
}

function required<T>(value: T | null, label: string): T {
  if (!value) throw new Error(`시스템 ${label} 응답을 확인해 주세요.`);
  return value;
}
