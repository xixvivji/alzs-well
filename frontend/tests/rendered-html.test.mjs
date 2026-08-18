import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

async function render() {
  const workerUrl = new URL("../dist/server/index.js", import.meta.url);
  workerUrl.searchParams.set("test", `${process.pid}-${Date.now()}`);
  const { default: worker } = await import(workerUrl.href);
  return worker.fetch(
    new Request("http://localhost/", { headers: { accept: "text/html" } }),
    { ASSETS: { fetch: async () => new Response("Not found", { status: 404 }) } },
    { waitUntil() {}, passThroughOnException() {} },
  );
}

test("server-renders the ALZ's well landing page", async () => {
  const response = await render();
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type") ?? "", /^text\/html\b/i);
  assert.match(response.headers.get("content-security-policy") ?? "", /default-src 'self'/);
  assert.equal(response.headers.get("referrer-policy"), "no-referrer");
  assert.equal(response.headers.get("x-content-type-options"), "nosniff");
  assert.equal(response.headers.get("x-frame-options"), "DENY");
  assert.match(response.headers.get("permissions-policy") ?? "", /camera=\(\)/);

  const html = await response.text();
  assert.match(html, /<html lang="ko">/i);
  assert.match(html, /ALZ(?:&#x27;|')s well \| 금융생활 변화 조기알림/);
  assert.match(html, /금융생활의 작은 변화/);
  assert.match(html, /안심 서비스 체험하기/);
  assert.match(html, /href="\/demo"/);
  assert.match(html, /href="\/staff\/cases"/);
  assert.match(html, /합성데이터만 사용하는 데모/);
  assert.doesNotMatch(html, /codex-preview|SkeletonPreview|Building your site/i);
});

test("keeps the safety boundary visible in product source", async () => {
  const [page, layout] = await Promise.all([
    readFile(new URL("../app/page.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/layout.tsx", import.meta.url), "utf8"),
  ]);

  assert.match(page, /실제 금융 실행이나 외부 연락을 수행하지 않습니다/);
  assert.match(page, /하나은행/);
  assert.match(page, /신한은행/);
  assert.match(page, /카카오뱅크/);
  assert.match(page, /KB증권/);
  assert.match(layout, /ALZ's well \| 금융생활 변화 조기알림/);
  assert.doesNotMatch(page, /codex-preview|SkeletonPreview|Building your site/i);
});
