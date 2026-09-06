import assert from "node:assert/strict";
import test from "node:test";
import { evaluateTransfer, loadBankingOverview, loadRecurringInsight, loadStatementDetail, loadTransactionInsight, loadTransferWorkspace, updateRecurringReminder, updateTransactionCategory, updateTransactionNote } from "../lib/private-banking";
import { loadLifeServices, searchKnowledge } from "../lib/private-life-services";
import { loadSafetyCenter, respondToSafetyAlert } from "../lib/private-safety-center";
import { loadAdminOperations, loadStaffOperations } from "../lib/operational-portal";
import { loadOperationalCaseBundle, loadOperationalCaseQueue, startOperationalCaseReview } from "../lib/private-staff-cases";
import { loadPrivateHelpOverview, loadPrivateLongitudinalAnalysis } from "../lib/private-help";
import type { PrivateCustomerSession } from "../lib/private-financial-products";
import { deferSafetyAlert, type SafetyCenterBundle } from "../lib/private-safety-center";
import { approveCaseGuidance, completeCaseReview, finishCaseFollowUp, loadCaseCustomerIntent, loadOperationalCasePage, scheduleCaseFollowUp, type OperationalCaseSummary } from "../lib/private-staff-cases";
import { caseIdFromSearch, safePortalNext, staffLoginPath } from "../lib/prototype-navigation";
import { adminLinks, canOpenStaffPage, loginDestination, portalHome, staffLinks, staffRoleFor } from "../lib/portal-access";
import { accountDisplayName, customerLabel, evidenceDescription } from "../lib/presentation-copy";
import { loadServiceAvailability, loadServiceMetadata } from "../lib/system-status";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { OperationalCaseReview } from "../components/OperationalCaseReview";
import { reviewEventLabel, reviewNextTask, reviewPrompt } from "../lib/staff-review-presentation";
import type { OperationalCaseBundle } from "../lib/private-staff-cases";

const reviewFixture: OperationalCaseBundle = {
  detail: { caseSummary: { caseId: "case-1", alertId: "alert-1", signalId: "signal-1", customerId: "customer-1", reviewPriority: "HIGH", taskStatus: "IN_REVIEW", version: 3, assignedTeam: "TEAM", assignedTo: "staff-1", createdAt: "2026-09-01T00:00:00Z", updatedAt: "2026-09-02T00:00:00Z" }, customerResponseCode: "UNRECOGNIZED", reasonCode: "MISSED_RECURRING_PAYMENT", alertState: "BANK_REVIEW", selectedActionCodes: [], guidancePlanId: null },
  evidence: { baselineValue: "0", currentValue: "1", unit: "COUNT", items: [{ evidenceId: "evidence-1", description: "예정된 정기납부가 확인되지 않은 합성 근거입니다.", occurredAt: "2026-09-01T00:00:00Z", amount: null, currency: null, sourceReference: "source-1" }] },
  notes: [], followUps: [], timeline: [{ eventType: "CASE_CREATED", summary: "운영형 행원 사건 생성", occurredAt: "2026-09-01T00:00:00Z", previousState: null, resultingState: "PENDING" }],
};
const renderReview = (bundle: OperationalCaseBundle, principalId = "staff-1") => renderToStaticMarkup(createElement(OperationalCaseReview, { bundle, session: { principalId, roles: ["PROTECTION_STAFF"] } as PrivateCustomerSession, busy: false, runCommand: async () => false }));

test("고객 내용과 행원 작성 영역은 서로 다른 배경 클래스를 사용하며 참고자료와 섞지 않는다", () => {
  const bundle = structuredClone(reviewFixture);
  bundle.notes = [{ noteId: "note-1", noteText: "행원이 확인한 내용", createdAt: "2026-09-02T00:00:00Z", createdBy: "staff-1" }];
  const html = renderReview(bundle);
  assert.match(html, /class="case-customer-source"[^>]*data-origin="customer"/);
  assert.match(html, /class="case-section case-customer-source"[^>]*data-origin="customer"/);
  assert.match(html, /class="case-section case-staff-source"[^>]*aria-labelledby="case-decision-title"/);
  assert.match(html, /class="case-section case-staff-source"[^>]*aria-labelledby="case-followup-title"/);
  assert.match(html, /class="case-system-source"[^>]*data-origin="system"/);
  assert.match(html, /내가 작성<!-- --> · 저장됨|내가 작성 · 저장됨/);
  assert.doesNotMatch(html, /case-data-origin|origin-customer|origin-staff/);
});

