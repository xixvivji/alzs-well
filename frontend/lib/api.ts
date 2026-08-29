export type ApiResponse<T> = {
  success: boolean; status: number; code: string; message: string; data: T | null;
  errors: Array<{ field: string; message: string }>; timestamp: string; traceId: string;
};
export type ApiErrorKind = "http" | "network" | "timeout" | "parse";
export class ApiClientError extends Error {
  constructor(public readonly kind: ApiErrorKind, message: string, public readonly status?: number,
    public readonly code?: string, public readonly traceId?: string, public readonly retryable = false) {
    super(message); this.name = "ApiClientError";
  }
}
const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, "") ?? "";
export function resolveApiUrl(path: string, baseUrl = API_BASE_URL, browserUrl?: string): string {
  if (path !== "/api" && !path.startsWith("/api/")) {
    throw new ApiClientError("parse", "API 경로가 올바르지 않습니다.");
  }
  if (!baseUrl) return path;

  const currentUrl = browserUrl ?? (typeof window === "undefined" ? undefined : window.location.href);
  if (!currentUrl) {
    throw new ApiClientError("network", "서버 렌더링에서는 외부 API origin을 사용할 수 없습니다.");
  }

  let current: URL;
  let backend: URL;
  try {
    current = new URL(currentUrl);
    backend = new URL(baseUrl);
  } catch {
    throw new ApiClientError("network", "API 연결 설정이 올바르지 않습니다.");
  }
  if (
    current.protocol !== "http:" ||
    !isLoopback(current.hostname) ||
    backend.protocol !== "http:" ||
    !isLoopback(backend.hostname) ||
    backend.username ||
    backend.password ||
    backend.pathname !== "/" ||
    backend.search ||
    backend.hash
  ) {
    throw new ApiClientError("network", "배포 환경에서는 같은 origin의 API 프록시만 사용할 수 있습니다.");
  }
  return `${backend.origin}${path}`;
}
function requestSignal(signal: AbortSignal | null | undefined, timeoutMs: number) {
  const timeout = AbortSignal.timeout(timeoutMs);
  return signal ? AbortSignal.any([signal, timeout]) : timeout;
}
export async function apiRequest<T>(path: string, options: RequestInit & { capability?: string; staffCapability?: string; demoRunId?: string; idempotencyKey?: string; timeoutMs?: number } = {}) {
  const { capability, staffCapability, demoRunId, idempotencyKey, timeoutMs = 10_000, headers, signal, ...requestOptions } = options;
  let response: Response;
  try {
    response = await fetch(resolveApiUrl(path), { ...requestOptions, signal: requestSignal(signal, timeoutMs), headers: {
      "Content-Type": "application/json", ...(capability ? { "X-Demo-Capability": capability } : {}),
      ...(staffCapability ? { "X-Demo-Staff-Capability": staffCapability } : {}),
      ...(demoRunId ? { "X-Demo-Run-Id": demoRunId } : {}), ...(idempotencyKey ? { "Idempotency-Key": idempotencyKey } : {}), ...headers,
    } });
  } catch (error) {
    if (error instanceof ApiClientError) throw error;
    if (error instanceof DOMException && error.name === "TimeoutError") {
      throw new ApiClientError("timeout", "요청 시간이 초과되었습니다.", undefined, undefined, undefined, true);
    }
    throw new ApiClientError("network", "서버에 연결할 수 없습니다.", undefined, undefined, undefined, true);
  }
  const traceHeader = response.headers.get("X-Trace-Id") ?? undefined;
  const contentType = response.headers.get("content-type") ?? "";
  const raw = await response.text();
  let body: ApiResponse<T> | null = null;
  if (raw && contentType.toLowerCase().includes("application/json")) {
    try { body = JSON.parse(raw) as ApiResponse<T>; }
    catch { throw new ApiClientError("parse", "서버 응답을 해석할 수 없습니다.", response.status, undefined, traceHeader, response.status >= 500); }
  }
  if (!response.ok) {
    throw new ApiClientError("http", body?.message ?? `요청이 실패했습니다. (${response.status})`, response.status,
      body?.code, body?.traceId ?? traceHeader, response.status >= 500 || response.status === 429);
  }
  if (!body) throw new ApiClientError("parse", "서버가 JSON 응답을 반환하지 않았습니다.", response.status, undefined, traceHeader);
  return { body, headers: response.headers };
}

function isLoopback(hostname: string): boolean {
  const normalized = hostname.toLowerCase();
  return normalized === "localhost" || normalized === "127.0.0.1" || normalized === "[::1]";
}
