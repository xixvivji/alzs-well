import { AppShell } from "../../../components/AppShell";
import { StaffCaseQueue } from "../../../components/StaffCaseQueue";
import { requireChatGPTUser } from "../../chatgpt-auth";
export const dynamic = "force-dynamic";
export default async function CasesPage() { await requireChatGPTUser("/staff/cases"); return <AppShell mode="staff" title="보호업무 사건"><section className="toolbar"><div className="status-chip">전체 상태</div><div className="status-chip">우선순위</div></section><StaffCaseQueue /></AppShell>; }
