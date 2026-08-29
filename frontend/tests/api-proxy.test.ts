import assert from "node:assert/strict";
import test from "node:test";
import { isApiProxyPath, proxyApiRequest, resolveBackendOrigin } from "../worker/api-proxy";

const PROXY_SECRET = "0123456789abcdef".repeat(4);

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
    trustedClientAddress: "203.0.113.10",
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
