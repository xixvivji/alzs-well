import assert from "node:assert/strict";
import test from "node:test";
import { createDemoContext, discardDemoSession } from "../lib/demo-workflow";

const envelope = <T>(data: T) => JSON.stringify({
  success: true, status: 200, code: "OK", message: "ok", data,
  errors: [], timestamp: "2026-08-18T00:00:00Z", traceId: "trace-test",
});

test("creates a session and ingests the fixed scenario", async (t) => {
  const calls: Array<{ url: string; method: string; capability: string | null }> = [];
  t.mock.method(globalThis, "fetch", async (input, init) => {
    const url = String(input);
    calls.push({ url, method: String(init?.method), capability: new Headers(init?.headers).get("X-Demo-Capability") });
    if (url.endsWith("/api/v1/demo/sessions")) {
      return new Response(envelope({ sessionId: "session-1" }), {
        headers: { "content-type": "application/json", "X-Demo-Customer-Capability": "cap-1" },
      });
    }
    return new Response(envelope({ demoRunId: "run-1", customerId: "customer-1", alertId: "alert-1" }),
      { headers: { "content-type": "application/json" } });
  });

  assert.deepEqual(await createDemoContext(), {
    sessionId: "session-1", capability: "cap-1", demoRunId: "run-1", customerId: "customer-1", alertId: "alert-1",
  });
  assert.equal(calls[1]?.capability, "cap-1");
  assert.match(calls[1]?.url ?? "", /FIN_MGMT_AB_001\/ingest$/);
});

test("discards an issued server session when scenario ingest fails", async (t) => {
  const methods: string[] = [];
  t.mock.method(globalThis, "fetch", async (input, init) => {
    methods.push(String(init?.method));
    if (String(input).endsWith("/api/v1/demo/sessions")) {
      return new Response(envelope({ sessionId: "session-leak" }), {
        headers: { "content-type": "application/json", "X-Demo-Customer-Capability": "cap-leak" },
      });
    }
    if (init?.method === "DELETE") return new Response(envelope(null), { headers: { "content-type": "application/json" } });
    return new Response("gateway failure", { status: 502, headers: { "content-type": "text/plain" } });
  });

  await assert.rejects(createDemoContext());
  assert.deepEqual(methods, ["POST", "POST", "DELETE"]);
});

test("best-effort discard does not surface an expired-session failure", async (t) => {
  t.mock.method(globalThis, "fetch", async () => new Response("", { status: 404 }));
  await discardDemoSession("expired", "capability");
});
