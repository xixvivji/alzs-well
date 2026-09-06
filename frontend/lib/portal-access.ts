import { safePortalNext } from "./prototype-navigation";

export type StaffRole = "protection" | "admin";
export const staffLinks = [
  ["/staff/operations", "업무 현황"],
  ["/staff/cases", "사건 검토"],
  ["/staff/system-status", "서비스 상태"],
] as const;
export const adminLinks = [
  ["/staff/control-center", "관리·준법"],
  ["/staff/system-status", "서비스 상태"],
] as const;

export function staffRoleFor(roles: readonly string[]): StaffRole | null {
  if (roles.includes("DETECTION_ADMIN")) return "admin";
  if (roles.includes("PROTECTION_STAFF")) return "protection";
  return null;
}

export function portalHome(roles: readonly string[]) {
  const staffRole = staffRoleFor(roles);
  return staffRole === "admin" ? "/staff/control-center" : staffRole === "protection" ? "/staff/cases" : roles.includes("CUSTOMER") ? "/banking" : "/";
}

export function canOpenStaffPage(roles: readonly string[], required: "protection" | "admin" | "shared") {
  return required === "shared" || roles.includes(required === "admin" ? "DETECTION_ADMIN" : "PROTECTION_STAFF");
}

// The login selection chooses a form, never a role. The authenticated response chooses the destination.
export function loginDestination(roles: readonly string[], requested: string | null) {
  const home = portalHome(roles);
  if (!requested || /[\\\r\n]/.test(requested) || !requested.startsWith("/") || requested.startsWith("//")) return home;
  const url = new URL(requested, "https://portal.invalid");
  if (staffRoleFor(roles)) {
    const allowed = staffRoleFor(roles) === "admin" ? adminLinks : staffLinks;
    if (!allowed.some(([path]) => path === url.pathname)) return home;
    return url.pathname === "/staff/cases" ? safePortalNext(requested, "staff") : url.pathname;
  }
  if (roles.includes("CUSTOMER") && /^\/banking(?:\/|$)/.test(url.pathname)) return safePortalNext(requested, "customer");
  return home;
}