test("행원 작업 영역은 세 탭으로 나누고 고객 응답·수치를 공통 요약으로 유지한다", () => {
  const html = renderReview(reviewFixture);
  assert.equal((html.match(/role="tab"/g) ?? []).length, 3);
  assert.equal((html.match(/role="tabpanel"/g) ?? []).length, 3);
  assert.match(html, /id="case-tab-facts"[^>]*aria-selected="true"/);
  assert.match(html, /id="case-area-decision"[^>]*hidden=""/);
  assert.ok(html.indexOf("고객이 남긴 응답") < html.indexOf('role="tablist"'));
  assert.match(html, /제가 모르는 변화예요/);
  assert.match(html, /예정된 정기납부 내역이 확인되지 않았습니다/);
  assert.match(html, /공유 의향 확인/);
  assert.doesNotMatch(html, /합성 근거|API가 제공|백엔드의 결정론적|prototype-role-switch/);
});

test("검토 질문은 사건 사유에 맞는 백엔드 기본 질문만 표시한다", () => {
  assert.match(renderReview(reviewFixture), /최근 정기납부가 처리되지 않은 이유/);
  assert.doesNotMatch(renderReview(reviewFixture), /같은 금액을 두 번 송금한 사유/);
  assert.match(reviewPrompt("DUPLICATE_TRANSFER")!.question, /같은 금액을 두 번/);
  assert.match(reviewPrompt("REPEATED_CONFIRMATION")!.check, /결과화면 지연/);
  assert.equal(reviewPrompt("UNKNOWN_REASON"), null);
  assert.equal(reviewEventLabel("CASE_CREATED", "원문"), "은행 검토 접수");
  assert.equal(reviewEventLabel("UNKNOWN", "원문"), "원문");
});

test("검토 대기·타 담당 사건에는 승인·종결·후속 등록 폼을 열지 않는다", () => {
  const pending = structuredClone(reviewFixture); pending.detail.caseSummary.taskStatus = "PENDING";
  assert.match(renderReview(pending), />검토 시작<\/button>/);
  assert.doesNotMatch(renderReview(pending), /class="case-guidance-form"|class="case-close-form"|case-new-schedule/);
  const other = renderReview(reviewFixture, "other-staff");
  assert.match(other, /다른 행원이 담당/);
  assert.doesNotMatch(other, /class="case-guidance-form"|class="case-close-form"|case-new-schedule|>내부 메모 저장<\/button>/);
});

test("안내 승인 후에는 기존 안내와 종결 입력, 종결 후에는 후속관리만 제공한다", () => {
  const approved = structuredClone(reviewFixture); approved.detail.caseSummary.taskStatus = "GUIDANCE_APPROVED"; approved.detail.guidancePlanId = "plan-1"; approved.detail.selectedActionCodes = ["BRANCH_CONSULTATION"];
  const html = renderReview(approved);
  assert.match(html, /승인된 안내계획/); assert.match(html, /영업점 상담 안내/);
  assert.match(html, /class="case-close-form"/); assert.doesNotMatch(html, /class="case-guidance-form"/);
  approved.detail.caseSummary.taskStatus = "COMPLETED";
  const completed = renderReview(approved);
  assert.doesNotMatch(completed, /class="case-close-form"|class="case-guidance-form"/);
  assert.match(completed, /새 후속 일정 등록/);
  assert.match(reviewNextTask("COMPLETED"), /검토가 종결/);
});

test("각 예정 일정 바로 아래에 결과 입력을 배치하고 완료 일정에는 입력을 표시하지 않는다", () => {
  const bundle = structuredClone(reviewFixture);
  bundle.followUps = ["SCHEDULED", "SCHEDULED", "COMPLETED"].map((status, index) => ({ followUpId: `follow-${index}`, purpose: `일정 ${index}`, status, scheduledAt: "2026-09-07T00:00:00Z", outcome: null, version: 1 }));
  const html = renderReview(bundle);
  assert.equal((html.match(/확인 결과 또는 취소 사유 \(500자 이내\)/g) ?? []).length, 2);
  assert.equal((html.match(/>완료 기록<\/button>/g) ?? []).length, 2);
  assert.match(html, /예정 2건/);
});

test("서비스 상태는 전용 core·AI 준비상태만 먼저 읽고 버전은 별도 조회한다", async (t) => {
  const calls: string[] = [];
  t.mock.method(globalThis, "fetch", async (input) => {
    const path = String(input); calls.push(path);
    if (path.endsWith("/core-readiness")) return response({ ready: true, status: "READY", checks: { database: "UP" } });
    if (path.endsWith("/ai-readiness")) return response({ ready: false, status: "NOT_READY", checks: { aiRetrieval: "DOWN" } });
    return response({ featureFlags: { templateFallbackEnabled: true } });
  });
  const result = await loadServiceAvailability();
  assert.equal(result.coreReadiness.ready, true);
  assert.equal(result.aiReadiness.ready, false);
  assert.deepEqual(calls, ["/api/v1/system/core-readiness", "/api/v1/system/ai-readiness", "/api/v1/system/public-config"]);
  await loadServiceMetadata();
  assert.deepEqual(calls.slice(3), ["/api/v1/system/health", "/api/v1/system/versions"]);
});

