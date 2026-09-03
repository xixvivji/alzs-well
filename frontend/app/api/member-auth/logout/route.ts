import { clearMemberTokenCookies, readMemberAccessCookie } from "../../../../worker/member-auth-cookie";
import { proxyMemberAuth } from "../../../../worker/member-auth-bff";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

export async function POST(request: Request): Promise<Response> {
  const access = readMemberAccessCookie(request);
  const response = access ? await proxyMemberAuth(request, "/api/v1/auth/logout", undefined, access) : new Response(null, { status: 204 });
  const responseHeaders = new Headers(response.headers);
  for (const cookie of clearMemberTokenCookies(request.url)) responseHeaders.append("Set-Cookie", cookie);
  return new Response(response.body, { status: response.status, statusText: response.statusText, headers: responseHeaders });
}
