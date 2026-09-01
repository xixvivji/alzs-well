import { clearMemberTokenCookies, memberTokenCookies, readMemberRefreshCookie } from "../../../../worker/member-auth-cookie";
import { proxyMemberAuth, tokenlessSuccess, tokenPairFrom } from "../../../../worker/member-auth-bff";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

export async function POST(request: Request): Promise<Response> {
  const refreshToken = readMemberRefreshCookie(request);
  if (!refreshToken) {
    const headers = new Headers({ "Content-Type": "application/json" });
    for (const cookie of clearMemberTokenCookies(request.url)) headers.append("Set-Cookie", cookie);
    return new Response(JSON.stringify({ success: false, status: 401, code: "MEMBER_SESSION_EXPIRED", message: "로그인이 필요합니다.", data: null, errors: [] }), { status: 401, headers });
  }
  const response = await proxyMemberAuth(request, "/api/v1/auth/token/refresh", { refreshToken });
  if (!response.ok) {
    const headers = new Headers(response.headers);
    for (const cookie of clearMemberTokenCookies(request.url)) headers.append("Set-Cookie", cookie);
    return new Response(response.body, { status: response.status, statusText: response.statusText, headers });
  }
  try {
    const pair = await tokenPairFrom(response);
    const sanitized = tokenlessSuccess(response, "MEMBER_TOKEN_REFRESHED", "인증 세션을 갱신했습니다.");
    const headers = new Headers(sanitized.headers);
    for (const cookie of memberTokenCookies(request.url, pair)) headers.append("Set-Cookie", cookie);
    return new Response(sanitized.body, { status: sanitized.status, headers });
  } catch {
    return Response.json({ success: false, status: 502, code: "MEMBER_AUTH_RESPONSE_INVALID", message: "인증 응답을 안전하게 처리할 수 없습니다.", data: null, errors: [] }, { status: 502 });
  }
}