test("로그인 유형이 아닌 서버 역할로 시작 화면과 허용 목적지를 결정한다", () => {
  assert.equal(loginDestination(["CUSTOMER"], "/staff/control-center"), "/banking");
  assert.equal(loginDestination(["PROTECTION_STAFF"], "/banking/help"), "/staff/cases");
  assert.equal(loginDestination(["DETECTION_ADMIN"], "/staff/cases"), "/staff/control-center");
  assert.equal(loginDestination(["PROTECTION_STAFF"], "/staff/operations"), "/staff/operations");
  for (const roles of [["PROTECTION_STAFF"], ["DETECTION_ADMIN"]]) {
    assert.equal(loginDestination(roles, "/staff/system-status"), "/staff/system-status");
    for (const invalid of ["//evil.example", "/\\evil.example", "https://evil.example", "/staff/../banking"]) assert.equal(loginDestination(roles, invalid), portalHome(roles));
  }
  const caseId = "11111111-1111-4111-8111-111111111111";
  assert.equal(loginDestination(["PROTECTION_STAFF"], `/staff/cases?caseId=${caseId}`), `/staff/cases?caseId=${caseId}`);
  assert.equal(loginDestination(["CUSTOMER"], "/banking/help?alertId=one"), "/banking/help?alertId=one");
  assert.equal(loginDestination([], "/staff/cases"), "/");
});

test("공용 상태 페이지에서도 행원·관리자 메뉴를 유지하며 역할을 우회하지 않는다", () => {
  assert.equal(staffRoleFor(["PROTECTION_STAFF"]), "protection");
  assert.equal(staffRoleFor(["DETECTION_ADMIN"]), "admin");
  assert.equal(staffRoleFor(["CUSTOMER"]), null);
  assert.deepEqual(staffLinks.map(([path]) => path), ["/staff/operations", "/staff/cases", "/staff/system-status"]);
  assert.deepEqual(adminLinks.map(([path]) => path), ["/staff/control-center", "/staff/system-status"]);
  assert.equal(canOpenStaffPage(["CUSTOMER"], "protection"), false);
  assert.equal(canOpenStaffPage(["DETECTION_ADMIN"], "protection"), false);
  assert.equal(canOpenStaffPage(["PROTECTION_STAFF"], "admin"), false);
  assert.equal(canOpenStaffPage(["PROTECTION_STAFF"], "protection"), true);
  assert.equal(canOpenStaffPage([], "shared"), true);
});

test("업무용 문구는 알려진 시드 설명만 정리하며 근거·식별자를 조작하지 않는다", () => {
  assert.equal(evidenceDescription("예정된 정기납부가 확인되지 않은 합성 근거입니다."), "예정된 정기납부 내역이 확인되지 않았습니다.");
  assert.equal(evidenceDescription("같은 거래 결과를 반복 확인한 합성 상호작용 근거입니다."), "같은 거래 결과를 여러 번 확인한 기록이 있습니다.");
  assert.equal(evidenceDescription("9월 4일 50,000원 송금 실패"), "9월 4일 50,000원 송금 실패");
  assert.equal(customerLabel("SYN_V3_PUBLIC_4393bb3d_000001"), "demo001");
  assert.equal(customerLabel("customer-unknown"), "customer-unknown");
  assert.equal(accountDisplayName("합성 보호업무 행원 01"), "행원 01");
  assert.equal(accountDisplayName("고객이 설정한 이름"), "고객이 설정한 이름");
});

const session: PrivateCustomerSession = { principalId: "00000000-0000-0000-0000-000000000001", customerId: "customer-1", displayName: "합성고객", roles: ["CUSTOMER"], permissions: [] };
const envelope = (data: unknown) => JSON.stringify({ success: true, status: 200, code: "OK", message: "ok", data, errors: [], timestamp: "2026-09-01T00:00:00Z", traceId: "trace" });
const response = (data: unknown) => new Response(envelope(data), { headers: { "content-type": "application/json" } });

test("역할 전환은 로컬 허용 경로와 사건 식별자만 보존한다", () => {
  const id = "11111111-1111-4111-8111-111111111111";
  assert.equal(caseIdFromSearch(`?caseId=${id}`), id);
  assert.equal(caseIdFromSearch("?caseId=invalid"), null);
  assert.equal(safePortalNext(`/staff/cases?caseId=${id}`, "staff"), `/staff/cases?caseId=${id}`);
  assert.equal(safePortalNext("/staff/control-center", "staff"), "/staff/cases");
  for (const path of ["//evil.example", "/\\evil.example", "/banking-evil", "https://evil.example"]) assert.equal(safePortalNext(path, "customer"), "/banking/help");
  assert.equal(safePortalNext("/banking/help?alertId=one#help-context", "customer"), "/banking/help?alertId=one#help-context");
  assert.equal(staffLoginPath(id), `/staff/login?next=${encodeURIComponent(`/staff/cases?caseId=${id}`)}`);
});

