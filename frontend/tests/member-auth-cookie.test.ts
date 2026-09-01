import assert from "node:assert/strict";
import test from "node:test";
import {
  clearMemberTokenCookies, memberTokenCookies, readMemberAccessCookie, readMemberRefreshCookie,
} from "../worker/member-auth-cookie";

const access = "a".repeat(43);
const refresh = "b".repeat(43);

test("member tokens are serialized only as secure HttpOnly same-site cookies", () => {
  const cookies = memberTokenCookies("https://alzs-well.vercel.app/login", {
    accessToken: access, accessExpiresAt: new Date(Date.now() + 900_000).toISOString(),
    refreshToken: refresh, refreshExpiresAt: new Date(Date.now() + 28_800_000).toISOString(),
  });
  assert.equal(cookies.length, 2);
  assert.ok(cookies.every((cookie) => cookie.includes("HttpOnly") && cookie.includes("SameSite=Strict") && cookie.includes("Secure")));
  assert.match(cookies[0] ?? "", /^__Host-alzs-member-access=/);
  assert.match(cookies[1] ?? "", /^__Host-alzs-member-refresh=/);

  const request = new Request("https://alzs-well.vercel.app/api/v1/auth/me", {
    headers: { cookie: `__Host-alzs-member-access=${access}; __Host-alzs-member-refresh=${refresh}` },
  });
  assert.equal(readMemberAccessCookie(request), access);
  assert.equal(readMemberRefreshCookie(request), refresh);
});

test("logout clears both member cookies and malformed values are rejected", () => {
  const cleared = clearMemberTokenCookies("https://alzs-well.vercel.app/");
  assert.ok(cleared.every((cookie) => cookie.includes("Max-Age=0")));
  const malformed = new Request("https://alzs-well.vercel.app/", { headers: { cookie: "__Host-alzs-member-access=short" } });
  assert.equal(readMemberAccessCookie(malformed), null);
});
