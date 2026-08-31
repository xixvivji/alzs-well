import { ApiClientError } from "./api";
import { invokeApiOperation } from "./api-operation-client";
import type { PrivateCustomerSession } from "./private-financial-products";

type TokenPair = {
  accessToken: string;
  accessExpiresAt: string;
  refreshToken: string;
  refreshExpiresAt: string;
};

const EXPIRY_SKEW_MS = 30_000;
const refreshInFlight = new WeakMap<PrivateCustomerSession, Promise<string>>();

export class PrivateSessionExpiredError extends Error {
  constructor() {
    super("인증 시간이 만료되었습니다. 안전을 위해 로그아웃했으니 다시 로그인해 주세요.");
    this.name = "PrivateSessionExpiredError";
  }
}

export function isPrivateSessionExpiredError(reason: unknown): reason is PrivateSessionExpiredError {
  return reason instanceof PrivateSessionExpiredError;
}

export async function withPrivateCustomerSession<T>(
  session: PrivateCustomerSession,
  operation: (accessToken: string) => Promise<T>,
): Promise<T> {
  const accessToken = await currentAccessToken(session);
  try {
    return await operation(accessToken);
  } catch (reason) {
    if (!(reason instanceof ApiClientError) || reason.status !== 401) throw reason;
    if (session.invalidated) throw new PrivateSessionExpiredError();
    const retryToken = session.accessToken !== accessToken
      ? session.accessToken
      : await refreshAccessToken(session);
    try {
      return await operation(retryToken);
    } catch (retryReason) {
      if (retryReason instanceof ApiClientError && retryReason.status === 401) {
        invalidatePrivateCustomerSession(session);
        throw new PrivateSessionExpiredError();
      }
      throw retryReason;
    }
  }
}

export function invalidatePrivateCustomerSession(session: PrivateCustomerSession): void {
  session.accessToken = "";
  session.refreshToken = "";
  session.accessExpiresAt = "";
  session.refreshExpiresAt = "";
  session.invalidated = true;
}

async function currentAccessToken(session: PrivateCustomerSession): Promise<string> {
  if (session.invalidated || !session.accessToken || !session.refreshToken) {
    invalidatePrivateCustomerSession(session);
    throw new PrivateSessionExpiredError();
  }
  const expiry = Date.parse(session.accessExpiresAt);
  if (!Number.isNaN(expiry) && expiry <= Date.now() + EXPIRY_SKEW_MS) {
    return refreshAccessToken(session);
  }
  return session.accessToken;
}

async function refreshAccessToken(session: PrivateCustomerSession): Promise<string> {
  const active = refreshInFlight.get(session);
  if (active) return active;
  const refresh = performRefresh(session);
  refreshInFlight.set(session, refresh);
  try { return await refresh; }
  finally { refreshInFlight.delete(session); }
}

async function performRefresh(session: PrivateCustomerSession): Promise<string> {
  const refreshExpiry = Date.parse(session.refreshExpiresAt);
  if (!session.refreshToken || (!Number.isNaN(refreshExpiry) && refreshExpiry <= Date.now())) {
    invalidatePrivateCustomerSession(session);
    throw new PrivateSessionExpiredError();
  }
  try {
    const response = await invokeApiOperation<TokenPair>("POST /api/v1/auth/token/refresh", {
      body: { refreshToken: session.refreshToken },
      timeoutMs: 8_000,
    });
    const pair = response.body.data;
    const accessExpiry = Date.parse(pair?.accessExpiresAt ?? "");
    const refreshExpiry = Date.parse(pair?.refreshExpiresAt ?? "");
    if (
      !pair?.accessToken || !pair.refreshToken
      || Number.isNaN(accessExpiry) || Number.isNaN(refreshExpiry)
      || accessExpiry <= Date.now() || refreshExpiry <= accessExpiry
    ) throw new Error("token refresh response invalid");
    session.accessToken = pair.accessToken;
    session.accessExpiresAt = pair.accessExpiresAt;
    session.refreshToken = pair.refreshToken;
    session.refreshExpiresAt = pair.refreshExpiresAt;
    session.invalidated = false;
    return pair.accessToken;
  } catch {
    invalidatePrivateCustomerSession(session);
    throw new PrivateSessionExpiredError();
  }
}
