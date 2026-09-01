import { proxyApiRequest } from "./api-proxy";
import { resolveClientRateIdentity } from "./client-rate-identity";

export async function proxyMemberAuth(request: Request, backendPath: string, body?: unknown, bearerToken?: string): Promise<Response> {
  const incoming = new URL(request.url);
  incoming.pathname = backendPath;
  incoming.search = "";
  const headers = new Headers(request.headers);
  headers.delete("authorization");
  headers.delete("cookie");
  if (bearerToken) headers.set("Authorization", `Bearer ${bearerToken}`);
  const upstreamRequest = new Request(incoming, {
    method: request.method,
    headers,
    body: body === undefined ? request.body : JSON.stringify(body),
    ...(body === undefined && request.body ? { duplex: "half" as const } : {}),
  });
  const identity = await resolveClientRateIdentity(
    request,
    process.env.BACKEND_PROXY_SHARED_SECRET,
    process.env.VERCEL === "1" ? request.headers.get("x-vercel-forwarded-for") ?? "" : undefined,
  );
  const response = await proxyApiRequest(upstreamRequest, process.env.BACKEND_API_ORIGIN, {
    proxySharedSecret: process.env.BACKEND_PROXY_SHARED_SECRET,
    clientRateIdentity: identity.identity,
  });
  if (!identity.setCookie) return response;
  const responseHeaders = new Headers(response.headers);
  responseHeaders.append("Set-Cookie", identity.setCookie);
  return new Response(response.body, { status: response.status, statusText: response.statusText, headers: responseHeaders });
}

export async function tokenPairFrom(response: Response) {
  const body = await response.clone().json() as { data?: {
    accessToken?: string; accessExpiresAt?: string; refreshToken?: string; refreshExpiresAt?: string;
  } };
  const pair = body.data;
  if (!pair?.accessToken || !pair.accessExpiresAt || !pair.refreshToken || !pair.refreshExpiresAt) {
    throw new Error("invalid token pair");
  }
  const accessExpiry = Date.parse(pair.accessExpiresAt);
  const refreshExpiry = Date.parse(pair.refreshExpiresAt);
  if (Number.isNaN(accessExpiry) || Number.isNaN(refreshExpiry)
      || accessExpiry <= Date.now() || refreshExpiry <= accessExpiry) {
    throw new Error("invalid token expiry");
  }
  return pair as { accessToken: string; accessExpiresAt: string; refreshToken: string; refreshExpiresAt: string };
}

export function tokenlessSuccess(response: Response, code: string, message: string): Response {
  const headers = new Headers(response.headers);
  headers.set("Cache-Control", "no-store");
  headers.delete("content-length");
  headers.delete("content-encoding");
  return Response.json({
    success: true, status: 200, code, message,
    data: null, errors: [], timestamp: new Date().toISOString(),
    traceId: headers.get("X-Trace-Id") ?? crypto.randomUUID(),
  }, { status: 200, headers });
}
