import { issueStaffCapability } from "../../../../../worker/staff-capability";
import { readDemoCapabilityCookie } from "../../../../../worker/demo-capability-cookie";
import { resolveClientRateIdentity } from "../../../../../worker/client-rate-identity";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";
export const maxDuration = 15;

export async function POST(
  request: Request,
  { params }: { params: Promise<{ sessionId: string }> },
): Promise<Response> {
  const { sessionId } = await params;
  const requestUrl = new URL(request.url);
  requestUrl.pathname = `/api/internal/staff-capability/${encodeURIComponent(sessionId)}`;
  const headers = new Headers(request.headers);
  const capability = headers.get("X-Demo-Capability") ?? readDemoCapabilityCookie(request);
  if (capability) headers.set("X-Demo-Capability", capability);
  const clientRateIdentity = await resolveClientRateIdentity(
    request,
    process.env.BACKEND_PROXY_SHARED_SECRET,
    process.env.VERCEL === "1" ? request.headers.get("x-vercel-forwarded-for") ?? "" : undefined,
  );

  const response = await issueStaffCapability(new Request(requestUrl, { method: "POST", headers }), {
    backendOrigin: process.env.BACKEND_API_ORIGIN,
    proxySharedSecret: process.env.BACKEND_PROXY_SHARED_SECRET,
    clientRateIdentity: clientRateIdentity.identity,
    bootstrapToken: process.env.DEMO_STAFF_BOOTSTRAP_TOKEN,
    allowedUserIds: process.env.STAFF_ALLOWED_USER_IDS,
    identityPublicKeyPem: process.env.STAFF_IDENTITY_JWT_PUBLIC_KEY_PEM,
    identityIssuer: process.env.STAFF_IDENTITY_JWT_ISSUER,
    identityAudience: process.env.STAFF_IDENTITY_JWT_AUDIENCE,
    identityRequiredRole: process.env.STAFF_IDENTITY_REQUIRED_ROLE,
    publicDemo: process.env.DEMO_PUBLIC_STAFF_MODE === "true",
  });
  if (!clientRateIdentity.setCookie) return response;
  const responseHeaders = new Headers(response.headers);
  responseHeaders.append("Set-Cookie", clientRateIdentity.setCookie);
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers: responseHeaders,
  });
}
