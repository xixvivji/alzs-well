import { issueStaffCapability } from "../../../../../worker/staff-capability";
import { readDemoCapabilityCookie } from "../../../../../worker/demo-capability-cookie";

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

  return issueStaffCapability(new Request(requestUrl, { method: "POST", headers }), {
    backendOrigin: process.env.BACKEND_API_ORIGIN,
    proxySharedSecret: process.env.BACKEND_PROXY_SHARED_SECRET,
    bootstrapToken: process.env.DEMO_STAFF_BOOTSTRAP_TOKEN,
    allowedUserIds: process.env.STAFF_ALLOWED_USER_IDS,
    publicDemo: process.env.DEMO_PUBLIC_STAFF_MODE === "true",
  });
}
