import type { StaffCaseQueueItem } from "./staff-case-workflow";

export type StaffCaseQueueSort = "WORK_ORDER" | "NEWEST" | "OLDEST";

export type StaffCaseQueueMetrics = {
  loaded: number;
  highPriority: number;
  waiting: number;
  active: number;
};

const PRIORITY_ORDER: Record<string, number> = { HIGH: 1, MEDIUM: 2, LOW: 3 };

export function selectStaffCaseQueueItems(
  items: StaffCaseQueueItem[],
  query: string,
  sort: StaffCaseQueueSort,
): StaffCaseQueueItem[] {
  const normalized = query.trim().toLocaleLowerCase("ko-KR");
  const selected = normalized
    ? items.filter((item) => searchableText(item).includes(normalized))
    : [...items];

  return selected.sort((left, right) => {
    if (sort === "NEWEST") return compareDate(right.createdAt, left.createdAt) || left.caseId.localeCompare(right.caseId);
    if (sort === "OLDEST") return compareDate(left.createdAt, right.createdAt) || left.caseId.localeCompare(right.caseId);
    return (PRIORITY_ORDER[left.reviewPriority] ?? 99) - (PRIORITY_ORDER[right.reviewPriority] ?? 99)
      || compareDate(left.createdAt, right.createdAt)
      || left.caseId.localeCompare(right.caseId);
  });
}

export function staffCaseQueueMetrics(items: StaffCaseQueueItem[]): StaffCaseQueueMetrics {
  return {
    loaded: items.length,
    highPriority: items.filter((item) => item.reviewPriority === "HIGH").length,
    waiting: items.filter((item) => item.state === "PENDING_BANK_REVIEW").length,
    active: items.filter((item) => item.state === "IN_BANK_REVIEW" || item.state === "FOLLOW_UP_REQUIRED").length,
  };
}

function searchableText(item: StaffCaseQueueItem): string {
  return [
    item.caseId,
    item.customerId,
    item.alertId,
    item.summary,
    item.state,
    item.reviewPriority,
    item.customerResponseCode,
    ...item.reasonCodes,
  ].join(" ").toLocaleLowerCase("ko-KR");
}

function compareDate(left: string, right: string): number {
  const leftTime = Date.parse(left);
  const rightTime = Date.parse(right);
  if (Number.isNaN(leftTime) || Number.isNaN(rightTime)) return left.localeCompare(right);
  return leftTime - rightTime;
}