test("통합 도움은 선택 알림의 signalId·baselineId로만 근거를 묶고 목록을 재요청하지 않는다", async (t) => {
  const calls: string[] = [];
  const lists = {
    baselines: [{ baselineId: "wrong" }, { baselineId: "right-baseline" }],
    signals: [{ signalId: "wrong", baselineId: "wrong" }, { signalId: "right-signal", baselineId: "right-baseline" }],
    alerts: [{ alertId: "chosen", signalId: "right-signal", state: "AWAITING_CONTEXT" }],
  } as Pick<SafetyCenterBundle, "baselines" | "signals" | "alerts">;
  t.mock.method(globalThis, "fetch", async (input) => {
    const path = String(input); calls.push(path);
    if (path.endsWith("/chosen")) return response({ alert: lists.alerts[0] });
    if (path.endsWith("/context-options")) return response({ question: "알고 있는 활동인가요?", options: [] });
    return response({ items: [] });
  });
  const result = await loadSafetyCenter(session, "chosen", undefined, lists);
  assert.equal(result.selectedAlert?.signalId, "right-signal");
  assert.equal(calls.length, 5);
  assert.ok(calls.includes("/api/v1/signals/right-signal/evidence"));
  assert.ok(calls.includes("/api/v1/customers/customer-1/baselines/right-baseline/features"));
  assert.ok(calls.every((path) => !path.includes("wrong")));
  await assert.rejects(() => loadSafetyCenter(session, "not-owned", undefined, lists), /찾을 수 없습니다/);
  assert.equal(calls.length, 5);
});

test("나중에 확인은 하루 뒤까지 유예하고 사건 생성 명령을 보내지 않는다", async (t) => {
  const calls: { path: string; body: Record<string, unknown> }[] = [];
  t.mock.method(globalThis, "fetch", async (input, init) => { calls.push({ path: String(input), body: JSON.parse(String(init?.body)) }); return response({ currentState: "DEFERRED" }); });
  await deferSafetyAlert(session, { alertId: "alert-1", version: 4 } as SafetyCenterBundle["alerts"][number]);
  assert.equal(calls.length, 1); assert.equal(calls[0].path, "/api/v1/alerts/alert-1/defer");
  assert.equal(calls[0].body.expectedVersion, 4);
  assert.ok(Math.abs(new Date(String(calls[0].body.deferredUntil)).getTime() - Date.now() - 86400000) < 2000);
});

test("행원 안내·종결·후속관리는 기존 API의 버전과 안전한 명령 계약을 따른다", async (t) => {
  const calls: { path: string; method: string; body: Record<string, unknown> }[] = [];
  t.mock.method(globalThis, "fetch", async (input, init) => {
    calls.push({ path: String(input), method: init?.method ?? "GET", body: JSON.parse(String(init?.body ?? "{}")) }); return response({});
  });
  const staff = { ...session, roles: ["PROTECTION_STAFF"] };
  const item = { caseId: "case-1", version: 7 } as OperationalCaseSummary;
  await approveCaseGuidance(staff, item, ["BRANCH_CONSULTATION"]);
  await completeCaseReview(staff, item, "고객이 설명을 이해했는지 확인함");
  await scheduleCaseFollowUp(staff, item, "2026-09-09T00:00:00Z", "추가 확인");
  await finishCaseFollowUp(staff, { followUpId: "follow-1", version: 2 } as Parameters<typeof finishCaseFollowUp>[1], "COMPLETE", "확인 완료");
  assert.deepEqual(calls.map((call) => call.method), ["POST", "POST", "POST", "PATCH"]);
  assert.equal(calls[0].body.expectedVersion, 7);
  assert.equal(calls[1].body.actionCode, "COMPLETE_REVIEW");
  assert.equal(calls[2].body.expectedCaseVersion, 7);
  assert.equal(calls[3].body.expectedVersion, 2);
  assert.equal(calls[3].path, "/api/v1/staff/follow-ups/follow-1");
  await assert.rejects(() => approveCaseGuidance(session, item, ["BRANCH_CONSULTATION"]), /권한/);
  assert.equal(calls.length, 4);
});

test("행원은 고객 의향 원문이 아닌 공유 동의 요약만 요청하며 사건 목록은 cursor를 유지한다", async (t) => {
  const paths: string[] = [];
  t.mock.method(globalThis, "fetch", async (input) => {
    paths.push(String(input));
    return String(input).includes("financial-intent-summary") ? response({ intentId: "approved", explanationMode: "SIMPLE_TEXT", paymentContinuity: null, helpCondition: null, sharedScopes: ["EXPLANATION_PREFERENCE"], nonConsentedFieldsExcluded: true }) : response({ items: [], nextCursor: "cursor-2" });
  });
  const staff = { ...session, roles: ["PROTECTION_STAFF"] };
  const intent = await loadCaseCustomerIntent(staff, "customer-1");
  assert.equal(intent?.intentId, "approved");
  assert.equal(intent?.paymentContinuity, null);
  assert.equal(paths[0], "/api/v1/staff/customers/customer-1/financial-intent-summary");
  assert.equal((await loadOperationalCasePage(staff, "cursor-1")).nextCursor, "cursor-2");
  assert.ok(paths[1].includes("cursor=cursor-1"));
});

