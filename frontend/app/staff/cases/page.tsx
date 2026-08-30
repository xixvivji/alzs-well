import { AppShell } from "../../../components/AppShell";
import { StaffCaseQueue } from "../../../components/StaffCaseQueue";
export const dynamic = "force-dynamic";
export default function CasesPage() { return <AppShell mode="staff" title="보호업무 사건"><section className="toolbar"><div className="status-chip">합성데이터 공개 시연</div><div className="status-chip">고객 데모 세션 연결</div></section><StaffCaseQueue /></AppShell>; }
