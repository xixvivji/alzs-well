import assert from "node:assert/strict";
import { generateKeyPairSync, sign } from "node:crypto";
import test from "node:test";
import { isApiProxyPath, proxyApiRequest, resolveBackendOrigin } from "../worker/api-proxy";
import { resolveClientRateIdentity } from "../worker/client-rate-identity";
import { issueStaffCapability } from "../worker/staff-capability";

const PROXY_SECRET = "0123456789abcdef".repeat(4);
const RATE_IDENTITY = "A".repeat(22);

test("intercepts only the API path boundary", () => {
  assert.equal(isApiProxyPath("/api"), true);
  assert.equal(isApiProxyPath("/api/v1/system/health"), true);
  assert.equal(isApiProxyPath("/apiary"), false);
  assert.equal(isApiProxyPath("/demo"), false);
});

test("accepts HTTPS deployment origins and loopback-only local HTTP", () => {
  assert.equal(resolveBackendOrigin("https://api.example.com", "https://app.example.com/demo"), "https://api.example.com");
  assert.equal(resolveBackendOrigin("http://127.0.0.1:8080", "http://localhost:3000/demo"), "http://127.0.0.1:8080");
  for (const value of [
    undefined,
    " https://api.example.com",
    "ftp://api.example.com",
    "https://user@api.example.com",
    "https://api.example.com/base",
    "https://api.example.com?debug=true",
    "https://api.example.com/#fragment",
    "http://api.example.com",
    "https://app.example.com",
  ]) {
    assert.throws(() => resolveBackendOrigin(value, "https://app.example.com/demo"));
  }
  assert.throws(() => resolveBackendOrigin("https://api.example.com", "http://app.example.com/demo"));
  assert.throws(() => resolveBackendOrigin("https://api.example.com", "http://localhost:3000/demo"));
  assert.throws(() => resolveBackendOrigin("https://api.example.com", "ftp://app.example.com/demo"));
  assert.throws(() => resolveBackendOrigin("http://127.0.0.1:8080", "http://app.example.com/demo"));
});

test("preserves method, body and API headers while stripping platform identity", async () => {
  let forwarded: Request | undefined;
  const response = await proxyApiRequest(new Request(
    "https://app.example.com/api/v1/demo/sessions?mode=%ED%95%A9%EC%84%B1",
    {
      method: "POST",
      headers: {
        Authorization: "Bearer capability",
        "Content-Type": "application/json",
        "Idempotency-Key": "idem-12345678",
        "X-Demo-Capability": "demo-capability",
        Cookie: "site-session=secret",
        Origin: "https://app.example.com",
        Referer: "https://app.example.com/demo",
        "OAI-Authenticated-User-Id": "site-user",
        "CF-Connecting-IP": "203.0.113.10",
        "X-Forwarded-For": "203.0.113.10",
        Forwarded: "for=203.0.113.10;proto=https",
        "Proxy-Connection": "keep-alive",
        "X-Alzs-Client-Key": "attacker-controlled",
      },
      body: JSON.stringify({ scenario: "safe" }),
    },
  ), "https://backend.example.com", {
    proxySharedSecret: PROXY_SECRET,
    clientRateIdentity: RATE_IDENTITY,
    fetchImpl: async (request) => {
      forwarded = request;
      return Response.json({ ok: true }, {
        headers: { "X-Trace-Id": "backend-trace", "Set-Cookie": "backend=secret" },
      });
    },
  });

  assert.ok(forwarded);
  assert.equal(forwarded.url, "https://backend.example.com/api/v1/demo/sessions?mode=%ED%95%A9%EC%84%B1");
  assert.equal(forwarded.method, "POST");
  assert.equal(await forwarded.text(), JSON.stringify({ scenario: "safe" }));
  assert.equal(forwarded.headers.get("authorization"), "Bearer capability");
  assert.equal(forwarded.headers.get("idempotency-key"), "idem-12345678");
  assert.equal(forwarded.headers.get("x-demo-capability"), "demo-capability");
  assert.equal(forwarded.headers.get("cookie"), null);
  assert.equal(forwarded.headers.get("origin"), null);
  assert.equal(forwarded.headers.get("referer"), null);
  assert.equal(forwarded.headers.get("oai-authenticated-user-id"), null);
  assert.equal(forwarded.headers.get("cf-connecting-ip"), null);
  assert.equal(forwarded.headers.get("x-forwarded-for"), null);
  assert.equal(forwarded.headers.get("forwarded"), null);
  assert.equal(forwarded.headers.get("proxy-connection"), null);
  assert.match(forwarded.headers.get("x-alzs-client-key") ?? "", /^[a-f0-9]{64}$/);
  assert.equal(forwarded.headers.get("x-alzs-proxy-secret"), PROXY_SECRET);
  assert.equal(response.headers.get("set-cookie"), null);
  assert.equal(response.headers.get("cache-control"), "no-store");
  assert.equal(response.headers.get("x-trace-id"), "backend-trace");
});

