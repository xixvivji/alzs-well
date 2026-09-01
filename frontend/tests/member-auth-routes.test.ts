import assert from "node:assert/strict";
import test from "node:test";
import { POST as login } from "../app/api/member-auth/login/route";
import { GET as proxyGet, POST as proxyPost } from "../app/api/[...path]/route";

const envelope = (data: unknown) => JSON.stringify({
  success: true, status: 200, code: "AUTH_LOGIN_SUCCEEDED", message: "ok", data,
  errors: [], timestamp: "2026-09-01T00:00:00Z", traceId: "trace-member-login",
});

test("generic BFF blocks raw token issuance and overwrites browser Authorization with the HttpOnly token", async (t) => {
  const previousOrigin = process.env.BACKEND_API_ORIGIN;
  const previousSecret = process.env.BACKEND_PROXY_SHARED_SECRET;
  process.env.BACKEND_API_ORIGIN = "http://127.0.0.1:8080";
  process.env.BACKEND_PROXY_SHARED_SECRET = "c".repeat(64);
  t.after(() => {
    if (previousOrigin === undefined) delete process.env.BACKEND_API_ORIGIN; else process.env.BACKEND_API_ORIGIN = previousOrigin;
    if (previousSecret === undefined) delete process.env.BACKEND_PROXY_SHARED_SECRET; else process.env.BACKEND_PROXY_SHARED_SECRET = previousSecret;
  });
  const blocked = await proxyPost(new Request("http://localhost:3000/api/v1/auth/login", {
    method: "POST", body: JSON.stringify({ loginId: "demo001", password: "not-forwarded" }),
  }));
  assert.equal(blocked.status, 404);

  t.mock.method(globalThis, "fetch", async (request: Request) => {
    assert.equal(request.headers.get("authorization"), `Bearer ${"a".repeat(43)}`);
    return new Response(envelope({ customerId: "customer-1", displayName: "합성고객", roles: ["CUSTOMER"] }), {
      headers: { "content-type": "application/json" },
    });
  });
  const response = await proxyGet(new Request("http://localhost:3000/api/v1/auth/me", {
    headers: {
      authorization: "Bearer attacker-controlled-token",
      cookie: `alzs-member-access=${"a".repeat(43)}`,
    },
  }));
  assert.equal(response.status, 200);
});

test("login BFF converts backend tokens to HttpOnly cookies and removes them from the response body", async (t) => {
  const previousOrigin = process.env.BACKEND_API_ORIGIN;
  const previousSecret = process.env.BACKEND_PROXY_SHARED_SECRET;
  process.env.BACKEND_API_ORIGIN = "http://127.0.0.1:8080";
  process.env.BACKEND_PROXY_SHARED_SECRET = "a".repeat(64);
  t.after(() => {
    if (previousOrigin === undefined) delete process.env.BACKEND_API_ORIGIN; else process.env.BACKEND_API_ORIGIN = previousOrigin;
    if (previousSecret === undefined) delete process.env.BACKEND_PROXY_SHARED_SECRET; else process.env.BACKEND_PROXY_SHARED_SECRET = previousSecret;
  });
  t.mock.method(globalThis, "fetch", async (request: Request) => {
    assert.equal(new URL(request.url).pathname, "/api/v1/auth/login");
    assert.equal(request.headers.get("authorization"), null);
    return new Response(envelope({
      tokenType: "Bearer", accessToken: "a".repeat(43),
      accessExpiresAt: new Date(Date.now() + 900_000).toISOString(),
      refreshToken: "b".repeat(43), refreshExpiresAt: new Date(Date.now() + 28_800_000).toISOString(),
    }), { headers: { "content-type": "application/json", "x-trace-id": "trace-member-login" } });
  });

  const response = await login(new Request("http://localhost:3000/api/member-auth/login", {
    method: "POST", headers: { "content-type": "application/json", origin: "http://localhost:3000" },
    body: JSON.stringify({ loginId: "demo001", password: "a-secure-demo-password" }),
  }));
  const text = await response.text();
  assert.equal(response.status, 200);
  assert.doesNotMatch(text, /a{32}|b{32}|accessToken|refreshToken/);
  const cookies = response.headers.get("set-cookie") ?? "";
  assert.match(cookies, /alzs-member-access=/);
  assert.match(cookies, /alzs-member-refresh=/);
  assert.match(cookies, /HttpOnly/);
});
