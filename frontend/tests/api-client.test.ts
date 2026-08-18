import assert from "node:assert/strict";
import test from "node:test";
import { ApiClientError, apiRequest } from "../lib/api.ts";

const responseBody = {
  success: true, status: 200, code: "OK", message: "ok", data: { value: 1 }, errors: [],
  timestamp: "2026-08-18T00:00:00Z", traceId: "trace-json",
};

test("parses a successful JSON API envelope", async (t) => {
  t.mock.method(globalThis, "fetch", async () => new Response(JSON.stringify(responseBody), {
    status: 200, headers: { "content-type": "application/json" },
  }));
  const result = await apiRequest<{ value: number }>("/api/v1/test");
  assert.equal(result.body.data?.value, 1);
});

test("preserves API status, code and trace ID", async (t) => {
  t.mock.method(globalThis, "fetch", async () => new Response(JSON.stringify({
    ...responseBody, success: false, status: 429, code: "RATE_LIMITED", message: "잠시 후 다시 시도", data: null,
  }), { status: 429, headers: { "content-type": "application/json" } }));
  await assert.rejects(apiRequest("/api/v1/test"), (error: unknown) => {
    assert.ok(error instanceof ApiClientError);
    assert.equal(error.status, 429); assert.equal(error.code, "RATE_LIMITED");
    assert.equal(error.traceId, "trace-json"); assert.equal(error.retryable, true);
    return true;
  });
});

test("converts an HTML proxy error into a stable client error", async (t) => {
  t.mock.method(globalThis, "fetch", async () => new Response("<html>gateway error</html>", {
    status: 502, headers: { "content-type": "text/html", "X-Trace-Id": "proxy-trace" },
  }));
  await assert.rejects(apiRequest("/api/v1/test"), (error: unknown) => {
    assert.ok(error instanceof ApiClientError);
    assert.equal(error.kind, "http"); assert.equal(error.status, 502);
    assert.equal(error.traceId, "proxy-trace");
    return true;
  });
});

test("rejects malformed JSON without losing the HTTP status", async (t) => {
  t.mock.method(globalThis, "fetch", async () => new Response("{", {
    status: 500, headers: { "content-type": "application/json", "X-Trace-Id": "parse-trace" },
  }));
  await assert.rejects(apiRequest("/api/v1/test"), (error: unknown) => {
    assert.ok(error instanceof ApiClientError);
    assert.equal(error.kind, "parse"); assert.equal(error.status, 500);
    assert.equal(error.traceId, "parse-trace");
    return true;
  });
});
