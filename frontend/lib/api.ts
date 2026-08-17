export type ApiResponse<T> = { success: boolean; status: number; code: string; message: string; data: T | null; errors: Array<{ field: string; message: string }>; timestamp: string; traceId: string };
const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
export async function apiRequest<T>(path: string, options: RequestInit & { capability?: string; demoRunId?: string; idempotencyKey?: string } = {}) {
  const { capability, demoRunId, idempotencyKey, headers, ...requestOptions } = options;
  const response = await fetch(`${API_BASE_URL}${path}`, { ...requestOptions, headers: { "Content-Type": "application/json", ...(capability ? { "X-Demo-Capability": capability } : {}), ...(demoRunId ? { "X-Demo-Run-Id": demoRunId } : {}), ...(idempotencyKey ? { "Idempotency-Key": idempotencyKey } : {}), ...headers } });
  const body = await response.json() as ApiResponse<T>;
  if (!response.ok) throw body;
  return { body, headers: response.headers };
}
