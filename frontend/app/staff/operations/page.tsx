import Link from "next/link";
import { ApiServiceCatalog } from "../../../components/ApiServiceCatalog";
import { AppShell } from "../../../components/AppShell";
import { StaffCaseQueue } from "../../../components/StaffCaseQueue";

export default function StaffOperationsPage() {
  return <AppShell mode="staff" title="보호업무 운영"><div className="operations-portal">
    <section className="staff-operations-hero"><div><p>OPERATION WORKSPACE</p><h2>고객의 확인 요청을<br />사람이 끝까지 처리합니다.</h2><span>사건 배정 · 근거 확인 · 내부 메모 · 후속관리 · 안내계획</span></div><div><strong>자동 금융조치 0건</strong><small>합성데이터 · 외부 연락 없음</small><Link href="/staff/cases">전체 사건 큐 열기 →</Link></div></section>
    <section className="operations-stage-grid"><article className="panel"><span>01</span><strong>고객 맥락 확인</strong><p>알고 있음·확인 어려움·잘 모르겠어요 응답을 그대로 보존합니다.</p></article><article className="panel"><span>02</span><strong>불변 근거 검토</strong><p>기준선·거래·감사이력과 승인된 citation을 함께 확인합니다.</p></article><article className="panel"><span>03</span><strong>사람 최종 결정</strong><p>안내계획 또는 오탐 종결 근거를 행원이 직접 기록합니다.</p></article></section>
    <div className="operations-heading"><div><p className="label">실제 사건 API 연결</p><h2>현재 데모 세션 사건</h2></div><span className="status-chip">고객→행원 폐루프</span></div>
    <StaffCaseQueue />
    <section className="operations-catalog-section"><div className="operations-heading"><div><p className="label">권한별 운영 계약</p><h2>추가 행원 API</h2></div><span className="status-chip">Bearer·목적기반 접근</span></div><ApiServiceCatalog mode="staff" /></section>
  </div></AppShell>;
}
