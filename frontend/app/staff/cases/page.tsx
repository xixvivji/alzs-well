import { AppShell } from "../../../components/AppShell";
import { StaffCaseQueue } from "../../../components/StaffCaseQueue";
export const dynamic = "force-dynamic";
export default function CasesPage() { return <AppShell mode="staff" title="보호업무 사건"><section className="toolbar"><div className="status-chip">합성데이터 공개 시연</div><div className="status-chip">직원 capability 보호</div><div className="status-chip">자동 금융조치 없음</div></section><StaffCaseQueue /></AppShell>; }
