"use client";

import { useMemo, useState } from "react";
import {
  API_OPERATION_CATALOG,
  type ApiAudience,
  type ApiImplementation,
  type ApiOperationDefinition,
} from "../lib/generated/api-operation-catalog";

type PortalMode = "customer" | "staff" | "admin";
type StatusFilter = "ALL" | ApiImplementation;

const MODE: Record<PortalMode, { audiences: ApiAudience[]; eyebrow: string; title: string; description: string }> = {
  customer: {
    audiences: ["PUBLIC", "CUSTOMER"], eyebrow: "금융서비스", title: "필요한 금융생활을 한곳에서",
    description: "조회·동의·금융생활 지원을 목적별로 찾을 수 있습니다. 운영 인증이 필요한 서비스는 공개 데모에서 실행되지 않습니다.",
  },
  staff: {
    audiences: ["STAFF"], eyebrow: "행원 업무", title: "보호업무 운영 포털",
    description: "사건 검토, 고객별 접근권한, 승인된 근거와 후속관리를 업무 흐름에 맞춰 연결합니다.",
  },
  admin: {
    audiences: ["ADMIN"], eyebrow: "관리·준법", title: "통제와 운영을 한 화면에서",
    description: "감사, 컴플라이언스, 정책·모델 승인과 배치 상태를 역할 기반 권한으로 분리합니다.",
  },
};

export function ApiServiceCatalog({ mode }: { mode: PortalMode }) {
  const config = MODE[mode];
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<StatusFilter>("ALL");
  const all = useMemo(() => API_OPERATION_CATALOG.filter((item) => config.audiences.includes(item.audience)), [config.audiences]);
  const visible = useMemo(() => all.filter((item) => {
    const matchesStatus = status === "ALL" || item.implementation === status;
    const keyword = search.trim().toLowerCase();
    const matchesKeyword = !keyword || `${item.domain} ${item.purpose} ${item.path}`.toLowerCase().includes(keyword);
    return matchesStatus && matchesKeyword;
  }), [all, search, status]);
  const groups = useMemo(() => groupByDomain(visible), [visible]);
  const implemented = all.filter((item) => item.implementation === "IMPLEMENTED").length;
  const restricted = all.filter((item) => item.implementation === "IMPLEMENTED" && item.authorityMode === "BEARER").length;
  const inactive = all.length - implemented;

  return <div className={`service-catalog ${mode}`}>
    <section className="catalog-hero panel">
      <div><p className="label">{config.eyebrow}</p><h2>{config.title}</h2><p>{config.description}</p></div>
      <div className="catalog-score"><strong>{implemented}</strong><span>구현 서비스</span><small>전체 계약 {all.length}개</small></div>
    </section>

    <section className="catalog-stats" aria-label="API 연결 현황">
      <article><span className="stat-symbol connected">✓</span><p><strong>{implemented}</strong><small>클라이언트 계약 연결</small></p></article>
      <article><span className="stat-symbol locked">⌁</span><p><strong>{restricted}</strong><small>사설 인증 필요</small></p></article>
      <article><span className="stat-symbol disabled">–</span><p><strong>{inactive}</strong><small>계획·외부 참고</small></p></article>
    </section>

    <section className="catalog-toolbar panel">
      <label><span className="sr-only">서비스 검색</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="서비스 이름이나 업무를 검색하세요" /></label>
      <div role="group" aria-label="구현 상태 필터">
        {(["ALL", "IMPLEMENTED", "PLANNED", "REFERENCE_ONLY"] as const).map((value) => <button className={status === value ? "active" : ""} key={value} onClick={() => setStatus(value)}>{statusLabel(value)}</button>)}
      </div>
    </section>

    {groups.length === 0 ? <section className="panel catalog-empty">검색 조건에 맞는 서비스가 없습니다.</section> : <div className="catalog-domain-grid">{groups.map(([domain, items]) => <section className="panel domain-card" key={domain}>
      <header><span className="domain-icon" aria-hidden="true">{domainIcon(domain)}</span><div><h3>{domain}</h3><p>{domainDescription(domain)}</p></div><strong>{items.length}</strong></header>
      <div className="operation-list">{items.map((item) => <OperationRow item={item} key={item.key} />)}</div>
    </section>)}</div>}

    <section className="catalog-boundary">
      <span aria-hidden="true">i</span><p><strong>화면 연결 원칙</strong> 구현된 234개 operation은 공통 호출 계약으로 관리합니다. 공개 데모는 합성데이터와 capability 범위만 실행하며, Bearer 인증 업무는 사설 IdP 연결 전까지 잠깁니다. 미구현·외부 참고 API에는 실행 버튼을 만들지 않습니다.</p>
    </section>
  </div>;
}