test("rejects a cross-origin browser request before contacting AWS", async () => {
  let called = false;
  const response = await proxyApiRequest(new Request("https://app.example.com/api/v1/demo/sessions", {
    method: "POST", headers: { Origin: "https://attacker.example" }, body: "{}",
  }), "https://backend.example.com", {
    proxySharedSecret: PROXY_SECRET,
    clientRateIdentity: RATE_IDENTITY,
    fetchImpl: async () => { called = true; return new Response(null); },
  });
  assert.equal(response.status, 403);
  assert.equal((await response.json()).code, "BACKEND_PROXY_ORIGIN_REJECTED");
  assert.equal(called, false);
});

test("fails closed with stable JSON for missing configuration and upstream failures", async () => {
  const missing = await proxyApiRequest(
    new Request("https://app.example.com/api/v1/system/health"),
    undefined,
  );
  assert.equal(missing.status, 503);
  assert.equal((await missing.json()).code, "BACKEND_PROXY_CONFIGURATION_INVALID");
  assert.equal(missing.headers.get("cache-control"), "no-store");

  const unavailable = await proxyApiRequest(
    new Request("https://app.example.com/api/v1/system/health"),
    "https://backend.example.com",
    {
      proxySharedSecret: PROXY_SECRET,
      clientRateIdentity: RATE_IDENTITY,
      fetchImpl: async () => { throw new Error("https://secret.internal/failed"); },
    },
  );
  assert.equal(unavailable.status, 502);
  const unavailableBody = await unavailable.text();
  assert.match(unavailableBody, /BACKEND_UNAVAILABLE/);
  assert.doesNotMatch(unavailableBody, /secret\.internal/);

  const redirect = await proxyApiRequest(
    new Request("https://app.example.com/api/v1/system/health"),
    "https://backend.example.com",
    {
      proxySharedSecret: PROXY_SECRET,
      clientRateIdentity: RATE_IDENTITY,
      fetchImpl: async () => new Response(null, { status: 302, headers: { Location: "https://outside.example" } }),
    },
  );
  assert.equal(redirect.status, 502);
  assert.equal((await redirect.json()).code, "BACKEND_INVALID_RESPONSE");
  assert.equal(redirect.headers.get("location"), null);
});

test("returns a timeout envelope without exposing upstream details", async () => {
  const response = await proxyApiRequest(
    new Request("https://app.example.com/api/v1/system/health"),
    "https://backend.example.com",
    {
      timeoutMs: 5,
      proxySharedSecret: PROXY_SECRET,
      clientRateIdentity: RATE_IDENTITY,
      fetchImpl: (request) => new Promise((_, reject) => {
        const safetyTimer = setTimeout(
          () => reject(new Error("timeout signal was not delivered")),
          1_000,
        );
        request.signal.addEventListener("abort", () => {
          clearTimeout(safetyTimer);
          reject(request.signal.reason);
        }, { once: true });
      }),
    },
  );
  assert.equal(response.status, 504);
  assert.equal((await response.json()).code, "BACKEND_TIMEOUT");
});

test("streams request bodies and rejects normal API bodies above 32 KiB", async () => {
  let upstreamCalled = false;
  const declaredTooLarge = await proxyApiRequest(new Request(
    "https://app.example.com/api/v1/demo/sessions",
    {
      method: "POST",
      headers: { "Content-Length": String(32 * 1024 + 1) },
      body: "x",
    },
  ), "https://backend.example.com", {
    proxySharedSecret: PROXY_SECRET,
    clientRateIdentity: RATE_IDENTITY,
    fetchImpl: async () => {
      upstreamCalled = true;
      return new Response(null, { status: 204 });
    },
  });
  assert.equal(declaredTooLarge.status, 413);
  assert.equal((await declaredTooLarge.json()).code, "BACKEND_PROXY_REQUEST_TOO_LARGE");
  assert.equal(upstreamCalled, false);

  const streamedTooLarge = await proxyApiRequest(new Request(
    "https://app.example.com/api/v1/demo/sessions",
    { method: "POST", body: "x".repeat(32 * 1024 + 1) },
  ), "https://backend.example.com", {
    proxySharedSecret: PROXY_SECRET,
    clientRateIdentity: RATE_IDENTITY,
    fetchImpl: async (upstream) => {
      await upstream.arrayBuffer();
      return new Response(null, { status: 204 });
    },
  });
  assert.equal(streamedTooLarge.status, 413);
  assert.equal((await streamedTooLarge.json()).code, "BACKEND_PROXY_REQUEST_TOO_LARGE");
});

