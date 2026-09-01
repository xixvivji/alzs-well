import { AppShell } from "../../../components/AppShell";
import { StaffCaseWorkspace } from "../../../components/StaffCaseWorkspace";
export const dynamic = "force-dynamic";
export default function CasesPage() { return <AppShell mode="staff" staffRole="protection" title="보호업무 사건"><section className="toolbar"><div className="status-chip">운영형 합성데이터</div><div className="status-chip">역할·목적 기반 보호</div><div className="status-chip">자동 금융조치 없음</div></section><StaffCaseWorkspace /></AppShell>; }
