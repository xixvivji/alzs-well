const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function caseIdFromSearch(search: string) {
  const value = new URLSearchParams(search).get("caseId");
  return value && uuid.test(value) ? value : null;
}

export function customerLoginFromId(customerId?: string) {
  const match = /^SYN_V3_PUBLIC_[A-Za-z0-9]+_(\d{6})$/.exec(customerId ?? "");
  const index = Number(match?.[1]);
  return index >= 1 && index <= 300 ? `demo${String(index).padStart(3, "0")}` : null;
}

export function staffLoginPath(caseId?: string | null, customerId?: string) {
  const next = caseId && uuid.test(caseId) ? `/staff/cases?caseId=${caseId}` : "/staff/cases";
  const login = customerLoginFromId(customerId);
  const staff = login ? `staff${String(((Number(login.slice(4)) - 1) % 5) + 1).padStart(3, "0")}` : null;
  return `/staff/login?next=${encodeURIComponent(next)}${staff ? `&loginId=${staff}` : ""}`;
}

export function safePortalNext(value: string | null, role: "customer" | "staff") {
  const fallback = role === "customer" ? "/banking/help" : "/staff/cases";
  if (!value || /[\\\r\n]/.test(value) || !value.startsWith("/") || value.startsWith("//")) return fallback;
  const url = new URL(value, "https://prototype.invalid");
  if (role === "customer") return /^\/banking(?:\/|$)/.test(url.pathname) ? `${url.pathname}${url.search}${url.hash}` : fallback;
  if (url.pathname !== "/staff/cases") return fallback;
  const caseId = caseIdFromSearch(url.search);
  return caseId ? `/staff/cases?caseId=${caseId}` : fallback;
}