test("requires a server-only proxy secret before contacting the backend", async () => {
  let called = false;
  const response = await proxyApiRequest(
    new Request("https://app.example.com/api/v1/system/health"),
    "https://backend.example.com",
    { fetchImpl: async () => { called = true; return new Response(null); } },
  );
  assert.equal(response.status, 503);
  assert.equal((await response.json()).code, "BACKEND_PROXY_CONFIGURATION_INVALID");
  assert.equal(called, false);
});

test("서명된 직원 identity JWT만 서버 비밀값으로 capability를 발급한다", async () => {
  const session = "98000000-0000-4000-8000-000000000001";
  const bootstrap = "b".repeat(64);
  const identity = staffIdentityJwt("staff-user-1");
  let upstream: Request | null = null;
  const response = await issueStaffCapability(new Request(`https://app.example.com/api/internal/staff-capability/${session}`, {
    method: "POST", headers: {
      "oai-authenticated-user-id": "attacker-controlled",
      "x-alzs-staff-identity-token": identity.token,
    },
  }), {
    backendOrigin: "https://backend.example.com", proxySharedSecret: PROXY_SECRET,
    bootstrapToken: bootstrap, allowedUserIds: "staff-user-1", clientRateIdentity: RATE_IDENTITY,
    identityPublicKeyPem: identity.publicKeyPem, identityIssuer: "https://identity.example.com/",
    identityAudience: "alzs-well-staff", identityRequiredRole: "PROTECTION_STAFF",
    fetchImpl: async (request) => {
      upstream = request;
      return new Response(JSON.stringify({ success: true }), {
        headers: { "Content-Type": "application/json", "X-Demo-Staff-Capability": "staff-secret" },
      });
    },
  });
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("X-Demo-Staff-Capability"), "staff-secret");
  assert.equal(upstream?.headers.get("Authorization"), `Bearer ${bootstrap}`);
});

test("원문 플랫폼 사용자 ID 헤더는 직원 capability 발급 전에 거부한다", async () => {
  const session = "98000000-0000-4000-8000-000000000001";
  const response = await issueStaffCapability(new Request(`https://app.example.com/api/internal/staff-capability/${session}`, {
    method: "POST", headers: { "oai-authenticated-user-id": "unknown" },
  }), { backendOrigin: "https://backend.example.com", proxySharedSecret: PROXY_SECRET,
    bootstrapToken: "b".repeat(64), allowedUserIds: "staff-user-1", clientRateIdentity: RATE_IDENTITY });
  assert.equal(response.status, 403);
  assert.equal((await response.json()).code, "STAFF_ACCESS_DENIED");
});

test("직원 subject와 역할이 있어도 서명이 위조된 JWT는 거부한다", async () => {
  const session = "98000000-0000-4000-8000-000000000001";
  const identity = staffIdentityJwt("staff-user-1");
  const parts = identity.token.split(".");
  const signature = parts[2] ?? "";
  parts[2] = `${signature.startsWith("A") ? "B" : "A"}${signature.slice(1)}`;
  const response = await issueStaffCapability(new Request(`https://app.example.com/api/internal/staff-capability/${session}`, {
    method: "POST", headers: { "x-alzs-staff-identity-token": parts.join(".") },
  }), {
    backendOrigin: "https://backend.example.com", proxySharedSecret: PROXY_SECRET,
    bootstrapToken: "b".repeat(64), clientRateIdentity: RATE_IDENTITY,
    identityPublicKeyPem: identity.publicKeyPem, identityIssuer: "https://identity.example.com/",
    identityAudience: "alzs-well-staff", identityRequiredRole: "PROTECTION_STAFF",
  });
  assert.equal(response.status, 403);
  assert.equal((await response.json()).code, "STAFF_ACCESS_DENIED");
});

test("공개 합성 데모는 현재 고객 capability를 검증한 뒤에만 직원 capability를 발급한다", async () => {
  const session = "98000000-0000-4000-8000-000000000001";
  const paths: string[] = [];
  const response = await issueStaffCapability(new Request(`https://app.example.com/api/internal/staff-capability/${session}`, {
    method: "POST", headers: { "X-Demo-Capability": "customer-capability-secret-123456789" },
  }), {
    backendOrigin: "https://backend.example.com", proxySharedSecret: PROXY_SECRET,
    bootstrapToken: "b".repeat(64), publicDemo: true, clientRateIdentity: RATE_IDENTITY,
    fetchImpl: async (request) => {
      paths.push(new URL(request.url).pathname);
      if (request.method === "GET") {
        assert.equal(request.headers.get("X-Demo-Capability"), "customer-capability-secret-123456789");
        return Response.json({ success: true });
      }
      assert.equal(request.headers.get("Authorization"), `Bearer ${"b".repeat(64)}`);
      return new Response(JSON.stringify({ success: true }), {
        headers: { "Content-Type": "application/json", "X-Demo-Staff-Capability": "staff-secret" },
      });
    },
  });
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("X-Demo-Staff-Capability"), "staff-secret");
  assert.deepEqual(paths, [
    `/api/v1/demo/sessions/${session}`,
    `/api/v1/demo/staff/sessions/${session}/capability`,
  ]);
});

