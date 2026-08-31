"use client";

import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import { readDemoContext } from "../lib/demo-session";
import {
  issueStaffCapability,
  loadStaffCaseQueue,
  type StaffCaseContext,
  type StaffCaseQueueItem,
  type StaffCaseState,
  type StaffReviewPriority,
} from "../lib/staff-case-workflow";
import {
  selectStaffCaseQueueItems,
  staffCaseQueueMetrics,
  type StaffCaseQueueSort,
} from "../lib/staff-case-queue-view";

type QueuePhase = "authorizing" | "loading" | "ready" | "no-context" | "error";
type QueueConnection = { context: StaffCaseContext; staffCapability: string };
type StaffCaseQueueProps = { compact?: boolean };

const PAGE_SIZE = 20;
const STATE_OPTIONS: Array<{ value: StaffCaseState | "ALL"; label: string }> = [
  { value: "ALL", label: "전체 상태" },
  { value: "PENDING_BANK_REVIEW", label: "검토 대기" },
  { value: "IN_BANK_REVIEW", label: "검토 중" },
  { value: "FOLLOW_UP_REQUIRED", label: "후속 확인" },
  { value: "GUIDANCE_PLAN_APPROVED", label: "안내계획 승인" },
  { value: "CLOSED_FALSE_POSITIVE", label: "오탐 종결" },
];
const PRIORITY_OPTIONS: Array<{ value: StaffReviewPriority | "ALL"; label: string }> = [
  { value: "ALL", label: "전체 업무순위" },
  { value: "HIGH", label: "우선 검토" },
  { value: "MEDIUM", label: "일반 검토" },
  { value: "LOW", label: "낮은 순서" },
];

