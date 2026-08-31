const PRODUCTION_COOKIE = "__Host-alzs-client-rate";
const DEVELOPMENT_COOKIE = "alzs-client-rate";
const MAX_AGE_SECONDS = 24 * 60 * 60;
const ID_PATTERN = /^[A-Za-z0-9_-]{22}$/;
const ISSUED_AT_PATTERN = /^[0-9a-z]{1,10}$/;
const SIGNATURE_PATTERN = /^[a-f0-9]{64}$/;
const SECRET_PATTERN = /^[a-f0-9]{64}$/;

export type ClientRateIdentity = {
  identity: string | null;
  setCookie: string | null;
};

export async function resolveClientRateIdentity(
  request: Request,
  secretHex: string | undefined,
  trustedClientIp?: string,
): Promise<ClientRateIdentity> {
  if (!secretHex || !SECRET_PATTERN.test(secretHex)) {
    return { identity: null, setCookie: null };
  }

  if (trustedClientIp !== undefined) {
    const normalizedIp = normalizeTrustedClientIp(trustedClientIp);
    if (!normalizedIp) return { identity: null, setCookie: null };
    return {
      identity: await networkIdentity(secretHex, normalizedIp),
      setCookie: null,
    };
  }

  const existing = readCookie(request, cookieName(new URL(request.url)));
  if (existing) {
    const [identity, issuedAt, signature, ...remainder] = existing.split(".");
    const issuedAtSeconds = Number.parseInt(issuedAt ?? "", 36);
    const nowSeconds = Math.floor(Date.now() / 1_000);
    if (
      remainder.length === 0
      && identity
      && issuedAt
      && signature
      && ID_PATTERN.test(identity)
      && ISSUED_AT_PATTERN.test(issuedAt)
      && SIGNATURE_PATTERN.test(signature)
      && Number.isSafeInteger(issuedAtSeconds)
      && issuedAtSeconds <= nowSeconds + 60
      && nowSeconds - issuedAtSeconds <= MAX_AGE_SECONDS
      && await sign(secretHex, `${identity}.${issuedAt}`) === signature
    ) {
      return { identity, setCookie: null };
    }
  }

  const bytes = crypto.getRandomValues(new Uint8Array(16));
  const identity = toBase64Url(bytes);
  const issuedAt = Math.floor(Date.now() / 1_000).toString(36);
  const signature = await sign(secretHex, `${identity}.${issuedAt}`);
  return {
    identity,
    setCookie: serializeCookie(request.url, `${identity}.${issuedAt}.${signature}`),
  };
}

function normalizeTrustedClientIp(value: string): string | null {
  const normalized = value.trim().toLowerCase();
  if (
    normalized.length < 3
    || normalized.length > 64
    || normalized.includes(",")
    || !/^[0-9a-f:.]+$/.test(normalized)
    || (!normalized.includes(":") && !/^(?:\d{1,3}\.){3}\d{1,3}$/.test(normalized))
  ) return null;
  if (!normalized.includes(":")) {
    const octets = normalized.split(".").map(Number);
    if (octets.length !== 4 || octets.some((octet) => !Number.isInteger(octet) || octet > 255)) return null;
    return octets.join(".");
  }
  return normalized;
}

async function networkIdentity(secretHex: string, normalizedIp: string): Promise<string> {
  const digest = await sign(secretHex, `network:${normalizedIp}`);
  const bytes = Uint8Array.from(digest.slice(0, 32).match(/.{2}/g) ?? [], (value) => Number.parseInt(value, 16));
  return toBase64Url(bytes);
}

function readCookie(request: Request, name: string): string | null {
  for (const part of (request.headers.get("cookie") ?? "").split(";")) {
    const [rawName, ...rawValue] = part.trim().split("=");
    if (rawName !== name) continue;
    try { return decodeURIComponent(rawValue.join("=")); }
    catch { return null; }
  }
  return null;
}

function serializeCookie(requestUrl: string, value: string): string {
  const url = new URL(requestUrl);
  return [
    `${cookieName(url)}=${encodeURIComponent(value)}`,
    "Path=/",
    "HttpOnly",
    "SameSite=Strict",
    `Max-Age=${MAX_AGE_SECONDS}`,
    ...(url.protocol === "https:" ? ["Secure"] : []),
  ].join("; ");
}

function cookieName(url: URL): string {
  return url.protocol === "https:" ? PRODUCTION_COOKIE : DEVELOPMENT_COOKIE;
}

async function sign(secretHex: string, payload: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    Uint8Array.from(secretHex.match(/.{2}/g) ?? [], (value) => Number.parseInt(value, 16)),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "HMAC",
    key,
    new TextEncoder().encode(`alzs-client-cookie-v1:${payload}`),
  );
  return Array.from(new Uint8Array(signature), (value) => value.toString(16).padStart(2, "0")).join("");
}

function toBase64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}
