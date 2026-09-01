import { ApiClientError } from "./api";
import type { PrivateCustomerSession } from "./private-financial-products";
const refreshInFlight = new WeakMap<PrivateCustomerSession, Promise<void>>();

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
  if (session.invalidated) throw new PrivateSessionExpiredError();
  try {
    return await operation("");
  } catch (reason) {
    if (!(reason instanceof ApiClientError) || reason.status !== 401) throw reason;
    if (session.invalidated) throw new PrivateSessionExpiredError();
    await refreshAccessToken(session);
    try {
      return await operation("");
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
  session.invalidated = true;
}

async function refreshAccessToken(session: PrivateCustomerSession): Promise<void> {
  const active = refreshInFlight.get(session);
  if (active) return active;
  const refresh = performRefresh(session);
  refreshInFlight.set(session, refresh);
  try { return await refresh; }
  finally { refreshInFlight.delete(session); }
}

async function performRefresh(session: PrivateCustomerSession): Promise<void> {
  try {
    const response = await fetch("/api/member-auth/refresh", { method: "POST", headers: { "Content-Type": "application/json" } });
    if (!response.ok) throw new Error("member session refresh failed");
    session.invalidated = false;
  } catch {
    invalidatePrivateCustomerSession(session);
    throw new PrivateSessionExpiredError();
  }
}