test("공유 의향의 명세상 없음만 빈 상태이며 권한 거부는 숨기지 않는다", async (t) => {
  let status = 404;
  t.mock.method(globalThis, "fetch", async () => new Response(JSON.stringify({ success: false, status, code: status === 404 ? "FINANCIAL_INTENT_NOT_FOUND" : "FORBIDDEN", message: "조회 불가", data: null, errors: [] }), { status, headers: { "content-type": "application/json" } }));
  const staff = { ...session, roles: ["PROTECTION_STAFF"] };
  assert.equal(await loadCaseCustomerIntent(staff, "customer-1"), null);
  status = 403;
  await assert.rejects(() => loadCaseCustomerIntent(staff, "customer-1"), /조회 불가/);
});

test("회원 통합금융 화면은 8개 운영 조회 API를 HttpOnly BFF로 연결한다", async (t) => {
  const paths: string[] = [];
  t.mock.method(globalThis, "fetch", async (input, init) => {
    const path = String(input); paths.push(path);
    assert.equal(new Headers(init?.headers).get("Authorization"), null);
    if (path.endsWith("/financial-summary")) return response({ totalAssets: 100, totalLiabilities: 20, netAssets: 80, periodInflow: 10, periodOutflow: 4, netCashflow: 6, accountCount: 2, liabilityCount: 1, currency: "KRW", dataAsOf: "2026-08-31", syntheticData: true });
    if (path.endsWith("/cashflow-summary")) return response({ totalInflow: 10, totalOutflow: 4, netCashflow: 6, categories: [] });
    if (path.endsWith("/expense-summary")) return response({ totalExpense: 4, items: [] });
    return response({ items: [] });
  });
  const overview = await loadBankingOverview(session);
  assert.equal(overview.summary.netAssets, 80);
  assert.equal(paths.length, 8);
  assert.ok(paths.every((path) => path.startsWith("/api/v1/")));
});

test("로그인 도움 허브는 같은 회원의 의향·알림·기준선·신호·확인 API를 연결한다", async (t) => {
  const paths: string[] = [];
  t.mock.method(globalThis, "fetch", async (input) => {
    const path = String(input); paths.push(path);
    if (path.endsWith("/continuity-preparation")) return response({ readiness: "READY", latestApproved: null, legalDisclaimerRequired: true });
    return response({ items: [] });
  });
  const overview = await loadPrivateHelpOverview(session);
  assert.equal(overview.preparation.readiness, "READY");
  assert.equal(paths.length, 6);
  assert.ok(paths.every((path) => path.includes("customer-1")));
  assert.ok(paths.some((path) => path.endsWith("/baselines")));
  assert.ok(paths.some((path) => path.endsWith("/alerts")));
});

test("회원 장기 변화는 같은 customerId의 30·60·90일 서버 분석만 요청한다", async (t) => {
  let method = "";
  t.mock.method(globalThis, "fetch", async (input, init) => {
    assert.ok(String(input).endsWith("/customers/customer-1/ai-financial-assistance/change-analysis"));
    method = init?.method ?? "GET";
    return response({ baselineDays: 60, recentDays: 30, analysisWindowDays: 90, changes: [], windowComparisons: [{ baselineDays: 30, recentDays: 30, changes: [] }, { baselineDays: 60, recentDays: 30, changes: [] }, { baselineDays: 90, recentDays: 30, changes: [] }], summary: "최근 변화가 없습니다.", confirmationQuestions: ["최근 이용 방식을 바꾸셨나요?"], reviewChecklist: ["표시된 값을 확인합니다."], guidanceMode: "EXPLAINABLE_CHANGE_GUIDANCE_V1", analysisMode: "FASTAPI_EWMA_CUSUM", fallbackUsed: false, syntheticData: true, diagnosisInferred: false, financialActionExecuted: false });
  });

  const analysis = await loadPrivateLongitudinalAnalysis(session);

  assert.equal(method, "POST");
  assert.deepEqual(analysis.windowComparisons.map((item) => item.baselineDays), [30, 60, 90]);
  assert.equal(analysis.guidanceMode, "EXPLAINABLE_CHANGE_GUIDANCE_V1");
  assert.equal(analysis.confirmationQuestions.length, 1);
});

