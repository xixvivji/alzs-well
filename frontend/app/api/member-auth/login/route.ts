import { memberTokenCookies } from "../../../../worker/member-auth-cookie";
import { proxyMemberAuth, tokenlessSuccess, tokenPairFrom } from "../../../../worker/member-auth-bff";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

export async function POST(request: Request): Promise<Response> {
  const response = await proxyMemberAuth(request, "/api/v1/auth/login");
  if (!response.ok) return response;
  try {
    const pair = await tokenPairFrom(response);
    const sanitized = tokenlessSuccess(response, "MEMBER_LOGIN_SUCCEEDED", "합성 금융서비스에 로그인했습니다.");
    const headers = new Headers(sanitized.headers);
    for (const cookie of memberTokenCookies(request.url, pair)) headers.append("Set-Cookie", cookie);
    return new Response(sanitized.body, { status: sanitized.status, headers });
  } catch {
    return Response.json({ success: false, status: 502, code: "MEMBER_AUTH_RESPONSE_INVALID", message: "인증 응답을 안전하게 처리할 수 없습니다.", data: null, errors: [] }, { status: 502 });
  }
}