test("서버 서명 client cookie는 안정적인 비식별 rate key이며 위조 시 재발급한다", async () => {
  const first = await resolveClientRateIdentity(new Request("https://app.example.com/demo"), PROXY_SECRET);
  assert.match(first.identity ?? "", /^[A-Za-z0-9_-]{22}$/);
  assert.match(first.setCookie ?? "", /^__Host-alzs-client-rate=/);
  assert.match(first.setCookie ?? "", /HttpOnly; SameSite=Strict; Max-Age=86400; Secure$/);
  assert.doesNotMatch(first.setCookie ?? "", /203\.0\.113|staff-user|oai/i);

  const cookie = first.setCookie!.split(";", 1)[0]!;
  const second = await resolveClientRateIdentity(new Request("https://app.example.com/demo", {
    headers: { Cookie: cookie },
  }), PROXY_SECRET);
  assert.equal(second.identity, first.identity);
  assert.equal(second.setCookie, null);

  const forged = await resolveClientRateIdentity(new Request("https://app.example.com/demo", {
    headers: { Cookie: `${cookie}0` },
  }), PROXY_SECRET);
  assert.notEqual(forged.identity, first.identity);
  assert.ok(forged.setCookie);
});

test("Vercel이 덮어쓴 client IP는 cookie 삭제로 바꿀 수 없는 비식별 rate key가 된다", async () => {
  const first = await resolveClientRateIdentity(
    new Request("https://app.example.com/api/v1/demo/sessions"), PROXY_SECRET, "203.0.113.10",
  );
  const withoutCookie = await resolveClientRateIdentity(
    new Request("https://app.example.com/api/v1/demo/sessions"), PROXY_SECRET, "203.0.113.10",
  );
  const anotherNetwork = await resolveClientRateIdentity(
    new Request("https://app.example.com/api/v1/demo/sessions"), PROXY_SECRET, "203.0.113.11",
  );

  assert.match(first.identity ?? "", /^[A-Za-z0-9_-]{22}$/);
  assert.equal(first.identity, withoutCookie.identity);
  assert.notEqual(first.identity, anotherNetwork.identity);
  assert.equal(first.setCookie, null);
  assert.doesNotMatch(first.identity ?? "", /203\.0\.113/);
  assert.equal((await resolveClientRateIdentity(
    new Request("https://app.example.com"), PROXY_SECRET, "203.0.113.10, 198.51.100.4",
  )).identity, null);
});

test("서로 다른 서버 발급 client ID는 별도 rate bucket 키로 중계된다", async () => {
  const rateKeys: string[] = [];
  for (const clientRateIdentity of ["A".repeat(22), "B".repeat(22)]) {
    const response = await proxyApiRequest(
      new Request("https://app.example.com/api/v1/system/health", {
        headers: { "X-Alzs-Client-Key": "browser-spoof" },
      }),
      "https://backend.example.com",
      {
        proxySharedSecret: PROXY_SECRET,
        clientRateIdentity,
        fetchImpl: async (request) => {
          rateKeys.push(request.headers.get("X-Alzs-Client-Key") ?? "");
          return Response.json({ success: true });
        },
      },
    );
    assert.equal(response.status, 200);
  }
  assert.equal(rateKeys.length, 2);
  assert.match(rateKeys[0] ?? "", /^[a-f0-9]{64}$/);
  assert.match(rateKeys[1] ?? "", /^[a-f0-9]{64}$/);
  assert.notEqual(rateKeys[0], rateKeys[1]);
  assert.ok(rateKeys.every((value) => value !== "browser-spoof"));
});

function staffIdentityJwt(subject: string): { token: string; publicKeyPem: string } {
  const { privateKey, publicKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const now = Math.floor(Date.now() / 1_000);
  const header = Buffer.from(JSON.stringify({ alg: "RS256", typ: "JWT" })).toString("base64url");
  const claims = Buffer.from(JSON.stringify({
    sub: subject, iss: "https://identity.example.com/", aud: "alzs-well-staff",
    exp: now + 300, iat: now, roles: ["PROTECTION_STAFF"],
  })).toString("base64url");
  const signature = sign("RSA-SHA256", Buffer.from(`${header}.${claims}`), privateKey).toString("base64url");
  return {
    token: `${header}.${claims}.${signature}`,
    publicKeyPem: publicKey.export({ type: "spki", format: "pem" }).toString(),
  };
}
