import { proxyApiRequest } from "./api-proxy";

const PATH = /^\/api\/internal\/staff-capability\/([0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})$/i;

type Options = { backendOrigin?: string; proxySharedSecret?: string; bootstrapToken?: string;
  allowedUserIds?: string; trustedClientAddress?: string | null;
  fetchImpl?: (request: Request) => Promise<Response> };

export function isStaffCapabilityPath(pathname: string): boolean { return PATH.test(pathname); }

export async function issueStaffCapability(request: Request, options: Options): Promise<Response> {
  const traceId = crypto.randomUUID();
  const match = new URL(request.url).pathname.match(PATH);
  if (request.method !== "POST" || !match) return error(404, "NOT_FOUND", "요청 경로를 찾을 수 없습니다.", traceId);
  const userId = request.headers.get("oai-authenticated-user-id");
  const allowed = new Set((options.allowedUserIds ?? "").split(",").map((value) => value.trim()).filter(Boolean));
  if (!options.bootstrapToken || options.bootstrapToken.length < 64 || allowed.size === 0) {
    return error(503, "STAFF_ACCESS_CONFIGURATION_INVALID", "직원 접근 설정을 확인할 수 없습니다.", traceId);
  }
  if (!userId || !allowed.has(userId)) return error(403, "STAFF_ACCESS_DENIED", "직원 화면 접근 권한이 없습니다.", traceId);
  const upstreamRequest = new Request(new URL(`/api/v1/demo/staff/sessions/${match[1]}/capability`, request.url), {
    method: "POST", headers: { Authorization: `Bearer ${options.bootstrapToken}` },
  });
  return proxyApiRequest(upstreamRequest, options.backendOrigin, {
    proxySharedSecret: options.proxySharedSecret, trustedClientAddress: options.trustedClientAddress,
    fetchImpl: options.fetchImpl,
  });
}

function error(status: number, code: string, message: string, traceId: string): Response {
  return Response.json({ success: false, status, code, message, data: null, errors: [], timestamp: new Date().toISOString(), traceId },
    { status, headers: { "Cache-Control": "no-store", "X-Trace-Id": traceId } });
}