test("이체 화면은 회원별 계좌·수취인·한도·양식과 실행 없는 사전검증만 사용한다", async (t) => {
  const calls: Array<{ path: string; body?: string }> = [];
  t.mock.method(globalThis, "fetch", async (input, init) => {
    const path = String(input); calls.push({ path, body: init?.body?.toString() });
    if (path.endsWith("/accounts")) return response({ items: [{ accountId: "account-1" }] });
    if (path.endsWith("/beneficiaries")) return response({ items: [{ beneficiaryId: "beneficiary-1" }] });
    if (path.endsWith("/transfer-limits")) return response({ perTransferLimit: 5000000, dailyLimit: 10000000, dailyUsedAmount: 0, dailyRemainingAmount: 10000000, currency: "KRW", dataAsOf: "2026-08-31" });
    if (path.endsWith("/transfer-templates")) return response({ items: [] });
    return response({ outcomeCode: "ALLOW", allowed: true, decisionCode: "ALLOW", checks: [], transferCreated: false, authorizationCreated: false });
  });
  const workspace = await loadTransferWorkspace(session);
  const result = await evaluateTransfer(session, "account-1", "beneficiary-1", 100000, "LIVING_EXPENSE");
  assert.equal(workspace.limit.dailyRemainingAmount, 10000000);
  assert.equal(result.simulation.transferCreated, false);
  assert.equal(result.validation.authorizationCreated, false);
  assert.ok(calls.some((call) => call.path === "/api/v1/transfer-simulations"));
  assert.ok(calls.some((call) => call.path === "/api/v1/transfer-validations"));
});

test("계좌 화면은 거래·거래처·정기납부·명세서 상세를 고객 요청 시 조회한다", async (t) => {
  const paths: string[] = [];
  t.mock.method(globalThis, "fetch", async (input) => {
    const path = String(input); paths.push(path);
    if (path.endsWith("/enrichment")) return response({ normalizedDescription: "공과금", inferredCategory: "UTILITIES", effectiveCategory: "UTILITIES", recurringCandidate: true, newCounterparty: false, confidence: 0.95, reasonCodes: ["RECURRING_PATTERN"], deterministic: true });
    if (path.includes("/transaction-history")) return response({ items: [] });
    if (path.includes("/transactions/transaction-1")) return response({ transaction: { transactionId: "transaction-1" }, originalDescriptionAvailable: false, cancellationAvailable: false, correctionAvailable: true });
    if (path.endsWith("/occurrences")) return response({ items: [] });
    if (path.includes("/recurring-payments/")) return response({ payment: { recurringPaymentId: "recurring-1" }, latestOccurrence: null, cancellationAvailable: false, externalActionExecuted: false });
    return response({ accountId: "account-1", statement: { statementId: "statement-1" }, transactionRowsIncluded: false, externalDownloadAvailable: false });
  });
  const transaction = { transactionId: "transaction-1", accountId: "account-1", accountDisplayName: "생활비", institutionName: "합성은행", counterpartyId: "counterparty-1", counterpartyName: "공과금", occurredAt: "2026-08-31T00:00:00Z", direction: "DEBIT", transactionType: "TRANSFER", status: "POSTED", amount: 10000, currency: "KRW", balanceAfter: 100000, description: "공과금", category: "UTILITIES", preferenceVersion: 1 };
  const insight = await loadTransactionInsight(session, transaction);
  await loadRecurringInsight(session, "recurring-1");
  await loadStatementDetail(session, "account-1", "statement-1");
  assert.equal(insight.enrichment.deterministic, true);
  assert.equal(paths.length, 6);
  assert.ok(paths.includes("/api/v1/transactions/transaction-1/enrichment"));
  assert.ok(paths.includes("/api/v1/counterparties/counterparty-1/transaction-history?limit=10"));
  assert.ok(paths.includes("/api/v1/accounts/account-1/statements/statement-1"));
});

test("거래 분류·기억 메모·납부 알림은 버전과 멱등키를 가진 회원 수정 API를 사용한다", async (t) => {
  const calls: Array<{ path: string; method?: string; body?: string; idempotency?: string | null }> = [];
  t.mock.method(globalThis, "fetch", async (input, init) => {
    calls.push({ path: String(input), method: init?.method, body: init?.body?.toString(), idempotency: new Headers(init?.headers).get("Idempotency-Key") });
    return response({ rowVersion: 2 });
  });
  const transaction = { transactionId: "transaction-1", accountId: "account-1", accountDisplayName: "생활비", institutionName: "합성은행", occurredAt: "2026-08-31T00:00:00Z", direction: "DEBIT", transactionType: "TRANSFER", status: "POSTED", amount: 10000, currency: "KRW", balanceAfter: 100000, description: "공과금", category: "UTILITIES", preferenceVersion: 3 };
  const payment = { recurringPaymentId: "payment-1", institutionName: "합성은행", displayName: "통신비", paymentType: "AUTOPAY", categoryCode: "COMMUNICATION", cadence: "MONTHLY", expectedAmount: 50000, currency: "KRW", nextExpectedDate: "2026-09-10", status: "ACTIVE", observationStatus: "ON_TRACK", version: 4, reminderSettings: { enabled: true, leadDays: 2, channels: ["IN_APP"] } };
  await updateTransactionCategory(session, transaction, "FINANCE");
  await updateTransactionNote(session, transaction, "정기 생활비");
  await updateRecurringReminder(session, payment, false, 1);
  assert.equal(calls.length, 3);
  assert.ok(calls.every((call) => call.method === "PUT" && Boolean(call.idempotency)));
  assert.match(calls[0]?.body ?? "", /"expectedVersion":3/);
  assert.match(calls[1]?.body ?? "", /"note":"정기 생활비"/);
  assert.match(calls[2]?.body ?? "", /"expectedVersion":4/);
});

