import { ApiClientError } from "./api";
import { invokeApiOperation } from "./api-operation-client";

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
export type SystemStatusSnapshot = { health: SystemHealth; readiness: SystemReadiness; coreReadiness: SystemReadiness; aiReadiness: SystemReadiness; config: PublicConfig; versions: SystemVersions; checkedAt: string };

export async function loadSystemStatus(): Promise<SystemStatusSnapshot> {
  // 공개 배포의 IP 기반 burst 제한을 넘지 않도록 상태 API를 짧은 순차 요청으로 확인한다.
  const health = await invokeApiOperation<SystemHealth>("GET /api/v1/system/health", { timeoutMs: 5_000 });
  const readiness = await loadReadiness("GET /api/v1/system/readiness");
  const coreReadiness = await loadReadiness("GET /api/v1/system/core-readiness");
  const aiReadiness = await loadReadiness("GET /api/v1/system/ai-readiness");
  const config = await invokeApiOperation<PublicConfig>("GET /api/v1/system/public-config", { timeoutMs: 5_000 });
  const versions = await invokeApiOperation<SystemVersions>("GET /api/v1/system/versions", { timeoutMs: 5_000 });
  return {
    health: required(health.body.data, "상태"), readiness, coreReadiness, aiReadiness,
    config: required(config.body.data, "공개 설정"), versions: required(versions.body.data, "버전"),
    checkedAt: new Date().toISOString(),
  };
}

async function loadReadiness(operation: "GET /api/v1/system/readiness" | "GET /api/v1/system/core-readiness" | "GET /api/v1/system/ai-readiness"): Promise<SystemReadiness> {
  try {
    const response = await invokeApiOperation<SystemReadiness>(operation, { timeoutMs: 8_000 });
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
