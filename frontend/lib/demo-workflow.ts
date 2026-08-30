import { apiRequest } from "./api";
import type { DemoContext } from "./demo-session";

type Created = { sessionId: string };
type Ingested = { demoRunId: string; customerId: string; alertId: string };

export async function discardDemoSession(sessionId: string, capability: string): Promise<void> {
  try {
    await apiRequest(`/api/v1/demo/sessions/${sessionId}`, {
      method: "DELETE",
      capability,
      timeoutMs: 5_000,
    });
  } catch {
    // best-effort: 이미 만료됐거나 네트워크가 끊긴 세션은 서버 정리 작업이 회수한다.
  }
}

export async function createDemoContext(): Promise<DemoContext> {
  const created = await apiRequest<Created>("/api/v1/demo/sessions", { method: "POST" });
  const sessionId = created.body.data?.sessionId;
  const capability = created.headers.get("X-Demo-Customer-Capability");
  if (!sessionId || !capability) throw new Error("세션 발급 응답을 확인해 주세요.");

  try {
    const ingested = await apiRequest<Ingested>(
      `/api/v1/demo/sessions/${sessionId}/scenarios/FIN_MGMT_AB_001/ingest`,
      { method: "POST", capability, idempotencyKey: crypto.randomUUID() },
    );
    if (!ingested.body.data) throw new Error("시나리오 적재 응답을 확인해 주세요.");
    return { sessionId, capability, ...ingested.body.data };
  } catch (error) {
    await discardDemoSession(sessionId, capability);
    throw error;
  }
}
