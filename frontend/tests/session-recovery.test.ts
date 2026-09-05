import assert from "node:assert/strict";
import test from "node:test";
import { recoverPrivateSession } from "../lib/private-auth-session";

test("simultaneous recovery shares one rotation", async (t) => {
  let rotations = 0;
  t.mock.method(globalThis, "fetch", async (url: string) => {
    if (url.endsWith("/refresh")) { rotations++; return new Response(null); }
    return new Response(null, { status: 401 });
  });
  await Promise.all([recoverPrivateSession(), recoverPrivateSession()]);
  assert.equal(rotations, 1);
});

test("already rotated cookies skip another refresh", async (t) => {
  t.mock.method(globalThis, "fetch", async (url: string) => {
    assert.equal(url, "/api/v1/auth/me");
    return new Response(null);
  });
  await recoverPrivateSession();
});

test("temporary refresh failure allows a subsequent retry", async (t) => {
  let attempts = 0;
  t.mock.method(globalThis, "fetch", async (url: string) => {
    if (!url.endsWith("/refresh")) return new Response(null, { status: 401 });
    return new Response(null, { status: ++attempts === 1 ? 503 : 200 });
  });
  await assert.rejects(recoverPrivateSession(), /지연/);
  await recoverPrivateSession();
  assert.equal(attempts, 2);
});