export function StaffCaseQueue({ compact = false }: StaffCaseQueueProps) {
  const [connection, setConnection] = useState<QueueConnection | null>(null);
  const [phase, setPhase] = useState<QueuePhase>("authorizing");
  const [items, setItems] = useState<StaffCaseQueueItem[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [error, setError] = useState("");
  const [query, setQuery] = useState("");
  const [stateFilter, setStateFilter] = useState<StaffCaseState | "ALL">("ALL");
  const [priorityFilter, setPriorityFilter] = useState<StaffReviewPriority | "ALL">("ALL");
  const [sort, setSort] = useState<StaffCaseQueueSort>("WORK_ORDER");
  const [connectionKey, setConnectionKey] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const queueGeneration = useRef(0);
  const loadMoreAbort = useRef<AbortController | null>(null);

  useEffect(() => {
    let cancelled = false;
    const context = readDemoContext();
    setConnection(null);
    setError("");
    if (!context?.sessionId || !context.demoRunId) {
      setPhase("no-context");
      return;
    }
    setPhase("authorizing");
    void issueStaffCapability(context.sessionId).then((staffCapability) => {
      if (!cancelled) setConnection({ context, staffCapability });
    }).catch((reason: unknown) => {
      if (cancelled) return;
      setError(messageOf(reason, "직원 접근 권한을 확인하지 못했습니다."));
      setPhase("error");
    });
    return () => { cancelled = true; };
  }, [connectionKey]);

  useEffect(() => {
    if (!connection) return;
    const generation = ++queueGeneration.current;
    loadMoreAbort.current?.abort();
    loadMoreAbort.current = null;
    setLoadingMore(false);
    const controller = new AbortController();
    let cancelled = false;
    setPhase("loading");
    setError("");
    setItems([]);
    setNextCursor(null);
    setHasMore(false);
    void loadStaffCaseQueue(connection.context, connection.staffCapability, {
      ...(stateFilter === "ALL" ? {} : { state: stateFilter }),
      ...(priorityFilter === "ALL" ? {} : { reviewPriority: priorityFilter }),
      limit: PAGE_SIZE,
      signal: controller.signal,
    }).then((queue) => {
      if (cancelled || controller.signal.aborted || generation !== queueGeneration.current) return;
      setItems(queue.items);
      setNextCursor(queue.nextCursor);
      setHasMore(queue.hasMore);
      setPhase("ready");
    }).catch((reason: unknown) => {
      if (cancelled || controller.signal.aborted || generation !== queueGeneration.current) return;
      setError(messageOf(reason, "사건 큐를 불러오지 못했습니다."));
      setPhase("error");
    });
    return () => {
      cancelled = true;
      controller.abort();
      if (queueGeneration.current === generation) queueGeneration.current += 1;
      loadMoreAbort.current?.abort();
      loadMoreAbort.current = null;
    };
  }, [connection, priorityFilter, reloadKey, stateFilter]);

  const visibleItems = useMemo(
    () => selectStaffCaseQueueItems(items, query, sort),
    [items, query, sort],
  );
  const metrics = useMemo(() => staffCaseQueueMetrics(items), [items]);

  async function loadMore() {
    if (!connection || !nextCursor || loadingMore) return;
    const generation = queueGeneration.current;
    loadMoreAbort.current?.abort();
    const controller = new AbortController();
    loadMoreAbort.current = controller;
    setLoadingMore(true);
    setError("");
    try {
      const queue = await loadStaffCaseQueue(connection.context, connection.staffCapability, {
        ...(stateFilter === "ALL" ? {} : { state: stateFilter }),
        ...(priorityFilter === "ALL" ? {} : { reviewPriority: priorityFilter }),
        cursor: nextCursor,
        limit: PAGE_SIZE,
        signal: controller.signal,
      });
      if (controller.signal.aborted || generation !== queueGeneration.current) return;
      setItems((current) => deduplicateCases([...current, ...queue.items]));
      setNextCursor(queue.nextCursor);
      setHasMore(queue.hasMore);
    } catch (reason) {
      if (controller.signal.aborted || generation !== queueGeneration.current) return;
      setError(messageOf(reason, "다음 사건을 불러오지 못했습니다."));
    } finally {
      if (loadMoreAbort.current === controller) loadMoreAbort.current = null;
      if (generation === queueGeneration.current) setLoadingMore(false);
    }
  }

  function refreshQueue() {
    if (connection) setReloadKey((current) => current + 1);
    else setConnectionKey((current) => current + 1);
  }

  function resetFilters() {
    setQuery("");
    setStateFilter("ALL");
    setPriorityFilter("ALL");
    setSort("WORK_ORDER");
  }

  if (compact) {
    return <CompactCaseQueue
      phase={phase}
      items={visibleItems}
      hasMore={hasMore}
      error={error}
      onRefresh={refreshQueue}
    />;
  }

  const loadedLabel = `${metrics.loaded}${hasMore ? "+" : ""}`;
  return <div className="staff-case-queue-dashboard">
    <section className="staff-queue-hero">
      <div><p>PROTECTION CASE DESK</p><h2>먼저 확인할 사건부터<br />차분하게 처리합니다.</h2><span>고객 응답과 합성 근거를 확인하고, 최종 결정은 행원이 기록합니다.</span></div>
      <div className="staff-queue-hero-status"><span>현재 조회 사건</span><strong>{loadedLabel}</strong><small>{hasMore ? "다음 페이지 있음" : "불러온 범위 완료"}</small></div>
    </section>

    <section className="staff-queue-metrics" aria-label="현재 조회 사건 요약">
      <QueueMetric tone="navy" label="현재 조회" value={loadedLabel} detail="필터가 적용된 조회 결과" />
      <QueueMetric tone="coral" label="우선 검토" value={String(metrics.highPriority)} detail="위험 확률이 아닌 업무 순서" />
      <QueueMetric tone="amber" label="검토 대기" value={String(metrics.waiting)} detail="행원 확인을 기다리는 사건" />
      <QueueMetric tone="green" label="처리 중" value={String(metrics.active)} detail="검토 중·후속 확인 사건" />
    </section>

    <section className="panel staff-queue-controls" aria-label="사건 검색과 필터">
      <div className="staff-queue-section-heading"><div><p className="label">업무 큐 조회</p><h2>검색·필터</h2></div><button type="button" className="queue-refresh-button" onClick={refreshQueue} disabled={phase === "authorizing" || phase === "loading"}>새로고침</button></div>
      <div className="staff-queue-filter-grid">
        <label className="staff-queue-search"><span>현재 조회 결과 검색</span><input type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="고객번호, 사건번호, 변화 신호" /></label>
        <label><span>사건 상태</span><select value={stateFilter} onChange={(event) => setStateFilter(event.target.value as StaffCaseState | "ALL")}>{STATE_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>
        <label><span>업무 우선순위</span><select value={priorityFilter} onChange={(event) => setPriorityFilter(event.target.value as StaffReviewPriority | "ALL")}>{PRIORITY_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>
        <label><span>현재 결과 정렬</span><select value={sort} onChange={(event) => setSort(event.target.value as StaffCaseQueueSort)}><option value="WORK_ORDER">업무 기본순</option><option value="OLDEST">접수 오래된순</option><option value="NEWEST">접수 최신순</option></select></label>
      </div>
      <div className="staff-queue-boundary"><span>i</span><p><strong>업무 우선순위는 위험도·치매·사기 확률이 아닙니다.</strong> 서버의 상태·순위 필터와 불투명 cursor를 그대로 사용하며, 검색은 현재 불러온 합성 사건에만 적용됩니다.</p></div>
    </section>

    <section className="panel staff-queue-list-panel" aria-live="polite">
      <div className="staff-queue-section-heading"><div><p className="label">현재 담당 큐</p><h2>보호업무 사건</h2></div><span className="queue-result-count">전체 {loadedLabel}건 중 {visibleItems.length}건 표시</span></div>
      <QueueContents phase={phase} items={visibleItems} hasLoadedItems={items.length > 0} error={error} onRefresh={refreshQueue} onReset={resetFilters} />
      {phase === "ready" && hasMore && <button type="button" className="queue-load-more" onClick={() => void loadMore()} disabled={loadingMore}>{loadingMore ? "불러오는 중…" : "다음 사건 20건 불러오기"}</button>}
      {error && phase === "ready" && <p className="api-error" role="alert">{error}</p>}
    </section>
  </div>;
}

function QueueMetric({ tone, label, value, detail }: { tone: string; label: string; value: string; detail: string }) {
  return <article className="panel"><span className={`queue-metric-symbol ${tone}`} aria-hidden="true" /><div><small>{label}</small><strong>{value}</strong><p>{detail}</p></div></article>;
}

function QueueContents({ phase, items, hasLoadedItems, error, onRefresh, onReset }: {
  phase: QueuePhase;
  items: StaffCaseQueueItem[];
  hasLoadedItems: boolean;
  error: string;
  onRefresh: () => void;
  onReset: () => void;
}) {
  if (phase === "authorizing") return <QueueFeedback title="직원 접근 권한을 확인하고 있습니다." detail="브라우저에는 bootstrap 비밀값을 저장하지 않습니다." />;
  if (phase === "loading") return <QueueFeedback title="사건 큐를 불러오고 있습니다." detail="현재 데모 run의 합성 사건만 조회합니다." />;
  if (phase === "no-context") return <QueueFeedback title="연결된 고객 데모가 없습니다." detail="먼저 고객 화면에서 주의 시나리오를 실행하면 행원 사건이 생성됩니다." action={<Link href="/demo">고객 시나리오 시작</Link>} />;
  if (phase === "error") return <QueueFeedback title="사건 큐를 열 수 없습니다." detail={error} action={<button type="button" onClick={onRefresh}>다시 시도</button>} />;
  if (!hasLoadedItems) return <QueueFeedback title="현재 조건에 해당하는 사건이 없습니다." detail="필터를 바꾸거나 새 고객 시나리오를 실행해 주세요." action={<button type="button" onClick={onReset}>필터 초기화</button>} />;
  if (!items.length) return <QueueFeedback title="검색 결과가 없습니다." detail="현재 불러온 사건에서 다른 고객번호나 변화 신호를 검색해 보세요." action={<button type="button" onClick={onReset}>검색·필터 초기화</button>} />;
  return <div className="staff-queue-table-wrap"><table className="staff-queue-table">
    <caption className="visually-hidden">현재 데모 세션의 행원 보호업무 사건 목록</caption>
    <thead><tr><th scope="col">순위</th><th scope="col">고객·사건</th><th scope="col">변화 신호</th><th scope="col">고객 확인</th><th scope="col">상태</th><th scope="col">접수</th><th scope="col"><span className="visually-hidden">상세</span></th></tr></thead>
    <tbody>{items.map((item) => <tr key={item.caseId}>
      <td data-label="순위"><span className={`queue-priority priority-${item.reviewPriority.toLocaleLowerCase()}`}>{priorityLabel(item.reviewPriority)}</span></td>
      <td data-label="고객·사건"><strong title={item.customerId}>{compactIdentifier(item.customerId)}</strong><small title={item.caseId}>{compactIdentifier(item.caseId)}</small></td>
      <td data-label="변화 신호"><p>{item.summary}</p><div className="queue-reason-list">{item.reasonCodes.map((reason) => <span key={reason}>{reasonLabel(reason)}</span>)}</div></td>
      <td data-label="고객 확인"><span className="queue-response">{responseLabel(item.customerResponseCode)}</span></td>
      <td data-label="상태"><span className={`queue-state state-${item.state.toLocaleLowerCase()}`}>{stateLabel(item.state)}</span></td>
      <td data-label="접수"><time dateTime={item.createdAt}>{dateTime(item.createdAt)}</time></td>
      <td><Link className="queue-open-case" href={`/staff/cases/${encodeURIComponent(item.caseId)}`} aria-label={`${item.customerId} 사건 상세 보기`}>사건 열기<span aria-hidden="true">→</span></Link></td>
    </tr>)}</tbody>
  </table></div>;
}

function QueueFeedback({ title, detail, action }: { title: string; detail: string; action?: React.ReactNode }) {
  return <div className="staff-queue-feedback"><span aria-hidden="true">+</span><h3>{title}</h3><p>{detail}</p>{action && <div>{action}</div>}</div>;
}

function CompactCaseQueue({ phase, items, hasMore, error, onRefresh }: {
  phase: QueuePhase;
  items: StaffCaseQueueItem[];
  hasMore: boolean;
  error: string;
  onRefresh: () => void;
}) {
  return <section className="panel compact-case-queue" aria-live="polite">
    <div className="staff-queue-section-heading"><div><p className="label">현재 데모 세션</p><h2>보호업무 사건</h2></div><span className="queue-result-count">{items.length}{hasMore ? "+" : ""}건</span></div>
    {phase !== "ready" ? <QueueContents phase={phase} items={[]} hasLoadedItems={false} error={error} onRefresh={onRefresh} onReset={onRefresh} /> : items.length ? <div className="compact-case-list">{items.slice(0, 3).map((item) => <Link key={item.caseId} href={`/staff/cases/${encodeURIComponent(item.caseId)}`}><span className={`queue-priority priority-${item.reviewPriority.toLocaleLowerCase()}`}>{priorityLabel(item.reviewPriority)}</span><div><strong>{stateLabel(item.state)}</strong><small>{item.summary}</small></div><b aria-hidden="true">→</b></Link>)}</div> : <QueueFeedback title="현재 검토할 사건이 없습니다." detail="고객 주의 시나리오를 실행하면 이곳에 연결됩니다." />}
    <Link className="compact-queue-footer" href="/staff/cases">검색·필터가 있는 전체 사건 큐 열기 →</Link>
  </section>;
}

function deduplicateCases(items: StaffCaseQueueItem[]): StaffCaseQueueItem[] {
  return [...new Map(items.map((item) => [item.caseId, item])).values()];
}

function compactIdentifier(value: string): string {
  return value.length > 20 ? `${value.slice(0, 10)}…${value.slice(-6)}` : value;
}

function priorityLabel(value: string): string {
  return ({ HIGH: "우선", MEDIUM: "일반", LOW: "낮음" } as Record<string, string>)[value] ?? value;
}

function stateLabel(value: string): string {
  return ({ PENDING_BANK_REVIEW: "검토 대기", IN_BANK_REVIEW: "검토 중", FOLLOW_UP_REQUIRED: "후속 확인", GUIDANCE_PLAN_APPROVED: "안내 승인", CLOSED_FALSE_POSITIVE: "오탐 종결" } as Record<string, string>)[value] ?? value.replaceAll("_", " ");
}

function responseLabel(value: string): string {
  return ({ UNABLE_TO_CONFIRM: "확인 어려움", NOT_SURE: "잘 모르겠어요", DEFERRED: "나중에 확인", ACKNOWLEDGED: "알고 있는 활동" } as Record<string, string>)[value] ?? value.replaceAll("_", " ");
}

function reasonLabel(value: string): string {
  return ({ MISSED_RECURRING: "정기납부 누락", DUPLICATE_TRANSFER: "중복송금", REPEATED_CONFIRMATION: "반복확인", NEW_PAYEE: "새 수취인", UNUSUAL_TIME: "새 시간대" } as Record<string, string>)[value] ?? value.replaceAll("_", " ");
}

function dateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("ko-KR", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }).format(date);
}

function messageOf(reason: unknown, fallback: string): string {
  return reason instanceof Error && reason.message ? reason.message : fallback;
}
