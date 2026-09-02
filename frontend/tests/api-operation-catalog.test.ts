import assert from "node:assert/strict";
import test from "node:test";
import { ApiClientError } from "../lib/api.ts";
import {
  buildApiOperationPath,
  findApiOperation,
  invokeApiOperation,
} from "../lib/api-operation-client.ts";
import { API_OPERATION_CATALOG } from "../lib/generated/api-operation-catalog.ts";

test("문서와 코드의 전체 API operation을 중복 없이 분류한다", () => {
  assert.equal(API_OPERATION_CATALOG.length, 283);
  assert.equal(new Set(API_OPERATION_CATALOG.map(({ key }) => key)).size, 283);
  assert.equal(API_OPERATION_CATALOG.filter(({ implementation }) => implementation === "IMPLEMENTED").length, 238);
  assert.equal(API_OPERATION_CATALOG.filter(({ implementation }) => implementation === "PLANNED").length, 23);
  assert.equal(API_OPERATION_CATALOG.filter(({ implementation }) => implementation === "REFERENCE_ONLY").length, 22);
  assert.equal(API_OPERATION_CATALOG.every(({ externalActionAllowed }) => externalActionAllowed === false), true);
  for (const operation of API_OPERATION_CATALOG.filter(({ implementation }) => implementation === "IMPLEMENTED")) {
    const values = Object.fromEntries(operation.pathParameters.map((parameter) => [parameter, `${parameter}-test`]));
    assert.equal(findApiOperation(operation.key).key, operation.key);
    assert.doesNotMatch(buildApiOperationPath(operation, values), /\{[^}]+\}/);
  }
});

test("경로 매개변수와 검색 조건을 안전하게 조합한다", () => {
  const operation = findApiOperation("GET /api/v1/demo/sessions/{sessionId}/accounts/{accountId}/transactions");
  assert.equal(
    buildApiOperationPath(operation, { sessionId: "session/1", accountId: "account 2" }, { limit: 20, cursor: null }),
    "/api/v1/demo/sessions/session%2F1/accounts/account%202/transactions?limit=20",
  );
  assert.throws(
    () => buildApiOperationPath(operation, { sessionId: "session-1" }),
    (error: unknown) => error instanceof ApiClientError && /accountId/.test(error.message),
  );
});

test("미구현·외부 참고 operation은 브라우저 요청 전에 차단한다", async (t) => {
  let called = false;
  t.mock.method(globalThis, "fetch", async () => {
    called = true;
    return new Response();
  });
  const blocked = API_OPERATION_CATALOG.find(({ implementation }) => implementation !== "IMPLEMENTED");
  assert.ok(blocked);
  await assert.rejects(
    invokeApiOperation(blocked.key),
    (error: unknown) => error instanceof ApiClientError && error.kind === "parse",
  );
  assert.equal(called, false);
});

test("구현 operation은 공통 API 클라이언트와 Bearer 인증 계약을 사용한다", async (t) => {
  let authorization = "";
  t.mock.method(globalThis, "fetch", async (_input, init) => {
    authorization = new Headers(init?.headers).get("Authorization") ?? "";
    return new Response(JSON.stringify({
      success: true, status: 200, code: "OK", message: "ok", data: { ok: true }, errors: [],
      timestamp: "2026-08-30T00:00:00Z", traceId: "trace-operation",
    }), { status: 200, headers: { "content-type": "application/json" } });
  });
  const response = await invokeApiOperation<{ ok: boolean }>("GET /api/v1/customers/{customerId}", {
    path: { customerId: "customer-1" }, accessToken: "private-token",
  });
  assert.equal(response.body.data?.ok, true);
  assert.equal(authorization, "Bearer private-token");
});
