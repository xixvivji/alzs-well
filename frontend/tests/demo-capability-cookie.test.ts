import assert from "node:assert/strict";
import test from "node:test";
import {
  clearDemoCapabilityCookie,
  demoCapabilityCookie,
  readDemoCapabilityCookie,
} from "../worker/demo-capability-cookie";

test("stores the deployed customer capability in a Secure HttpOnly host cookie", () => {
  const value = demoCapabilityCookie("https://demo.example.com/api/v1/demo/sessions", "secret/value");
  assert.match(value, /^__Host-alzs-demo-capability=secret%2Fvalue;/);
  assert.match(value, /HttpOnly/);
  assert.match(value, /SameSite=Strict/);
  assert.match(value, /Secure/);
  assert.doesNotMatch(value, /Domain=/);
});

test("reads only the environment-appropriate capability cookie", () => {
  const deployed = new Request("https://demo.example.com/api", {
    headers: { Cookie: "other=1; __Host-alzs-demo-capability=secret%2Fvalue" },
  });
  const local = new Request("http://localhost:3000/api", {
    headers: { Cookie: "alzs-demo-capability=local-secret" },
  });
  assert.equal(readDemoCapabilityCookie(deployed), "secret/value");
  assert.equal(readDemoCapabilityCookie(local), "local-secret");
});

test("expires the same host cookie when the synthetic session is discarded", () => {
  const value = clearDemoCapabilityCookie("https://demo.example.com/api/v1/demo/sessions/one");
  assert.match(value, /^__Host-alzs-demo-capability=;/);
  assert.match(value, /Max-Age=0/);
  assert.match(value, /Secure/);
});
