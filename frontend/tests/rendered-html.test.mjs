import assert from "node:assert/strict";
import { readFile, readdir } from "node:fs/promises";
import test from "node:test";

test("Vercel Next.js 빌드가 ALZ's well 첫 화면을 정적으로 렌더링한다", async () => {
  const html = await readFile(new URL("../.next/server/app/index.html", import.meta.url), "utf8");
  assert.match(html, /<html lang="ko"(?:\s[^>]*)?>/i);
  assert.match(html, /ALZ(?:&#x27;|')s well/);
  assert.match(html, /금융생활의 작은 변화/);
  assert.match(html, /2026 금융 AI Challenge 참가 프로젝트/);
  assert.match(html, /고객 흐름 체험하기/);
  assert.match(html, /href="\/demo"/);
  assert.match(html, /href="\/staff\/cases"/);
  assert.match(html, /합성데이터/);
  assert.doesNotMatch(html, /codex-preview|SkeletonPreview|Building your site/i);
});

test("Vercel BFF와 보안 헤더가 배포 구성에 포함된다", async () => {
  const [config, manifest] = await Promise.all([
    readFile(new URL("../next.config.ts", import.meta.url), "utf8"),
    readFile(new URL("../.next/server/app-paths-manifest.json", import.meta.url), "utf8"),
  ]);
  assert.match(config, /Content-Security-Policy/);
  assert.match(config, /frame-ancestors 'none'/);
  assert.match(config, /Permissions-Policy/);
  assert.match(manifest, /\/api\/\[\.\.\.path\]\/route/);
  assert.match(manifest, /\/api\/internal\/staff-capability\/\[sessionId\]\/route/);
  for (const route of ["/demo/finance/page", "/demo/services/page", "/staff/operations/page", "/staff/control-center/page"]) {
    assert.match(manifest, new RegExp(route.replaceAll("/", "\\/")));
  }
});

test("고객·행원·관리자 금융 포털 화면이 정적으로 렌더링된다", async () => {
  const [services, operations, control] = await Promise.all([
    readFile(new URL("../.next/server/app/demo/services.html", import.meta.url), "utf8"),
    readFile(new URL("../.next/server/app/staff/operations.html", import.meta.url), "utf8"),
    readFile(new URL("../.next/server/app/staff/control-center.html", import.meta.url), "utf8"),
  ]);
  assert.match(services, /필요한 금융생활을 한곳에서/);
  assert.match(services, /전체 계약/);
  assert.match(services, /외부 참고/);
  assert.match(operations, /보호업무 운영 포털/);
  assert.match(control, /통제와 운영을 한 화면에서/);
  for (const html of [services, operations, control]) {
    assert.match(html, /사설 인증 필요|인증 필요/);
  }
});

test("제품 안전 경계와 대회 참여 기관 표기가 화면 소스에 남는다", async () => {
  const [page, staffCaseDetail, alertDetail] = await Promise.all([
    readFile(new URL("../app/page.tsx", import.meta.url), "utf8"),
    readFile(new URL("../components/StaffCaseDetail.tsx", import.meta.url), "utf8"),
    readFile(new URL("../components/AlertDetail.tsx", import.meta.url), "utf8"),
  ]);
  for (const name of ["금융보안원", "금융위원회", "하나은행", "신한은행", "카카오뱅크", "KB증권", "생명보험협회"]) assert.match(page, new RegExp(name));
  assert.match(page, /각 기관의 공식 서비스가 아닙니다/);
  assert.match(staffCaseDetail, /AI는 검토 초안과 승인된 근거를 제시할 뿐/);
  assert.match(staffCaseDetail, /사람 검토 필수/);
  assert.match(alertDetail, /나중에 확인할게요/);
});

test("서버 전용 비밀값의 예시 placeholder가 Next.js 산출물에 직렬화되지 않는다", async () => {
  const files = await allFiles(new URL("../.next/", import.meta.url));
  for (const file of files) {
    const content = await readFile(file);
    assert.equal(content.includes(Buffer.from("replace-with-64-lowercase-hex")), false, `placeholder leaked into ${file.pathname}`);
  }
});

async function allFiles(directory) {
  const result = [];
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const target = new URL(entry.name + (entry.isDirectory() ? "/" : ""), directory);
    if (entry.isDirectory()) result.push(...await allFiles(target));
    else if (entry.isFile()) result.push(target);
  }
  return result;
}
