export type StaffIdentityVerificationOptions = {
  publicKeyPem?: string;
  issuer?: string;
  audience?: string;
  requiredRole?: string;
  allowedUserIds?: string;
  now?: number;
};

type JwtHeader = { alg?: unknown; typ?: unknown };
type JwtClaims = {
  sub?: unknown;
  iss?: unknown;
  aud?: unknown;
  exp?: unknown;
  nbf?: unknown;
  iat?: unknown;
  roles?: unknown;
};

export async function verifyStaffIdentityJwt(
  token: string | null,
  options: StaffIdentityVerificationOptions,
): Promise<string | null> {
  if (!token || token.length > 8_192) return null;
  const issuer = options.issuer?.trim();
  const audience = options.audience?.trim();
  const requiredRole = options.requiredRole?.trim() || "PROTECTION_STAFF";
  if (!issuer || !audience || !options.publicKeyPem) return null;

  const parts = token.split(".");
  if (parts.length !== 3 || parts.some((part) => !/^[A-Za-z0-9_-]+$/.test(part))) return null;
  try {
    const header = decodeJson<JwtHeader>(parts[0]!);
    const claims = decodeJson<JwtClaims>(parts[1]!);
    if (header.alg !== "RS256" || (header.typ !== undefined && header.typ !== "JWT")) return null;

    const key = await crypto.subtle.importKey(
      "spki",
      decodePublicKey(options.publicKeyPem),
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
      false,
      ["verify"],
    );
    const validSignature = await crypto.subtle.verify(
      "RSASSA-PKCS1-v1_5",
      key,
      decodeBase64Url(parts[2]!),
      new TextEncoder().encode(`${parts[0]}.${parts[1]}`),
    );
    if (!validSignature) return null;

    const now = options.now ?? Math.floor(Date.now() / 1_000);
    if (
      typeof claims.sub !== "string"
      || claims.sub.length < 1
      || claims.sub.length > 200
      || claims.iss !== issuer
      || !hasAudience(claims.aud, audience)
      || typeof claims.exp !== "number"
      || !Number.isSafeInteger(claims.exp)
      || claims.exp <= now
      || claims.exp > now + 3_600
      || (claims.nbf !== undefined && (typeof claims.nbf !== "number" || claims.nbf > now + 30))
      || (claims.iat !== undefined && (typeof claims.iat !== "number" || claims.iat > now + 30))
      || !Array.isArray(claims.roles)
      || !claims.roles.every((role) => typeof role === "string")
      || !claims.roles.includes(requiredRole)
    ) return null;

    const allowed = new Set((options.allowedUserIds ?? "").split(",").map((value) => value.trim()).filter(Boolean));
    if (allowed.size > 0 && !allowed.has(claims.sub)) return null;
    return claims.sub;
  } catch {
    return null;
  }
}

function hasAudience(value: unknown, expected: string): boolean {
  return value === expected || (Array.isArray(value) && value.every((item) => typeof item === "string") && value.includes(expected));
}

function decodeJson<T>(value: string): T {
  return JSON.parse(new TextDecoder().decode(decodeBase64Url(value))) as T;
}

function decodePublicKey(pem: string): ArrayBuffer {
  const normalized = pem.replaceAll("\\n", "\n").trim();
  const body = normalized
    .replace("-----BEGIN PUBLIC KEY-----", "")
    .replace("-----END PUBLIC KEY-----", "")
    .replace(/\s/g, "");
  if (!body || !/^[A-Za-z0-9+/=]+$/.test(body)) throw new Error("invalid public key");
  return decodeBase64(body);
}

function decodeBase64Url(value: string): ArrayBuffer {
  const padded = value.replaceAll("-", "+").replaceAll("_", "/").padEnd(Math.ceil(value.length / 4) * 4, "=");
  return decodeBase64(padded);
}

function decodeBase64(value: string): ArrayBuffer {
  const binary = atob(value);
  const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));
  return bytes.buffer;
}
