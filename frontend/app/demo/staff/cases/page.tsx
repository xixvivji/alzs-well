import { AppShell } from "../../../../components/AppShell";
import { StaffCaseQueue } from "../../../../components/StaffCaseQueue";

export default function DemoCasesPage() {
  return <AppShell mode="staff" staffRole="protection" demoStaff title="시연 사건 검토"><StaffCaseQueue /></AppShell>;
}
