import { AppShell } from "../../../components/AppShell";
import { StaffCaseWorkspace } from "../../../components/StaffCaseWorkspace";
export const dynamic = "force-dynamic";
export default function CasesPage() { return <AppShell mode="staff" staffRole="protection" title="사건 검토"><StaffCaseWorkspace /></AppShell>; }
