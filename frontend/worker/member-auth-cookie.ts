const ACCESS_PRODUCTION = "__Host-alzs-member-access";
const REFRESH_PRODUCTION = "__Host-alzs-member-refresh";
const ACCESS_DEVELOPMENT = "alzs-member-access";
const REFRESH_DEVELOPMENT = "alzs-member-refresh";
const TOKEN_PATTERN = /^[A-Za-z0-9_-]{32,256}$/;

export type MemberTokens = {
  accessToken: string;
  accessExpiresAt: string;
  refreshToken: string;
  refreshExpiresAt: string;
};

export function readMemberAccessCookie(request: Request): string | null {
  return readToken(request, cookieNames(new URL(request.url)).access);
}

export function readMemberRefreshCookie(request: Request): string | null {
  return readToken(request, cookieNames(new URL(request.url)).refresh);
}

export function memberTokenCookies(requestUrl: string, pair: MemberTokens): string[] {
  if (!TOKEN_PATTERN.test(pair.accessToken) || !TOKEN_PATTERN.test(pair.refreshToken)) {
    throw new Error("invalid member token response");
  }
  return [
    serialize(requestUrl, "access", pair.accessToken, maxAge(pair.accessExpiresAt)),
    serialize(requestUrl, "refresh", pair.refreshToken, maxAge(pair.refreshExpiresAt)),
  ];
}

export function clearMemberTokenCookies(requestUrl: string): string[] {
  return [serialize(requestUrl, "access", "", 0), serialize(requestUrl, "refresh", "", 0)];
}

function readToken(request: Request, name: string): string | null {
  for (const part of (request.headers.get("cookie") ?? "").split(";")) {
    const [rawName, ...rawValue] = part.trim().split("=");
    if (rawName !== name) continue;
    try {
      const value = decodeURIComponent(rawValue.join("="));
      return TOKEN_PATTERN.test(value) ? value : null;
    } catch { return null; }
  }
  return null;
}

function maxAge(expiresAt: string): number {
  const expiry = Date.parse(expiresAt);
  if (Number.isNaN(expiry) || expiry <= Date.now()) throw new Error("invalid member token expiry");
  return Math.max(1, Math.floor((expiry - Date.now()) / 1_000));
}

function serialize(requestUrl: string, kind: "access" | "refresh", value: string, maxAgeSeconds: number): string {
  const url = new URL(requestUrl);
  const names = cookieNames(url);
  return [
    `${names[kind]}=${encodeURIComponent(value)}`,
    "Path=/",
    "HttpOnly",
    "SameSite=Strict",
    `Max-Age=${maxAgeSeconds}`,
    ...(url.protocol === "https:" ? ["Secure"] : []),
  ].join("; ");
}

function cookieNames(url: URL) {
  return url.protocol === "https:"
    ? { access: ACCESS_PRODUCTION, refresh: REFRESH_PRODUCTION }
    : { access: ACCESS_DEVELOPMENT, refresh: REFRESH_DEVELOPMENT };
}
