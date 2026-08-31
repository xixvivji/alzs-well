import { proxyApiRequest } from "../../../worker/api-proxy";
import { resolveClientRateIdentity } from "../../../worker/client-rate-identity";
import {
  clearDemoCapabilityCookie,
  DEMO_CAPABILITY_MODE,
  DEMO_CAPABILITY_MODE_HEADER,
  demoCapabilityCookie,
  readDemoCapabilityCookie,
} from "../../../worker/demo-capability-cookie";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";
export const maxDuration = 15;

async function handler(request: Request): Promise<Response> {
  const requestUrl = new URL(request.url);
  const capability = request.headers.get("X-Demo-Capability") ?? readDemoCapabilityCookie(request);
  const forwardedHeaders = new Headers(request.headers);
  if (capability && !forwardedHeaders.has("X-Demo-Capability")) forwardedHeaders.set("X-Demo-Capability", capability);
  const upstreamRequest = new Request(request, { headers: forwardedHeaders });
  const clientRateIdentity = await resolveClientRateIdentity(
    request,
    process.env.BACKEND_PROXY_SHARED_SECRET,
    process.env.VERCEL === "1" ? request.headers.get("x-vercel-forwarded-for") ?? "" : undefined,
  );
  const response = await proxyApiRequest(upstreamRequest, process.env.BACKEND_API_ORIGIN, {
    proxySharedSecret: process.env.BACKEND_PROXY_SHARED_SECRET,
    clientRateIdentity: clientRateIdentity.identity,
  });
  const headers = new Headers(response.headers);
  if (clientRateIdentity.setCookie) headers.append("Set-Cookie", clientRateIdentity.setCookie);
  const creatingSession = request.method === "POST" && requestUrl.pathname === "/api/v1/demo/sessions";
  if (creatingSession && response.ok) {
    const issued = headers.get("X-Demo-Customer-Capability");
    if (issued) {
      headers.delete("X-Demo-Customer-Capability");
      headers.append("Set-Cookie", demoCapabilityCookie(request.url, issued));
      headers.set(DEMO_CAPABILITY_MODE_HEADER, DEMO_CAPABILITY_MODE);
    }
  }
  const discardingSession = request.method === "DELETE" && /^\/api\/v1\/demo\/sessions\/[0-9a-f-]+$/i.test(requestUrl.pathname);
  if (discardingSession) headers.append("Set-Cookie", clearDemoCapabilityCookie(request.url));
  return new Response(response.body, { status: response.status, statusText: response.statusText, headers });
}

export const GET = handler;
export const POST = handler;
export const PUT = handler;
export const PATCH = handler;
export const DELETE = handler;
export const HEAD = handler;
export const OPTIONS = handler;
