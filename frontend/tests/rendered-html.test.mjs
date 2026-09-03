import assert from "node:assert/strict";
import { readFile, readdir } from "node:fs/promises";
import test from "node:test";

test("Vercel Next.js 빌드가 ALZ's well 첫 화면을 정적으로 렌더링한다", async () => {
  const html = await readFile(new URL("../.next/server/app/index.html", import.meta.url), "utf8");
  assert.match(html, /<html lang="ko"(?:\s[^>]*)?>/i);
  assert.match(html, /ALZ(?:&#x27;|')s well/);
  assert.match(html, /내 금융생활을 한눈에/);
  assert.match(html, /href="#home-main"/);
  assert.match(html, /합성데이터 전용 체험 서비스/);
  assert.match(html, /무엇을 하시겠어요/);
  assert.match(html, /금융생활 도움받기/);
  assert.match(html, /href="\/login\?next=\/banking\/accounts"/);
  assert.match(html, /href="\/login\?next=\/banking\/transfer"/);
  assert.match(html, /송금 전 확인/);
  assert.match(html, /href="\/help"/);
  assert.match(html, /href="\/help"/);
  assert.match(html, /href="\/staff\/login"/);
  assert.match(html, /합성데이터/);
  assert.doesNotMatch(html, /codex-preview|SkeletonPreview|Building your site/i);
});

test("Vercel BFF와 보안 헤더가 배포 구성에 포함된다", async () => {
  const [config, manifest, routesManifest] = await Promise.all([
    readFile(new URL("../next.config.ts", import.meta.url), "utf8"),
    readFile(new URL("../.next/server/app-paths-manifest.json", import.meta.url), "utf8"),
    readFile(new URL("../.next/routes-manifest.json", import.meta.url), "utf8"),
  ]);
  assert.match(config, /Content-Security-Policy/);
  assert.match(config, /frame-ancestors 'none'/);
  assert.match(config, /Permissions-Policy/);
  assert.doesNotMatch(routesManifest, /unsafe-eval/);
  assert.match(manifest, /\/api\/\[\.\.\.path\]\/route/);
  assert.match(manifest, /\/api\/internal\/staff-capability\/\[sessionId\]\/route/);
  for (const route of ["/api/member-auth/login/route", "/api/member-auth/refresh/route", "/api/member-auth/logout/route", "/login/page", "/help/page", "/banking/help/page", "/staff/login/page", "/_not-found/page"]) {
    assert.match(manifest, new RegExp(route.replaceAll("/", "\\/")));
  }
  for (const route of ["/demo/protection/page", "/demo/finance/page", "/demo/products/page", "/demo/settings/page", "/demo/services/page", "/staff/operations/page", "/staff/control-center/page", "/staff/system-status/page"]) {
    assert.match(manifest, new RegExp(route.replaceAll("/", "\\/")));
  }
});

test("고객·행원·관리자 금융 포털 화면이 정적으로 렌더링된다", async () => {
  const [demo, protection, products, settings, services, operations, control, systemStatus] = await Promise.all([
    readFile(new URL("../.next/server/app/demo.html", import.meta.url), "utf8"),
    readFile(new URL("../.next/server/app/demo/protection.html", import.meta.url), "utf8"),
    readFile(new URL("../.next/server/app/demo/products.html", import.meta.url), "utf8"),
    readFile(new URL("../.next/server/app/demo/settings.html", import.meta.url), "utf8"),
    readFile(new URL("../.next/server/app/demo/services.html", import.meta.url), "utf8"),
    readFile(new URL("../.next/server/app/staff/operations.html", import.meta.url), "utf8"),
    readFile(new URL("../.next/server/app/staff/control-center.html", import.meta.url), "utf8"),
    readFile(new URL("../.next/server/app/staff/system-status.html", import.meta.url), "utf8"),
  ]);
  assert.match(demo, /복잡한 금융생활/);
  assert.match(demo, /내 금융생활 한눈에 보기/);
  assert.match(demo, /큰 글씨와 쉬운 문장/);
  assert.match(demo, /서비스 확인용 메뉴/);
  assert.doesNotMatch(demo, /행원 화면으로/);
  assert.match(protection, /오늘 확인할 금융생활을/);
  assert.match(protection, /보호센터 안전 체험 시작/);
  assert.match(protection, /외부 금융 실행 0건/);
  assert.match(protection, /aria-label="모바일 고객 서비스"/);
  for (const href of ["/demo/protection", "/demo/finance", "/demo/ai-assistant", "/demo/alerts", "/demo/services"]) {
    assert.match(protection, new RegExp(`href="${href}"`));
  }
  assert.match(products, /합성 금융서비스 인증/);
  assert.match(products, /금융서비스 로그인/);
  assert.match(products, /href="\/demo\/products"/);
  assert.match(settings, /고객 보호 설정/);
  assert.match(settings, /본인 인증 후 관리합니다/);
  assert.match(settings, /href="\/demo\/settings"/);
  assert.match(services, /백엔드 서비스 연결 현황/);
  assert.match(services, /실제 금융업무 메뉴가 아닙니다/);
  assert.match(services, /전체 계약/);
  assert.match(services, /외부 참고/);
  assert.match(operations, /고객의 확인 요청을/);
  assert.match(operations, /로그인 회원 보호사건/);
  assert.match(operations, /aria-label="모바일 행원 서비스"/);
  for (const href of ["/staff/cases", "/staff/operations", "/staff/system-status"]) {
    assert.match(operations, new RegExp(`href="${href}"`));
  }
  assert.doesNotMatch(operations, /href="\/staff\/control-center"/);
  assert.match(control, /정책과 AI가 안전 경계 안에서/);
  assert.match(control, /관리 기능은 운영자 인증 후에만 실행/);
  assert.match(control, /href="\/staff\/control-center"/);
  assert.doesNotMatch(control, /href="\/staff\/operations"/);
  assert.doesNotMatch(control, /href="\/staff\/cases"/);
  assert.match(systemStatus, /서비스 준비상태를 확인하고 있습니다/);
  for (const html of [services, operations, control]) {
    assert.match(html, /사설 인증 필요|인증 필요/);
  }
});

test("제품 안전 경계는 유지하고 대회 기관 표기는 화면에서 제외한다", async () => {
  const [page, staffCaseDetail, staffCaseQueue, alertDetail, productCenter, customerAssets, customerCare, privateLifeServices, privateHelpHub, protectionCenter, scenarioData] = await Promise.all([
    readFile(new URL("../app/page.tsx", import.meta.url), "utf8"),
    readFile(new URL("../components/StaffCaseDetail.tsx", import.meta.url), "utf8"),
    readFile(new URL("../components/StaffCaseQueue.tsx", import.meta.url), "utf8"),
    readFile(new URL("../components/AlertDetail.tsx", import.meta.url), "utf8"),
    readFile(new URL("../components/PrivateProductCenter.tsx", import.meta.url), "utf8"),
    readFile(new URL("../components/PrivateCustomerAssets.tsx", import.meta.url), "utf8"),
    readFile(new URL("../components/PrivateCustomerCare.tsx", import.meta.url), "utf8"),
    readFile(new URL("../components/PrivateLifeServices.tsx", import.meta.url), "utf8"),
    readFile(new URL("../components/PrivateHelpHub.tsx", import.meta.url), "utf8"),
    readFile(new URL("../components/CustomerProtectionCenter.tsx", import.meta.url), "utf8"),
    readFile(new URL("../data/rehearsal-scenarios-v1.json", import.meta.url), "utf8"),
  ]);
  for (const name of ["금융보안원", "금융위원회", "하나은행", "신한은행", "카카오뱅크", "KB증권", "생명보험협회"]) assert.doesNotMatch(page, new RegExp(name));
  assert.doesNotMatch(page, /각 기관의 공식 서비스가 아닙니다/);
  assert.match(staffCaseDetail, /AI는 검토 초안과 승인된 근거를 제시할 뿐/);
  assert.match(staffCaseDetail, /사람 검토 필수/);
  assert.match(staffCaseDetail, /고객 응답과 근거 확인/);
  assert.match(staffCaseDetail, /행원이 최종 결정/);
  assert.match(staffCaseQueue, /먼저 확인할 사건부터/);
  assert.match(staffCaseQueue, /업무 우선순위는 위험도·치매·사기 확률이 아닙니다/);
  assert.match(staffCaseQueue, /다음 사건 20건 불러오기/);
  assert.match(staffCaseQueue, /직원 접근 권한을 확인하고 있습니다/);
  assert.match(alertDetail, /나중에 확인할게요/);
  assert.match(alertDetail, /readEvidenceSignals\(detail\?\.t0AlertEvidence\)/);
  assert.match(alertDetail, /알림이 발생한 이유/);
  assert.match(alertDetail, /평소 .*최근 .*로 확인됐습니다/);
  assert.match(alertDetail, /질병 진단이나 사기 판정이 아닙니다/);
  for (const label of ["예금", "외환", "연금·신탁", "동의관리"]) assert.match(productCenter, new RegExp(label));
  assert.match(customerAssets, /가입·해지·외부 호출 없음/);
  assert.match(customerAssets, /외부 제공 자동 실행 없음/);
  assert.match(customerAssets, /useState\(1_000_000\)/);
  assert.match(customerAssets, /min=\{product\?\.minPrincipal/);
  assert.match(customerAssets, /max=\{product\?\.maxPrincipal/);
  assert.match(customerCare, /사람의 재검토를 요청/);
  assert.match(customerCare, /금융행위 대리권은 부여하지 않습니다/);
  assert.match(privateLifeServices, /생활금융 정보를 안전하게 불러오고 있습니다/);
  assert.match(privateHelpHub, /도움 방식 정하기/);
  assert.match(privateHelpHub, /AI가 수치에서 확인한 내용/);
  assert.match(privateHelpHub, /나에게 물어볼 질문/);
  assert.match(privateHelpHub, /내 상황 답하기/);
  assert.match(protectionCenter, /결정은 언제나 고객과 사람에게 있습니다/);
  assert.match(protectionCenter, /진단하거나 거래를 자동으로 막지 않습니다/);
  assert.match(scenarioData, /같은 T0 합성 snapshot/);
  assert.match(scenarioData, /CLOSED_FALSE_POSITIVE/);
});

test("서버 전용 비밀값의 예시 placeholder가 Next.js 산출물에 직렬화되지 않는다", async () => {
  const files = await allFiles(new URL("../.next/", import.meta.url));
  for (const file of files) {
    const content = await readFile(file);
    assert.equal(content.includes(Buffer.from("replace-with-64-lowercase-hex")), false, `placeholder leaked into ${file.pathname}`);
  }
});

test("고객·행원·관리자 공통 접근성 계약을 유지한다", async () => {
  const [shell, bankingShell, controls, login, operationalLogin, staffQueue, roleDashboard, accessibilityCss] = await Promise.all([
    readFile(new URL("../components/AppShell.tsx", import.meta.url), "utf8"),
    readFile(new URL("../components/BankingShell.tsx", import.meta.url), "utf8"),
    readFile(new URL("../components/AccessibilityControls.tsx", import.meta.url), "utf8"),
    readFile(new URL("../components/MemberLogin.tsx", import.meta.url), "utf8"),
    readFile(new URL("../components/OperationalLogin.tsx", import.meta.url), "utf8"),
    readFile(new URL("../components/PrivateStaffCaseQueue.tsx", import.meta.url), "utf8"),
    readFile(new URL("../components/OperationalRoleDashboard.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/accessibility-redesign.css", import.meta.url), "utf8"),
  ]);
  for (const source of [shell, bankingShell]) {
    assert.match(source, /본문 바로가기/);
    assert.match(source, /aria-current/);
  }
  assert.match(shell, /관리자 서비스/);
  assert.match(controls, /role="group"/);
  assert.match(controls, /aria-live="polite"/);
  assert.match(login, /aria-busy=\{busy\}/);
  assert.match(login, /aria-describedby="member-id-help"/);
  assert.match(login, /금융서비스 홈으로/);
  assert.match(operationalLogin, /aria-busy=\{busy\}/);
  assert.match(operationalLogin, /aria-describedby="operator-id-help"/);
  assert.match(staffQueue, /aria-describedby="staff-note-help"/);
  assert.match(roleDashboard, /aria-busy="true"/);
  assert.match(accessibilityCss, /prefers-reduced-motion:reduce/);
  assert.match(accessibilityCss, /outline:4px solid var\(--focus\)/);
  assert.match(accessibilityCss, /min-height:44px/);
  assert.match(accessibilityCss, /\.bank-panel small/);
  assert.match(accessibilityCss, /@media\(max-width:1150px\)\{\.banking-mobile-nav\{display:flex/);
});

test("전역 오류와 잘못된 주소에서 사용자가 복구할 수 있다", async () => {
  const [errorPage, notFoundPage] = await Promise.all([
    readFile(new URL("../app/error.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/not-found.tsx", import.meta.url), "utf8"),
  ]);
  assert.match(errorPage, /role="alert"/);
  assert.match(errorPage, /다시 시도/);
  assert.match(errorPage, /처음 화면으로/);
  assert.doesNotMatch(errorPage, /error\.message/);
  assert.match(notFoundPage, /페이지를 찾을 수 없습니다/);
  assert.match(notFoundPage, /도움 안내 보기/);
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