test("생활금융 화면은 의향·알림·연결·보호·근거·세션 API를 한 회원 범위로 조회한다", async (t) => {
  const paths: string[] = [];
  t.mock.method(globalThis, "fetch", async (input) => {
    const path = String(input); paths.push(path);
    if (path.endsWith("/continuity-preparation")) return response({ readiness: "READY", latestApproved: null, legalDisclaimerRequired: true });
    if (path.endsWith("/notification-preferences")) return response({ changeAlertEnabled: true, followUpEnabled: true, serviceNoticeEnabled: true, version: 1, externalDeliveryEnabled: false });
    if (path.endsWith("/knowledge/search")) return response({ items: [{ passage: { passageId: "passage-1", heading: "확인", content: "공식 근거", citationLabel: "근거 1", sourceUrl: "https://example.invalid" }, matchedKeywordCount: 1, retrievalMode: "DETERMINISTIC" }] });
    return response({ items: [] });
  });
  const bundle = await loadLifeServices(session);
  const hits = await searchKnowledge(session, "정기납부 확인");
  assert.equal(bundle.preparation.readiness, "READY");
  assert.equal(hits[0]?.passage.heading, "확인");
  assert.equal(paths.length, 13);
  assert.ok(paths.filter((path) => path.includes("customer-1")).length >= 6);
});

test("안심관리 화면은 기준선·변화신호·고객 확인·감사이력을 회원 범위로 연결한다", async (t) => {
  const calls: Array<{ path: string; method: string; body?: string; signal?: AbortSignal | null }> = [];
  t.mock.method(globalThis, "fetch", async (input, init) => {
    const path = String(input); calls.push({ path, method: init?.method ?? "GET", body: init?.body?.toString(), signal: init?.signal });
    if (path.endsWith("/baselines")) return response({ items: [{ baselineId: "baseline-1", featureCode: "REPEATED_CONFIRMATION", baselineValue: "2", currentValue: "8", unit: "COUNT", readiness: "READY", comparisonText: "월 2회에서 8회", algorithmVersion: "v1", calculatedAt: "2026-08-31T00:00:00Z", version: 1 }] });
    if (path.endsWith("/signals")) return response({ items: [{ signalId: "signal-1", baselineId: "baseline-1", signalType: "REPEATED_CONFIRMATION", severity: "MEDIUM", baselineValue: "2", currentValue: "8", unit: "COUNT", reasonCode: "REPEATED_CONFIRMATION", status: "OPEN", algorithmVersion: "v1", detectedAt: "2026-08-31T00:00:00Z" }] });
    if (path.endsWith("/alerts")) return response({ items: [{ alertId: "alert-1", signalId: "signal-1", state: "AWAITING_CONTEXT", severity: "MEDIUM", reasonCode: "REPEATED_CONFIRMATION", version: 1, deferredUntil: null, createdAt: "2026-08-31T00:00:00Z", updatedAt: "2026-08-31T00:00:00Z" }] });
    if (path.endsWith("/features")) return response({ items: [] });
    if (path.endsWith("/evidence")) return response({ items: [] });
    if (path.endsWith("/context-options")) return response({ question: "이 활동을 알고 계신가요?", options: [{ responseCode: "NOT_SURE", label: "잘 모르겠어요", description: "사람의 확인을 요청합니다." }] });
    if (path.endsWith("/audit")) return response({ items: [] });
    if (path.endsWith("/context-responses")) return response({ alertId: "alert-1", currentState: "BANK_REVIEW", version: 2 });
    return response({ alert: { alertId: "alert-1", signalId: "signal-1", state: "AWAITING_CONTEXT", severity: "MEDIUM", reasonCode: "REPEATED_CONFIRMATION", version: 1 } });
  });
  const controller = new AbortController();
  const bundle = await loadSafetyCenter(session, undefined, controller.signal);
  assert.equal(bundle.contextOptions[0]?.responseCode, "NOT_SURE");
  assert.equal(calls.length, 8);
  await respondToSafetyAlert(session, bundle.selectedAlert!, "NOT_SURE");
  const mutation = calls.find((call) => call.path.endsWith("/context-responses"));
  assert.equal(mutation?.method, "POST");
  assert.match(mutation?.body ?? "", /"expectedVersion":1/);
  assert.ok(calls.every((call) => call.path.startsWith("/api/v1/")));
  assert.ok(calls.slice(0, 8).every((call) => call.signal instanceof AbortSignal));
});