function OperationRow({ item }: { item: ApiOperationDefinition }) {
  const status = operationStatus(item);
  return <details className="operation-row">
    <summary><span className={`operation-status ${status.tone}`} aria-hidden="true" /><div><strong>{item.purpose}</strong><small>{authorityLabel(item)}</small></div><em>{status.label}</em></summary>
    <div className="operation-detail"><code>{item.method} {item.path}</code><span>{item.priority} · {item.boundary}</span><p>{detailText(item)}</p></div>
  </details>;
}

function operationStatus(item: ApiOperationDefinition) {
  if (item.implementation === "REFERENCE_ONLY") return { tone: "disabled", label: "외부 참고" };
  if (item.implementation === "PLANNED") return { tone: "planned", label: "구현 예정" };
  if (item.authorityMode === "BEARER" || item.authorityMode === "STAFF_BOOTSTRAP") return { tone: "locked", label: "인증 필요" };
  return { tone: "connected", label: "데모 연결" };
}
function authorityLabel(item: ApiOperationDefinition) {
  return ({ PUBLIC: "공개 상태 확인", DEMO_CAPABILITY: "데모 세션 범위", STAFF_BOOTSTRAP: "직원 데모 권한", BEARER: "역할 기반 사설 인증" } as const)[item.authorityMode];
}
function detailText(item: ApiOperationDefinition) {
  if (item.implementation === "REFERENCE_ONLY") return "외부 금융기관의 공식 채널로 위임되는 참고 계약이며 ALZ's well이 실행하지 않습니다.";
  if (item.implementation === "PLANNED") return "명세에만 존재하는 후속 범위입니다. 구현과 검증 전에는 호출되지 않습니다.";
  if (item.authorityMode === "BEARER") return "백엔드 구현과 클라이언트 계약이 연결되어 있으며 운영 IdP의 역할 권한이 있어야 실행됩니다.";
  return "백엔드 구현과 공통 클라이언트 계약이 연결되어 공개 합성데이터 시나리오에서 검증할 수 있습니다.";
}
function statusLabel(value: StatusFilter) { return ({ ALL: "전체", IMPLEMENTED: "구현됨", PLANNED: "구현 예정", REFERENCE_ONLY: "외부 참고" } as const)[value]; }
function groupByDomain(items: readonly ApiOperationDefinition[]): Array<[string, ApiOperationDefinition[]]> {
  const map = new Map<string, ApiOperationDefinition[]>();
  for (const item of items) map.set(item.domain, [...(map.get(item.domain) ?? []), item]);
  return [...map.entries()];
}
function domainIcon(domain: string) {
  if (/인증|권한/.test(domain)) return "◈";
  if (/계좌|자산|거래/.test(domain)) return "₩";
  if (/투자|증권/.test(domain)) return "↗";
  if (/행원|사건/.test(domain)) return "◎";
  if (/감사|관리|운영/.test(domain)) return "⌘";
  if (/보험|보호/.test(domain)) return "+";
  if (/AI|기준선|신호/.test(domain)) return "✦";
  return "◇";
}
function domainDescription(domain: string) {
  if (/계좌|자산|거래/.test(domain)) return "조회와 설명 중심의 금융생활 정보";
  if (/행원|사건/.test(domain)) return "사람의 최종 판단을 돕는 보호업무";
  if (/감사|컴플라이언스|관리/.test(domain)) return "승인·감사·통제 근거를 남기는 업무";
  if (/AI|기준선|신호/.test(domain)) return "변화를 설명하고 확인을 돕는 기능";
  return "역할과 동의 범위 안에서 제공되는 서비스";
}
