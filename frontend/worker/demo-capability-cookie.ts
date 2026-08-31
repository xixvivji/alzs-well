export const DEMO_CAPABILITY_MODE_HEADER = "X-Demo-Capability-Mode";
export const DEMO_CAPABILITY_MODE = "HTTP_ONLY_COOKIE";

const PRODUCTION_COOKIE = "__Host-alzs-demo-capability";
const DEVELOPMENT_COOKIE = "alzs-demo-capability";

export function readDemoCapabilityCookie(request: Request): string | null {
  const name = cookieName(new URL(request.url));
  const cookieHeader = request.headers.get("cookie") ?? "";
  for (const part of cookieHeader.split(";")) {
    const [rawName, ...rawValue] = part.trim().split("=");
    if (rawName !== name) continue;
    try { return decodeURIComponent(rawValue.join("=")); }
    catch { return null; }
  }
  return null;
}

export function demoCapabilityCookie(requestUrl: string, capability: string): string {
  const url = new URL(requestUrl);
  const secure = url.protocol === "https:";
  return [
    `${cookieName(url)}=${encodeURIComponent(capability)}`,
    "Path=/",
    "HttpOnly",
    "SameSite=Strict",
    "Max-Age=1800",
    ...(secure ? ["Secure"] : []),
  ].join("; ");
}

export function clearDemoCapabilityCookie(requestUrl: string): string {
  const url = new URL(requestUrl);
  const secure = url.protocol === "https:";
  return [
    `${cookieName(url)}=`,
    "Path=/",
    "HttpOnly",
    "SameSite=Strict",
    "Max-Age=0",
    ...(secure ? ["Secure"] : []),
  ].join("; ");
}

function cookieName(url: URL): string {
  return url.protocol === "https:" ? PRODUCTION_COOKIE : DEVELOPMENT_COOKIE;
}