test("보호업무와 관리자는 서로 다른 Bearer 역할의 운영 조회 API만 사용한다", async (t) => {
  const paths: string[] = [];
  t.mock.method(globalThis, "fetch", async (input) => { paths.push(String(input)); return response({ items: [] }); });
  const staff = { ...session, roles: ["PROTECTION_STAFF"] };
  const admin = { ...session, roles: ["DETECTION_ADMIN"], permissions: ["AUDIT_READ_ALL"] };
  await loadStaffOperations(staff);
  await loadAdminOperations(admin);
  assert.ok(paths.includes("/api/v1/staff/cases?limit=50"));
  assert.ok(paths.includes("/api/v1/admin/rules"));
  assert.ok(paths.includes("/api/v1/admin/ai-quality/summary?hours=24"));
  assert.ok(paths.includes("/api/v1/audit/events?limit=25"));
  await assert.rejects(() => loadAdminOperations(staff), /역할/);
  await assert.rejects(() => loadStaffOperations(admin), /역할/);
});

test("감사권한이 없는 탐지 관리자는 감사 API를 호출하지 않고 권한 분리를 표시한다", async (t) => {
  const paths: string[] = [];
  t.mock.method(globalThis, "fetch", async (input) => { paths.push(String(input)); return response({ items: [] }); });
  const admin = { ...session, roles: ["DETECTION_ADMIN"], permissions: [] };
  const bundle = await loadAdminOperations(admin);
  assert.equal(bundle.auditAuthorized, false);
  assert.equal(bundle.audit.length, 0);
  assert.ok(!paths.some((path) => path.startsWith("/api/v1/audit/events")));
});

test("로그인 행원 사건 화면은 데모 capability가 아닌 운영 Bearer 큐와 상세를 조회한다", async (t) => {
  const paths: string[] = [];
  t.mock.method(globalThis, "fetch", async (input, init) => {
    const path = String(input); paths.push(path);
    assert.equal(new Headers(init?.headers).get("X-Demo-Capability"), null);
    if (path === "/api/v1/staff/cases?limit=100") return response({ items: [{ caseId: "case-1", customerId: "customer-1", reviewPriority: "HIGH", taskStatus: "PENDING", version: 1 }] });
    if (path.endsWith("/timeline") || path.endsWith("/notes") || path.endsWith("/follow-ups")) return response({ items: [] });
    if (path.endsWith("/evidence")) return response({ count: 1, items: [] });
    return response({ caseSummary: { caseId: "case-1" }, customerResponseCode: "NOT_SURE" });
  });
  const staff = { ...session, roles: ["PROTECTION_STAFF"] };
  const queue = await loadOperationalCaseQueue(staff);
  const bundle = await loadOperationalCaseBundle(staff, "case-1");
  assert.equal(queue[0]?.caseId, "case-1");
  assert.equal(bundle.timeline.length, 0);
  assert.equal(paths.length, 6);
  assert.ok(paths.every((path) => path.startsWith("/api/v1/staff/cases")));
});

test("미배정 사건의 검토 시작은 로그인 행원에게 최소권한 배정 후 상태를 전이한다", async (t) => {
  const calls: Array<{ path: string; method?: string; body?: string }> = [];
  t.mock.method(globalThis, "fetch", async (input, init) => {
    calls.push({ path: String(input), method: init?.method, body: init?.body?.toString() });
    return response({ version: calls.length + 1 });
  });
  const staff = { ...session, roles: ["PROTECTION_STAFF"] };
  await startOperationalCaseReview(staff, {
    caseId: "case-1", alertId: "alert-1", signalId: "signal-1", customerId: "customer-1",
    reviewPriority: "HIGH", taskStatus: "PENDING", version: 1, assignedTeam: null, assignedTo: null,
    createdAt: "2026-09-01T00:00:00Z", updatedAt: "2026-09-01T00:00:00Z",
  });
  assert.equal(calls.length, 2);
  assert.equal(calls[0]?.path, "/api/v1/staff/cases/case-1/assignment");
  assert.equal(calls[0]?.method, "PUT");
  assert.match(calls[0]?.body ?? "", /"assignedTo":"00000000-0000-0000-0000-000000000001"/);
  assert.equal(calls[1]?.path, "/api/v1/staff/cases/case-1/reviews");
  assert.match(calls[1]?.body ?? "", /"expectedVersion":2/);
});
