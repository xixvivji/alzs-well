import { ApiClientError } from "./api";
import type { PrivateCustomerSession } from "./private-financial-products";
let refreshInFlight: Promise<void> | undefined;

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
    try { await recoverPrivateSession(); session.invalidated = false; }
    catch (error) {
      if (error instanceof PrivateSessionExpiredError) invalidatePrivateCustomerSession(session);
      throw error;
    }
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

export async function recoverPrivateSession(): Promise<void> {
  if (refreshInFlight) return refreshInFlight;
  const recover = async () => {
    // Recheck inside the cross-tab lock: another tab may have rotated cookies.
    const probe = await fetch("/api/v1/auth/me", { cache: "no-store" });
    if (probe.ok) return;
    if (probe.status !== 401) throw new Error("인증 상태를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    await performRefresh();
  };
  refreshInFlight = typeof navigator !== "undefined" && navigator.locks
    ? navigator.locks.request("alzs-member-refresh", recover).then(() => undefined)
    : recover();
  try { await refreshInFlight; }
  finally { refreshInFlight = undefined; }
}

async function performRefresh(): Promise<void> {
    const response = await fetch("/api/member-auth/refresh", { method: "POST", headers: { "Content-Type": "application/json" } });
    if (response.status === 401) throw new PrivateSessionExpiredError();
    if (!response.ok) throw new Error("인증 갱신이 지연되고 있습니다. 잠시 후 다시 시도해 주세요.");
}
